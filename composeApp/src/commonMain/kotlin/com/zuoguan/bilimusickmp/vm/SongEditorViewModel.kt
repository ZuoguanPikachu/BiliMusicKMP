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
 * 取代了原先 Android 独有的 `SongEditService` + `SongEditPageViewModel`：
 * 前者只是一个"跨导航边界传参数"的信箱，后者承载的补全/保存逻辑其实与平台无关。
 *
 * 设计要点：
 * - 编辑中的内容放在 [SongEditorState.draft]，是输入 [Song] 的 **copy**。
 *   因为 [Song] 不可变，草稿与仓库列表里的实例天然隔离，取消编辑不会污染原对象。
 * - 打开方式区分意图而不是靠来源页面字符串：新建走 [openForCreate]（按需补全元数据），
 *   编辑已有歌曲走 [openForEdit]。
 * - 两个平台只共用这一个状态源，差异仅限于"用整页还是用对话框承载"。
 */
class SongEditorViewModel(
    private val songRepository: SongRepositoryService,
    private val songMetadataService: SongMetadataService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(SongEditorState())
    val uiState: StateFlow<SongEditorState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
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
     * 从搜索结果新建歌曲。
     *
     * B 站视频的标题是原始投稿标题（"【4K60FPS】陈奕迅《人来人往》…"），
     * 需要先用 LLM 提取歌名/歌手，再据此补出酷狗的歌词与封面 ID，同时取回 cid。
     * 网易云/酷狗的结果字段已经直接可用，不需要这一步。
     */
    fun openForCreate(song: Song) {
        val needsResolve = song.audioSource == AudioSource.BILI_BILI
        startSession(song, isCreating = true, isLoading = needsResolve) {
            songMetadataService.resolve(song)
        }
    }

    /** 编辑歌单里已有的歌曲：字段都是现成的，不需要补全。 */
    fun openForEdit(song: Song) {
        startSession(song, isCreating = false, isLoading = false, prepare = null)
    }

    private fun startSession(
        song: Song,
        isCreating: Boolean,
        isLoading: Boolean,
        prepare: (suspend () -> Song)?,
    ) {
        prepareJob?.cancel()
        _uiState.update {
            it.copy(
                isOpen = true,
                isCreating = isCreating,
                isLoading = isLoading,
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

    /** 用户切换歌词来源后，按当前标题/歌手重新查一次歌词 ID。 */
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

    /** 用户切换封面来源后，重新查封面 ID 并取回封面地址。 */
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
        val draft = _uiState.value.draft ?: return
        // 与旧行为一致：没有标签的歌归入 Default
        val toSave = draft.copy(tags = draft.tags.ifEmpty { listOf("Default") })

        scope.launch {
            try {
                songRepository.saveSong(toSave)
                _uiState.update { SongEditorState(allTags = it.allTags) }
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
    /** true = 新建（来自搜索结果），false = 编辑已有歌曲。 */
    val isCreating: Boolean = false,
    /** 正在等待元数据补全。 */
    val isLoading: Boolean = false,
    /** 编辑中的副本；为 null 表示还没有内容可编辑。 */
    val draft: Song? = null,
    val allTags: List<String> = emptyList(),
)
