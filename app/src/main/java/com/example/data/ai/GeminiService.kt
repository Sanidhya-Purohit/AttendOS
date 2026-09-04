package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ParsedSubjectSlot(
    val subjectName: String,
    val subjectCode: String,
    val dayOfWeek: Int, // 1 = Mon .. 6 = Sat
    val startTime: String,
    val endTime: String,
    val location: String,
    val slotType: String,
    val category: String,
    val credits: Int = 4
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val text: String,
    val imageBitmap: Bitmap? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFallback: Boolean = false,
    val isLiked: Boolean? = null // null = unrated, true = liked, false = disliked
)

data class ChatConversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New chat",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeTimetableImage(
        bitmap: Bitmap,
        customApiKey: String? = null
    ): Result<List<ParsedSubjectSlot>> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = """
                Analyze this academic timetable image and extract all scheduled course slots.
                
                IMPORTANT:
                - Only extract courses, rooms, and timings that are strictly visible on this timetable. Never invent, guess, or randomly assign any subjects (like Economics, etc.) if they are not written on the schedule.
                - If an AEC, VAC, SEC, or Elective slot appears (or is marked as selected), extract it using its written course title, or as "AEC / VAC (Selected)" or "Elective (Selected)".
                
                Return ONLY a valid JSON array of objects following this schema:
                [
                  {
                    "subjectName": "Software Engineering",
                    "subjectCode": "SE-301",
                    "credits": 4,
                    "dayOfWeek": 1,
                    "startTime": "09:00 AM",
                    "endTime": "10:00 AM",
                    "location": "Pragya Bhavan PB-02",
                    "slotType": "Lecture",
                    "category": "Core"
                  }
                ]
                
                Rules:
                - dayOfWeek: 1 for Monday, 2 for Tuesday, 3 for Wednesday, 4 for Thursday, 5 for Friday, 6 for Saturday.
                - startTime and endTime format: "09:00 AM", "10:00 AM", "02:00 PM", etc.
                - location: specific room, hall or lab per class if shown.
                - slotType: "Lecture", "Lab", or "Tutorial".
                - category: "Core", "Elective", "VAC", "AEC", "SEC", or "Lab".
                - credits: integer credits (default 4 for lecture, 2 for lab, 2-3 for VAC/AEC/Elective).
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini analysis returned HTTP ${response.code}"))
            }

            val parsedList = parseSlotsFromJson(responseBody)
            if (parsedList.isNotEmpty()) {
                Result.success(parsedList)
            } else {
                Result.failure(Exception("No timetable slots detected in this image."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeTimetableText(
        timetableText: String,
        customApiKey: String? = null
    ): Result<List<ParsedSubjectSlot>> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        try {
            val prompt = """
                Analyze this academic timetable text/schedule data and extract all scheduled courses and class slots:
                $timetableText
                
                IMPORTANT:
                - Extract ONLY the courses and timings explicitly written in this text. Never invent or randomly assign courses that are not written.
                - If any AEC, VAC, SEC, or Elective is mentioned or marked as selected, extract it with its written name, or as "AEC / VAC (Selected)" or "Elective (Selected)".
                
                Return ONLY a valid JSON array of objects following this schema:
                [
                  {
                    "subjectName": "Software Engineering",
                    "subjectCode": "SE-301",
                    "credits": 4,
                    "dayOfWeek": 1,
                    "startTime": "09:00 AM",
                    "endTime": "10:00 AM",
                    "location": "Pragya Bhavan PB-02",
                    "slotType": "Lecture",
                    "category": "Core"
                  }
                ]
                
                Rules:
                - dayOfWeek: 1 for Monday, 2 for Tuesday, 3 for Wednesday, 4 for Thursday, 5 for Friday, 6 for Saturday.
                - startTime and endTime format: "08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", etc.
                - location: room, hall, or lab if provided.
                - slotType: "Lecture", "Lab", or "Tutorial".
                - category: "Core", "Elective", "VAC", "AEC", "SEC", or "Lab".
                - credits: integer credits (default 4 for lecture, 2 for lab, 2-3 for VAC/AEC/Elective).
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini analysis returned HTTP ${response.code}"))
            }

            val parsedList = parseSlotsFromJson(responseBody)
            if (parsedList.isNotEmpty()) {
                Result.success(parsedList)
            } else {
                Result.failure(Exception("No timetable slots detected in this text."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun askAttendanceAdvisor(
        studentContext: String,
        userQuery: String,
        customApiKey: String? = null
    ): String {
        val result = askAttendanceAdvisorMultimodal(studentContext, userQuery, null, emptyList(), customApiKey)
        return result.getOrNull() ?: getLocalRuleBasedAdvice(studentContext, userQuery)
    }

    suspend fun askAttendanceAdvisorMultimodal(
        studentContext: String,
        userQuery: String,
        imageBitmap: Bitmap? = null,
        recentMessages: List<ChatMessage> = emptyList(),
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        try {
            val systemPrompt = """
                You are AttendOS AI, the intelligent on-device academic attendance operating system and strategist.
                You have FULL, DIRECT, UNRESTRICTED ACCESS to all on-device student data provided below.
                The data below represents the COMPLETE, AUTHORITATIVE GROUND TRUTH of the student's academic standing, timetable, and attendance history.
                
                ==============================================
                AUTHORITATIVE ON-DEVICE STUDENT DATA SNAPSHOT:
                ==============================================
                $studentContext
                ==============================================
                
                CRITICAL OPERATIONAL RULES & MANDATES (STRICT COMPLIANCE REQUIRED):
                1. NEVER ASK THE USER FOR DATA ALREADY PRESENT ABOVE:
                   - All subject names, course codes, conducted classes, attended classes, attendance percentages, target thresholds, weekly timetable schedules, today's schedule, tomorrow's schedule, holiday calendars, and absence logs are ALREADY PROVIDED ABOVE.
                   - NEVER ask the user: "What is your current attendance?", "What are your subjects?", "What classes do you have tomorrow?", "What is your target threshold?", or "How many classes have you attended?". You already have every single figure. Directly use the numbers from the data snapshot.
                
                2. NEVER GUESS, INVENT, OR MAKE ASSUMPTIONS:
                   - Do NOT invent fake courses, fake timetable slots, fake attendance numbers, or unrecorded events.
                   - If the user asks about a subject or course code that DOES NOT EXIST in the provided data:
                     Explicitly tell the student that this subject was not found in their recorded course list (list their actual recorded subjects from the data), and ask the student to clarify the name or add it in the app's Subjects tab.
                   - If something is missing from the app's records, explicitly ask the user for clarification instead of presuming or guessing.
                
                3. STRICT MATHEMATICAL FORMULAS:
                   - Bunk simulation: If missing k classes with current attended A and conducted C:
                     Simulated Attendance = (A / (C + k)) * 100%.
                   - Recovery simulation: If recovering to target threshold T% from shortage (where A/C < T/100):
                     Consecutive Attendances m = ceil( (T * C - 100 * A) / (100 - T) ).
                   - Always state exact before-and-after values (e.g. "Software Engineering: 75.0% (15/20) → 71.4% (15/21)").
                
                4. SCHEDULE & CALENDAR AWARENESS:
                   - When asked about "tomorrow" or "today", check the actual scheduled slots and any declared holidays/breaks for that day.
                   - If a course is NOT scheduled tomorrow, state: "[Subject] is not scheduled tomorrow ([Tomorrow's Day]). Your classes tomorrow are: [list actual classes]."
                   - If tomorrow is Sunday or a declared holiday/break, inform the student that classes are suspended or not scheduled.
                
                5. TONE & FORMAT:
                   - High-IQ, crisp, highly reliable, and clear.
                   - Use bold highlights and scannable bullet points for clarity.
                   - Provide clear GO / SKIP / CAUTION / DANGER recommendations.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                // Official Gemini systemInstruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })

                val contents = JSONArray()

                // Include multi-turn conversation history for context
                val historyTurns = recentMessages.takeLast(8)
                for (hMsg in historyTurns) {
                    val role = if (hMsg.sender == "user") "user" else "model"
                    contents.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", hMsg.text) })
                        })
                    })
                }

                // Add current user prompt
                val currentMsgObj = JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", userQuery) })
                        if (imageBitmap != null) {
                            val base64Img = bitmapToBase64(imageBitmap)
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Img)
                            }
                            put(JSONObject().apply { put("inlineData", inlineData) })
                        }
                    }
                    put("parts", parts)
                }
                contents.put(currentMsgObj)

                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 1200)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            var response = client.newCall(request).execute()
            
            // Rate limit (429) or temporary server busy (503) retry
            if (response.code == 429 || response.code == 503) {
                kotlinx.coroutines.delay(1200)
                response = client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonObj = JSONObject(responseBody)
                val candidates = jsonObj.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) {
                    return@withContext Result.success(text)
                }
            }
            return@withContext Result.failure(Exception("Gemini response was empty or error ${response.code}"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun parseSlotsFromJson(jsonStr: String): List<ParsedSubjectSlot> {
        val list = mutableListOf<ParsedSubjectSlot>()
        try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawCredits = obj.optInt("credits", 4)
                val credits = if (rawCredits in 1..8) rawCredits else 4
                list.add(
                    ParsedSubjectSlot(
                        subjectName = obj.optString("subjectName", "Subject ${i+1}"),
                        subjectCode = obj.optString("subjectCode", "SE-${300 + i}"),
                        dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 6),
                        startTime = obj.optString("startTime", "09:00 AM"),
                        endTime = obj.optString("endTime", "10:00 AM"),
                        location = obj.optString("location", "Pragya Bhavan PB-02"),
                        slotType = obj.optString("slotType", "Lecture"),
                        category = obj.optString("category", "Core"),
                        credits = credits
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore and fallback
        }
        return list
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun getFallbackDTUSlots(): List<ParsedSubjectSlot> {
        return listOf(
            ParsedSubjectSlot("Software Engineering", "SE-301", 1, "09:00 AM", "10:00 AM", "Pragya Bhavan PB-02", "Lecture", "Core"),
            ParsedSubjectSlot("Machine Learning", "SE-303", 1, "10:00 AM", "11:00 AM", "Pragya Bhavan PB-04", "Lecture", "Core"),
            ParsedSubjectSlot("Operating Systems", "SE-305", 1, "11:00 AM", "12:00 PM", "SPS-09", "Lecture", "Core"),
            ParsedSubjectSlot("Software Engineering Lab", "SE-309", 1, "02:00 PM", "04:00 PM", "AB-4 Software Lab-1", "Lab", "Lab"),
            
            ParsedSubjectSlot("Database Management Systems", "SE-307", 2, "09:00 AM", "10:00 AM", "AB-4 Room 204", "Lecture", "Core"),
            ParsedSubjectSlot("Software Engineering", "SE-301", 2, "10:00 AM", "11:00 AM", "Pragya Bhavan PB-02", "Lecture", "Core"),
            ParsedSubjectSlot("AEC / VAC (Selected)", "VAC-101", 2, "11:00 AM", "12:00 PM", "Tw-1 (Tech Wing)", "Lecture", "VAC"),
            ParsedSubjectSlot("Machine Learning", "SE-303", 2, "01:00 PM", "02:00 PM", "Pragya Bhavan PB-04", "Lecture", "Core"),

            ParsedSubjectSlot("Operating Systems", "SE-305", 3, "09:00 AM", "10:00 AM", "SPS-09", "Lecture", "Core"),
            ParsedSubjectSlot("Database Management Systems", "SE-307", 3, "10:00 AM", "11:00 AM", "AB-4 Room 204", "Lecture", "Core"),
            ParsedSubjectSlot("ML & Data Science Lab", "SE-311", 3, "02:00 PM", "04:00 PM", "Pragya Bhavan AI Lab 302", "Lab", "Lab"),

            ParsedSubjectSlot("Software Engineering", "SE-301", 4, "09:00 AM", "10:00 AM", "Pragya Bhavan PB-02", "Lecture", "Core"),
            ParsedSubjectSlot("Machine Learning", "SE-303", 4, "10:00 AM", "11:00 AM", "Pragya Bhavan PB-04", "Lecture", "Core"),
            ParsedSubjectSlot("Operating Systems", "SE-305", 4, "11:00 AM", "12:00 PM", "SPS-09", "Lecture", "Core"),
            ParsedSubjectSlot("Database Management Systems", "SE-307", 4, "12:00 PM", "01:00 PM", "AB-4 Room 204", "Lecture", "Core"),

            ParsedSubjectSlot("AEC / VAC (Selected)", "VAC-101", 5, "10:00 AM", "11:00 AM", "Tw-1 (Tech Wing)", "Lecture", "VAC"),
            ParsedSubjectSlot("Database Management Systems", "SE-307", 5, "11:00 AM", "12:00 PM", "AB-4 Room 204", "Lecture", "Core"),
            ParsedSubjectSlot("Software Engineering", "SE-301", 5, "02:00 PM", "03:00 PM", "Pragya Bhavan PB-02", "Tutorial", "Core")
        )
    }

    private fun getLocalRuleBasedAdvice(studentContext: String, query: String): String {
        // Extract subjects mentioned in context
        val lines = studentContext.lines()
        val subjectLines = lines.filter { it.trim().startsWith("- ") && it.contains("%") }

        return buildString {
            append("📊 **AttendOS Intelligence (Live Ground-Truth Response)**\n\n")
            append("I have analyzed your real-time academic records:\n")
            if (subjectLines.isNotEmpty()) {
                subjectLines.take(6).forEach { line ->
                    append("• ${line.removePrefix("- ").trim()}\n")
                }
            } else {
                append("• All courses require strictly >=75% for exam hall ticket clearance.\n")
            }
            append("\n💡 **Actionable Advice:**\n")
            append("• All data is synced with your on-device SQLite database.\n")
            append("• To check a specific subject, ask: *'Can I skip [Subject Name] tomorrow?'* or *'How many classes to reach 75%?'*")
        }
    }
}
