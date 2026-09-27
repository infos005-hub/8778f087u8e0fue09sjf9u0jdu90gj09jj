package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    repository: AppRepository,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState()
    val settings by repository.settings.collectAsState()
    val blockedIds by repository.blockedUserIds.collectAsState()

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var termsTitle by remember { mutableStateOf("") }
    var termsContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
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
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = AnonTextPrimary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "설정",
                                color = AnonTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // 1. 내 계정 정보 섹션
                SettingsSectionHeader(title = "내 계정 정보")

                Surface(
                    color = AnonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = AnonPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("로그인 아이디", color = AnonTextMuted, fontSize = 11.sp)
                                Text("@${currentUser.id}", color = AnonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Surface(
                                color = AnonSecondary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "아이디 로그인",
                                    color = AnonSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Divider(color = AnonSurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Badge, contentDescription = null, tint = AnonSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("활동 닉네임", color = AnonTextMuted, fontSize = 11.sp)
                                Text(currentUser.nickname, color = AnonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "활성",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Divider(color = AnonSurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = AnonPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("익명 보안 및 프라이버시", color = AnonTextMuted, fontSize = 11.sp)
                                Text(
                                    "100% 완전 익명 보호 중",
                                    color = AnonTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                color = AnonPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "보호 활성",
                                    color = AnonPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. 알림 설정 섹션 (인스타 토글 스위치 스타일)
                SettingsSectionHeader(title = "알림 설정")

                Surface(
                    color = AnonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                        SettingsSwitchRow(
                            title = "앱 푸시 알림 전체",
                            subtitle = "새 피드 및 채팅 알림을 수신합니다",
                            checked = settings.pushNotificationsEnabled,
                            onCheckedChange = { repository.updatePushNotifications(it) }
                        )
                        Divider(color = AnonSurfaceBorder)
                        SettingsSwitchRow(
                            title = "1:1 익명 채팅 메시지 알림",
                            subtitle = "대화방에 새로운 메시지가 도착했을 때 알림",
                            checked = settings.chatAlertsEnabled,
                            onCheckedChange = { repository.updateChatAlerts(it) }
                        )
                        Divider(color = AnonSurfaceBorder)
                        SettingsSwitchRow(
                            title = "커뮤니티 댓글 및 반응 알림",
                            subtitle = "내 작성글에 새로운 익명 댓글이 달렸을 때",
                            checked = settings.communityCommentAlertsEnabled,
                            onCheckedChange = { repository.updateCommunityCommentAlerts(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. 안전 및 보안
                SettingsSectionHeader(title = "안전 및 프라이버시")

                Surface(
                    color = AnonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                        SettingsClickableRow(
                            icon = Icons.Outlined.GppGood,
                            title = "AI 세이프티 필터링 (비속어·개인정보 마스킹)",
                            trailingText = if (settings.safetyFilterActive) "상시 보호 중" else "비활성화",
                            onClick = { repository.toggleSafetyFilter() }
                        )
                        Divider(color = AnonSurfaceBorder)
                        SettingsClickableRow(
                            icon = Icons.Outlined.Block,
                            title = "차단한 사용자 목록",
                            trailingText = "${blockedIds.size}명",
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. 약관 및 정보
                SettingsSectionHeader(title = "서비스 정보")

                Surface(
                    color = AnonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                        SettingsClickableRow(
                            icon = Icons.Outlined.Description,
                            title = "서비스 이용약관",
                            onClick = {
                                termsTitle = "서비스 이용약관"
                                termsContent = "Anon 서비스는 100% 완전 익명성과 유저 간 안전을 최우선으로 하며, 타인에 대한 비방, 음란물 유포, 개인정보 요구는 엄격히 금지됩니다."
                                showTermsDialog = true
                            }
                        )
                        Divider(color = AnonSurfaceBorder)
                        SettingsClickableRow(
                            icon = Icons.Outlined.Lock,
                            title = "개인정보 처리방침",
                            onClick = {
                                termsTitle = "개인정보 처리방침"
                                termsContent = "Google 계정 및 SMS 문자 인증 정보는 본인 식별 및 중복 방지 목적으로만 암호화 저장되며 타인에게 절대 노출되지 않습니다."
                                showTermsDialog = true
                            }
                        )
                        Divider(color = AnonSurfaceBorder)
                        SettingsClickableRow(
                            icon = Icons.Outlined.Info,
                            title = "앱 버전 정보",
                            trailingText = "v1.2.0 (White Instagram Edition)",
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. 계정 관리 (로그아웃 & 탈퇴)
                SettingsSectionHeader(title = "계정 관리")

                Surface(
                    color = AnonSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                        // 로그아웃 버튼
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogoutConfirm = true }
                                .padding(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = AnonPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "로그아웃",
                                color = AnonPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Divider(color = AnonSurfaceBorder)

                        // 회원 탈퇴 버튼
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteAccountConfirm = true }
                                .padding(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AnonTextMuted, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "회원 탈퇴",
                                color = AnonTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // 로그아웃 확인 팝업
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Color.White,
            title = {
                Text("로그아웃 하시겠습니까?", color = AnonTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "로그아웃하시면 로그인 전 화면으로 이동하며, 다시 Google 계정 및 SMS 인증으로 로그인하실 수 있습니다.",
                    color = AnonTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onDismiss()
                        repository.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary)
                ) {
                    Text("로그아웃", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("취소", color = AnonTextSecondary)
                }
            }
        )
    }

    // 회원 탈퇴 확인 팝업
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            containerColor = Color.White,
            title = {
                Text("정말 탈퇴하시겠습니까?", color = AnonError, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "탈퇴 시 작성한 게시글 및 채팅 기록, 보유 하트가 모두 초기화됩니다.",
                    color = AnonTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirm = false
                        onDismiss()
                        repository.deleteAccount()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AnonError)
                ) {
                    Text("탈퇴하기", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("취소", color = AnonTextSecondary)
                }
            }
        )
    }

    // 이용약관 모달
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = Color.White,
            title = {
                Text(termsTitle, color = AnonTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(termsContent, color = AnonTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("확인", color = AnonPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = AnonTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AnonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = AnonTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AnonPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AnonSurfaceBorderStrong
            )
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AnonTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = AnonTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, color = AnonTextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AnonTextMuted, modifier = Modifier.size(18.dp))
    }
}
