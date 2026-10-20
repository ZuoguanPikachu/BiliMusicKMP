package com.zuoguan.bilimusickmp.vm

import androidx.compose.material3.SnackbarDuration
import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.services.SongMetadataService
import com.zuoguan.bilimusickmp.services.SongRepositoryService
import com.zuoguan.bilimusickmp.utils.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 歌曲编辑会话（两个平台共用）。
 *
 * 设计要点：
 * - 编辑中的内容放在 [SongEditorState.draft]，是输入 [Song] 的 **copy**。
 *   因为 [Song] 不可变，草稿与仓库列表里的实例天然隔离，取消编辑不会污染原对象。
 * - 打开方式区分意图而不是靠来源页面字符串：新建走 [openForCreate]（按需补全元数据），
 *   编辑已有歌曲走 [openForEdit]。
 * - 歌单以歌曲 id 为主键，同一首歌只可能有一条：[openForCreate] 命中已有条目时不会
 *   再新建，而是打开本地那条并让界面标注，避免搜索结果的原始字段覆盖用户调好的信息。
 * - 两个平台共用同一个状态源，差异只在于用整页还是用对话框承载。
 */
class SongEditorViewModel(
    private val songRepository: SongRepositoryService,
    private val songMetadataService: SongMetadataService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(SongEditorState())
    val uiState: StateFlow<SongEditorState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /** 一次性 UI 事件（补全或保存失败的提示）。 */
    val uiEvents = _uiEvents.receiveAsFlow()

    /** 打开会话时可能正在做的元数据补全，关闭/重新打开时要能取消。 */
    private var prepareJob: Job? = null

    init {
        scope.launch {
            songRepository.allTags.collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
    }

    // ---------- 打开 / 关闭 ----------

    /**
     * 从搜索结果添加歌曲。
     *
     * B 站视频的标题是原始投稿标题（"【4K60FPS】陈奕迅《人来人往》…"），
     * 需要先用 LLM 提取歌名/歌手，再据此补出酷狗的歌词与封面 ID，同时取回 cid。
     * 网易云/酷狗/QQ 音乐的结果字段已经直接可用，不需要这一步。
     *
     * 歌单里已经有同一首歌（歌曲 id 相同）时不再新建：直接打开本地已保存的那一条，
     * 并置上 [SongEditorState.isAddingExistingSong]，由界面标注「歌曲已在歌单中」。
     * 这样用户之前调好的歌名、标签、歌词/封面来源不会被搜索结果的原始字段冲掉，
     * 也不会在这里产生第二条记录（歌单以歌曲 id 为主键，本来就不可能重复）。
     */
    fun openForCreate(song: Song) {
        val existing = songRepository.getSongById(song.id)
        if (existing != null) {
            startSession(existing, isCreating = true, isUpdate = true, prepare = null)
            return
        }
        startSession(song, isCreating = true, isUpdate = false, prepare = resolveIfNeeded(song))
    }

    /**
     * 新建时是否要补全：只有 B 站的搜索结果需要。
     *
     * 网易云/酷狗/QQ 音乐的搜索结果本身就是完整的歌曲（歌名、歌手、封面、歌词 ID 都已就位），
     * 再走一遍 LLM 抽取与平台反查只会把它们悄悄改写成别的匹配结果，因此返回 null 跳过。
     */
    private fun resolveIfNeeded(song: Song): (suspend () -> Song)? =
        if (song.audioSource == AudioSource.BILI_BILI) {
            { songMetadataService.resolve(song) }
        } else {
            null
        }

    /** 编辑歌单里已有的歌曲：字段都是现成的，不需要补全。 */
    fun openForEdit(song: Song) {
        startSession(song, isCreating = false, isUpdate = true, prepare = null)
    }

    private fun startSession(
        song: Song,
        isCreating: Boolean,
        isUpdate: Boolean,
        prepare: (suspend () -> Song)?,
    ) {
        // 切换到新的编辑对象，上一个会话的补全结果已无意义
        prepareJob?.cancel()
        _uiState.update {
            it.copy(
                isOpen = true,
                isCreating = isCreating,
                isUpdate = isUpdate,
                // 加载态只描述"正在等补全结果"，因此与补全任务同生共死：没有任务就不该转圈
                isLoading = prepare != null,
                draft = song,
            )
        }
        if (prepare == null) return

        prepareJob = scope.launch {
            val result = try {
                prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = "自动补全歌曲信息失败：${e.message ?: "未知错误"}",
                        duration = SnackbarDuration.Long,
                    )
                )
                song // 补全失败就退回原字段，用户仍可手动填写
            }
            _uiState.update { state ->
                // 会话可能已经被关闭/替换，避免把旧结果写回去
                if (state.isOpen && state.isCreating) {
                    state.copy(draft = result, isLoading = false)
                } else {
                    state
                }
            }
        }
    }

    /** 关闭会话，并取消进行中的元数据补全。 */
    fun dismiss() {
        prepareJob?.cancel()
        _uiState.update { SongEditorState(allTags = it.allTags) }
    }

    // ---------- 编辑草稿 ----------

    /** 统一入口：`edit { it.copy(title = newTitle) }`。 */
    fun edit(transform: (Song) -> Song) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = transform(draft))
        }
    }

    /**
     * 用户切换歌词来源后，按当前标题/歌手重新查一次歌词 ID。
     *
     * 歌词 ID 是来源内部的标识，换了来源后原 ID 会失效，所以要重新查询。
     */
    fun resolveLyricId() {
        val draft = _uiState.value.draft ?: return
        if (draft.title.isEmpty() || draft.author.isEmpty()) return

        scope.launch {
            val id = songMetadataService.resolveSongId(draft.lyricSource, draft.title, draft.author)
            _uiState.update { state ->
                state.draft?.takeIf { it.lyricSource == draft.lyricSource }?.let {
                    state.copy(draft = it.copy(lyricId = id))
                } ?: state
            }
        }
    }

    /**
     * 用户切换封面来源后，重新查封面 ID 并取回封面地址。
     *
     * 封面 ID 同样是来源内部的标识，换来源后需要重新查询。
     */
    fun resolveCover() {
        val draft = _uiState.value.draft ?: return
        if (draft.coverSource == CoverSource.BILI_BILI || draft.coverSource == CoverSource.NONE) return
        if (draft.title.isEmpty() || draft.author.isEmpty()) return

        val source = draft.coverSource
        scope.launch {
            val coverId = songMetadataService.resolveSongId(source, draft.title, draft.author)
            val pic = if (coverId.isEmpty()) "" else songMetadataService.resolvePic(source, coverId)
            _uiState.update { state ->
                state.draft?.takeIf { it.coverSource == source }?.let {
                    state.copy(draft = it.copy(coverId = coverId, pic = pic))
                } ?: state
            }
        }
    }

    // ---------- 保存 ----------

    /** 保存并关闭会话；调用方（整页/对话框外壳）负责各自的关闭动作。 */
    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        persist(draft.forSave(), updated = state.isUpdate)
    }

    /** 未填标签的歌曲归入 Default 标签。 */
    private fun Song.forSave(): Song = copy(tags = tags.ifEmpty { listOf("Default") })

    /**
     * 落库并关闭会话。
     *
     * 写库是异步的，失败时保留会话与草稿，用户可以直接重试。
     *
     * @param updated true 表示写的是歌单里已有的条目（覆盖更新），用于提示文案。
     */
    private fun persist(song: Song, updated: Boolean) {
        scope.launch {
            try {
                songRepository.saveSong(song)
                _uiState.update { SongEditorState(allTags = it.allTags) }
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = if (updated) {
                            "已更新歌单中的「${song.title}」"
                        } else {
                            "已添加到歌单"
                        }
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvents.send(
                    UiEvent.ShowSnackBar(
                        message = "保存失败：${e.message ?: "未知错误"}",
                        duration = SnackbarDuration.Long,
                    )
                )
            }
        }
    }
}

data class SongEditorState(
    /** 是否有正在进行的编辑会话。 */
    val isOpen: Boolean = false,
    /** true = 从搜索结果进入的添加流程，false = 从歌单进入的编辑流程。 */
    val isCreating: Boolean = false,
    /** 正在等待元数据补全。 */
    val isLoading: Boolean = false,
    /** 本次保存的是歌单里已有的记录（更新），而不是新增。 */
    val isUpdate: Boolean = false,
    /** 编辑中的副本；为 null 表示还没有内容可编辑。 */
    val draft: Song? = null,
    val allTags: List<String> = emptyList(),
) {
    /**
     * 点「添加」却命中了歌单里已有的歌曲：界面要标注「歌曲已在歌单中」。
     *
     * 从歌单进去编辑同一首歌（[isUpdate] 为 true 但 [isCreating] 为 false）不需要标注，
     * 用户本来就知道自己在编辑哪一条。
     */
    val isAddingExistingSong: Boolean get() = isCreating && isUpdate
}
