package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AppRepository
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CommunityScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MyProfileScreen
import com.example.ui.theme.*

enum class AppNavTab(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    COMMUNITY("피드", Icons.Filled.Forum, Icons.Outlined.Forum),
    CHAT("채팅", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    MY("프로필", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnonTheme {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val repository = remember { AppRepository() }
    val isLoggedIn by repository.isLoggedIn.collectAsState()

    AnimatedContent(
        targetState = isLoggedIn,
        label = "AuthTransition"
    ) { loggedIn ->
        if (!loggedIn) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = { /* StateFlow updates automatically */ }
            )
        } else {
            MainAppRoot(repository = repository)
        }
    }
}

@Composable
fun MainAppRoot(repository: AppRepository = remember { AppRepository() }) {
    val currentUser by repository.currentUser.collectAsState()
    val chatRooms by repository.chatRooms.collectAsState()

    var currentTab by remember { mutableStateOf(AppNavTab.COMMUNITY) }
    var directChatRoomId by remember { mutableStateOf<String?>(null) }

    val totalUnreadCount = remember(chatRooms) {
        chatRooms.sumOf { it.unreadCount }
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
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // 인스타그램 감성 로고 및 타이틀
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(InstagramStoryBorder)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(InstagramGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Anon",
                            color = AnonTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AnonSurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
                        ) {
                            Text(
                                text = "100% 익명",
                                color = AnonTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 상단 우측 알림/프로필 바로가기
                    IconButton(
                        onClick = { currentTab = AppNavTab.MY },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AnonSurfaceElevated)
                            .border(1.dp, AnonSurfaceBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "내 프로필",
                            tint = AnonTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(1.dp, AnonSurfaceBorder)
            ) {
                AppNavTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (tab == AppNavTab.CHAT) {
                                directChatRoomId = null
                            }
                            currentTab = tab
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == AppNavTab.CHAT && totalUnreadCount > 0) {
                                        Badge(containerColor = AnonPrimary) {
                                            Text("$totalUnreadCount", color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AnonTextPrimary,
                            selectedTextColor = AnonTextPrimary,
                            unselectedIconColor = AnonTextMuted,
                            unselectedTextColor = AnonTextMuted,
                            indicatorColor = AnonSurfaceElevated
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppNavTab.COMMUNITY -> {
                    CommunityScreen(repository = repository)
                }
                AppNavTab.CHAT -> {
                    ChatScreen(
                        repository = repository,
                        selectedRoomId = directChatRoomId,
                        onBackToList = { directChatRoomId = null }
                    )
                }
                AppNavTab.MY -> {
                    MyProfileScreen(
                        repository = repository,
                        onLogout = {
                            // 로그아웃 시 AppNavigator가 즉시 LoginScreen으로 자동 전환
                        }
                    )
                }
            }
        }
    }
}
