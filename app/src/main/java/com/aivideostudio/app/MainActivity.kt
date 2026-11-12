package com.aivideostudio.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.random.Random

enum class MotionPreset(val title: String) {
    ZOOM_IN("تقريب سينمائي (Zoom In)"),
    ZOOM_OUT("تبعيد درامي (Zoom Out)"),
    PAN_HORIZONTAL("تحريك أفقي (Pan)"),
    DYNAMIC_MIX("حركة ذكية متنوعة")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7C4DFF),
                    secondary = Color(0xFF00E5FF),
                    background = Color(0xFF0D0F14),
                    surface = Color(0xFF181B24)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AIVideoStudioScreen()
                }
            }
        }
    }
}

@Composable
fun AIVideoStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var promptText by remember {
        mutableStateOf("A futuristic luxury kitchen with glowing marble island, cinematic golden hour lighting\nAerial view of a cyberpunk neon city in heavy rain, 8k photorealistic\nClose up of a robotic chef preparing gourmet food with holographic screens")
    }
    var selectedMotion by remember { mutableStateOf(MotionPreset.DYNAMIC_MIX) }
    var secondsPerScene by remember { mutableFloatStateOf(3f) }
    var showSubtitles by remember { mutableStateOf(true) }

    val pickedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("جاهز لتوليد الفيديو مجاناً بدون مفتاح API") }
    var progressValue by remember { mutableFloatStateOf(0f) }
    var generatedVideoFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        scope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                decodeUriToBitmap(context, uri)?.let { bmp ->
                    withContext(Dispatchers.Main) { pickedBitmaps.add(bmp) }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AI Video Studio Pro", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("توليد فيديو من النصوص والصور مجاناً 100%", fontSize = 12.sp, color = Color(0xFF00E5FF))
            }
            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(34.dp))
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("من النص (AI)") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("من الصور") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("دمج الاثنين") })
        }

        if (selectedTab == 0 || selectedTab == 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وصف مشاهد الفيديو (كل سطر يمثل مشهداً مستقلاً):", fontWeight = FontWeight.SemiBold, color = Color.White)
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("اكتب وصف المشاهد هنا...") }
                    )
                }
            }
        }

        if (selectedTab == 1 || selectedTab == 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الصور المختارة (${pickedBitmaps.size}):", fontWeight = FontWeight.SemiBold, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pickedBitmaps.isNotEmpty()) {
                                OutlinedButton(onClick = { pickedBitmaps.clear() }) { Text("مسح") }
                            }
                            Button(onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("إضافة صور")
                            }
                        }
                    }

                    if (pickedBitmaps.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(pickedBitmaps) { idx, bmp ->
                                Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp))) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { pickedBitmaps.removeAt(idx) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إعدادات التحريك السينمائي (AI Camera Motion):", fontWeight = FontWeight.SemiBold, color = Color.White)
                MotionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { selectedMotion = preset }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMotion == preset, onClick = { selectedMotion = preset })
                        Text(preset.title, color = Color.White, fontSize = 14.sp)
                    }
                }
                Text("مدة كل مشهد: ${secondsPerScene.toInt()} ثوانٍ", color = Color.White, fontSize = 13.sp)
                Slider(value = secondsPerScene, onValueChange = { secondsPerScene = it }, valueRange = 2f..6f, steps = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showSubtitles, onCheckedChange = { showSubtitles = it })
                    Text("إظهار النص التوضيحي أسفل الفيديو", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    isProcessing = true
                    generatedVideoFile = null
                    progressValue = 0.05f
                    try {
                        val scenes = mutableListOf<Pair<Bitmap, String>>()
                        if (selectedTab == 0 || selectedTab == 2) {
                            val lines = promptText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            lines.forEachIndexed { index, line ->
                                statusMessage = "جاري توليد المشهد ${index + 1} من ${lines.size}..."
                                progressValue = 0.1f + (0.4f * (index.toFloat() / max(1, lines.size)))
                                scenes.add(fetchAiSceneBitmap(line) to line)
                            }
                        }
                        if (selectedTab == 1 || selectedTab == 2) {
                            pickedBitmaps.forEachIndexed { idx, bmp -> scenes.add(bmp to "Scene ${idx + 1}") }
                        }
                        if (scenes.isEmpty()) {
                            statusMessage = "⚠️ الرجاء إدخال نص أو اختيار صورة أولاً!"
                            isProcessing = false
                            return@launch
                        }
                        statusMessage = "جاري تحريك المشاهد وتصدير الفيديو بصيغة MP4..."
                        val outFile = File(context.cacheDir, "ai_video_${System.currentTimeMillis()}.mp4")
                        renderScenesToMp4(scenes, outFile, secondsPerScene.toInt(), selectedMotion, showSubtitles) { p ->
                            progressValue = 0.5f + (p * 0.5f)
                        }
                        generatedVideoFile = outFile
                        statusMessage = "✅ تم توليد الفيديو بنجاح!"
                    } catch (e: Exception) {
                        statusMessage = "❌ خطأ: ${e.localizedMessage}"
                    } finally {
                        isProcessing = false
                    }
                }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isProcessing) "جاري المعالجة..." else "توليد الفيديو بالذكاء الاصطناعي (MP4)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (isProcessing) {
            LinearProgressIndicator(progress = { progressValue }, modifier = Modifier.fillMaxWidth())
        }
        Text(statusMessage, color = Color(0xFF00E5FF), fontSize = 13.sp)

        generatedVideoFile?.let { videoFile ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(videoFile.absolutePath)
                                setOnPreparedListener { mp -> mp.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val saved = saveVideoToGallery(context, videoFile)
                                statusMessage = if (saved) "✅ تم الحفظ في معرض الهاتف!" else "❌ تعذر الحفظ"
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("حفظ بالمعرض") }
                        OutlinedButton(onClick = { shareVideoFile(context, videoFile) }, modifier = Modifier.weight(1f)) { Text("مشاركة") }
                    }
                }
            }
        }
    }
}

suspend fun fetchAiSceneBitmap(prompt: String): Bitmap = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode("$prompt, cinematic lighting, ultra detailed, 8k", "UTF-8")
        val seed = Random.nextInt(1000, 999999)
        val urlStr = "https://image.pollinations.ai/prompt/$encoded?width=720&height=720&seed=$seed&nologo=true"
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) ?: createFallbackSceneBitmap(prompt) }
    } catch (e: Exception) {
        createFallbackSceneBitmap(prompt)
    }
}

fun createFallbackSceneBitmap(prompt: String): Bitmap {
    val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 720f, 720f, intArrayOf(android.graphics.Color.rgb(25, 18, 65), android.graphics.Color.rgb(12, 85, 115)), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 720f, 720f, paint)
    paint.shader = null
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 26f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(prompt.take(36), 360f, 360f, paint)
    return bmp
}

fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) { null }

suspend fun renderScenesToMp4(
    scenes: List<Pair<Bitmap, String>>,
    outputFile: File,
    secondsPerScene: Int,
    motionPreset: MotionPreset,
    drawCaption: Boolean,
    onProgress: (Float) -> Unit
) = withContext(Dispatchers.Default) {
    val width = 720; val height = 720; val fps = 24
    val framesPerScene = secondsPerScene * fps
    val totalFrames = scenes.size * framesPerScene

    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val inputSurface = encoder.createInputSurface()
    encoder.start()

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val bufferInfo = MediaCodec.BufferInfo()
    var trackIndex = -1
    var muxerStarted = false

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bgRectPaint = Paint().apply { color = android.graphics.Color.argb(150, 0, 0, 0) }
    var globalFrame = 0
    var ptsUs = 0L

    for ((sceneIdx, pair) in scenes.withIndex()) {
        val scaledSource = Bitmap.createScaledBitmap(pair.first, width, height, true)
        val caption = pair.second

        for (frame in 0 until framesPerScene) {
            val t = frame.toFloat() / max(1, framesPerScene - 1)
            val surfaceCanvas = inputSurface.lockCanvas(null)
            surfaceCanvas.drawColor(android.graphics.Color.BLACK)

            val activePreset = if (motionPreset == MotionPreset.DYNAMIC_MIX) MotionPreset.entries[sceneIdx % 3] else motionPreset
            val scale = when (activePreset) {
                MotionPreset.ZOOM_IN -> 1.0f + (0.22f * t)
                MotionPreset.ZOOM_OUT -> 1.22f - (0.22f * t)
                else -> 1.15f
            }
            val dx = if (activePreset == MotionPreset.PAN_HORIZONTAL) (-40f + 80f * t) else 0f

            surfaceCanvas.save()
            surfaceCanvas.translate(width / 2f + dx, height / 2f)
            surfaceCanvas.scale(scale, scale)
            surfaceCanvas.translate(-width / 2f, -height / 2f)
            surfaceCanvas.drawBitmap(scaledSource, 0f, 0f, paint)
            surfaceCanvas.restore()

            if (drawCaption && caption.isNotBlank()) {
                surfaceCanvas.drawRect(30f, height - 105f, width - 30f, height - 35f, bgRectPaint)
                surfaceCanvas.drawText(caption.take(42), width / 2f, height - 62f, textPaint)
            }

            inputSurface.unlockCanvasAndPost(surfaceCanvas)

            while (true) {
                val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) break
                else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (status >= 0) {
                    val encodedData = encoder.getOutputBuffer(status) ?: continue
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0
                    if (bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = ptsUs
                        ptsUs += 1_000_000L / fps
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
            globalFrame++
            onProgress(globalFrame.toFloat() / max(1, totalFrames))
        }
    }

    encoder.signalEndOfInputStream()
    encoder.stop()
    encoder.release()
    inputSurface.release()
    if (muxerStarted) muxer.stop()
    muxer.release()
}

// دالة مصححة بالكامل لتجنب أخطاء تعبيرات الـ return
fun saveVideoToGallery(context: Context, videoFile: File): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AIVideoStudio")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out -> 
            videoFile.inputStream().use { input -> input.copyTo(out) } 
        }
        true
    } catch (e: Exception) { 
        false 
    }
}

fun shareVideoFile(context: Context, videoFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة الفيديو"))
}
