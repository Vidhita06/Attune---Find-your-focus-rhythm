package com.attune.app.ui.theme
import androidx.compose.material3.Typography; import androidx.compose.ui.text.TextStyle; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.unit.sp
val AttuneTypography=Typography(
    displayLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=44.sp,lineHeight=52.sp,letterSpacing=(-0.5).sp),
    headlineLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=26.sp,lineHeight=34.sp,letterSpacing=(-0.2).sp),
    headlineMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=18.sp,lineHeight=26.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge=TextStyle(fontWeight=FontWeight.Normal,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontWeight=FontWeight.Normal,fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
    labelMedium=TextStyle(fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=16.sp),
    labelSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=11.sp,lineHeight=16.sp,letterSpacing=0.6.sp)
)
