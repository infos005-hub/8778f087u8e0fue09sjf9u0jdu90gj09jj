package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.Post
import com.example.core.model.UserProfile
import com.example.core.safety.SafetyTextFilter
import com.example.data.repository.AppRepository
import com.example.ui.components.TagChip
import com.example.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AvatarPresetItem(
    val id: String,
    val title: String,
    val resId: Int
)

val AVATAR_PRESETS = listOf(
    AvatarPresetItem("default", "클래식 3D", R.drawable.anon_avatar_demo_1789521505031),
    AvatarPresetItem("cat", "네온 냥이", R.drawable.anon_avatar_cat_1789530718973),
    AvatarPresetItem("cosmic", "코스믹 별빛", R.drawable.anon_avatar_star_1789530730730),
    AvatarPresetItem("leaf", "말차 플랜트", R.drawable.anon_avatar_leaf_1789530742900)
)

@Composable
fun ProfileAvatar(
    avatarUri: String?,
    avatarResId: Int?,
    modifier: Modifier = Modifier
) {
    if (!avatarUri.isNullOrBlank()) {
        AsyncImage(
            model = avatarUri,
            contentDescription = "프로필 사진",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        val res = avatarResId ?: R.drawable.anon_avatar_demo_1789521505031
        Image(
            painter = painterResource(id = res),
            contentDescription = "프로필 사진",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(
    repository: AppRepository,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentUser by repository.currentUser.collectAsState()
    val visitors by repository.visitors.collectAsState()
    val posts by repository.posts.collectAsState()

    val myPosts = remember(posts, currentUser.id) {
        posts.filter { it.isMyPost || (it.authorId.isNotBlank() && it.authorId == currentUser.id) }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 프로필 & 방문자/하트, 1: 내가 쓴 게시글
    var selectedPostForDetail by remember { mutableStateOf<Post?>(null) }
    var postToDeleteFromProfile by remember { mutableStateOf<Post?>(null) }

    var isSyncingServer by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAdSimulationDialog by remember { mutableStateOf(false) }

    // 게시글 상세 전체 화면 뷰가 활성화된 경우
    if (selectedPostForDetail != null) {
        val targetPost = posts.find { it.id == selectedPostForDetail?.id } ?: selectedPostForDetail!!
        PostDetailFullScreen(
            post = targetPost,
            onBack = { selectedPostForDetail = null },
            onVote = { option -> repository.voteBalanceGame(targetPost.id, option) },
            onAddComment = { text -> repository.addComment(targetPost.id, text) },
            onDeletePost = {
                repository.deletePost(targetPost.id)
                selectedPostForDetail = null
            },
            onDeleteComment = { commentId ->
                repository.deleteComment(targetPost.id, commentId)
            },
            onReportPost = { reason, shouldBlock ->
                repository.reportPost(targetPost.id, reason, shouldBlock)
                selectedPostForDetail = null
            },
            onReportComment = { commentId, reason, shouldBlock ->
                repository.reportComment(targetPost.id, commentId, reason, shouldBlock)
            }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 90.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(AnonBackground)
        ) {
            // 상단 헤더: 닉네임, 클라우드 동기화 상태, 동기화/설정 버튼
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = currentUser.nickname,
                            color = AnonTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "서버 저장 상태",
                                tint = AnonPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "개인 프로필 서버 저장됨",
                                color = AnonPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 수동 서버 즉시 동기화 버튼
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isSyncingServer = true
                                    val success = repository.syncProfileToServer()
                                    isSyncingServer = false
                                    snackbarHostState.showSnackbar(
                                        if (success) "프로필이 클라우드 서버에 동기화되었습니다" else "서버 동기화 완료"
                                    )
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AnonSurfaceElevated)
                                .border(1.dp, AnonSurfaceBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "서버 동기화",
                                tint = if (isSyncingServer) AnonPrimary else AnonTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AnonSurfaceElevated)
                                .border(1.dp, AnonSurfaceBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "설정",
                                tint = AnonTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 인스타그램 스타일 프로필 헤더 카드 (방문자수, 보유 하트, 작성글 수 직관 표시)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AnonSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 아바타
                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .clickable { showEditProfileDialog = true }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(InstagramStoryBorder)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(2.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ProfileAvatar(
                                        avatarUri = currentUser.avatarUri,
                                        avatarResId = currentUser.avatarResId,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // 카메라/수정 뱃지
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(AnonPrimary)
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = "사진 변경",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            // 핵심 스탯: 방문자수, 보유 하트, 내가 쓴 글 (클릭 시 해당 탭 이동)
                            Row(
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileStatItem(
                                    title = "방문자수",
                                    count = "${currentUser.profileViews}회",
                                    onClick = { selectedTab = 0 }
                                )
                                ProfileStatItem(
                                    title = "보유 하트",
                                    count = "${currentUser.heartBalance}개",
                                    onClick = { selectedTab = 0 }
                                )
                                ProfileStatItem(
                                    title = "내가 쓴 글",
                                    count = "${myPosts.size}개",
                                    onClick = { selectedTab = 1 }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 닉네임 및 기본 인포
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser.nickname,
                                color = AnonTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = AnonPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "100% 익명",
                                    color = AnonPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${currentUser.displayGender} · ${currentUser.displayAge} · ${currentUser.mbti}",
                                color = AnonTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (!currentUser.isAgeVisible || !currentUser.isGenderVisible) {
                                Surface(
                                    color = AnonSurfaceElevated,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
                                ) {
                                    Text(
                                        text = "🔒 일부 비공개",
                                        fontSize = 10.sp,
                                        color = AnonTextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 상세 프로필 카드 ([한줄소개], [성격], [취미], [선호하는 친구], [특이사항])
                        Surface(
                            color = AnonSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // [한줄소개]
                                Column {
                                    Text(
                                        text = "💬 한줄소개",
                                        color = AnonPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (currentUser.bio.isNotBlank()) currentUser.bio else "등록된 한줄소개가 없습니다.",
                                        color = AnonTextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }

                                Divider(color = AnonSurfaceBorder.copy(alpha = 0.6f))

                                // [성격]
                                Column {
                                    Text(
                                        text = "🌿 성격",
                                        color = AnonPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (currentUser.personality.isNotBlank()) currentUser.personality else "등록된 성격 설명이 없습니다.",
                                        color = AnonTextPrimary,
                                        fontSize = 13.sp
                                    )
                                }

                                Divider(color = AnonSurfaceBorder.copy(alpha = 0.6f))

                                // [취미]
                                Column {
                                    Text(
                                        text = "🎯 취미",
                                        color = AnonPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    if (currentUser.interests.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            currentUser.interests.forEach { tag ->
                                                TagChip(text = tag, isHighlighted = true)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "선택된 취미가 없습니다.",
                                            color = AnonTextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Divider(color = AnonSurfaceBorder.copy(alpha = 0.6f))

                                // [선호하는 친구]
                                Column {
                                    Text(
                                        text = "🤝 선호하는 친구 스타일",
                                        color = AnonPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (currentUser.preferredFriendStyle.isNotBlank()) currentUser.preferredFriendStyle else "편하게 대화 나눌 모든 친구 환영해요",
                                        color = AnonTextPrimary,
                                        fontSize = 13.sp
                                    )
                                }

                                // [특이사항] (입력된 경우에만 표시)
                                if (currentUser.specialNotes.isNotBlank()) {
                                    Divider(color = AnonSurfaceBorder.copy(alpha = 0.6f))
                                    Column {
                                        Text(
                                            text = "💡 특이사항",
                                            color = AnonPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = currentUser.specialNotes,
                                            color = AnonTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 액션 버튼: [프로필 편집] + [설정]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AnonSurfaceElevated,
                                    contentColor = AnonTextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                elevation = ButtonDefaults.buttonElevation(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = AnonTextPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "프로필 편집",
                                    color = AnonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { showSettingsDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = AnonSurfaceElevated),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "설정",
                                    tint = AnonTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "설정",
                                    color = AnonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 탭 선택기: [프로필 & 방문자/하트] vs [내가 쓴 글 (N개)]
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AnonSurface,
                    contentColor = AnonPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, AnonSurfaceBorder, RoundedCornerShape(14.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 0) AnonPrimary else AnonTextSecondary
                                )
                                Text(
                                    text = "프로필 & 방문자/하트",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) AnonPrimary else AnonTextSecondary
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (selectedTab == 1) AnonPrimary else AnonTextSecondary
                                )
                                Text(
                                    text = "내가 쓴 글 (${myPosts.size})",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) AnonPrimary else AnonTextSecondary
                                )
                            }
                        }
                    )
                }
            }

            if (selectedTab == 0) {
                // ==================== [탭 0] 프로필, 방문자수, 보유하트 ====================
                // 1. 개인 프로필 클라우드 서버 저장 안내 및 수동 동기화 카드
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AnonSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = AnonPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "개인 프로필 서버 안전 보관",
                                        color = AnonTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = AnonPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "실시간 동기화",
                                        color = AnonPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "내 프로필 정보(닉네임, 소개글, 관심사)와 방문자수(${currentUser.profileViews}회), 보유 하트(${currentUser.heartBalance}개)가 회원님의 고유 프로필(ID: ${currentUser.id.take(8)})로 Firestore 클라우드 서버에 안전하게 영구 저장 및 실시간 동기화됩니다.",
                                color = AnonTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isSyncingServer = true
                                        val ok = repository.syncProfileToServer()
                                        isSyncingServer = false
                                        snackbarHostState.showSnackbar(
                                            if (ok) "클라우드 서버에 성공적으로 동기화되었습니다!" else "서버 동기화 완료"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = AnonPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSyncingServer) "서버 동기화 중..." else "서버 즉시 동기화",
                                    color = AnonTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 2. 보유 하트 지갑 카드 & 보상형 광고 충전
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AnonSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = AnonSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "내 보유 하트",
                                        color = AnonTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${currentUser.heartBalance}개",
                                    color = AnonSecondary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 보상형 동영상 광고 버튼
                            Button(
                                onClick = { showAdSimulationDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "보상형 광고 보고 하트 +3개 받기",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 방문자수 확인 & 누가 내 프로필을 봤을까? 카드
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AnonSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = AnonTertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "누가 내 프로필을 봤을까?",
                                        color = AnonTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = AnonTertiary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "총 ${currentUser.profileViews}회 방문",
                                        color = AnonTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "하트 2개를 소모하여 최근 방문자의 닉네임과 관심사를 잠금 해제할 수 있습니다.",
                                color = AnonTextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            visitors.forEach { visitor ->
                                Surface(
                                    color = AnonSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (visitor.isUnlocked) visitor.maskedNickname.replace("*", "님") else visitor.maskedNickname,
                                                color = if (visitor.isUnlocked) AnonTertiary else AnonTextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${visitor.age}세 · ${visitor.interests.joinToString()} · ${visitor.visitedTimeAgo}",
                                                color = AnonTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (visitor.isUnlocked) {
                                            Surface(
                                                color = AnonTertiary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "해금됨",
                                                    color = AnonTertiary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { repository.unlockVisitor(visitor.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AnonSecondary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("해금 (2하트)", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== [탭 1] 내가 지금까지 쓴 게시글 ====================
                // 1. 작성 게시글 종합 통계 카드
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AnonSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Article,
                                        contentDescription = null,
                                        tint = AnonPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "내 게시글 활동 요약",
                                        color = AnonTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = AnonPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "총 ${myPosts.size}개 작성",
                                        color = AnonPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 4개 통계 지표
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ProfileStatItem(title = "작성글", count = "${myPosts.size}")
                                ProfileStatItem(title = "누적 조회수", count = "${myPosts.sumOf { it.viewsCount }}")
                                ProfileStatItem(title = "받은 하트", count = "${myPosts.sumOf { it.likesCount }}")
                                ProfileStatItem(title = "댓글 반응", count = "${myPosts.sumOf { it.commentsCount }}")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "💡 작성하신 모든 글은 클라우드 서버에 실시간 저장되며 다른 유저에게는 완벽한 익명 닉네임으로 표시됩니다.",
                                color = AnonTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // 2. 게시글 목록 또는 비어있음 안내
                if (myPosts.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AnonSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp, horizontal = 20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = AnonTextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "아직 작성하신 게시글이 없습니다",
                                    color = AnonTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "커뮤니티 탭에서 일상, 고민, 밸런스 게임을\n익명으로 자유롭게 공유해보세요!",
                                    color = AnonTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                } else {
                    items(myPosts, key = { it.id }) { post ->
                        SimplifiedPostCard(
                            post = post,
                            onClick = { selectedPostForDetail = post },
                            onDelete = { postToDeleteFromProfile = post }
                        )
                    }
                }
            }
        }

        // 서버 동기화 스낵바
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }

    // 게시글 삭제 확인 다이얼로그
    if (postToDeleteFromProfile != null) {
        val target = postToDeleteFromProfile
        AlertDialog(
            onDismissRequest = { postToDeleteFromProfile = null },
            title = {
                Text(
                    text = "게시글 삭제",
                    fontWeight = FontWeight.Bold,
                    color = AnonTextPrimary
                )
            },
            text = {
                Text(
                    text = "작성하신 익명 게시글을 삭제하시겠습니까?\n삭제 후에는 클라우드 서버에서도 영구 삭제됩니다.",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target?.id
                        postToDeleteFromProfile = null
                        if (id != null) {
                            repository.deletePost(id)
                        }
                    }
                ) {
                    Text(text = "삭제", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDeleteFromProfile = null }) {
                    Text(text = "취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 프로필 편집 다이얼로그 (이름/닉네임, 사진/아바타, 소개글, 성격, 취미, 선호친구, 특이사항, 공개설정 수정)
    if (showEditProfileDialog) {
        EditProfileDialog(
            user = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onSave = { newNickname, newBio, resId, uri, personality, interests, friendStyle, specialNotes, isAgeVisible, isGenderVisible, newAge ->
                repository.updateProfile(
                    newNickname = newNickname,
                    newBio = newBio,
                    avatarResId = resId,
                    avatarUri = uri,
                    personality = personality,
                    interests = interests,
                    preferredFriendStyle = friendStyle,
                    specialNotes = specialNotes,
                    isAgeVisible = isAgeVisible,
                    isGenderVisible = isGenderVisible,
                    age = newAge
                )
                showEditProfileDialog = false
            }
        )
    }

    // 보상형 동영상 광고 시뮬레이션 모달
    if (showAdSimulationDialog) {
        RewardedAdSimulationDialog(
            onDismiss = { showAdSimulationDialog = false },
            onRewardEarned = {
                repository.claimRewardedAdReward()
                showAdSimulationDialog = false
            }
        )
    }

    // 설정 모달 다이얼로그 (계정 정보, 알림, 보안, 로그아웃)
    if (showSettingsDialog) {
        SettingsDialog(
            repository = repository,
            onDismiss = { showSettingsDialog = false },
            onLogout = {
                showSettingsDialog = false
                onLogout()
            }
        )
    }
}

@Composable
fun ProfileStatItem(
    title: String,
    count: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        } else Modifier
    ) {
        Text(
            text = count,
            color = AnonTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = AnonTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun RewardedAdSimulationDialog(
    onDismiss: () -> Unit,
    onRewardEarned: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(3) }
    var isCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        isCompleted = true
    }

    AlertDialog(
        onDismissRequest = { if (isCompleted) onDismiss() },
        containerColor = AnonSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = AnonSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCompleted) "광고 시청 완료! 🎉" else "보상형 동영상 광고 재생 중",
                    color = AnonTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                if (!isCompleted) {
                    CircularProgressIndicator(color = AnonSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "광고 종료까지 $countdown 초 남았습니다...",
                        color = AnonTextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = AnonSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "하트 3개가 충전되었습니다!",
                        color = AnonTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            if (isCompleted) {
                Button(
                    onClick = onRewardEarned,
                    colors = ButtonDefaults.buttonColors(containerColor = AnonSecondary)
                ) {
                    Text("하트 3개 받기", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isCompleted) {
                TextButton(onClick = onDismiss) {
                    Text("중도 취소", color = AnonTextMuted)
                }
            }
        }
    )
}

/**
 * 인스타그램 스타일의 프로필 편집 모달
 * 이름(닉네임) 변경, 한줄 소개글 수정, 사진 변경(갤러리 앨범 또는 감성 익명 아바타 프리셋)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (
        newNickname: String,
        newBio: String,
        resId: Int?,
        uri: String?,
        personality: String,
        interests: List<String>,
        friendStyle: String,
        specialNotes: String,
        isAgeVisible: Boolean,
        isGenderVisible: Boolean,
        newAge: Int
    ) -> Unit
) {
    var nickname by remember { mutableStateOf(user.nickname) }
    var ageText by remember { mutableStateOf(user.age.coerceIn(0, 100).toString()) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf(user.bio) }
    var personality by remember { mutableStateOf(user.personality) }
    var preferredFriendStyle by remember { mutableStateOf(user.preferredFriendStyle) }
    var specialNotes by remember { mutableStateOf(user.specialNotes) }
    var isAgeVisible by remember { mutableStateOf(user.isAgeVisible) }
    var isGenderVisible by remember { mutableStateOf(user.isGenderVisible) }
    var interests by remember { mutableStateOf(user.interests) }
    var customInterestInput by remember { mutableStateOf("") }

    var selectedResId by remember { mutableStateOf(user.avatarResId ?: R.drawable.anon_avatar_demo_1789521505031) }
    var selectedUri by remember { mutableStateOf(user.avatarUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri.toString()
            selectedResId = 0
        }
    }

    val popularHobbies = remember {
        listOf(
            "☕ 카페투어", "📚 독서", "🏃 러닝/운동", "🎵 음악감상",
            "🎬 영화/넷플", "🎮 게임", "🍳 요리/맛집", "🐱 반려동물",
            "✈️ 여행", "📸 사진", "🎨 전시/미술", "🏕️ 캠핑"
        )
    }

    val personalityPresets = remember {
        listOf("다정다감", "차분하고 조용한", "밝고 긍정적인", "솔직유쾌", "경청을 잘하는", "사려깊은")
    }

    val isNicknameValid = nickname.trim().length in 2..12

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnonSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "프로필 편집",
                    color = AnonTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = AnonTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 아바타 미리보기 및 변경 영역
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(InstagramStoryBorder)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatar(
                                avatarUri = selectedUri,
                                avatarResId = if (selectedResId != 0) selectedResId else null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 내 사진첩에서 직접 선택 버튼
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonPrimary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = AnonPrimary.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = AnonPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "내 앨범에서 사진 선택",
                                color = AnonPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 2. 익명 아바타 프리셋 목록
                item {
                    Column {
                        Text(
                            text = "익명 아바타 프리셋",
                            color = AnonTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(AVATAR_PRESETS, key = { it.id }) { preset ->
                                val isSelected = selectedUri == null && selectedResId == preset.resId
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            selectedUri = null
                                            selectedResId = preset.resId
                                        }
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) AnonPrimary else AnonSurfaceBorder,
                                                shape = CircleShape
                                            )
                                            .padding(if (isSelected) 2.dp else 0.dp)
                                            .clip(CircleShape)
                                    ) {
                                        Image(
                                            painter = painterResource(id = preset.resId),
                                            contentDescription = preset.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.title,
                                        color = if (isSelected) AnonPrimary else AnonTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 닉네임 입력
                item {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "닉네임",
                                color = AnonTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${nickname.length}/12",
                                color = if (isNicknameValid) AnonTextMuted else AnonWarning,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { if (it.length <= 12) nickname = it },
                            placeholder = { Text("변경할 닉네임 입력") },
                            singleLine = true,
                            isError = !isNicknameValid,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated,
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 4. 나이 설정 (0~100세 직접 숫자 입력) 및 공개 설정 토글
                item {
                    val currentYear = 2026
                    val currentAgeInt = ageText.trim().toIntOrNull()
                    val calculatedBirthYear = if (currentAgeInt != null && currentAgeInt in 0..100) {
                        currentYear - currentAgeInt
                    } else null

                    Surface(
                        color = AnonSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "나이 입력 (0~100세)",
                                        color = AnonTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (calculatedBirthYear != null) "${currentAgeInt}세 (${calculatedBirthYear}년생)" else "0~100 사이 숫자 입력",
                                        color = if (calculatedBirthYear != null) AnonPrimary else AnonTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // 나이 직접 숫자 입력 필드 (슬라이더 대신 숫자 키패드 직접 입력)
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { input ->
                                    // 음수 기호(-) 및 문자 자동 차단: 오직 숫자만 허용
                                    val digitsOnly = input.filter { it.isDigit() }
                                    if (digitsOnly.isEmpty()) {
                                        ageText = ""
                                        ageError = "나이를 입력해 주세요 (0~100)."
                                    } else {
                                        val parsed = digitsOnly.toIntOrNull()
                                        if (parsed == null || parsed > 100) {
                                            // 100 초과(예: 200 등) 차단
                                            ageError = "나이는 최대 100세까지만 입력 가능합니다."
                                        } else {
                                            ageText = parsed.toString()
                                            ageError = null
                                        }
                                    }
                                },
                                placeholder = {
                                    Text("나이 입력 (예: 24)", color = AnonTextMuted, fontSize = 13.sp)
                                },
                                trailingIcon = {
                                    Text(
                                        text = "세",
                                        color = AnonTextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                },
                                isError = ageError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnonPrimary,
                                    unfocusedBorderColor = AnonSurfaceBorderStrong,
                                    errorBorderColor = MaterialTheme.colorScheme.error,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            if (ageError != null) {
                                Text(
                                    text = ageError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Divider(color = AnonSurfaceBorder.copy(alpha = 0.5f))

                            Text(
                                text = "🔒 프로필 공개 설정",
                                color = AnonTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // 나이 공개 스위치
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (currentAgeInt != null && currentAgeInt in 0..100) "나이 프로필 공개 (${currentAgeInt}세)" else "나이 프로필 공개",
                                        fontSize = 12.sp,
                                        color = AnonTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (isAgeVisible) "다른 사용자에게 나이가 공개됩니다." else "나이가 '비공개'로 숨겨집니다.",
                                        fontSize = 11.sp,
                                        color = if (isAgeVisible) AnonPrimary else AnonTextMuted
                                    )
                                }
                                Switch(
                                    checked = isAgeVisible,
                                    onCheckedChange = { isAgeVisible = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AnonPrimary)
                                )
                            }

                            Divider(color = AnonSurfaceBorder.copy(alpha = 0.5f))

                            // 성별 공개 스위치
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "성별 프로필 공개 (${user.gender})",
                                        fontSize = 12.sp,
                                        color = AnonTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (isGenderVisible) "다른 사용자에게 성별이 공개됩니다." else "성별이 '비공개'로 숨겨집니다.",
                                        fontSize = 11.sp,
                                        color = if (isGenderVisible) AnonPrimary else AnonTextMuted
                                    )
                                }
                                Switch(
                                    checked = isGenderVisible,
                                    onCheckedChange = { isGenderVisible = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AnonPrimary)
                                )
                            }
                        }
                    }
                }

                // 5. [한줄소개]
                item {
                    Column {
                        Text(
                            text = "[한줄소개] 💬",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 100) bio = it },
                            placeholder = { Text("나를 소개하는 한 줄을 적어보세요") },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated,
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 6. [성격]
                item {
                    Column {
                        Text(
                            text = "[성격] 🌿",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = personality,
                            onValueChange = { personality = it },
                            placeholder = { Text("예: 다정다감하고 긍정적인, 배려심 깊은") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated,
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(personalityPresets) { p ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (personality.contains(p)) AnonPrimary.copy(alpha = 0.12f) else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                    modifier = Modifier.clickable {
                                        personality = if (personality.isBlank()) p else "$personality, $p"
                                    }
                                ) {
                                    Text(
                                        text = "+ $p",
                                        fontSize = 11.sp,
                                        color = AnonTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. [취미]
                item {
                    Column {
                        Text(
                            text = "[취미] 🎯",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            popularHobbies.forEach { hobby ->
                                val isSelected = interests.contains(hobby)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) AnonPrimary else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                    modifier = Modifier.clickable {
                                        interests = if (isSelected) interests - hobby else interests + hobby
                                    }
                                ) {
                                    Text(
                                        text = hobby,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else AnonTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 커스텀 취미 추가
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customInterestInput,
                                onValueChange = { customInterestInput = it },
                                placeholder = { Text("취미 직접 추가", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = AnonSurfaceElevated,
                                    unfocusedContainerColor = AnonSurfaceElevated
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val t = customInterestInput.trim()
                                    if (t.isNotBlank() && !interests.contains(t)) {
                                        interests = interests + t
                                        customInterestInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary)
                            ) {
                                Text("추가", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 8. [선호하는 친구]
                item {
                    Column {
                        Text(
                            text = "[선호하는 친구] 🤝",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = preferredFriendStyle,
                            onValueChange = { preferredFriendStyle = it },
                            placeholder = { Text("예: 티키타카 잘 통하는 친구, 편하게 일상 나눌 친구") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated,
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 9. [특이사항]
                item {
                    Column {
                        Text(
                            text = "[특이사항] 💡 (선택 기재)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = specialNotes,
                            onValueChange = { specialNotes = it },
                            placeholder = { Text("예: 답장이 조금 느릴 수 있어요, 주로 밤에 활동해요") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated,
                                focusedTextColor = AnonTextPrimary,
                                unfocusedTextColor = AnonTextPrimary,
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            val parsedAge = ageText.trim().toIntOrNull()
            val isAgeValid = parsedAge != null && parsedAge in 0..100
            Button(
                onClick = {
                    if (isAgeValid && parsedAge != null) {
                        onSave(
                            nickname.trim(),
                            bio.trim(),
                            if (selectedUri != null) null else selectedResId,
                            selectedUri,
                            personality.trim(),
                            interests,
                            preferredFriendStyle.trim(),
                            specialNotes.trim(),
                            isAgeVisible,
                            isGenderVisible,
                            parsedAge
                        )
                    }
                },
                enabled = isNicknameValid && isAgeValid && ageError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnonPrimary,
                    disabledContainerColor = AnonSurfaceBorderStrong
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = "변경 사항 저장",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {}
    )
}
