package com.example.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.BuildConfig
import java.util.concurrent.TimeUnit

// --- Paper Structure Data Models ---

@JsonClass(generateAdapter = true)
data class Bab1Response(
    val latarBelakang: String,
    val rumusanMasalah: List<String>,
    val tujuan: List<String>
)

@JsonClass(generateAdapter = true)
data class Bab2Response(
    val subjudul: String,
    val konten: String
)

@JsonClass(generateAdapter = true)
data class Bab3Response(
    val kesimpulan: String,
    val saran: String
)

@JsonClass(generateAdapter = true)
data class PaperResponse(
    val kataPengantar: String,
    val bab1: Bab1Response,
    val bab2: List<Bab2Response>,
    val bab3: Bab3Response,
    val daftarPustaka: List<String>
)

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val paperAdapter = moshi.adapter(PaperResponse::class.java)

    suspend fun generatePaper(
        title: String,
        author: String,
        school: String,
        subject: String,
        year: String,
        instructions: String
    ): PaperResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API Key is missing! Silakan tambahkan API Key di panel Secrets.")
        }

        // Direct REST API Endpoint for gemini-3.5-flash
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val prompt = """
Buatkan makalah akademik berbahasa Indonesia dengan spesifikasi berikut:
- Judul: "$title"
- Penulis: $author
- Institusi: $school
- Mata Pelajaran/Kuliah: ${if (subject.isBlank()) "Otomatis sesuaikan dengan topik judul" else subject}
- Tahun: $year
- PERINTAH KHUSUS PENGGUNA (TARGET PANJANG & ARAH MATERI): "${if (instructions.isBlank()) "Buat makalah dengan standar normal." else instructions}"

Instruksi Konten WAJIB:
1. PENTING: CARI DATA FAKTUAL dari sumber nyata dan terverifikasi sesuai topik.
2. PARAFRASE & BAHASA: JANGAN COPY-PASTE MENTAH! Jelaskan konsep dengan bahasa yang mengalir, akademis, bermutu, mudah dipahami, namun tetap berbobot.
3. FOOTNOTE & ELABORASI: Letakkan tag [footnote:Nama, Tahun. Judul. Penerbit/URL] TEPAT DI BELAKANG TITIK kalimat referensi tanpa spasi. Batasi maksimal 8-10 footnote untuk seluruh makalah. Letakkan footnote di Bab 1 atau Bab 2.
   - ATURAN KETAT: JANGAN buat 1 paragraf 1 footnote! Setelah ada kalimat yang menggunakan footnote, berikan elaborasi/deskripsi tambahan atau opini logis yang mengalir tanpa footnote baru.
4. KATA PENGANTAR: Tulis isi saja. DILARANG KERAS menyisipkan tanggal (seperti "Jakarta, 2026") atau nama penulis di bagian akhir kata pengantar.
5. BAB 2 (PEMBAHASAN - SANGAT PENTING): 
   - WAJIB SINKRON DENGAN RUMUSAN MASALAH: Jumlah dan topik Subbab di Bab 2 WAJIB SAMA PERSIS dengan Rumusan Masalah di Bab 1. Jika Rumusan Masalah ada 3 pertanyaan, maka Bab 2 WAJIB memiliki tepat 3 Subbab yang secara berurutan membahas dan menjawab masing-masing pertanyaan rumusan masalah tersebut (Subbab diubah menjadi kalimat pernyataan).
   - JANGAN menjadikan isi makalah full penomoran! Gunakan format poin (1., 2., 3.) HANYA jika benar-benar butuh rincian mutlak (seperti: komponen, faktor). 
   - SANGAT PENTING: Jika menggunakan poin rincian, berikan paragraf penjelasan biasa di bawahnya untuk menguraikan poin tersebut secara mendalam. Perbanyak paragraf naratif utuh (essay style).
6. EKSEKUSI PERINTAH KHUSUS (HUKUM TERTINGGI MUTLAK): 
   - BACA KEMBALI PERINTAH KHUSUS INI: "${if (instructions.isBlank()) "Tidak ada instruksi khusus." else instructions}"
   - Anda WAJIB 100% TUNDUK pada perintah khusus tersebut. Jikalau diarahkan ke alur tertentu, maka tulislah pembahasan dengan alur tersebut!
7. DAFTAR PUSTAKA: Seluruh isi Daftar Pustaka WAJIB diambil 100% dari sumber yang Anda letakkan di footnote (format APA Style).

Format Output WAJIB JSON murni (tanpa markdown format blocks) berstruktur persis seperti ini:
{
  "kataPengantar": "Teks kata pengantar lengkap (gunakan \\n untuk paragraf baru). INGAT: TANPA tanggal dan nama di akhir.",
  "bab1": {
    "latarBelakang": "Teks latar belakang yang panjang dengan sesekali [footnote:Penulis, Tahun. Judul. Penerbit] jika ada (gunakan \\n untuk paragraf baru)",
    "rumusanMasalah": ["Pertanyaan masalah 1", "Pertanyaan masalah 2", "Pertanyaan masalah 3"],
    "tujuan": ["Poin tujuan 1", "Poin tujuan 2", "Poin tujuan 3"]
  },
  "bab2": [
    {
      "subjudul": "Pembahasan Masalah 1",
      "konten": "Isi pembahasan detail. Gunakan \\n untuk pergantian paragraf. Jika ada list rincian sampaikan dalam format: \\n1. Judul Poin: Penjelasannya."
    }
  ],
  "bab3": {
    "kesimpulan": "Kesimpulan akademis terperinci (gunakan \\n untuk paragraf baru)",
    "saran": "Saran-saran bermanfaat (gunakan \\n untuk paragraf baru)"
  },
  "daftarPustaka": [
    "Sitasi sumber 1 format APA",
    "Sitasi sumber 2 format APA"
  ]
}
        """.trimIndent()

        // Create the JSON payload for Gemini REST API
        val requestJson = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": ${escapeJsonString(prompt)}
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API error status: ${response.code}, body: $errorBody")
                throw Exception("Gagal menghubungi Gemini AI (HTTP ${response.code}): $errorBody")
            }

            val bodyString = response.body?.string() ?: throw Exception("Response body is empty")
            Log.d(TAG, "Payload received: $bodyString")

            // Parse response json
            val rawJson = parseTextFromResponse(bodyString)
            Log.d(TAG, "Extracted text: $rawJson")

            try {
                paperAdapter.fromJson(rawJson) ?: throw Exception("Gagal mem-parsing hasil JSON makalah")
            } catch (e: Exception) {
                Log.e(TAG, "Moshi parsing error", e)
                throw Exception("Gagal mengekstrak format makalah yang sah dari AI. Silakan coba kembali. Detail: ${e.localizedMessage}")
            }
        }
    }

    private fun escapeJsonString(str: String): String {
        val builder = java.lang.StringBuilder()
        builder.append("\"")
        for (c in str) {
            when (c) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (c.code < 32) {
                        builder.append(String.format("\\u%04x", c.code))
                    } else {
                        builder.append(c)
                    }
                }
            }
        }
        builder.append("\"")
        return builder.toString()
    }

    private fun parseTextFromResponse(responseBody: String): String {
        // Extract content.parts[0].text via custom lightweight parser to avoid complex JSON setup
        try {
            val moshi = Moshi.Builder().build()
            val mapAdapter = moshi.adapter(Map::class.java)
            val responseMap = mapAdapter.fromJson(responseBody)
            val candidates = responseMap?.get("candidates") as? List<*>
            val candidate = candidates?.getOrNull(0) as? Map<*, *>
            val content = candidate?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val part = parts?.getOrNull(0) as? Map<*, *>
            val text = part?.get("text") as? String
            if (text != null) {
                return text.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Response parsing error", e)
        }
        
        // Manual fallback search if JSON layout was unexpected
        val startIndexToken = "\"text\":"
        val index = responseBody.indexOf(startIndexToken)
        if (index != -1) {
            val afterText = responseBody.substring(index + startIndexToken.length).trim()
            if (afterText.startsWith("\"")) {
                val endQuoteIndex = findEndQuote(afterText)
                if (endQuoteIndex != -1) {
                    val literalText = afterText.substring(1, endQuoteIndex)
                    return unescapeString(literalText)
                }
            }
        }
        throw Exception("Gagal membaca struktur konten teks dari respons Gemini.")
    }

    private fun findEndQuote(str: String): Int {
        var isEscaped = false
        for (i in 1..str.lastIndex) {
            val c = str[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                return i
            }
        }
        return -1
    }

    private fun unescapeString(str: String): String {
        val builder = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                val next = str[i + 1]
                when (next) {
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    '\\' -> builder.append('\\')
                    '"' -> builder.append('"')
                    'u' -> {
                        if (i + 5 < str.length) {
                            val hex = str.substring(i + 2, i + 6)
                            builder.append(hex.toInt(16).toChar())
                            i += 5
                        } else {
                            builder.append(c)
                        }
                    }
                    else -> builder.append(next)
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }
}
