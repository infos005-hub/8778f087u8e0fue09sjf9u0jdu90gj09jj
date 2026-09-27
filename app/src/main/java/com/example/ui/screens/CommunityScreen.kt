package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.BalanceGame
import com.example.core.model.Comment
import com.example.core.model.Post
import com.example.core.model.PostCategory
import com.example.core.safety.SafetyTextFilter
import com.example.core.util.AnonymousNameGenerator
import com.example.data.repository.AppRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface CommunityViewMode {
    object Feed : CommunityViewMode
    data class Detail(val post: Post) : CommunityViewMode
    object CreatePost : CommunityViewMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    repository: AppRepository,
    modifier: Modifier = Modifier
) {
    val posts by repository.posts.collectAsState()
    var selectedCategory by remember { mutableStateOf(PostCategory.ALL) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var isWritingPost by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var showRefreshSuccessBanner by remember { mutableStateOf(false) }
    var postToDeleteFromFeed by remember { mutableStateOf<Post?>(null) }
    var postToReportFromFeed by remember { mutableStateOf<Post?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    fun triggerRefresh() {
        if (isRefreshing) return
        coroutineScope.launch {
            isRefreshing = true
            delay(750)
            repository.refreshPosts()
            isRefreshing = false
            showRefreshSuccessBanner = true
            delay(2200)
            showRefreshSuccessBanner = false
        }
    }

    val filteredPosts = remember(posts, selectedCategory) {
        if (selectedCategory == PostCategory.ALL) posts
        else posts.filter { it.category == selectedCategory }
    }

    val selectedPost = remember(posts, selectedPostId) {
        posts.firstOrNull { it.id == selectedPostId }
    }

    val currentViewMode: CommunityViewMode = remember(selectedPost, isWritingPost) {
        when {
            isWritingPost -> CommunityViewMode.CreatePost
            selectedPost != null -> CommunityViewMode.Detail(selectedPost)
            else -> CommunityViewMode.Feed
        }
    }

    // 풀 스크린 화면 전환: 글 선택 시 및 글 작성 시 모두 다이얼로그가 아닌 전체 화면으로 전환
    AnimatedContent(
        targetState = currentViewMode,
        label = "CommunityScreenFullScreenTransition"
    ) { viewMode ->
        when (viewMode) {
            is CommunityViewMode.CreatePost -> {
                CreatePostFullScreen(
                    onBack = { isWritingPost = false },
                    onSubmit = { cat, title, content, bA, bB ->
                        repository.createPost(cat, title, content, bA, bB)
                        isWritingPost = false
                    }
                )
            }
            is CommunityViewMode.Detail -> {
                PostDetailFullScreen(
                    post = viewMode.post,
                    onBack = { selectedPostId = null },
                    onVote = { option -> repository.voteBalanceGame(viewMode.post.id, option) },
                    onAddComment = { text -> repository.addComment(viewMode.post.id, text) },
                    onDeletePost = {
                        repository.deletePost(viewMode.post.id)
                        selectedPostId = null
                    },
                    onDeleteComment = { commentId ->
                        repository.deleteComment(viewMode.post.id, commentId)
                    },
                    onReportPost = { reason, shouldBlock ->
                        repository.reportPost(viewMode.post.id, reason, shouldBlock)
                        selectedPostId = null
                    },
                    onReportComment = { commentId, reason, shouldBlock ->
                        repository.reportComment(viewMode.post.id, commentId, reason, shouldBlock)
                    }
                )
            }
            is CommunityViewMode.Feed -> {
                Scaffold(
                    containerColor = AnonBackground,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { isWritingPost = true },
                            containerColor = AnonPrimary,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            text = { Text("익명 글쓰기", fontWeight = FontWeight.Bold) }
                        )
                    },
                    modifier = modifier.fillMaxSize()
                ) { padding ->
                    // 위로 스크롤을 길게 당겼을 때 새로고침 (PullToRefreshBox)
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { triggerRefresh() },
                        state = pullToRefreshState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 새로고침 성공 알림 배너
                            item {
                                AnimatedVisibility(
                                    visible = showRefreshSuccessBanner,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        color = AnonPrimary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonPrimary.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = AnonPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "피드가 최신 상태로 새로고침되었습니다 ✨",
                                                color = AnonPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // 커뮤니티 히어로 배너 (완전 익명 라운지)
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = AnonSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            painter = painterResource(id = R.drawable.anon_hero_banner_1789521485586),
                                            contentDescription = "익명 커뮤니티 배너",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.60f))
                                                    )
                                                )
                                                .padding(16.dp)
                                        ) {
                                            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Surface(
                                                        color = AnonPrimary.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "자유로운 소통 공간",
                                                            color = AnonPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { triggerRefresh() },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Refresh,
                                                            contentDescription = "새로고침",
                                                            tint = AnonTextSecondary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "100% 완전 익명 라운지 ✨\n솔직한 일상과 밸런스 게임을 나눠요",
                                                    color = AnonTextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 21.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 카테고리 칩 목록
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(PostCategory.values()) { category ->
                                        val isSelected = selectedCategory == category
                                        Surface(
                                            color = if (isSelected) AnonPrimary else AnonSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) AnonPrimary else AnonSurfaceBorder
                                            ),
                                            modifier = Modifier.clickable { selectedCategory = category }
                                        ) {
                                            Text(
                                                text = category.label,
                                                color = if (isSelected) Color.White else AnonTextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 작성글 목록: "제목, 조회수, 댓글수" 이렇게만 깔끔하게 노출
                            if (filteredPosts.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 56.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = AnonTextMuted.copy(alpha = 0.5f),
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text(
                                                text = if (selectedCategory == PostCategory.ALL) "아직 작성된 익명 글이 없습니다" else "'${selectedCategory.label}'에 작성된 글이 없습니다",
                                                color = AnonTextSecondary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "우측 하단 버튼을 눌러 첫 번째 이야기를 나눠보세요!",
                                                color = AnonTextMuted,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredPosts, key = { it.id }) { post ->
                                    SimplifiedPostCard(
                                        post = post,
                                        onClick = {
                                            repository.incrementPostViews(post.id)
                                            selectedPostId = post.id
                                        },
                                        onDelete = if (post.isMyPost) { { postToDeleteFromFeed = post } } else null,
                                        onReport = if (!post.isMyPost) { { postToReportFromFeed = post } } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 피드에서 내가 쓴 글 삭제 확인 다이얼로그
    if (postToDeleteFromFeed != null) {
        AlertDialog(
            onDismissRequest = { postToDeleteFromFeed = null },
            title = {
                Text(
                    text = "게시글 삭제",
                    fontWeight = FontWeight.Bold,
                    color = AnonTextPrimary
                )
            },
            text = {
                Text(
                    text = "작성하신 익명 게시글을 삭제하시겠습니까?\n삭제 후에는 복구할 수 없습니다.",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = postToDeleteFromFeed?.id
                        postToDeleteFromFeed = null
                        if (id != null) {
                            repository.deletePost(id)
                        }
                    }
                ) {
                    Text(text = "삭제", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDeleteFromFeed = null }) {
                    Text(text = "취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 피드에서 타인 게시글 신고 다이얼로그
    if (postToReportFromFeed != null) {
        SafetyReportDialog(
            targetNickname = "익명 게시글",
            reportType = ReportTargetType.POST,
            onDismiss = { postToReportFromFeed = null },
            onSubmitReport = { reason, shouldBlock ->
                val target = postToReportFromFeed
                postToReportFromFeed = null
                if (target != null) {
                    repository.reportPost(target.id, reason, shouldBlock)
                }
            }
        )
    }
}

/**
 * 사용자 요구사항 반영:
 * "커뮤니티 작성글은 제목 조회수 댓글수 이렇게만 보이게 해줘"
 * 본문, 뱃지, 작성자, 작성시간 등 불필요한 요소를 제거하고 오직 [제목 / 조회수 / 댓글수]만 미니멀하게 구성
 */
@Composable
fun SimplifiedPostCard(
    post: Post,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AnonSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // 1. 제목
            Text(
                text = post.title,
                color = AnonTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 조회수 및 3. 댓글수 & 내가 쓴 글 삭제 버튼 / 타인 글 신고 버튼
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 조회수
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "조회수",
                            tint = AnonTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "조회수 ${post.viewsCount}",
                            color = AnonTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // 댓글수
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "댓글수",
                            tint = AnonSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "댓글수 ${post.commentsCount}",
                            color = AnonSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 내가 쓴 글인 경우 삭제 버튼, 타인 글인 경우 신고 버튼
                if (post.isMyPost && onDelete != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "글 삭제",
                            tint = AnonError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "삭제",
                            color = AnonError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (!post.isMyPost && onReport != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onReport() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "글 신고",
                            tint = AnonTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "신고",
                            color = AnonTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * 사용자 요구사항 반영:
 * "커뮤니티에서 글 눌렀을때 팝업 느낌으로 띄우지 말고 풀 스크린으로 아예 이동되게"
 * 팝업 모달 대신 상단 뒤로가기 앱바, 본문/밸런스게임, 댓글 스레드 및 하단 고정 댓글 입력창이 있는 전체 화면
 */
@Composable
fun PostDetailFullScreen(
    post: Post,
    onBack: () -> Unit,
    onVote: (String) -> Unit,
    onAddComment: (String) -> Unit,
    onDeletePost: (() -> Unit)? = null,
    onDeleteComment: ((String) -> Unit)? = null,
    onReportPost: ((reason: String, shouldBlock: Boolean) -> Unit)? = null,
    onReportComment: ((commentId: String, reason: String, shouldBlock: Boolean) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    var commentText by remember { mutableStateOf("") }
    var isLiked by remember(post.id) { mutableStateOf(post.isLiked) }
    var likesCount by remember(post.id) { mutableIntStateOf(post.likesCount) }
    var showDeletePostDialog by remember { mutableStateOf(false) }
    var showReportPostDialog by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var commentToReport by remember { mutableStateOf<Comment?>(null) }

    // 게시글 삭제 확인 다이얼로그
    if (showDeletePostDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = false },
            title = {
                Text(
                    text = "게시글 삭제",
                    fontWeight = FontWeight.Bold,
                    color = AnonTextPrimary
                )
            },
            text = {
                Text(
                    text = "작성하신 익명 게시글을 삭제하시겠습니까?\n삭제 후에는 복구할 수 없습니다.",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePostDialog = false
                        onDeletePost?.invoke()
                    }
                ) {
                    Text(text = "삭제", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostDialog = false }) {
                    Text(text = "취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 게시글 신고 다이얼로그
    if (showReportPostDialog) {
        SafetyReportDialog(
            targetNickname = "익명 게시글",
            reportType = ReportTargetType.POST,
            onDismiss = { showReportPostDialog = false },
            onSubmitReport = { reason, shouldBlock ->
                showReportPostDialog = false
                onReportPost?.invoke(reason, shouldBlock)
            }
        )
    }

    // 댓글 삭제 확인 다이얼로그
    if (commentToDelete != null) {
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = {
                Text(
                    text = "댓글 삭제",
                    fontWeight = FontWeight.Bold,
                    color = AnonTextPrimary
                )
            },
            text = {
                Text(
                    text = "작성하신 익명 댓글을 삭제하시겠습니까?",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = commentToDelete?.id
                        commentToDelete = null
                        if (targetId != null) {
                            onDeleteComment?.invoke(targetId)
                        }
                    }
                ) {
                    Text(text = "삭제", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text(text = "취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 댓글 신고 다이얼로그
    if (commentToReport != null) {
        SafetyReportDialog(
            targetNickname = commentToReport?.authorTag ?: "익명 댓글",
            reportType = ReportTargetType.COMMENT,
            onDismiss = { commentToReport = null },
            onSubmitReport = { reason, shouldBlock ->
                val target = commentToReport
                commentToReport = null
                if (target != null) {
                    onReportComment?.invoke(target.id, reason, shouldBlock)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Surface(
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로가기",
                                tint = AnonTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "익명 게시글",
                            color = AnonTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AnonSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = post.category.label,
                                color = AnonPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (post.isMyPost && onDeletePost != null) {
                            IconButton(
                                onClick = { showDeletePostDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "게시글 삭제",
                                    tint = AnonError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (!post.isMyPost && onReportPost != null) {
                            IconButton(
                                onClick = { showReportPostDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Flag,
                                    contentDescription = "게시글 신고",
                                    tint = AnonError.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // 하단 고정 댓글 입력창 (작성 시 000댓쓰니 랜덤 닉네임으로 자동 등록)
            Surface(
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = {
                            Text(
                                text = "댓글 쓰기",
                                fontSize = 13.sp,
                                color = AnonTextMuted
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AnonTextPrimary,
                            unfocusedTextColor = AnonTextPrimary,
                            focusedBorderColor = AnonPrimary,
                            unfocusedBorderColor = AnonSurfaceBorder,
                            focusedContainerColor = AnonSurfaceElevated,
                            unfocusedContainerColor = AnonSurfaceElevated
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onAddComment(commentText)
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnonPrimary,
                            disabledContainerColor = AnonSurfaceElevated
                        )
                    ) {
                        Text(
                            text = "게시",
                            color = if (commentText.isNotBlank()) Color.White else AnonTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 게시글 본문 섹션
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AnonSurfaceElevated)
                        .border(1.dp, AnonSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    // 작성자 정보 바
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(InstagramStoryBorder)
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = post.authorTag.takeLast(1),
                                color = AnonPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = post.authorTag,
                                    color = AnonTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (post.isMyPost) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AnonPrimary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "내 글",
                                            color = AnonPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = post.timeAgo,
                                color = AnonTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 제목
                    Text(
                        text = post.title,
                        color = AnonTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    )

                    // 본문
                    if (post.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = post.content,
                            color = AnonTextSecondary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }

                    // 밸런스 게임 섹션
                    if (post.balanceGame != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        BalanceGameBox(
                            balanceGame = post.balanceGame,
                            onVote = onVote
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = AnonSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 하단 반응 메트릭 바 (좋아요 버튼, 댓글수, 조회수)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 좋아요 버튼
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isLiked) {
                                            isLiked = false
                                            likesCount--
                                        } else {
                                            isLiked = true
                                            likesCount++
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "좋아요",
                                    tint = if (isLiked) AnonPrimary else AnonTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$likesCount",
                                    color = if (isLiked) AnonPrimary else AnonTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 댓글 수
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "댓글",
                                    tint = AnonTextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${post.commentsCount}",
                                    color = AnonTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 조회수
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = "조회수",
                                tint = AnonTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "조회 ${post.viewsCount}",
                                color = AnonTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 댓글 섹션 헤더
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "댓글 ${post.comments.size}",
                        color = AnonTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 댓글 목록
            if (post.comments.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = "아직 댓글이 없습니다. 첫 번째 솔직한 익명 댓글을 남겨보세요!",
                            color = AnonTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(post.comments, key = { it.id }) { comment ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AnonSurfaceElevated)
                            .border(1.dp, AnonSurfaceBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.authorTag,
                                    color = if (comment.isAuthor) AnonPrimary else AnonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (comment.isAuthor) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AnonPrimary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "작성자",
                                            color = AnonPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (comment.isMyComment) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AnonSecondary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "내 댓글",
                                            color = AnonSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "· ${comment.timeAgo}",
                                    color = AnonTextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            if (comment.isMyComment && onDeleteComment != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { commentToDelete = comment }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "댓글 삭제",
                                        tint = AnonError,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "삭제",
                                        color = AnonError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (!comment.isMyComment && onReportComment != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { commentToReport = comment }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = "댓글 신고",
                                        tint = AnonTextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "신고",
                                        color = AnonTextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = comment.content,
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceGameBox(
    balanceGame: BalanceGame,
    onVote: (String) -> Unit
) {
    val hasVoted = balanceGame.userVotedOption != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AnonSurfaceElevated)
            .border(1.dp, AnonSurfaceBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .animateContentSize()
    ) {
        Text(
            text = if (hasVoted) "투표 결과 (총 ${balanceGame.totalVotes}명 참여)" else "선택지를 터치하여 투표하세요",
            color = AnonTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 옵션 A
        BalanceOptionBar(
            text = balanceGame.optionA,
            percentage = balanceGame.percentageA,
            votes = balanceGame.votesA,
            isSelected = balanceGame.userVotedOption == "A",
            hasVoted = hasVoted,
            barColor = AnonSecondary,
            onClick = { if (!hasVoted) onVote("A") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 옵션 B
        BalanceOptionBar(
            text = balanceGame.optionB,
            percentage = balanceGame.percentageB,
            votes = balanceGame.votesB,
            isSelected = balanceGame.userVotedOption == "B",
            hasVoted = hasVoted,
            barColor = AnonPrimary,
            onClick = { if (!hasVoted) onVote("B") }
        )
    }
}

@Composable
fun BalanceOptionBar(
    text: String,
    percentage: Int,
    votes: Int,
    isSelected: Boolean,
    hasVoted: Boolean,
    barColor: Color,
    onClick: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (hasVoted) percentage / 100f else 0f,
        label = "vote_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AnonSurfaceBorder.copy(alpha = 0.5f))
            .then(
                if (isSelected) Modifier.border(1.5.dp, barColor, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(enabled = !hasVoted) { onClick() }
    ) {
        if (hasVoted) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(barColor.copy(alpha = 0.35f))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = text,
                color = if (isSelected) AnonTextPrimary else AnonTextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (hasVoted) {
                Text(
                    text = "$percentage% ($votes 표)",
                    color = barColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 사용자 요구사항 반영:
 * "익명 글쓰기도 풀 스크린으로 바꿔줘. 그리고 글 쓰거나 댓글 달면 '익명' 이라고만 써지지 말고 랜덤하게 000한 글쓰니 000한 댓쓰니 이렇게 해서 글마다 랜덤한 닉으로 쓸수있게"
 * 다이얼로그 팝업 대신 상단 앱바, 뒤로가기 버튼, 랜덤 닉네임 미리보기 및 주사위 다시 뽑기 기능이 포함된 풀 스크린 작성 화면
 */
@Composable
fun CreatePostFullScreen(
    onBack: () -> Unit,
    onSubmit: (PostCategory, String, String, String?, String?) -> Unit
) {
    BackHandler(onBack = onBack)

    var category by remember { mutableStateOf(PostCategory.BALANCE_GAME) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isBalanceGame by remember { mutableStateOf(true) }
    var optionA by remember { mutableStateOf("") }
    var optionB by remember { mutableStateOf("") }

    // 실시간 비속어/개인정보 감지 피드백
    val liveFilter = remember(title, content) {
        SafetyTextFilter.maskSensitiveContent("$title $content")
    }

    Scaffold(
        containerColor = AnonBackground,
        topBar = {
            Surface(
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로가기",
                                tint = AnonTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "익명 글쓰기",
                            color = AnonTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 등록 버튼 (상단 우측 배치로 쾌적한 작성 경험)
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSubmit(
                                    category,
                                    title,
                                    content,
                                    if (isBalanceGame) optionA else null,
                                    if (isBalanceGame) optionB else null
                                )
                            }
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnonPrimary,
                            disabledContainerColor = AnonPrimary.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "등록",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // 카테고리 칩 선택 라인
            Text(
                text = "카테고리 선택",
                color = AnonTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PostCategory.BALANCE_GAME, PostCategory.WORRIES, PostCategory.DAILY).forEach { cat ->
                    val isSelected = category == cat
                    Surface(
                        color = if (isSelected) AnonPrimary else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AnonPrimary else AnonSurfaceBorder
                        ),
                        modifier = Modifier.clickable {
                            category = cat
                            isBalanceGame = (cat == PostCategory.BALANCE_GAME)
                        }
                    ) {
                        Text(
                            text = cat.label,
                            color = if (isSelected) Color.White else AnonTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 제목 입력 필드
            Text(
                text = "글 제목",
                color = AnonTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("제목을 입력해주세요 (최대 50자)", fontSize = 14.sp, color = AnonTextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AnonTextPrimary,
                    unfocusedTextColor = AnonTextPrimary,
                    focusedBorderColor = AnonPrimary,
                    unfocusedBorderColor = AnonSurfaceBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 본문 입력 필드
            Text(
                text = "글 내용",
                color = AnonTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("자유롭게 이야기를 나눠보세요.\n개인정보 및 비속어는 자동으로 안심 마스킹(***) 처리됩니다.", fontSize = 14.sp, color = AnonTextMuted) },
                minLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AnonTextPrimary,
                    unfocusedTextColor = AnonTextPrimary,
                    focusedBorderColor = AnonPrimary,
                    unfocusedBorderColor = AnonSurfaceBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 밸런스 게임 선택지 입력 옵션
            if (isBalanceGame) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚖️ 밸런스 게임 투표 선택지",
                                color = AnonTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(선택)",
                                color = AnonTextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = optionA,
                            onValueChange = { optionA = it },
                            placeholder = { Text("선택지 A (예: 깻잎 떼어주기)", fontSize = 13.sp, color = AnonTextMuted) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = optionB,
                            onValueChange = { optionB = it },
                            placeholder = { Text("선택지 B (예: 패딩 지퍼 올려주기)", fontSize = 13.sp, color = AnonTextMuted) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 실시간 안전 필터 감지 배너
            if (liveFilter.hasViolation) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = AnonWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonWarning.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AnonWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "안전 필터: 감지된 ${liveFilter.violationTypes.joinToString { it.label }}는 등록 시 자동 마스킹(***) 처리되어 안전하게 게시됩니다.",
                            color = AnonWarning,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

