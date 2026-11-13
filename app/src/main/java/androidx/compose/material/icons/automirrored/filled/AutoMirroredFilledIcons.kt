package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.buildMaterialIcon
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.AutoMirrored.Filled.Logout: ImageVector by lazy {
    buildMaterialIcon("AutoMirrored.Filled.Logout", autoMirror = true) {
        moveTo(17f, 7f)
        lineToRelative(-1.41f, 1.41f)
        lineTo(18.17f, 11f)
        horizontalLineTo(8f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(10.17f)
        lineToRelative(-2.58f, 2.58f)
        lineTo(17f, 17f)
        lineToRelative(5f, -5f)
        close()
        moveTo(4f, 5f)
        horizontalLineToRelative(8f)
        verticalLineTo(3f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineTo(5f)
        close()
    }
}

val Icons.AutoMirrored.Filled.Reply: ImageVector by lazy {
    buildMaterialIcon("AutoMirrored.Filled.Reply", autoMirror = true) {
        moveTo(10f, 9f)
        verticalLineTo(5f)
        lineToRelative(-7f, 7f)
        lineToRelative(7f, 7f)
        verticalLineToRelative(-4.1f)
        curveToRelative(5f, 0f, 8.5f, 1.6f, 11f, 5.1f)
        curveToRelative(-1f, -5f, -4f, -10f, -11f, -11f)
        close()
    }
}

val Icons.AutoMirrored.Filled.Send: ImageVector by lazy {
    buildMaterialIcon("AutoMirrored.Filled.Send", autoMirror = true) {
        moveTo(2.01f, 21f)
        lineTo(23f, 12f)
        lineTo(2.01f, 3f)
        lineTo(2f, 10f)
        lineToRelative(15f, 2f)
        lineToRelative(-15f, 2f)
        close()
    }
}
