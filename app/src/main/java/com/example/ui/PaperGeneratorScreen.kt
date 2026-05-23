package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.GeminiApiClient
import com.example.api.PaperResponse
import com.example.data.DocxExporter
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*

// --- Main App Colours matching yellow / green and slate theme ---
object AppThemeColors {
    val DeepSlate = Color(0xFF0F172A)
    val CardSlate = Color(0xFF1E293B)
    val TextSlate = Color(0xFFF1F5F9)
    val LightBg = Color(0xFFF8FAFC)
    val LightCard = Color(0xFFFFFFFF)
    
    val ElectricYellow = Color(0xFFEAB308) // Amber-500
    val EmeraldGreen = Color(0xFF059669) // Emerald-600
    val AccentGold = Color(0xFFD97706) // Amber-600
}

// --- Dynamic Greeting State & Logic ---
data class TimeGreeting(val greeting: String, val isDarkDefault: Boolean)

fun getGreetingAndDefaultTheme(): TimeGreeting {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..10 -> TimeGreeting("Selamat Pagi", false)
        hour in 11..14 -> TimeGreeting("Selamat Siang", false)
        hour in 15..17 -> TimeGreeting("Selamat Sore", true)
        else -> TimeGreeting("Selamat Malam", true)
    }
}

// --- ViewModel ---
class PaperGeneratorViewModel(application: Application) : AndroidViewModel(application) {
    
    // Form data
    var title by mutableStateOf("")
    var author by mutableStateOf("")
    var school by mutableStateOf("")
    var subject by mutableStateOf("")
    var year by mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString())
    var instructions by mutableStateOf("")

    // States
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _loadingText = MutableStateFlow("")
    val loadingText = _loadingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _generatedPaper = MutableStateFlow<PaperResponse?>(null)
    val generatedPaper = _generatedPaper.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _themeOverride = MutableStateFlow<Boolean?>(null)
    val themeOverride = _themeOverride.asStateFlow()

    private val timeGreeting = getGreetingAndDefaultTheme()
    
    val currentGreeting: String
        get() = timeGreeting.greeting

    val isDarkTheme: Boolean
        get() = _themeOverride.value ?: timeGreeting.isDarkDefault

    fun toggleTheme() {
        _themeOverride.value = !isDarkTheme
    }

    fun resetPaper() {
        _generatedPaper.value = null
        _error.value = null
    }

    fun generatePaper() {
        if (title.isBlank() || author.isBlank() || school.isBlank() || year.isBlank()) {
            _error.value = "Judul, Penulis, Institusi, dan Tahun wajib diisi!"
            return
        }

        _loading.value = true
        _error.value = null
        _generatedPaper.value = null

        val loadingTexts = listOf(
            "Mengumpulkan data dan literatur faktual (Live Search)...",
            "Menyusun struktur penomoran rincian makalah...",
            "Menulis bab pendahuluan secara mendetail...",
            "Menganalisis bab pembahasan, menata paragraf...",
            "Merumuskan kesimpulan komprehensif...",
            "Menyiapkan pratinjau..."
        )

        // Run loading cycling animations in viewModelscope
        viewModelScope.launch {
            val textJob = launch {
                var idx = 0
                while (true) {
                    _loadingText.value = loadingTexts[idx]
                    delay(4000)
                    idx = (idx + 1) % loadingTexts.size
                }
            }

            try {
                val paper = GeminiApiClient.generatePaper(
                    title = title,
                    author = author,
                    school = school,
                    subject = subject,
                    year = year,
                    instructions = instructions
                )
                _generatedPaper.value = paper
                textJob.cancel()
            } catch (e: Exception) {
                textJob.cancel()
                _error.value = e.localizedMessage ?: "Terjadi kesalahan yang tidak terduga."
            } finally {
                _loading.value = false
            }
        }
    }

    fun exportDocxFile(context: android.content.Context) {
        val paper = _generatedPaper.value ?: return
        _isDownloading.value = true

        viewModelScope.launch {
            try {
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                val filename = "Makalah_${safeTitle}.docx"

                val isSaved = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                DocxExporter.exportToDocx(
                                    paper = paper,
                                    title = title,
                                    author = author,
                                    school = school,
                                    subject = subject,
                                    year = year,
                                    outputStream = outputStream
                                )
                                true
                            } ?: false
                        } else false
                    } else {
                        // For Older versions, fallback to direct Files API in downloads dir
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val file = java.io.File(downloadsDir, filename)
                        file.outputStream().use { outputStream ->
                            DocxExporter.exportToDocx(
                                paper = paper,
                                title = title,
                                author = author,
                                school = school,
                                subject = subject,
                                year = year,
                                outputStream = outputStream
                            )
                        }
                        true
                    }
                }

                if (isSaved) {
                    Toast.makeText(context, "Makalah berhasil disimpan di folder Downloads: $filename", Toast.LENGTH_LONG).show()
                } else {
                    _error.value = "Gagal membuat berkas Word."
                }
            } catch (e: Exception) {
                _error.value = "Gagal mengekspor makalah: ${e.localizedMessage}"
            } finally {
                _isDownloading.value = false
            }
        }
    }
}

// --- Main Composable screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperGeneratorScreen(viewModel: PaperGeneratorViewModel = viewModel()) {
    val isDark = viewModel.isDarkTheme
    val generatedPaper by viewModel.generatedPaper.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingText by viewModel.loadingText.collectAsState()
    val error by viewModel.error.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val context = LocalContext.current

    // Material 3 Color Theme
    val backgroundColor = if (isDark) AppThemeColors.DeepSlate else AppThemeColors.LightBg
    val cardColor = if (isDark) AppThemeColors.CardSlate else AppThemeColors.LightCard
    val textColor = if (isDark) AppThemeColors.TextSlate else Color(0xFF1E293B)
    val mutedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppThemeColors.EmeraldGreen, AppThemeColors.ElectricYellow)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Custom Electric Bolt SVG Icon represented in Compose vector draw
                        Icon(
                            imageVector = Icons.Filled.OfflineBolt,
                            contentDescription = "Bolt",
                            tint = AppThemeColors.ElectricYellow,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 4.dp)
                        )
                        Column {
                            Text(
                                text = "Paper Generator by MF",
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "DO YOUR PAPER INSTANTLY",
                                color = AppThemeColors.EmeraldGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Change Theme",
                            tint = if (isDark) AppThemeColors.ElectricYellow else Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isDark) AppThemeColors.DeepSlate else Color.White,
                    titleContentColor = if (isDark) Color.White else Color(0xFF0F172A)
                ),
                modifier = Modifier.shadow(1.dp)
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            AnimatedContent(
                targetState = generatedPaper != null,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "MainContentTransition"
            ) { hasPaper ->
                if (!hasPaper) {
                    // FORM INPUT MODE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dynamic Time Greeting Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "${viewModel.currentGreeting}!",
                                    color = AppThemeColors.EmeraldGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Buat Makalah Otomatis, Cepat dan Tepat.",
                                    color = textColor,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 28.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Isi form di bawah, lihat pratinjau (Preview) makalah Anda, lalu langsung unduh dokumen dalam format MS Word (.docx).",
                                    color = mutedTextColor,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Form input block with emerald gradient top divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardColor)
                                .border(1.dp, Color(0xFFE2E8F0).copy(alpha = if (isDark) 0.1f else 0.8f), RoundedCornerShape(16.dp))
                        ) {
                            Column {
                                // Beautiful Gradient Line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(borderBrush)
                                )

                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Title Input
                                    OutlinedTextField(
                                        value = viewModel.title,
                                        onValueChange = { viewModel.title = it },
                                        label = { Text("Judul Makalah *") },
                                        placeholder = { Text("Contoh: Dampak AI Terhadap Sistem Pembelajaran") },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = "Title", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier.fillMaxWidth().testTag("title_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Author Input
                                    OutlinedTextField(
                                        value = viewModel.author,
                                        onValueChange = { viewModel.author = it },
                                        label = { Text("Nama Penulis *") },
                                        placeholder = { Text("Nama Lengkap / Kelompok") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Penulis", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier.fillMaxWidth().testTag("author_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // School Input
                                    OutlinedTextField(
                                        value = viewModel.school,
                                        onValueChange = { viewModel.school = it },
                                        label = { Text("Nama Institusi *") },
                                        placeholder = { Text("Contoh: Universitas Indonesia / SMA Tunas") },
                                        leadingIcon = { Icon(Icons.Default.School, contentDescription = "Institusi", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier.fillMaxWidth().testTag("school_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Subject Input
                                    OutlinedTextField(
                                        value = viewModel.subject,
                                        onValueChange = { viewModel.subject = it },
                                        label = { Text("Mata Pelajaran (Opsional)") },
                                        placeholder = { Text("Kosongkan agar otomatis menyesuaikan") },
                                        leadingIcon = { Icon(Icons.Default.Class, contentDescription = "Mapel", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier.fillMaxWidth().testTag("subject_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Year Input
                                    OutlinedTextField(
                                        value = viewModel.year,
                                        onValueChange = { viewModel.year = it },
                                        label = { Text("Tahun *") },
                                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "Tahun", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier.fillMaxWidth().testTag("year_input"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Instructions Textarea
                                    OutlinedTextField(
                                        value = viewModel.instructions,
                                        onValueChange = { viewModel.instructions = it },
                                        label = { Text("Instruksi Tambahan (Opsional)") },
                                        placeholder = { Text("Contoh: Buat pembahasan makalah sangat panjang & detail. Fokuskan pada studi kasus Indonesia...") },
                                        leadingIcon = { Icon(Icons.Default.Message, contentDescription = "Instruksi", tint = AppThemeColors.EmeraldGreen) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .testTag("instructions_input"),
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppThemeColors.EmeraldGreen,
                                            unfocusedBorderColor = mutedTextColor.copy(alpha = 0.5f)
                                        )
                                    )

                                    // Display and handle error
                                    error?.let { err ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFFEE2E2)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = "Error",
                                                    tint = Color(0xFFDC2626)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = err,
                                                    color = Color(0xFF991B1B),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Generate button
                                    Button(
                                        onClick = { viewModel.generatePaper() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("submit_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AppThemeColors.EmeraldGreen,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !loading
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Submit"
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "GENERATE YOUR PAPER",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Caution warning block as per developer instructions
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Security Warning: I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                            color = mutedTextColor,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                } else {
                    // PREVIEW MODE
                    val paper = generatedPaper!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Floating Button Navigation Bar for Paper Operations
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetPaper() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, mutedTextColor.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Buat Baru", color = textColor)
                            }

                            Button(
                                onClick = { viewModel.exportDocxFile(context) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppThemeColors.ElectricYellow,
                                    contentColor = Color.Black
                                ),
                                enabled = !isDownloading,
                                modifier = Modifier.testTag("download_button")
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download"
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDownloading) "Mengekspor..." else "Download DOCX",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Elegant Times New Roman Paper Document simulator sheet
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // --- AESTHETIC PAPER COVER SIMULATION ---
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = viewModel.title.uppercase(),
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 24.sp
                                    )
                                    Spacer(modifier = Modifier.height(48.dp))
                                    Text(
                                        text = "MAKALAH",
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(96.dp))
                                    Text(
                                        text = "Disusun Oleh:",
                                        fontFamily = FontFamily.Serif,
                                        color = Color.Black,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = viewModel.author,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(130.dp))
                                    if (viewModel.subject.isNotBlank()) {
                                        Text(
                                            text = "Mata Pelajaran: " + viewModel.subject,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.Black,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = viewModel.school.uppercase(),
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = viewModel.year,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- DAFTAR ISI SIMULATOR ---
                                Text(
                                    text = "DAFTAR ISI",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                PreviewTOCItem("KATA PENGANTAR", "ii")
                                PreviewTOCItem("DAFTAR ISI", "iii")
                                PreviewTOCItem("BAB I PENDAHULUAN", "1")
                                PreviewTOCSubItem("A. Latar Belakang", "1")
                                PreviewTOCSubItem("B. Rumusan Masalah", "2")
                                PreviewTOCSubItem("C. Tujuan", "2")
                                PreviewTOCItem("BAB II PEMBAHASAN", "3")
                                var labelChar = 'A'
                                for (sec in paper.bab2) {
                                    PreviewTOCSubItem("$labelChar. ${sec.subjudul}", "3")
                                    labelChar++
                                }
                                PreviewTOCItem("BAB III PENUTUP", "9")
                                PreviewTOCSubItem("A. Kesimpulan", "9")
                                PreviewTOCSubItem("B. Saran", "10")
                                PreviewTOCItem("DAFTAR PUSTAKA", "11")

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- KATA PENGANTAR ---
                                PreviewHeading1("KATA PENGANTAR")
                                RenderSmartPaperText(paper.kataPengantar)

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- BAB I PENDAHULUAN ---
                                PreviewHeading1("BAB I\nPENDAHULUAN")
                                PreviewHeading2("A. Latar Belakang")
                                RenderSmartPaperText(paper.bab1.latarBelakang)

                                PreviewHeading2("B. Rumusan Masalah")
                                paper.bab1.rumusanMasalah.forEachIndexed { i, q ->
                                    PreviewListItem(i + 1, q)
                                }

                                PreviewHeading2("C. Tujuan")
                                paper.bab1.tujuan.forEachIndexed { i, t ->
                                    PreviewListItem(i + 1, t)
                                }

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- BAB II PEMBAHASAN ---
                                PreviewHeading1("BAB II\nPEMBAHASAN")
                                labelChar = 'A'
                                for (sec in paper.bab2) {
                                    PreviewHeading2("$labelChar. ${sec.subjudul}")
                                    RenderSmartPaperText(sec.konten)
                                    labelChar++
                                }

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- BAB III PENUTUP ---
                                PreviewHeading1("BAB III\nPENUTUP")
                                PreviewHeading2("A. Kesimpulan")
                                RenderSmartPaperText(paper.bab3.kesimpulan)

                                PreviewHeading2("B. Saran")
                                RenderSmartPaperText(paper.bab3.saran)

                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 24.dp))

                                // --- DAFTAR PUSTAKA ---
                                PreviewHeading1("DAFTAR PUSTAKA")
                                paper.daftarPustaka.forEach { name ->
                                    Text(
                                        text = name,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 13.sp,
                                        color = Color.Black,
                                        lineHeight = 18.sp,
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .padding(start = 16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }

            // FULLSCREEN LOADING LAYER with animated cycles
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f))
                        .clickable(enabled = false) {}, // Scrim block
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AppThemeColors.EmeraldGreen,
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 5.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = loadingText,
                            color = textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.animateContentSize()
                        )
                    }
                }
            }
        }
    }
}

// --- Preview Components ---

@Composable
fun PreviewTOCItem(title: String, page: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.Black
        )
        Text(
            text = "...........................................................................",
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = page,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.Black
        )
    }
}

@Composable
fun PreviewTOCSubItem(title: String, page: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .padding(start = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Black
        )
        Text(
            text = "...........................................................................",
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = page,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Black
        )
    }
}

@Composable
fun PreviewHeading1(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp)
    )
}

@Composable
fun PreviewHeading2(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun PreviewListItem(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .padding(start = 16.dp)
    ) {
        Text(
            text = "$number. ",
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Black
        )
        Text(
            text = text,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = Color.Black,
            textAlign = TextAlign.Justify,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun RenderSmartPaperText(text: String) {
    val paragraphs = text.split("\n").filter { it.trim().isNotBlank() }
    
    // Maintain a linear counter for footnotes across this block
    var footnoteCounter = 1

    paragraphs.forEach { p ->
        val isNumbered = p.trim().matches(Regex("^\\d+\\.\\s.*"))
        
        // Parse custom [footnote:...] markers to show a nice superscript inline identifier link
        val annotatedText = buildAnnotatedString {
            val raw = p.trim()
            val regex = Regex("\\[footnote:(.*?)\\]")
            var lastIndex = 0
            
            val matches = regex.findAll(raw)
            for (match in matches) {
                val start = match.range.first
                if (start > lastIndex) {
                    append(raw.substring(lastIndex, start))
                }
                
                // Print a visual superscript footnote number representing the APA citation
                withStyle(
                    style = SpanStyle(
                        baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppThemeColors.EmeraldGreen
                    )
                ) {
                    append("[$footnoteCounter]")
                    footnoteCounter++
                }
                
                lastIndex = match.range.last + 1
            }
            if (lastIndex < raw.length) {
                append(raw.substring(lastIndex))
            }
        }

        if (isNumbered) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = annotatedText,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Justify,
                    lineHeight = 18.sp
                )
            }
        } else {
            Text(
                text = annotatedText,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                color = Color.Black,
                textAlign = TextAlign.Justify,
                lineHeight = 18.sp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    // Regular paragraph first-line mock indentation inside simulation (approx 20.dp indent)
                    .padding(start = 16.dp)
            )
        }
    }
}
