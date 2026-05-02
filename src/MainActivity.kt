package bobko.stitch_screenshots

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

enum class Orientation {
    Vert, Hori;

    val uiString: String
        get() = when (this) {
            Vert -> "Vertical"
            Hori -> "Horizontal"
        }
}
enum class HandleSide { TopOrLeft, BottomOrRight }
data class JunctionHit(val index: Int, val side: HandleSide)

class ImageItem(val bitmap: Bitmap)

/**
 * Crop state for the junction between image[i] and image[i+1].
 * [topOrLeftCrop] crops from the bottom (vertical) or right (horizontal) of the first image.
 * [bottomOrRightCrop] crops from the top (vertical) or left (horizontal) of the second image.
 * Values are fractions in 0..1 representing proportion of the image dimension to crop away.
 */
class JunctionCrop(
    initialTopOrLeft: Float = 0f,
    initialBottomOrRight: Float = 0f,
) {
    var topOrLeftCrop by mutableFloatStateOf(initialTopOrLeft)
    var bottomOrRightCrop by mutableFloatStateOf(initialBottomOrRight)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StitchScreenshotsApp()
                }
            }
        }
    }
}

private val constAny = Any()

@Composable
fun StitchScreenshotsApp() {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<ImageItem>() }
    val junctions = remember { mutableStateListOf<JunctionCrop>() }

    var relaunchPicker: Any by remember { mutableStateOf(constAny) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.size == 1) {
            Toast.makeText(context, "Please select at least 2 images", Toast.LENGTH_SHORT).show()
            relaunchPicker = Any()
            return@rememberLauncherForActivityResult
        }
        images.clear()
        junctions.clear()
        val newImages = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { ImageItem(it) }
            }
        }
        images.addAll(newImages)
        repeat(maxOf(0, newImages.size - 1)) { junctions.add(JunctionCrop()) }
    }

    fun launchPhotoPicker() = photoPicker.launch(
        PickVisualMediaRequest(
            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
            isOrderedSelection = true
        )
    )

    LaunchedEffect(relaunchPicker) {
        if (relaunchPicker != constAny) {
            launchPhotoPicker()
        }
    }

    BackHandler(enabled = images.isNotEmpty()) {
        images.clear()
        junctions.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (images.isNotEmpty()) {
            var orientation by remember { mutableStateOf(Orientation.Vert) }
            StitchPreview(
                images = images.toList(),
                junctions = junctions,
                orientation = orientation,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow {
                    for ((index, entry) in Orientation.entries.withIndex()) {
                        SegmentedButton(
                            selected = orientation == entry,
                            onClick = { orientation = entry },
                            shape = SegmentedButtonDefaults.itemShape(index, Orientation.entries.size)
                        ) {
                           Text(entry.uiString)
                        }
                    }
                }

                Button(onClick = {
                    val result = stitchImages(images.toList(), junctions.toList(), orientation)
                    if (result != null) {
                        save(context as MainActivity, result)
                    }
                }) {
                    Text("Save")
                }
            }
        } else {
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { launchPhotoPicker() }) {
                    Text("Select Screenshots to stitch")
                }
            }
        }
    }
}

@Composable
fun StitchPreview(
    images: List<ImageItem>,
    junctions: List<JunctionCrop>,
    orientation: Orientation,
    modifier: Modifier = Modifier,
) {
    // Compute layout: each image gets a scale factor so that the cross-axis dimension matches
    // a common size, then we lay them out along the main axis with crop applied.
    val previewData = remember(images, orientation) {
        computePreviewLayout(images, orientation)
    }

    val scrollState = rememberScrollState()

    val scrollModifier = when (orientation) {
        Orientation.Vert -> modifier.verticalScroll(scrollState)
        Orientation.Hori -> modifier.horizontalScroll(scrollState)
    }

    Box(modifier = scrollModifier) {
        StitchCanvas(images, junctions, previewData, orientation)
    }
}

data class PreviewImageInfo(
    val scaledWidth: Float,
    val scaledHeight: Float,
    val scale: Float,
)

data class PreviewLayout(
    val infos: List<PreviewImageInfo>,
    val crossAxisSize: Float,
)

fun computePreviewLayout(images: List<ImageItem>, orientation: Orientation): PreviewLayout {
    if (images.isEmpty()) return PreviewLayout(emptyList(), 0f)

    // For vertical: normalize all widths to the narrowest image width (or a max of 1000px)
    // For horizontal: normalize all heights to the shortest image height
    val crossAxisSize = when (orientation) {
        Orientation.Vert -> images.minOf { it.bitmap.width.toFloat() }.coerceAtMost(1000f)
        Orientation.Hori -> images.minOf { it.bitmap.height.toFloat() }.coerceAtMost(1000f)
    }

    val infos = images.map { img ->
        val scale = when (orientation) {
            Orientation.Vert -> crossAxisSize / img.bitmap.width
            Orientation.Hori -> crossAxisSize / img.bitmap.height
        }
        PreviewImageInfo(
            scaledWidth = img.bitmap.width * scale,
            scaledHeight = img.bitmap.height * scale,
            scale = scale,
        )
    }

    return PreviewLayout(infos, crossAxisSize)
}

@Composable
fun StitchCanvas(
    images: List<ImageItem>,
    junctions: List<JunctionCrop>,
    layout: PreviewLayout,
    orientation: Orientation,
) {
    val density = LocalDensity.current
    val isVertical = orientation == Orientation.Vert

    // No crops subtracted — dimmed regions are rendered in place
    val totalMainAxis = layout.infos.sumOf { info ->
        (if (isVertical) info.scaledHeight else info.scaledWidth).toDouble()
    }.toFloat()

    val crossAxisDp = with(density) { layout.crossAxisSize.toDp() }
    val mainAxisDp = with(density) { totalMainAxis.toDp() }

    Canvas(
        modifier = Modifier
            .width(if (isVertical) crossAxisDp else mainAxisDp)
            .height(if (isVertical) mainAxisDp else crossAxisDp)
            .pointerInput(images.size, layout, orientation) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pos = if (isVertical) down.position.y else down.position.x
                    val crossPos = if (isVertical) down.position.x else down.position.y
                    val hit = findNearestJunction(pos, crossPos, layout, junctions, orientation)
                        ?: return@awaitEachGesture // Not near a handle - don't consume, let scroll handle it
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val dragPos = if (isVertical) change.position.y else change.position.x
                        handleDrag(dragPos, hit, layout, junctions, orientation)
                    }
                }
            }
    ) {
        data class JunctionDraw(val boundary: Float, val topCropOffset: Float, val bottomCropOffset: Float)
        val junctionDraws = mutableListOf<JunctionDraw>()
        var current = 0f
        for (i in images.indices) {
            val info = layout.infos[i]
            val bmp = images[i].bitmap
            val startCrop = if (i > 0) junctions[i - 1].bottomOrRightCrop else 0f
            val endCrop = if (i < junctions.size) junctions[i].topOrLeftCrop else 0f
            val mainSize = if (isVertical) info.scaledHeight else info.scaledWidth
            val dimmedTopSize = mainSize * startCrop
            val visibleSize = mainSize * (1f - startCrop - endCrop)
            val dimmedBottomSize = mainSize * endCrop

            val dimmedColor = Color.Red.copy(alpha = 0.4f)
            val bmpMain = if (isVertical) bmp.height else bmp.width
            val bmpCross = if (isVertical) bmp.width else bmp.height
            val scaledCross = if (isVertical) info.scaledWidth else info.scaledHeight
            fun mainCrossOffset(main: Int, cross: Int) = if (isVertical) IntOffset(cross, main) else IntOffset(main, cross)
            fun mainCrossSize(main: Int, cross: Int) = if (isVertical) IntSize(cross, main) else IntSize(main, cross)
            fun mainCrossOffsetF(main: Float, cross: Float) = if (isVertical) Offset(cross, main) else Offset(main, cross)
            fun mainCrossSizeF(main: Float, cross: Float) = if (isVertical) Size(cross, main) else Size(main, cross)

            fun drawRegion(srcStart: Float, srcEnd: Float, dstOffset: Float, dstSize: Float, dimmed: Boolean) {
                drawImage(
                    image = bmp.asImageBitmap(),
                    srcOffset = mainCrossOffset((srcStart * bmpMain).roundToInt(), 0),
                    srcSize = mainCrossSize(((srcEnd - srcStart) * bmpMain).roundToInt(), bmpCross),
                    dstOffset = mainCrossOffset(dstOffset.roundToInt(), 0),
                    dstSize = mainCrossSize(dstSize.roundToInt(), scaledCross.roundToInt()),
                )
                if (dimmed) {
                    drawRect(
                        color = dimmedColor,
                        topLeft = mainCrossOffsetF(dstOffset, 0f),
                        size = mainCrossSizeF(dstSize, scaledCross),
                    )
                }
            }

            if (dimmedTopSize > 0f) {
                drawRegion(0f, startCrop, current, dimmedTopSize, dimmed = true)
            }
            drawRegion(startCrop, 1f - endCrop, current + dimmedTopSize, visibleSize, dimmed = false)
            if (dimmedBottomSize > 0f) {
                drawRegion(1f - endCrop, 1f, current + dimmedTopSize + visibleSize, dimmedBottomSize, dimmed = true)
            }

            if (i < junctions.size) {
                val boundary = current + mainSize // natural boundary between images
                val topOffset = dimmedBottomSize // dimmed bottom of this image
                val nextInfo = layout.infos[i + 1]
                val nextMainSize = if (isVertical) nextInfo.scaledHeight else nextInfo.scaledWidth
                val bottomOffset = nextMainSize * junctions[i].bottomOrRightCrop
                junctionDraws.add(JunctionDraw(boundary, topOffset, bottomOffset))
            }
            current += mainSize
        }
        // Draw junction lines on top of all images so handles aren't covered
        for (jd in junctionDraws) {
            drawJunctionLine(jd.boundary, jd.topCropOffset, jd.bottomCropOffset, layout.crossAxisSize, orientation)
        }
    }
}

// Handle dimensions shared between drawing and hit detection
private const val HANDLE_HEIGHT = 60f
private const val HANDLE_WIDTH = 70f
private const val HANDLE_GAP = 3f
private const val HANDLE_INSET = HANDLE_WIDTH * 0.5f
private const val HANDLE_TOUCH_PADDING = 16f // extra touch area around the handle

fun DrawScope.drawJunctionLine(
    boundary: Float,
    topCropOffset: Float,
    bottomCropOffset: Float,
    crossAxisSize: Float,
    orientation: Orientation
) {
    val isVertical = orientation == Orientation.Vert
    val lineColor = Color(0xFFFF6B00)
    val cornerRadius = 7f
    val topLinePos = boundary - topCropOffset
    val bottomLinePos = boundary + bottomCropOffset
    fun mc(main: Float, cross: Float) = if (isVertical) Offset(cross, main) else Offset(main, cross)
    fun mcSize(main: Float, cross: Float) = if (isVertical) Size(cross, main) else Size(main, cross)

    drawLine(color = lineColor, start = mc(topLinePos, 0f), end = mc(topLinePos, crossAxisSize), strokeWidth = 3f)
    drawLine(color = lineColor, start = mc(bottomLinePos, 0f), end = mc(bottomLinePos, crossAxisSize), strokeWidth = 3f)
    // Top/left handle
    drawRoundRect(
        color = lineColor,
        topLeft = mc(topLinePos - HANDLE_GAP - HANDLE_HEIGHT, HANDLE_INSET),
        size = mcSize(HANDLE_HEIGHT, HANDLE_WIDTH),
        cornerRadius = CornerRadius(cornerRadius),
    )
    // Bottom/right handle
    drawRoundRect(
        color = lineColor,
        topLeft = mc(bottomLinePos + HANDLE_GAP, crossAxisSize - HANDLE_INSET - HANDLE_WIDTH),
        size = mcSize(HANDLE_HEIGHT, HANDLE_WIDTH),
        cornerRadius = CornerRadius(cornerRadius),
    )
}

fun findNearestJunction(
    pos: Float,
    crossPos: Float,
    layout: PreviewLayout,
    junctions: List<JunctionCrop>,
    orientation: Orientation
): JunctionHit? {
    val isVertical = orientation == Orientation.Vert
    val crossAxisSize = layout.crossAxisSize
    val pad = HANDLE_TOUCH_PADDING
    var current = 0f
    for (i in layout.infos.indices) {
        val info = layout.infos[i]
        val mainSize = if (isVertical) info.scaledHeight else info.scaledWidth

        if (i < junctions.size) {
            val boundary = current + mainSize
            val topLinePos = boundary - junctions[i].topOrLeftCrop * mainSize
            val nextInfo = layout.infos[i + 1]
            val nextMainSize = if (isVertical) nextInfo.scaledHeight else nextInfo.scaledWidth
            val bottomLinePos = boundary + junctions[i].bottomOrRightCrop * nextMainSize

            // TopOrLeft handle
            val topMainStart = topLinePos - HANDLE_GAP - HANDLE_HEIGHT - pad
            val topMainEnd = topLinePos + HANDLE_GAP + HANDLE_HEIGHT + pad
            val topCrossStart = HANDLE_INSET - pad
            val topCrossEnd = HANDLE_INSET + HANDLE_WIDTH + pad
            if (pos in topMainStart..topMainEnd && crossPos in topCrossStart..topCrossEnd) {
                return JunctionHit(i, HandleSide.TopOrLeft)
            }

            // BottomOrRight handle
            val botMainStart = bottomLinePos - HANDLE_GAP - HANDLE_HEIGHT - pad
            val botMainEnd = bottomLinePos + HANDLE_GAP + HANDLE_HEIGHT + pad
            val botCrossStart = crossAxisSize - HANDLE_INSET - HANDLE_WIDTH - pad
            val botCrossEnd = crossAxisSize - HANDLE_INSET + pad
            if (pos in botMainStart..botMainEnd && crossPos in botCrossStart..botCrossEnd) {
                return JunctionHit(i, HandleSide.BottomOrRight)
            }
        }
        current += mainSize
    }
    return null
}

fun handleDrag(
    pos: Float,
    hit: JunctionHit,
    layout: PreviewLayout,
    junctions: List<JunctionCrop>,
    orientation: Orientation
) {
    val isVertical = orientation == Orientation.Vert
    val junctionIndex = hit.index
    // Natural boundary between images — sum of full sizes, independent of any crops
    var boundary = 0f
    for (i in 0..junctionIndex) {
        boundary += if (isVertical) layout.infos[i].scaledHeight else layout.infos[i].scaledWidth
    }

    when (hit.side) {
        HandleSide.TopOrLeft -> {
            val mainSize = if (isVertical) layout.infos[junctionIndex].scaledHeight else layout.infos[junctionIndex].scaledWidth
            val relativePos = (boundary - pos) / mainSize
            junctions[junctionIndex].topOrLeftCrop = relativePos.coerceIn(0f, 1f)
        }
        HandleSide.BottomOrRight -> {
            val nextMainSize = if (isVertical) layout.infos[junctionIndex + 1].scaledHeight else layout.infos[junctionIndex + 1].scaledWidth
            val relativePos = (pos - boundary) / nextMainSize
            junctions[junctionIndex].bottomOrRightCrop = relativePos.coerceIn(0f, 1f)
        }
    }
}

fun stitchImages(
    images: List<ImageItem>,
    junctions: List<JunctionCrop>,
    orientation: Orientation,
): Bitmap? {
    if (images.isEmpty()) return null

    val isVertical = orientation == Orientation.Vert
    fun Bitmap.mainSize() = if (isVertical) height else width
    fun Bitmap.crossSize() = if (isVertical) width else height

    val targetCross = images.minOf { it.bitmap.crossSize() }

    val totalMain = images.mapIndexed { i, img ->
        val scale = targetCross.toFloat() / img.bitmap.crossSize()
        val main = (img.bitmap.mainSize() * scale).roundToInt()
        val startCrop = if (i > 0) (junctions[i - 1].bottomOrRightCrop * main).roundToInt() else 0
        val endCrop = if (i < junctions.size) (junctions[i].topOrLeftCrop * main).roundToInt() else 0
        main - startCrop - endCrop
    }.sum()

    val resultWidth = if (isVertical) targetCross else totalMain
    val resultHeight = if (isVertical) totalMain else targetCross
    val result = createBitmap(resultWidth, resultHeight)
    val canvas = android.graphics.Canvas(result)

    var current = 0
    for (i in images.indices) {
        val bmp = images[i].bitmap
        val scale = targetCross.toFloat() / bmp.crossSize()
        val fullMain = (bmp.mainSize() * scale).roundToInt()
        val startCrop = if (i > 0) (junctions[i - 1].bottomOrRightCrop * fullMain).roundToInt() else 0
        val endCrop = if (i < junctions.size) (junctions[i].topOrLeftCrop * fullMain).roundToInt() else 0
        val drawnMain = fullMain - startCrop - endCrop

        val srcStart = (startCrop / scale).roundToInt()
        val srcEnd = bmp.mainSize() - (endCrop / scale).roundToInt()

        val src = when (orientation) {
            Orientation.Vert -> android.graphics.Rect(0, srcStart, bmp.width, srcEnd)
            Orientation.Hori -> android.graphics.Rect(srcStart, 0, srcEnd, bmp.height)
        }

        val dst = when (orientation) {
            Orientation.Vert -> android.graphics.Rect(0, current, targetCross, current + drawnMain)
            Orientation.Hori -> android.graphics.Rect(current, 0, current + drawnMain, targetCross)
        }

        canvas.drawBitmap(bmp, src, dst, null)
        current += drawnMain
    }
    return result
}

fun save(activity: MainActivity, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "long_screenshot_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LongScreenshot")
    }

    val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        activity.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(activity, "Saved to gallery", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(activity, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}
