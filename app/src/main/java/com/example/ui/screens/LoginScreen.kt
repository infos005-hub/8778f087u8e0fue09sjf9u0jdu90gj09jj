package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.AppRepository
import com.example.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 간편한 아이디 기반 로그인 및 회원가입 화면
 * - 아이디 영구 저장 및 자동완성 빠른 전환 지원
 * - 프로필 생성 시 [한줄소개], [성격], [취미], [선호하는 친구], [특이사항] 기재
 * - 나이와 성별 프로필 공유 / 비공개(숨김) 선택 토글 지원
 */
enum class AuthMode {
    CREATE_ACCOUNT, // 아이디 새로 만들기
    LOGIN           // 기존 아이디로 로그인
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    repository: AppRepository,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // 저장된 아이디 목록 및 마지막 사용 아이디
    val lastSavedId = remember { repository.getLastSavedId() }
    val initialSavedIds = remember { repository.getAllSavedIds().distinct() }
    var savedIdList by remember { mutableStateOf(initialSavedIds) }
    var rememberId by remember { mutableStateOf(repository.isRememberIdEnabled()) }

    // 저장된 아이디가 있다면 기본으로 로그인 화면을 띄우고, 없으면 아이디 만들기 화면을 띄움
    var authMode by remember {
        mutableStateOf(if (lastSavedId.isNotBlank()) AuthMode.LOGIN else AuthMode.CREATE_ACCOUNT)
    }

    // [로그인 필드 상태]
    var loginId by remember { mutableStateOf(lastSavedId) }
    var loginPassword by remember { mutableStateOf(repository.getSavedAccountPassword(lastSavedId) ?: "") }
    var isLoginPasswordVisible by remember { mutableStateOf(false) }
    var isSyncingCloud by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val currentSaved = repository.getAllSavedIds().distinct()
        savedIdList = currentSaved
        val currentLast = repository.getLastSavedId()
        if (currentLast.isNotBlank()) {
            loginId = currentLast
            repository.getSavedAccountPassword(currentLast)?.let { loginPassword = it }
            authMode = AuthMode.LOGIN
        } else if (currentSaved.isNotEmpty()) {
            val first = currentSaved.first()
            loginId = first
            repository.getSavedAccountPassword(first)?.let { loginPassword = it }
            authMode = AuthMode.LOGIN
        } else {
            authMode = AuthMode.CREATE_ACCOUNT
            loginId = ""
        }

        // 앱 재빌드/업그레이드/재설치 시에도 계정 정보가 즉시 복원되도록 클라우드 백그라운드 자동 동기화
        isSyncingCloud = true
        repository.syncCloudAccountsAndSession { success ->
            isSyncingCloud = false
            val refreshed = repository.getAllSavedIds().distinct()
            if (refreshed.isNotEmpty()) {
                savedIdList = refreshed
                if (loginId.isBlank()) {
                    val newLast = repository.getLastSavedId().ifBlank { refreshed.first() }
                    loginId = newLast
                    repository.getSavedAccountPassword(newLast)?.let { loginPassword = it }
                    authMode = AuthMode.LOGIN
                }
            }
        }
    }

    // [아이디 만들기 필드 상태]
    var registerId by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerPasswordConfirm by remember { mutableStateOf("") }
    var registerNickname by remember { mutableStateOf("") }
    var registerAgeText by remember { mutableStateOf("24") }
    var registerAgeError by remember { mutableStateOf<String?>(null) }
    var registerGender by remember { mutableStateOf("여성") }

    // 나이 및 성별 선택적 공개/숨김 플래그
    var isAgeVisible by remember { mutableStateOf(true) }
    var isGenderVisible by remember { mutableStateOf(true) }

    // 필수 및 맞춤 프로필 항목
    var registerBio by remember { mutableStateOf("솔직하고 따뜻한 일상 대화를 나누고 싶어요 ✨") }
    var registerPersonality by remember { mutableStateOf("다정하고 긍정적인") }
    var registerFriendStyle by remember { mutableStateOf("편하게 일상 나눌 친구") }
    var registerSpecialNotes by remember { mutableStateOf("") }

    // 취미 다중 선택 목록 및 커스텀 취미 추가 입력
    var registerInterests by remember {
        mutableStateOf(listOf("☕ 카페투어", "📚 독서", "🎵 음악감상"))
    }
    var customInterestInput by remember { mutableStateOf("") }

    var selectedAvatarResId by remember { mutableStateOf<Int?>(R.drawable.anon_avatar_demo_1789521505031) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var isRegisterPasswordVisible by remember { mutableStateOf(false) }

    // [중복 확인 및 로딩 상태]
    var isCheckingDuplicate by remember { mutableStateOf(false) }
    var duplicateCheckResult by remember { mutableStateOf<AppRepository.IdAvailabilityResult?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // 에러 메시지
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val avatarPresets = remember {
        listOf(
            R.drawable.anon_avatar_demo_1789521505031 to "클래식 3D",
            R.drawable.anon_avatar_cat_1789530718973 to "네온 냥이",
            R.drawable.anon_avatar_star_1789530730730 to "코스믹 별빛",
            R.drawable.anon_avatar_leaf_1789530742900 to "말차 플랜트"
        )
    }

    val popularHobbies = remember {
        listOf(
            "☕ 카페투어", "📚 독서", "🏃 러닝/운동", "🎵 음악감상",
            "🎬 영화/넷플", "🎮 게임", "🍳 요리/맛집", "🐱 반려동물",
            "✈️ 여행", "📸 사진", "🎨 전시/미술", "🏕️ 캠핑", "🧘 요가/명상"
        )
    }

    val personalityPresets = remember {
        listOf("다정다감", "차분하고 조용한", "밝고 긍정적인", "솔직유쾌", "경청을 잘하는", "사려깊은", "호기심 많은")
    }

    val friendStylePresets = remember {
        listOf("티키타카 잘 통하는 친구", "편하게 일상 나눌 친구", "고민 상담 잘해주는 친구", "취향/취미 같이 나눌 친구", "예의 있고 배려심 넘치는 친구")
    }

    val specialNotePresets = remember {
        listOf("🌙 새벽에 주로 활동해요", "🐢 답장이 조금 느릴 수 있어요", "🐾 고양이 집사예요", "🎧 항상 이어폰을 끼고 있어요", "🚫 예의 없는 대화는 사양해요")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            selectedAvatarResId = null
        }
    }

    // 회원가입 실행 함수
    fun handleRegister() {
        focusManager.clearFocus()
        errorMessage = null

        val cleanId = registerId.trim().lowercase()
        if (cleanId.length < 3) {
            errorMessage = "아이디를 영문/숫자 3자 이상으로 입력해주세요."
            return
        }
        if (registerPassword.length < 4) {
            errorMessage = "비밀번호를 4자 이상으로 입력해주세요."
            return
        }
        val effectiveConfirm = if (registerPasswordConfirm.isBlank()) registerPassword else registerPasswordConfirm
        if (registerPassword != effectiveConfirm) {
            errorMessage = "비밀번호 확인이 일치하지 않습니다."
            return
        }
        if (registerNickname.trim().isBlank()) {
            errorMessage = "활동할 닉네임을 입력해주세요."
            return
        }

        val parsedAge = registerAgeText.trim().toIntOrNull()
        if (parsedAge == null || parsedAge !in 0..100) {
            errorMessage = "나이는 0세부터 100세 사이의 숫자로 올바르게 입력해주세요 (음수 또는 100 초과 불가)."
            return
        }
        val safeAge = parsedAge.coerceIn(0, 100)

        coroutineScope.launch {
            isRegistering = true
            errorMessage = null

            // 아이디 저장 설정 반영
            repository.saveRememberedId(cleanId, rememberId)

            val result = repository.registerWithId(
                id = cleanId,
                password = registerPassword,
                nickname = registerNickname.trim(),
                gender = registerGender,
                birthYear = 2026 - safeAge,
                age = safeAge,
                bio = registerBio.trim(),
                personality = registerPersonality.trim(),
                interests = if (registerInterests.isNotEmpty()) registerInterests else listOf("일상", "소통", "음악"),
                preferredFriendStyle = registerFriendStyle.trim(),
                specialNotes = registerSpecialNotes.trim(),
                isAgeVisible = isAgeVisible,
                isGenderVisible = isGenderVisible,
                avatarResId = selectedAvatarResId,
                avatarUri = selectedAvatarUri?.toString()
            )

            isRegistering = false
            result.onSuccess { profile ->
                savedIdList = repository.getAllSavedIds()
                Toast.makeText(context, "'${profile.nickname}'님 계정이 안전하게 저장되었습니다! 입장합니다 🎉", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            }.onFailure { error ->
                errorMessage = error.message ?: "회원가입 중 오류가 발생했습니다."
            }
        }
    }

    // 로그인 실행 함수
    fun handleLogin() {
        focusManager.clearFocus()
        errorMessage = null

        val cleanId = loginId.trim().lowercase()
        if (cleanId.isBlank()) {
            errorMessage = "아이디를 입력해주세요."
            return
        }
        if (loginPassword.isBlank()) {
            errorMessage = "비밀번호를 입력해주세요."
            return
        }

        coroutineScope.launch {
            isLoggingIn = true
            errorMessage = null

            // 아이디 기억 설정 반영
            repository.saveRememberedId(cleanId, rememberId)

            val result = repository.loginWithId(
                id = cleanId,
                password = loginPassword
            )

            isLoggingIn = false
            result.onSuccess { profile ->
                savedIdList = repository.getAllSavedIds()
                Toast.makeText(context, "'${profile.nickname}'님 환영합니다 ✨", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            }.onFailure { error ->
                errorMessage = error.message ?: "로그인 중 오류가 발생했습니다."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 앱 로고 아이콘
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(InstagramGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Anon Logo",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 타이틀
            Text(
                text = "Anon",
                color = AnonTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "100% 완전 익명 소통 커뮤니티",
                color = AnonTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 탭 선택기: [아이디 만들기] vs [로그인]
            Surface(
                color = AnonSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // 아이디 만들기 탭
                    Surface(
                        color = if (authMode == AuthMode.CREATE_ACCOUNT) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = if (authMode == AuthMode.CREATE_ACCOUNT) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable {
                                authMode = AuthMode.CREATE_ACCOUNT
                                errorMessage = null
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "아이디 만들기",
                                color = if (authMode == AuthMode.CREATE_ACCOUNT) AnonPrimary else AnonTextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (authMode == AuthMode.CREATE_ACCOUNT) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // 로그인 탭
                    Surface(
                        color = if (authMode == AuthMode.LOGIN) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = if (authMode == AuthMode.LOGIN) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable {
                                authMode = AuthMode.LOGIN
                                errorMessage = null
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "로그인",
                                color = if (authMode == AuthMode.LOGIN) AnonPrimary else AnonTextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (authMode == AuthMode.LOGIN) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 에러 메시지 표시 배너
            if (errorMessage != null) {
                Surface(
                    color = AnonError.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonError.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AnonError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = AnonError,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 모드별 입력 폼
            AnimatedContent(
                targetState = authMode,
                label = "AuthModeTransition"
            ) { mode ->
                if (mode == AuthMode.CREATE_ACCOUNT) {
                    // ============================================
                    // 1. 아이디 만들기 (신규 가입 & 상세 프로필 설정)
                    // ============================================
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 계정 중복 또는 이미 등록된 계정 감지 배너
                        val isAlreadyRegisteredHere = registerId.isNotBlank() && repository.isAccountRegisteredOnDevice(registerId)
                        if (isAlreadyRegisteredHere) {
                            Surface(
                                color = AnonPrimary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AnonPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = AnonPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "이 기기에 이미 저장된 내 아이디입니다!",
                                            color = AnonPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "비밀번호를 입력하고 바로 시작하거나 로그인 탭으로 이동할 수 있습니다.",
                                            color = AnonTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            loginId = registerId
                                            authMode = AuthMode.LOGIN
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("로그인", color = AnonPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 저장된 기존 계정이 있을 때 빠른 로그인 안내 배너 (앱 수정/업그레이드 후 빠른 복구)
                        if (savedIdList.isNotEmpty()) {
                            Surface(
                                color = AnonPrimary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AnonPrimary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        authMode = AuthMode.LOGIN
                                        val target = savedIdList.first()
                                        loginId = target
                                        repository.getSavedAccountPassword(target)?.let { loginPassword = it }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = AnonPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "저장된 기존 계정 발견 (${savedIdList.size}개)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AnonPrimary
                                        )
                                        Text(
                                            text = "아이디 [${savedIdList.joinToString(", ")}] (탭하여 바로 로그인)",
                                            fontSize = 11.sp,
                                            color = AnonTextSecondary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = AnonPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 아이디 입력 라벨
                        Text(
                            text = "아이디 (필수)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = registerId,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                                    if (filtered != registerId) {
                                        registerId = filtered
                                        duplicateCheckResult = null
                                        errorMessage = null
                                    }
                                },
                                placeholder = {
                                    Text("영문/숫자 3자 이상 (예: myid123)", color = AnonTextMuted, fontSize = 13.sp)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AlternateEmail, contentDescription = null, tint = AnonSecondary)
                                },
                                trailingIcon = {
                                    when (duplicateCheckResult) {
                                        AppRepository.IdAvailabilityResult.AVAILABLE -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "사용 가능",
                                                tint = Color(0xFF10B981)
                                            )
                                        }
                                        AppRepository.IdAvailabilityResult.ALREADY_TAKEN -> {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "중복된 아이디",
                                                tint = Color(0xFFEF4444)
                                            )
                                        }
                                        else -> null
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (duplicateCheckResult == AppRepository.IdAvailabilityResult.AVAILABLE) Color(0xFF10B981) else AnonPrimary,
                                    unfocusedBorderColor = if (duplicateCheckResult == AppRepository.IdAvailabilityResult.AVAILABLE) Color(0xFF10B981).copy(alpha = 0.6f) else AnonSurfaceBorderStrong,
                                    focusedContainerColor = AnonSurfaceElevated,
                                    unfocusedContainerColor = AnonSurfaceElevated
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // 중복 확인 버튼
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanId = registerId.trim().lowercase()
                                    if (cleanId.length < 3) {
                                        errorMessage = "아이디를 영문/숫자 3자 이상 입력해주세요."
                                        return@OutlinedButton
                                    }
                                    coroutineScope.launch {
                                        isCheckingDuplicate = true
                                        errorMessage = null
                                        val check = repository.checkIdAvailability(cleanId)
                                        duplicateCheckResult = check
                                        isCheckingDuplicate = false
                                        if (check == AppRepository.IdAvailabilityResult.AVAILABLE) {
                                            Toast.makeText(context, "✅ 사용 가능한 멋진 아이디입니다!", Toast.LENGTH_SHORT).show()
                                        } else if (check == AppRepository.IdAvailabilityResult.ALREADY_TAKEN) {
                                            if (repository.isAccountRegisteredOnDevice(cleanId)) {
                                                Toast.makeText(context, "💡 내 기기에 이미 등록된 아이디입니다! 그대로 사용하실 수 있습니다.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                errorMessage = "이미 다른 회원이 사용 중인 아이디입니다. 다른 아이디를 입력해주세요."
                                            }
                                        } else {
                                            errorMessage = "아이디는 영문, 숫자 조합 3자 이상이어야 합니다."
                                        }
                                    }
                                },
                                enabled = registerId.trim().length >= 3 && !isCheckingDuplicate,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (registerId.trim().length >= 3) AnonPrimary else AnonSurfaceBorderStrong
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AnonPrimary,
                                    disabledContentColor = AnonTextMuted
                                ),
                                modifier = Modifier.height(54.dp)
                            ) {
                                if (isCheckingDuplicate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = AnonPrimary
                                    )
                                } else {
                                    Text(
                                        text = "중복 확인",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 저장된 아이디 빠른 선택 칩
                        if (savedIdList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("저장된 아이디: ", fontSize = 11.sp, color = AnonTextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(savedIdList) { sid ->
                                        Surface(
                                            color = AnonSurfaceElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = sid,
                                                    fontSize = 11.sp,
                                                    color = AnonPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        registerId = sid
                                                        duplicateCheckResult = null
                                                        errorMessage = null
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "아이디 삭제",
                                                    tint = AnonTextMuted,
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clickable {
                                                            repository.removeSavedId(sid)
                                                            savedIdList = repository.getAllSavedIds()
                                                            if (registerId == sid) registerId = ""
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 아이디 저장 체크박스
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    rememberId = !rememberId
                                    if (registerId.isNotBlank()) {
                                        repository.saveRememberedId(registerId, rememberId)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = rememberId,
                                onCheckedChange = {
                                    rememberId = it
                                    if (registerId.isNotBlank()) {
                                        repository.saveRememberedId(registerId, it)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AnonPrimary)
                            )
                            Text(
                                text = "이 기기에 아이디 저장 (자동 기억)",
                                fontSize = 12.sp,
                                color = AnonTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 비밀번호 입력
                        Text(
                            text = "비밀번호 (필수)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerPassword,
                            onValueChange = {
                                registerPassword = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("비밀번호 4자 이상", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = AnonSecondary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isRegisterPasswordVisible = !isRegisterPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isRegisterPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = AnonTextMuted
                                    )
                                }
                            },
                            visualTransformation = if (isRegisterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 비밀번호 확인
                        Text(
                            text = "비밀번호 확인",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerPasswordConfirm,
                            onValueChange = {
                                registerPasswordConfirm = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("비밀번호를 한 번 더 입력해주세요", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = AnonSecondary)
                            },
                            trailingIcon = {
                                if (registerPassword.isNotBlank() && registerPassword == registerPasswordConfirm) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "비밀번호 일치",
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            },
                            visualTransformation = if (isRegisterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 활동 닉네임 직접 입력
                        Text(
                            text = "활동 닉네임 (필수)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerNickname,
                            onValueChange = {
                                registerNickname = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("활동할 닉네임 입력 (2~12자)", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Badge, contentDescription = null, tint = AnonSecondary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [나이 설정] (0 ~ 100세 직접 숫자 입력) & 나이 선택적 공개 토글
                        // ============================================
                        val currentYear = 2026
                        val currentAgeInt = registerAgeText.trim().toIntOrNull()
                        val calculatedBirthYear = if (currentAgeInt != null && currentAgeInt in 0..100) {
                            currentYear - currentAgeInt
                        } else null

                        Surface(
                            color = AnonSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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

                                    // 나이 공개/숨김 스위치
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isAgeVisible) "나이 공개" else "나이 비공개",
                                            fontSize = 12.sp,
                                            color = if (isAgeVisible) AnonPrimary else AnonTextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(
                                            checked = isAgeVisible,
                                            onCheckedChange = { isAgeVisible = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AnonPrimary
                                            ),
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 나이 직접 숫자 입력 필드 (슬라이더 대신 숫자 키패드 직접 입력)
                                OutlinedTextField(
                                    value = registerAgeText,
                                    onValueChange = { input ->
                                        // 음수 기호(-) 및 문자 자동 차단: 오직 숫자만 허용
                                        val digitsOnly = input.filter { it.isDigit() }
                                        if (digitsOnly.isEmpty()) {
                                            registerAgeText = ""
                                            registerAgeError = "나이를 입력해 주세요 (0~100)."
                                        } else {
                                            val parsed = digitsOnly.toIntOrNull()
                                            if (parsed == null || parsed > 100) {
                                                // 100 초과(예: 200 등) 입력 즉시 차단
                                                registerAgeError = "나이는 최대 100세까지만 입력 가능합니다."
                                            } else {
                                                registerAgeText = parsed.toString()
                                                registerAgeError = null
                                            }
                                        }
                                        errorMessage = null
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
                                    isError = registerAgeError != null,
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

                                if (registerAgeError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = registerAgeError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isAgeVisible) {
                                        if (currentAgeInt != null && currentAgeInt in 0..100) {
                                            "💡 프로필에 '${currentAgeInt}세'로 표시됩니다."
                                        } else {
                                            "💡 프로필에 나이가 표시됩니다."
                                        }
                                    } else {
                                        "🔒 다른 사용자에게 나이가 숨겨지고 '비공개'로 표시됩니다."
                                    },
                                    fontSize = 11.sp,
                                    color = if (isAgeVisible) AnonPrimary else AnonTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ============================================
                        // [성별] & 성별 선택적 공개 토글
                        // ============================================
                        Surface(
                            color = AnonSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "성별",
                                        color = AnonTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // 성별 공개/숨김 스위치
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isGenderVisible) "성별 공개" else "성별 비공개",
                                            fontSize = 12.sp,
                                            color = if (isGenderVisible) AnonPrimary else AnonTextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(
                                            checked = isGenderVisible,
                                            onCheckedChange = { isGenderVisible = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AnonPrimary
                                            ),
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("여성", "남성", "기타").forEach { g ->
                                        val isSelected = registerGender == g
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) AnonPrimary.copy(alpha = 0.12f) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) AnonPrimary else AnonSurfaceBorderStrong
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clickable { registerGender = g }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = g,
                                                    color = if (isSelected) AnonPrimary else AnonTextSecondary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isGenderVisible) "💡 프로필에 '${registerGender}'으로 표시됩니다." else "🔒 다른 사용자에게 성별이 숨겨지고 '비공개'로 표시됩니다.",
                                    fontSize = 11.sp,
                                    color = if (isGenderVisible) AnonPrimary else AnonTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [한줄소개]
                        // ============================================
                        Text(
                            text = "[한줄소개] 💬",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerBio,
                            onValueChange = { if (it.length <= 100) registerBio = it },
                            placeholder = {
                                Text("예: 솔직하고 따뜻한 일상 대화를 나누고 싶어요 ✨", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 한줄소개 추천 칩
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val bioPresets = listOf(
                                "솔직하고 편안한 일상 대화 나눠요 ☕",
                                "취향 맞는 친구와 음악/영화 이야기해요 🎵",
                                "고민이나 속마음 편하게 털어놓을 분 환영해요 🌿",
                                "새벽에 잔잔한 대화 나눌 친구 찾아요 🌙"
                            )
                            items(bioPresets) { preset ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                                    modifier = Modifier.clickable { registerBio = preset }
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        color = AnonTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [성격]
                        // ============================================
                        Text(
                            text = "[성격] 🌿",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerPersonality,
                            onValueChange = { registerPersonality = it },
                            placeholder = {
                                Text("예: 다정다감하고 긍정적인, 배려심 깊은", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(personalityPresets) { p ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (registerPersonality.contains(p)) AnonPrimary.copy(alpha = 0.12f) else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (registerPersonality.contains(p)) AnonPrimary else AnonSurfaceBorderStrong
                                    ),
                                    modifier = Modifier.clickable {
                                        registerPersonality = if (registerPersonality.isBlank()) p else "${registerPersonality}, $p"
                                    }
                                ) {
                                    Text(
                                        text = "+ $p",
                                        fontSize = 11.sp,
                                        color = if (registerPersonality.contains(p)) AnonPrimary else AnonTextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [취미] (다중 선택 및 직접 추가)
                        // ============================================
                        Text(
                            text = "[취미] 🎯 (탭하여 선택)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // 취미 칩 플로우 레이아웃
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            popularHobbies.forEach { hobby ->
                                val isSelected = registerInterests.contains(hobby)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) AnonPrimary else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) AnonPrimary else AnonSurfaceBorderStrong
                                    ),
                                    modifier = Modifier.clickable {
                                        registerInterests = if (isSelected) {
                                            registerInterests - hobby
                                        } else {
                                            registerInterests + hobby
                                        }
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

                        // 커스텀 취미 직접 입력
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customInterestInput,
                                onValueChange = { customInterestInput = it },
                                placeholder = { Text("취미 직접 입력 (예: 보드게임, 베이킹)", color = AnonTextMuted, fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnonPrimary,
                                    unfocusedBorderColor = AnonSurfaceBorderStrong,
                                    focusedContainerColor = AnonSurfaceElevated,
                                    unfocusedContainerColor = AnonSurfaceElevated
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val trimmed = customInterestInput.trim()
                                    if (trimmed.isNotBlank() && !registerInterests.contains(trimmed)) {
                                        registerInterests = registerInterests + trimmed
                                        customInterestInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("추가", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [선호하는 친구]
                        // ============================================
                        Text(
                            text = "[선호하는 친구] 🤝",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerFriendStyle,
                            onValueChange = { registerFriendStyle = it },
                            placeholder = {
                                Text("예: 티키타카 잘 통하는 친구, 비밀 보장되는 편한 친구", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(friendStylePresets) { style ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (registerFriendStyle == style) AnonPrimary.copy(alpha = 0.12f) else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (registerFriendStyle == style) AnonPrimary else AnonSurfaceBorderStrong
                                    ),
                                    modifier = Modifier.clickable { registerFriendStyle = style }
                                ) {
                                    Text(
                                        text = style,
                                        fontSize = 11.sp,
                                        color = if (registerFriendStyle == style) AnonPrimary else AnonTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ============================================
                        // [특이사항] (선택사항)
                        // ============================================
                        Text(
                            text = "[특이사항] 💡 (선택 기재)",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = registerSpecialNotes,
                            onValueChange = { registerSpecialNotes = it },
                            placeholder = {
                                Text("예: 새벽형 인간이에요, 답장이 조금 느려도 이해해주세요", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(specialNotePresets) { note ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (registerSpecialNotes == note) AnonPrimary.copy(alpha = 0.12f) else AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (registerSpecialNotes == note) AnonPrimary else AnonSurfaceBorderStrong
                                    ),
                                    modifier = Modifier.clickable { registerSpecialNotes = note }
                                ) {
                                    Text(
                                        text = note,
                                        fontSize = 11.sp,
                                        color = if (registerSpecialNotes == note) AnonPrimary else AnonTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 캐릭터 아바타 선택
                        Text(
                            text = "캐릭터 아바타 선택",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            avatarPresets.forEach { (resId, name) ->
                                val isSelected = selectedAvatarResId == resId && selectedAvatarUri == null
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedAvatarResId = resId
                                            selectedAvatarUri = null
                                        }
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) AnonPrimary else AnonSurfaceBorderStrong
                                        ),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = name,
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        color = if (isSelected) AnonPrimary else AnonTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // 갤러리 업로드 버튼
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = AnonSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (selectedAvatarUri != null) 3.dp else 1.dp,
                                        color = if (selectedAvatarUri != null) AnonPrimary else AnonSurfaceBorderStrong
                                    ),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    if (selectedAvatarUri != null) {
                                        AsyncImage(
                                            model = selectedAvatarUri,
                                            contentDescription = "내 사진",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.PhotoCamera,
                                                contentDescription = "갤러리",
                                                tint = AnonTextSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "직접 선택",
                                    fontSize = 10.sp,
                                    color = if (selectedAvatarUri != null) AnonPrimary else AnonTextSecondary,
                                    fontWeight = if (selectedAvatarUri != null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 아이디 만들고 시작하기 버튼
                        Button(
                            onClick = { handleRegister() },
                            enabled = !isRegistering,
                            colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isRegistering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "계정 생성 및 영구 저장 중...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "아이디 만들고 시작하기 ✨",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    authMode = AuthMode.LOGIN
                                    errorMessage = null
                                }
                            ) {
                                Text(
                                    text = "이미 생성한 아이디가 있으신가요? 로그인하기",
                                    color = AnonTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    // ============================================
                    // 2. 기존 아이디로 로그인
                    // ============================================
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 아이디 입력
                        Text(
                            text = "아이디",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = loginId,
                            onValueChange = {
                                loginId = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("등록한 아이디 입력", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = AnonSecondary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 저장된 아이디 빠른 선택 칩
                        if (savedIdList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("저장된 아이디: ", fontSize = 11.sp, color = AnonTextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(savedIdList) { sid ->
                                        Surface(
                                            color = if (loginId == sid) AnonPrimary.copy(alpha = 0.12f) else AnonSurfaceElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (loginId == sid) AnonPrimary else AnonSurfaceBorderStrong
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                            ) {
                                                Text(
                                                    text = sid,
                                                    fontSize = 11.sp,
                                                    color = if (loginId == sid) AnonPrimary else AnonTextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        loginId = sid
                                                        repository.getSavedAccountPassword(sid)?.let { pwd ->
                                                            loginPassword = pwd
                                                        }
                                                        errorMessage = null
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "아이디 삭제",
                                                    tint = AnonTextMuted,
                                                    modifier = Modifier
                                                        .size(13.dp)
                                                        .clickable {
                                                            repository.removeSavedId(sid)
                                                            val updated = repository.getAllSavedIds()
                                                            savedIdList = updated
                                                            if (loginId == sid) {
                                                                loginId = updated.firstOrNull() ?: ""
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 아이디 저장 체크박스
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    rememberId = !rememberId
                                    if (loginId.isNotBlank()) {
                                        repository.saveRememberedId(loginId, rememberId)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = rememberId,
                                onCheckedChange = {
                                    rememberId = it
                                    if (loginId.isNotBlank()) {
                                        repository.saveRememberedId(loginId, it)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AnonPrimary)
                            )
                            Text(
                                text = "이 기기에 아이디 저장 (자동 기억)",
                                fontSize = 12.sp,
                                color = AnonTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 비밀번호 입력
                        Text(
                            text = "비밀번호",
                            color = AnonTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = {
                                loginPassword = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text("비밀번호 입력", color = AnonTextMuted, fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = AnonSecondary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isLoginPasswordVisible = !isLoginPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isLoginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = AnonTextMuted
                                    )
                                }
                            },
                            visualTransformation = if (isLoginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { handleLogin() }),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnonPrimary,
                                unfocusedBorderColor = AnonSurfaceBorderStrong,
                                focusedContainerColor = AnonSurfaceElevated,
                                unfocusedContainerColor = AnonSurfaceElevated
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 로그인 버튼
                        Button(
                            onClick = { handleLogin() },
                            enabled = !isLoggingIn,
                            colors = ButtonDefaults.buttonColors(containerColor = AnonPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "로그인 중...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Login,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "로그인 ✨",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 클라우드 계정 정보 및 세션 복원 버튼
                        OutlinedButton(
                            onClick = {
                                isSyncingCloud = true
                                repository.syncCloudAccountsAndSession { success ->
                                    isSyncingCloud = false
                                    savedIdList = repository.getAllSavedIds().distinct()
                                    val last = repository.getLastSavedId().ifBlank { savedIdList.firstOrNull() ?: "" }
                                    if (last.isNotBlank()) {
                                        loginId = last
                                        repository.getSavedAccountPassword(last)?.let { loginPassword = it }
                                    }
                                    val count = savedIdList.size
                                    Toast.makeText(
                                        context,
                                        if (count > 0) "☁️ 클라우드 및 영구 금고에서 계정 ${count}개를 복원했습니다." else "클라우드 계정 동기화를 완료했습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isSyncingCloud,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorderStrong),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AnonTextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isSyncingCloud) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AnonPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("클라우드 계정 복원 중...", fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = AnonPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "클라우드 저장 계정 불러오기 / 복구",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    authMode = AuthMode.CREATE_ACCOUNT
                                    errorMessage = null
                                }
                            ) {
                                Text(
                                    text = "계정이 없으신가요? 아이디 새로 만들기",
                                    color = AnonPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 안심 안내 캡션
            Surface(
                color = AnonSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AnonSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AnonSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "개인정보나 전화번호 없이 아이디만으로 안전하게 소통할 수 있으며, 나이와 성별은 언제든지 자유롭게 숨길 수 있습니다.",
                        color = AnonTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
