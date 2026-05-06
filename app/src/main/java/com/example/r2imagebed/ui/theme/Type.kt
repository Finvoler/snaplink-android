package com.example.r2imagebed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
	headlineSmall = TextStyle(
		fontSize = 28.sp,
		fontWeight = FontWeight.SemiBold
	),
	titleMedium = TextStyle(
		fontSize = 18.sp,
		fontWeight = FontWeight.Medium
	),
	bodyLarge = TextStyle(
		fontSize = 16.sp,
		lineHeight = 22.sp
	),
	bodyMedium = TextStyle(
		fontSize = 14.sp,
		lineHeight = 20.sp
	),
	labelLarge = TextStyle(
		fontSize = 14.sp,
		fontWeight = FontWeight.Medium
	)
)