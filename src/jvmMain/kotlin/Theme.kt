import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

// نفس ألوان تصميم index.html الأصلي (:root في الـ CSS) عشان الهوية البصرية تفضل زي ما هي
object AppColors {
    val bg = Color(0xFF07080A)
    val panel = Color(0xFF111419)
    val panel2 = Color(0xFF181B21)
    val line = Color(0xFF2A2F38)
    val orange = Color(0xFFFF8A18)
    val orange2 = Color(0xFFFFAD55)
    val text = Color(0xFFF3F4F6)
    val muted = Color(0xFF969EAA)
    val green = Color(0xFF36D399)
    val red = Color(0xFFEF6464)
    val topBar = Color(0xFF101217)
    val sidebarBg = Color(0xFF0E1014)
    val mainBg = Color(0xFF090A0D)
    val fieldBg = Color(0xFF090B0F)
    val fieldBorder = Color(0xFF353B45)
    val danger = Color(0xFF422327)
    val dangerText = Color(0xFFFFB2B2)
    val boardFill = Color(0xFFCBB992)
    val boardBorder = Color(0xFF8F7D5D)
    val partFill = Color(0x52FF8A18) // rgba(255,138,24,.32)
    val partBorder = Color(0xFF171717)
}

fun appDarkColors(): Colors = darkColors(
    primary = AppColors.orange,
    primaryVariant = AppColors.orange2,
    onPrimary = Color(0xFF111111),
    background = AppColors.bg,
    onBackground = AppColors.text,
    surface = AppColors.panel,
    onSurface = AppColors.text,
    error = AppColors.red
)
