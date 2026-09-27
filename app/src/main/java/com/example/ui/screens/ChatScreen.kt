package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ChatMessage
import com.example.core.model.ChatRoom
import com.example.core.safety.SafetyTextFilter
import com.example.data.repository.AppRepository
import com.example.ui.theme.*

@Composable
fun ChatScreen(
    repository: AppRepository,
    selectedRoomId: String? = null,
    onBackToList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatRooms by repository.chatRooms.collectAsState()
    var activeRoomId by remember(selectedRoomId) { mutableStateOf(selectedRoomId) }

    val currentRoom = remember(chatRooms, activeRoomId) {
        chatRooms.firstOrNull { it.id == activeRoomId }
    }

    if (currentRoom != null) {
        ChatDetailScreen(
            room = currentRoom,
            repository = repository,
            onBack = {
                activeRoomId = null
                onBackToList()
            }
        )
    } else {
        ChatRoomListScreen(
            chatRooms = chatRooms,
            onSelectRoom = { activeRoomId = it.id },
            onLeaveRoom = { repository.leaveChatRoom(it.id) },
            modifier = modifier
        )
    }
}

@Composable
fun ChatRoomListScreen(
    chatRooms: List<ChatRoom>,
    onSelectRoom: (ChatRoom) -> Unit,
    onLeaveRoom: (ChatRoom) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var roomToLeave by remember { mutableStateOf<ChatRoom?>(null) }

    if (roomToLeave != null) {
        AlertDialog(
            onDismissRequest = { roomToLeave = null },
            title = {
                Text("채팅방 나가기", fontWeight = FontWeight.Bold, color = AnonTextPrimary)
            },
            text = {
                Text(
                    text = "'${roomToLeave?.peerUser?.nickname}'님과의 채팅방에서 나가시겠습니까?\n채팅방을 나가면 대화 내용이 모두 삭제되며 목록에서 사라집니다.",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = roomToLeave
                        roomToLeave = null
                        if (target != null) {
                            onLeaveRoom(target)
                        }
                    }
                ) {
                    Text("나가기", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToLeave = null }) {
                    Text("취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnonBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = AnonSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AnonTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "관심사가 통하는 상대와의 1:1 안전한 익명 대화방입니다.",
                    color = AnonTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (chatRooms.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.QuestionAnswer,
                        contentDescription = null,
                        tint = AnonTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "아직 진행 중인 대화가 없습니다",
                        color = AnonTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "피드에서 마음이 맞는 유저와 익명 대화를 시작해보세요!",
                        color = AnonTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(chatRooms, key = { it.id }) { room ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AnonSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRoom(room) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AnonPrimary.copy(alpha = 0.2f))
                                    .border(1.5.dp, AnonPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = room.peerUser.nickname.take(1),
                                    color = AnonPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = room.peerUser.nickname,
                                    color = AnonTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = room.lastMessage,
                                    color = AnonTextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = room.lastTime,
                                        color = AnonTextMuted,
                                        fontSize = 11.sp
                                    )
                                    if (room.unreadCount > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = AnonSecondary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${room.unreadCount}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { roomToLeave = room },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "채팅방 나가기",
                                        tint = AnonTextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(
    room: ChatRoom,
    repository: AppRepository,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var reportingUser by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    val liveFilter = remember(inputText) {
        SafetyTextFilter.maskSensitiveContent(inputText)
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text("채팅방 나가기", fontWeight = FontWeight.Bold, color = AnonTextPrimary)
            },
            text = {
                Text(
                    text = "'${room.peerUser.nickname}'님과의 대화방에서 나가시겠습니까?\n대화방을 나가면 이전 대화 기록이 모두 삭제되며 채팅 목록에서 사라집니다.",
                    color = AnonTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        repository.leaveChatRoom(room.id)
                        onBack()
                    }
                ) {
                    Text("나가기", color = AnonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("취소", color = AnonTextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = AnonSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = AnonTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.peerUser.nickname,
                            color = AnonTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${room.peerUser.mbti} · ${room.peerUser.interests.joinToString()}",
                            color = AnonTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // 상대방 신고 버튼
                    IconButton(onClick = { reportingUser = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "상대방 신고",
                            tint = AnonError.copy(alpha = 0.85f)
                        )
                    }

                    // 채팅방 나가기 버튼
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "채팅방 나가기",
                            tint = AnonTextSecondary
                        )
                    }
                }
            }
        },
        containerColor = AnonBackground,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AnonSurface)
                    .navigationBarsPadding()
                    .padding(8.dp)
            ) {
                // 실시간 필터 경고 표시
                if (liveFilter.hasViolation) {
                    Surface(
                        color = AnonWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnonWarning.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AnonWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "보안 감지: ${liveFilter.violationTypes.joinToString { it.label }} -> 전송 시 자동 마스킹(***)됩니다.",
                                color = AnonWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("메시지를 입력하세요 (전화번호/욕설 마스킹)") },
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

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                repository.sendMessage(room.id, inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) AnonPrimary else AnonSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = if (inputText.isNotBlank()) Color.White else AnonTextMuted
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(room.messages) { msg ->
                ChatBubble(message = msg)
            }
        }
    }

    if (reportingUser) {
        SafetyReportDialog(
            targetNickname = room.peerUser.nickname,
            reportType = ReportTargetType.USER,
            onDismiss = { reportingUser = false },
            onSubmitReport = { reason, shouldBlock ->
                repository.reportChatRoom(room.id, reason, shouldBlock)
                reportingUser = false
                onBack()
            }
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isSystem = message.senderId == "system"

    if (isSystem) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Surface(
                color = AnonSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder)
            ) {
                Text(
                    text = message.text,
                    color = AnonTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        val isMe = message.isFromMe

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isMe) {
                Text(
                    text = message.senderNickname,
                    color = AnonTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Surface(
                color = if (isMe) AnonPrimary else AnonSurfaceElevated,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 16.dp
                ),
                border = if (isMe) null else androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.text,
                        color = if (isMe) Color.White else AnonTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )

                    if (message.isFiltered) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🔒 안전 필터링 마스킹됨",
                            color = if (isMe) Color.White.copy(alpha = 0.7f) else AnonWarning,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = message.timestamp,
                color = AnonTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
            )
        }
    }
}
