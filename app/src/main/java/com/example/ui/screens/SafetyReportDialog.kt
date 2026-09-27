package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class ReportTargetType(
    val titleSuffix: String,
    val reasons: List<String>,
    val blockOptionLabel: String
) {
    USER(
        "사용자 신고하기",
        listOf(
            "개인정보(전화번호/SNS) 유도 및 요구",
            "욕설, 혐오 표현 및 비하 발언",
            "성희롱 또는 불쾌한 성적 언행",
            "상업적 광고 및 사기/피싱 의심",
            "기타 커뮤니티 가이드라인 위반"
        ),
        "이 사용자를 즉시 차단하고 대화에서 제외"
    ),
    POST(
        "게시글 신고하기",
        listOf(
            "욕설, 비하 및 악성 비방",
            "불법 정보 및 성인/음란물",
            "개인정보(연락처/SNS) 무단 노출",
            "상업적 스팸 및 도배 광고",
            "사기/피싱 또는 허위 사실 유포",
            "기타 부적절한 게시글 내용"
        ),
        "이 작성자의 글과 댓글을 즉시 차단하고 숨김"
    ),
    COMMENT(
        "댓글 신고하기",
        listOf(
            "욕설, 조롱 및 악성 비방",
            "개인정보 유출 및 사생활 침해",
            "성희롱 또는 불쾌한 표현",
            "상업적 광고 및 도배",
            "기타 커뮤니티 가이드라인 위반"
        ),
        "이 작성자를 차단하고 댓글 숨김"
    )
}

@Composable
fun SafetyReportDialog(
    targetNickname: String,
    reportType: ReportTargetType = ReportTargetType.USER,
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String, shouldBlock: Boolean) -> Unit
) {
    val reasons = reportType.reasons
    var selectedReason by remember(reportType) { mutableStateOf(reasons[0]) }
    var shouldBlock by remember(reportType) { mutableStateOf(true) }

    val displayTitle = when (reportType) {
        ReportTargetType.USER -> "'$targetNickname' 사용자 신고"
        ReportTargetType.POST -> "'$targetNickname' 게시글 신고"
        ReportTargetType.COMMENT -> "'$targetNickname' 댓글 신고"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnonSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AnonError,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayTitle,
                    color = AnonTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "신고 사유를 선택해 주세요. 접수 즉시 검토되며 부적절한 내용은 신속히 숨김 처리됩니다.",
                    color = AnonTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                reasons.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AnonPrimary.copy(alpha = 0.15f) else AnonSurfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) AnonPrimary else AnonSurfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AnonPrimary,
                                unselectedColor = AnonTextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reason,
                            color = if (isSelected) AnonTextPrimary else AnonTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shouldBlock = !shouldBlock }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = shouldBlock,
                        onCheckedChange = { shouldBlock = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AnonError,
                            checkmarkColor = AnonTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reportType.blockOptionLabel,
                        color = AnonError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReport(selectedReason, shouldBlock) },
                colors = ButtonDefaults.buttonColors(containerColor = AnonError)
            ) {
                Text("신고 및 접수", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = AnonTextSecondary)
            }
        }
    )
}
