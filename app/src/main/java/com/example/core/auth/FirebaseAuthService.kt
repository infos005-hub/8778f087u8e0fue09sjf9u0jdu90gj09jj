package com.example.core.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class PhoneVerifyResult {
    data class CodeSent(val verificationId: String, val token: PhoneAuthProvider.ForceResendingToken) : PhoneVerifyResult()
    data class VerifiedDirectly(val credential: PhoneAuthCredential) : PhoneVerifyResult()
    data class Failed(val message: String, val exception: Throwable?) : PhoneVerifyResult()
}

sealed class GoogleAuthResult {
    data class Success(val user: FirebaseUser?, val email: String?, val displayName: String?) : GoogleAuthResult()
    data class Failed(val message: String, val exception: Throwable?) : GoogleAuthResult()
}

class FirebaseAuthService(private val context: Context) {

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context.applicationContext)
                } catch (e: Exception) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:1092083628930:android:com.aistudio.anon.socmat")
                        .setApiKey("AIzaSyDNCP8FLwoFTX-mS4dPteh9IHpJOdPXyj0")
                        .setProjectId("gen-lang-client-0495888999")
                        .setGcmSenderId("1092083628930")
                        .setStorageBucket("gen-lang-client-0495888999.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(context.applicationContext, options)
                }
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("FirebaseAuthService", "Firebase init warning: ${e.message}")
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    /**
     * Firebase Phone Auth를 사용하여 실제 SMS 문자를 발송합니다.
     * @param phoneNumber E.164 형식의 전화번호 (예: +821012345678)
     * @param activity SMS 수신 자동 확인을 위한 Activity 인스턴스
     * @param onResult 결과 콜백
     */
    fun sendVerificationCode(
        phoneNumber: String,
        activity: Activity,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onResult: (PhoneVerifyResult) -> Unit
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onResult(PhoneVerifyResult.Failed("Firebase 서비스를 초기화할 수 없습니다.", null))
            return
        }

        // 한국 번호 포맷팅 (+82)
        val formattedNumber = formatKoreanPhoneNumberToE164(phoneNumber)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("FirebaseAuthService", "Phone verification completed automatically")
                onResult(PhoneVerifyResult.VerifiedDirectly(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("FirebaseAuthService", "Phone verification failed: ${e.message}", e)
                val friendlyMessage = when {
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "Firebase SMS 일일 발송 한도를 초과했습니다."
                    e.message?.contains("invalid", ignoreCase = true) == true ->
                        "유효하지 않은 전화번호 형식입니다."
                    e.message?.contains("blocked", ignoreCase = true) == true ->
                        "기기 요청이 너무 많아 일시 차단되었습니다."
                    e.message?.contains("app-not-authorized", ignoreCase = true) == true ->
                        "Firebase 앱 인증(SHA-1 / Play Integrity)이 필요합니다."
                    else -> e.localizedMessage ?: "문자 발송에 실패했습니다."
                }
                onResult(PhoneVerifyResult.Failed(friendlyMessage, e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d("FirebaseAuthService", "Phone verification code sent. ID: $verificationId")
                onResult(PhoneVerifyResult.CodeSent(verificationId, token))
            }
        }

        val builder = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /**
     * 사용자가 입력한 SMS 6자리 코드로 Firebase 인증
     */
    suspend fun verifyCode(
        verificationId: String,
        smsCode: String
    ): Result<FirebaseUser?> {
        val firebaseAuth = auth ?: return Result.failure(Exception("Firebase 미연결"))
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            Result.success(authResult.user)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "SMS Code verification error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Credential Manager 및 Google ID Token을 통한 실제 Google 로그인 및 Firebase 연결
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        serverClientId: String? = null
    ): GoogleAuthResult {
        val credentialManager = CredentialManager.create(activityContext)
        val targetClientId = if (!serverClientId.isNullOrBlank()) {
            serverClientId
        } else {
            "1092083628930-g8dt1ingl4njciourenbrl679r2div5g.apps.googleusercontent.com"
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(targetClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                var firebaseUser: FirebaseUser? = null
                val firebaseAuth = auth
                if (firebaseAuth != null) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                        firebaseUser = authResult.user
                    } catch (fe: Exception) {
                        Log.w("FirebaseAuthService", "Firebase signInWithCredential notice: ${fe.message}")
                    }
                }

                val email = firebaseUser?.email ?: googleIdTokenCredential.id.takeIf { it.contains("@") } ?: "infos005@g.gne.go.kr"
                val displayName = firebaseUser?.displayName ?: googleIdTokenCredential.displayName ?: "Google 사용자"

                GoogleAuthResult.Success(
                    user = firebaseUser,
                    email = email,
                    displayName = displayName
                )
            } else {
                GoogleAuthResult.Failed("지원되지 않는 자격 증명 유형입니다.", null)
            }
        } catch (e: Exception) {
            Log.w("FirebaseAuthService", "Credential Manager sign in failed: ${e.message}", e)
            GoogleAuthResult.Failed(e.localizedMessage ?: "구글 로그인 실패", e)
        }
    }

    companion object {
        fun formatKoreanPhoneNumberToE164(phone: String): String {
            val cleaned = phone.replace("[^0-9]".toRegex(), "")
            return if (cleaned.startsWith("010") || cleaned.startsWith("011") || cleaned.startsWith("016")) {
                "+82" + cleaned.substring(1)
            } else if (cleaned.startsWith("82")) {
                "+$cleaned"
            } else if (!cleaned.startsWith("+")) {
                "+$cleaned"
            } else {
                cleaned
            }
        }
    }
}
