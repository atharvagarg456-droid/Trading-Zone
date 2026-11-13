package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code Generator (Byte mode, ECC Level L, Versions 1..10).
 * Produces a 100% permanent, scannable QR Code Bitmap offline with zero external library dependencies.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalDataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int,
        val dataCodewordsGroup2: Int,
        val alignmentCenters: IntArray
    )

    // ISO/IEC 18004 ECC Level L specifications for Versions 1..10
    private val VERSION_SPECS = listOf(
        VersionSpec(1, 19, 7, 1, 19, 0, 0, intArrayOf()),
        VersionSpec(2, 34, 10, 1, 34, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, 1, 55, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, 1, 80, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, 1, 108, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 136, 18, 2, 68, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 156, 20, 2, 78, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 194, 24, 2, 97, 0, 0, intArrayOf(6, 24, 42)),
        VersionSpec(9, 232, 30, 2, 116, 0, 0, intArrayOf(6, 26, 46)),
        VersionSpec(10, 274, 18, 2, 68, 2, 69, intArrayOf(6, 28, 50))
    )

    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap {
        val payload = content.trim().ifBlank { "https://atharvagarg456-droid.github.io/TradingCoLTD/" }
            .toByteArray(Charsets.UTF_8)

        val spec = VERSION_SPECS.firstOrNull { s ->
            val countBits = if (s.version <= 9) 8 else 16
            val maxBytes = s.totalDataCodewords - ((4 + countBits + 7) / 8)
            payload.size <= maxBytes
        } ?: VERSION_SPECS.last()

        val trimmedPayload = if (payload.size > spec.totalDataCodewords - 3) {
            payload.copyOf(spec.totalDataCodewords - 3)
        } else {
            payload
        }

        val dataCodewords = encodeDataCodewords(trimmedPayload, spec)
        val allCodewords = interleaveWithEcc(dataCodewords, spec)
        val matrixSize = 17 + 4 * spec.version
        val modules = Array(matrixSize) { BooleanArray(matrixSize) }
        val isFunction = Array(matrixSize) { BooleanArray(matrixSize) }

        drawFunctionPatterns(modules, isFunction, spec)
        drawCodewords(modules, isFunction, allCodewords)

        // Apply Mask 0: (row + col) % 2 == 0
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                if (!isFunction[r][c] && (r + c) % 2 == 0) {
                    modules[r][c] = !modules[r][c]
                }
            }
        }
        drawFormatBits(modules, isFunction, mask = 0)
        if (spec.version >= 7) {
            drawVersionBits(modules, isFunction, spec.version)
        }

        // Render Bitmap with 4-module quiet zone
        val quietZone = 4
        val totalModules = matrixSize + quietZone * 2
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val whitePaint = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val darkPaint = Paint().apply { color = android.graphics.Color.rgb(8, 13, 26); style = Paint.Style.FILL }

        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), whitePaint)
        val cellSize = sizePx.toFloat() / totalModules.toFloat()

        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                if (modules[r][c]) {
                    val left = (c + quietZone) * cellSize
                    val top = (r + quietZone) * cellSize
                    canvas.drawRect(left, top, left + cellSize + 0.5f, top + cellSize + 0.5f, darkPaint)
                }
            }
        }
        return bmp
    }

    private fun encodeDataCodewords(bytes: ByteArray, spec: VersionSpec): IntArray {
        val bits = ArrayList<Int>()
        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        // Byte mode indicator (0100)
        appendBits(0b0100, 4)
        val charCountBits = if (spec.version <= 9) 8 else 16
        appendBits(bytes.size, charCountBits)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val capacityBits = spec.totalDataCodewords * 8
        val terminatorLen = minOf(4, capacityBits - bits.size)
        appendBits(0, terminatorLen)
        while (bits.size % 8 != 0) {
            bits.add(0)
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < capacityBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }
        val result = IntArray(spec.totalDataCodewords)
        for (i in result.indices) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + j]
            }
            result[i] = b
        }
        return result
    }

    private fun interleaveWithEcc(data: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = ArrayList<IntArray>()
        val eccBlocks = ArrayList<IntArray>()
        var offset = 0
        for (i in 0 until spec.numBlocksGroup1) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            eccBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }
        for (i in 0 until spec.numBlocksGroup2) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            eccBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }

        val result = ArrayList<Int>()
        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) result.add(block[i])
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ecc in eccBlocks) {
                result.add(ecc[i])
            }
        }
        return result.toIntArray()
    }

    private fun computeReedSolomon(data: IntArray, degree: Int): IntArray {
        val gen = IntArray(degree)
        gen[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                gen[j] = gfMultiply(gen[j], root)
                if (j + 1 < degree) {
                    gen[j] = gen[j] xor gen[j + 1]
                }
            }
            root = gfMultiply(root, 0x02)
        }

        val rem = IntArray(degree)
        for (b in data) {
            val factor = b xor rem[0]
            for (i in 0 until degree - 1) {
                rem[i] = rem[i + 1] xor gfMultiply(gen[i], factor)
            }
            rem[degree - 1] = gfMultiply(gen[degree - 1], factor)
        }
        return rem
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun drawFunctionPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: VersionSpec
    ) {
        val size = modules.size
        fun setFunctionModule(r: Int, c: Int, isDark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = isDark
                isFunction[r][c] = true
            }
        }

        fun drawFinderPattern(centerR: Int, centerC: Int) {
            for (dr in -4..4) {
                for (dc in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                    val isDark = dist != 2 && dist != 4
                    setFunctionModule(centerR + dr, centerC + dc, isDark)
                }
            }
        }

        drawFinderPattern(3, 3)
        drawFinderPattern(3, size - 4)
        drawFinderPattern(size - 4, 3)

        // Timing patterns
        for (i in 0 until size) {
            if (!isFunction[6][i]) setFunctionModule(6, i, i % 2 == 0)
            if (!isFunction[i][6]) setFunctionModule(i, 6, i % 2 == 0)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (i in centers.indices) {
            for (j in centers.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == centers.lastIndex) || (i == centers.lastIndex && j == 0)) {
                    continue
                }
                val cr = centers[i]
                val cc = centers[j]
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        setFunctionModule(cr + dr, cc + dc, dist != 1)
                    }
                }
            }
        }

        // Reserve format bits & dark module
        for (i in 0..8) {
            if (i != 6) {
                if (!isFunction[8][i]) setFunctionModule(8, i, false)
                if (!isFunction[i][8]) setFunctionModule(i, 8, false)
            }
        }
        for (i in 0..7) {
            if (!isFunction[8][size - 1 - i]) setFunctionModule(8, size - 1 - i, false)
            if (!isFunction[size - 1 - i][8]) setFunctionModule(size - 1 - i, 8, false)
        }
        setFunctionModule(size - 8, 8, true)

        if (spec.version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    setFunctionModule(size - 11 + j, i, false)
                    setFunctionModule(i, size - 11 + j, false)
                }
            }
        }
    }

    private fun drawCodewords(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray
    ) {
        val size = modules.size
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                val r = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[r][c] = dark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun drawFormatBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int
    ) {
        val size = modules.size
        // ECC Level L = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int): Boolean = ((bits ushr i) and 1) != 0

        for (i in 0..5) modules[8][i] = bit(14 - i)
        modules[8][7] = bit(8)
        modules[8][8] = bit(7)
        modules[7][8] = bit(6)
        for (i in 9..14) modules[14 - i][8] = bit(14 - i)

        for (i in 0..7) modules[size - 1 - i][8] = bit(14 - i)
        for (i in 8..14) modules[8][size - 15 + i] = bit(14 - i)
        modules[size - 8][8] = true
    }

    private fun drawVersionBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        version: Int
    ) {
        val size = modules.size
        var rem = version
        for (i in 0 until 12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[a][b] = dark
            modules[b][a] = dark
        }
    }
}

object AppShareHelper {

    fun shareAppleWebAppLink(context: Context, webUrl: String, apkUrl: String) {
        val shareText = buildString {
            appendLine("📲 *Trading Zone — Apple iPhone / iPad & Android Live Access*")
            appendLine()
            appendLine("🍎 *For Apple iPhone / iPad (Safari Web App):*")
            appendLine("1. Open this link in Safari: $webUrl")
            appendLine("2. Tap the Share button (⬆️) at the bottom of Safari")
            appendLine("3. Tap 'Add to Home Screen' to install Trading Zone with full live Broadcasts & Commodity stream!")
            appendLine()
            appendLine("🤖 *For Android (.APK Direct Download):*")
            appendLine(apkUrl)
            appendLine()
            appendLine("Operated by Atharva Garg • Guide: Mr. Vikash Chandra Garg")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Trading Zone — Apple iPhone & Android App Link")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Trading Zone Link via"))
    }

    fun shareAndroidApkLink(context: Context, apkUrl: String, webUrl: String) {
        val shareText = buildString {
            appendLine("🤖 *Trading Zone — Official Android APK & Apple Web App*")
            appendLine()
            appendLine("📥 *Download Android APK:*")
            appendLine(apkUrl)
            appendLine()
            appendLine("🍎 *Apple iPhone / iPad Web App Link:*")
            appendLine(webUrl)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Trading Zone — Download Android APK")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Android APK Link via"))
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PermanentQrAndShareCard(
    appleWebAppUrl: String,
    androidApkUrl: String,
    isAdmin: Boolean,
    onUpdateUrls: (newAppleUrl: String, newAndroidUrl: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var selectedQrTab by remember { mutableIntStateOf(0) } // 0 = Apple iPhone Website QR, 1 = Android APK QR
    var showFullScreenQr by remember { mutableStateOf(false) }
    var showEditUrlDialog by remember { mutableStateOf(false) }

    val activeUrl = if (selectedQrTab == 0) appleWebAppUrl else androidApkUrl
    val activeTitle = if (selectedQrTab == 0) {
        "Apple iPhone / iPad Safari Web App QR"
    } else {
        "Android Direct APK Download QR"
    }

    val qrBitmap = remember(activeUrl) {
        QrCodeGenerator.generateQrBitmap(activeUrl, sizePx = 680)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permanent_qr_share_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Permanent Website QR",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Permanent Website QR & Apple/Android Share",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scan on any iPhone, iPad, or Android device — never expires",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isAdmin) {
                    IconButton(
                        onClick = { showEditUrlDialog = true },
                        modifier = Modifier.testTag("edit_permanent_urls_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Configure Permanent Link")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedQrTab == 0,
                    onClick = { selectedQrTab = 0 },
                    label = { Text("🍎 Apple iPhone / Website QR") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedQrTab == 1,
                    onClick = { selectedQrTab = 1 },
                    label = { Text("🤖 Android APK QR") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scannable Permanent QR Code Image
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(220.dp)
                    .clickable { showFullScreenQr = true }
                    .testTag("permanent_qr_image")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = activeTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activeTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = activeUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { showFullScreenQr = true }) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tap to Enlarge Permanent QR Code")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Share Buttons for Apple iPhone Website & Android APK
            Button(
                onClick = {
                    AppShareHelper.shareAppleWebAppLink(
                        context = context,
                        webUrl = appleWebAppUrl,
                        apkUrl = androidApkUrl
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_apple_webapp_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Apple iPhone Website & App Link", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        AppShareHelper.copyToClipboard(
                            context = context,
                            label = "Website Link",
                            text = appleWebAppUrl
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_apple_webapp_link_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Website Link")
                }

                OutlinedButton(
                    onClick = {
                        AppShareHelper.shareAndroidApkLink(
                            context = context,
                            apkUrl = androidApkUrl,
                            webUrl = appleWebAppUrl
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_android_apk_button")
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share APK Link")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step-by-step instructions on how the permanent QR & Apple Web App works
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "How Apple iPhone Users Install via Permanent QR or Link:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. Scan the Permanent QR Code above with the iPhone Camera or open the shared link in Safari.\n" +
                            "2. Tap the Safari Share icon (⬆️) → Tap 'Add to Home Screen'.\n" +
                            "3. The Apple Web App connects to the exact same live Firebase database as Android (Google Sign-In, App Lock PIN, Click-to-Open Broadcasts, Clean Commodity Feed & Admin Studio).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showFullScreenQr) {
        Dialog(
            onDismissRequest = { showFullScreenQr = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = activeTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Point any Apple iPhone, iPad, or Android camera at this permanent QR code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        modifier = Modifier.size(300.dp)
                    ) {
                        Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = activeTitle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = activeUrl,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                AppShareHelper.shareAppleWebAppLink(
                                    context = context,
                                    webUrl = appleWebAppUrl,
                                    apkUrl = androidApkUrl
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Link")
                        }
                        Button(
                            onClick = { showFullScreenQr = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (showEditUrlDialog) {
        var editAppleUrl by remember(appleWebAppUrl) { mutableStateOf(appleWebAppUrl) }
        var editAndroidUrl by remember(androidApkUrl) { mutableStateOf(androidApkUrl) }

        AlertDialog(
            onDismissRequest = { showEditUrlDialog = false },
            title = { Text("Configure Permanent Website & APK Links") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "The Permanent QR Code automatically encodes whatever URL is set below. Default is your GitHub Pages Apple Web App URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editAppleUrl,
                        onValueChange = { editAppleUrl = it },
                        label = { Text("Apple iPhone / Website URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAndroidUrl,
                        onValueChange = { editAndroidUrl = it },
                        label = { Text("Android Direct APK Download URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUrls(editAppleUrl.trim(), editAndroidUrl.trim())
                        showEditUrlDialog = false
                    }
                ) {
                    Text("Save & Regenerate QR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
