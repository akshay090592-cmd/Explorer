package com.example.malaysiaitinerary.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.malaysiaitinerary.data.repository.GemmaModelChoice
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MediaPipeGemmaEngine(
    private val context: Context,
    private val modelChoice: GemmaModelChoice,
    private val customModelPath: String?
) : AiEngine {

    override val engineName: String = "On-Device ${modelChoice.displayName}"

    private var liteRtEngine: com.google.ai.edge.litertlm.Engine? = null
    private var liteRtConversation: com.google.ai.edge.litertlm.Conversation? = null
    private var llmInference: LlmInference? = null
    var initErrorReason: String? = null

    init {
        initInference()
    }

    private fun initInference() {
        try {
            val targetPath = when {
                !customModelPath.isNullOrEmpty() && File(customModelPath).exists() -> customModelPath
                File(context.filesDir, modelChoice.filename).exists() -> File(context.filesDir, modelChoice.filename).absolutePath
                else -> {
                    val appFile = context.filesDir.listFiles()?.firstOrNull { f ->
                        f.isFile && (f.name.endsWith(".litertlm") || f.name.endsWith(".task") || f.name.endsWith(".bin"))
                    }
                    if (appFile != null) {
                        appFile.absolutePath
                    } else {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val downloadFile = downloadsDir.listFiles()?.firstOrNull { f ->
                            f.isFile && (f.name.endsWith(".litertlm") || f.name.endsWith(".task") || f.name.endsWith(".bin"))
                        }
                        downloadFile?.absolutePath
                    }
                }
            }

            if (targetPath == null) {
                initErrorReason = "No Gemma 4 model file (.litertlm or .task) found. Please download or select a Gemma 4 model file."
                return
            }

            val targetFile = File(targetPath)
            val fileNameLower = targetFile.name.lowercase()

            if (targetFile.length() < 50 * 1024 * 1024L) {
                initErrorReason = "Model file '${targetFile.name}' is incomplete (${targetFile.length() / (1024 * 1024)} MB). Please download the complete Gemma 4 model file."
                return
            }

            if (isRawTfliteFlatbuffer(targetFile) && !isZipArchive(targetFile)) {
                initErrorReason = "Model file '${targetFile.name}' is an uncompiled TFLite FlatBuffer (raw weights). The LiteRT-LM C++ loader requires a compiled .litertlm model bundle exported via Google's 'litert-torch' export tool."
                return
            }

            // Step 1: Initialize Google AI Edge LiteRT-LM Engine for Gemma 4 (GPU first with automated CPU fallback)
            var lastException: Throwable? = null

            // Attempt GPU Backend first with OpenCL/Vulkan hardware acceleration
            try {
                val gpuBackend = com.google.ai.edge.litertlm.Backend.GPU()
                val config = com.google.ai.edge.litertlm.EngineConfig(
                    modelPath = targetFile.absolutePath,
                    backend = gpuBackend,
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = com.google.ai.edge.litertlm.Engine(config)
                newEngine.initialize()
                liteRtEngine = newEngine
                liteRtConversation = newEngine.createConversation()
            } catch (gpuError: Throwable) {
                gpuError.printStackTrace()
                lastException = gpuError
                liteRtEngine = null
                liteRtConversation = null

                // Seamless Fallback: Initialize CPU Backend on the local model file
                try {
                    val cpuBackend = com.google.ai.edge.litertlm.Backend.CPU()
                    val cpuConfig = com.google.ai.edge.litertlm.EngineConfig(
                        modelPath = targetFile.absolutePath,
                        backend = cpuBackend,
                        cacheDir = context.cacheDir.absolutePath
                    )
                    val cpuEngine = com.google.ai.edge.litertlm.Engine(cpuConfig)
                    cpuEngine.initialize()
                    liteRtEngine = cpuEngine
                    liteRtConversation = cpuEngine.createConversation()
                } catch (cpuError: Throwable) {
                    cpuError.printStackTrace()
                    lastException = cpuError
                    liteRtEngine = null
                    liteRtConversation = null
                }
            }

            // Step 2: Fallback to MediaPipe LlmInference if LiteRT-LM wasn't initialized and file is a zip task
            if (liteRtEngine == null && isZipArchive(targetFile)) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(targetFile.absolutePath)
                        .setMaxTokens(1024)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    lastException = t
                    llmInference = null
                }
            }

            if (liteRtEngine == null && llmInference == null) {
                val rawMsg = lastException?.localizedMessage ?: lastException?.javaClass?.simpleName ?: ""
                initErrorReason = "Gemma 4 LiteRT-LM Engine Error: ${rawMsg.ifBlank { "Failed to initialize Gemma 4 model" }}"
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            liteRtEngine = null
            liteRtConversation = null
            llmInference = null
            initErrorReason = "Engine Error: ${t.localizedMessage ?: "Failed to initialize model"}"
        }
    }

    override suspend fun parseDocument(file: File, mimeType: String): Result<ExtractedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val extractedText = extractTextFromFile(file, mimeType)
            if (extractedText.isBlank()) {
                return@withContext Result.failure(Exception("No text could be extracted from document via OCR."))
            }

            val prompt = """
                Extract travel document details from this text and return strict JSON format:
                Text: "$extractedText"
                JSON Schema:
                {
                   "title": "string",
                   "type": "FLIGHT | ACCOMMODATION | TICKET | MEAL | ID | OTHERS",
                   "date": "YYYY-MM-DD",
                   "startTime": "HH:MM",
                   "location": "string",
                   "expenseAmount": number,
                   "currency": "MYR | INR | USD",
                   "description": "string"
                }
            """.trimIndent()

            val jsonOutput = generateTextInternal(prompt).getOrDefault("")
            val json = parseJsonObject(jsonOutput)

            val title = json.optString("title", file.nameWithoutExtension)
            val type = json.optString("type", "TICKET").uppercase()
            val date = json.optString("date").takeIf { it.isNotBlank() }
            val startTime = json.optString("startTime").takeIf { it.isNotBlank() }
            val location = json.optString("location").takeIf { it.isNotBlank() }
            val expenseAmount = json.optDouble("expenseAmount", 0.0)
            val currency = json.optString("currency", "MYR")
            val description = json.optString("description", extractedText.take(150))

            Result.success(
                ExtractedDocumentResult(
                    title = title,
                    type = type,
                    date = date,
                    startTime = startTime,
                    location = location,
                    expenseAmount = if (expenseAmount > 0) expenseAmount else null,
                    currency = currency,
                    description = description
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateTextInternal(
        prompt: String,
        imageBitmap: Bitmap? = null,
        audioBytes: ByteArray? = null
    ): Result<String> {
        // 1. LiteRT-LM Engine (Google AI Edge Gemma 4 Multimodal)
        if (liteRtConversation != null) {
            return try {
                val contentsList = mutableListOf<com.google.ai.edge.litertlm.Content>()

                if (prompt.isNotBlank()) {
                    contentsList.add(com.google.ai.edge.litertlm.Content.Text(prompt))
                }

                if (imageBitmap != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val bytes = stream.toByteArray()
                    contentsList.add(com.google.ai.edge.litertlm.Content.ImageBytes(bytes))
                }

                val response = if (contentsList.isNotEmpty()) {
                    val contents = com.google.ai.edge.litertlm.Contents.of(contentsList)
                    liteRtConversation!!.sendMessage(contents)
                } else {
                    liteRtConversation!!.sendMessage(prompt)
                }

                val textResult = response.toString()
                if (textResult.isNotBlank()) {
                    Result.success(textResult)
                } else {
                    Result.failure(Exception("Gemma 4 model returned empty response."))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to ML Kit GenAI Vision if photo was attached and LiteRT-LM vision delegate is not present
                if (imageBitmap != null) {
                    val mlKitResult = runMlKitGenAiVision(prompt, imageBitmap)
                    if (!mlKitResult.isNullOrBlank()) {
                        return Result.success(mlKitResult)
                    }
                }
                Result.failure(Exception("Gemma 4 response failed: ${e.localizedMessage}"))
            }
        }

        // 2. Google ML Kit GenAI Image Description API (on-device vision API)
        if (imageBitmap != null) {
            val mlKitVisionResult = runMlKitGenAiVision(prompt, imageBitmap)
            if (!mlKitVisionResult.isNullOrBlank()) {
                return Result.success(mlKitVisionResult)
            }
        }

        // 3. MediaPipe Tasks GenAI (LlmInference)
        val inference = llmInference
            ?: return Result.failure(Exception(initErrorReason ?: "Local Gemma model (.task or .litertlm file) not found or initialized at target path. Please place a valid Gemma model file in Downloads or app files."))

        return try {
            val response = if (imageBitmap != null) {
                try {
                    val mpImageClass = Class.forName("com.google.mediapipe.framework.image.MPImage")
                    val builderClass = Class.forName("com.google.mediapipe.framework.image.BitmapImageBuilder")
                    val constructor = builderClass.getConstructor(Bitmap::class.java)
                    val builder = constructor.newInstance(imageBitmap)
                    val buildMethod = builderClass.getMethod("build")
                    val mpImage = buildMethod.invoke(builder)

                    val genMethod = inference.javaClass.getMethod("generateResponse", mpImageClass, String::class.java)
                    genMethod.invoke(inference, mpImage, prompt) as String
                } catch (e: Exception) {
                    inference.generateResponse(prompt)
                }
            } else {
                inference.generateResponse(prompt)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun runMlKitGenAiVision(prompt: String, bitmap: Bitmap): String? {
        return try {
            val imgDescClass = Class.forName("com.google.mlkit.genai.imagedescription.ImageDescription")
            val getClientMethod = imgDescClass.getMethod("getClient")
            val client = getClientMethod.invoke(null)
            if (client != null) {
                val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
                val fromBitmapMethod = inputImageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.javaPrimitiveType)
                val inputImage = fromBitmapMethod.invoke(null, bitmap, 0)
                val describeMethod = client.javaClass.getMethod("describe", inputImageClass)
                val task = describeMethod.invoke(client, inputImage)
                val getResultMethod = task.javaClass.getMethod("getResult")
                val result = getResultMethod.invoke(task)
                result?.toString()
            } else null
        } catch (e: Exception) {
            try {
                val genClass = Class.forName("com.google.mlkit.genai.prompt.Generation")
                val getClientMethod = genClass.getMethod("getClient")
                val client = getClientMethod.invoke(null) ?: return null
                val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
                val fromBitmapMethod = inputImageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.javaPrimitiveType)
                val inputImage = fromBitmapMethod.invoke(null, bitmap, 0)
                val generateMethod = client.javaClass.getMethod("generateContent", String::class.java, inputImageClass)
                val result = generateMethod.invoke(client, prompt, inputImage)
                result?.toString()
            } catch (ignored: Exception) {
                null
            }
        }
    }

    override suspend fun chat(
        userPrompt: String,
        chatHistory: List<Pair<String, String>>,
        tripSummaryContext: String,
        imageBitmap: Bitmap?,
        audioBytes: ByteArray?
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        val historyContext = chatHistory.takeLast(6).joinToString("\n") {
            "${it.first}: ${it.second}"
        }

        val prompt = """
            System: You are Explorer AI, an offline Gemma 4 Travel Assistant running on mobile.
            Trip Context:
            $tripSummaryContext

            You have access to the following tools:
            <tools>
            [
              {
                "name": "addItineraryItem",
                "description": "Add an activity or meal to the trip schedule",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "date": {"type": "string"},
                    "title": {"type": "string"},
                    "location": {"type": "string"},
                    "startTime": {"type": "string"},
                    "type": {"type": "string", "enum": ["ACTIVITY", "MEAL", "TRANSIT", "ACCOMMODATION"]},
                    "description": {"type": "string"}
                  },
                  "required": ["title", "location"]
                }
              },
              {
                "name": "addExpense",
                "description": "Log an expense into travel ledger",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "amount": {"type": "string"},
                    "currency": {"type": "string"},
                    "category": {"type": "string"},
                    "date": {"type": "string"},
                    "note": {"type": "string"}
                  },
                  "required": ["amount", "category"]
                }
              },
              {
                "name": "addDocument",
                "description": "Add ticket or pass to Briefcase vault",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "fileName": {"type": "string"},
                    "type": {"type": "string"},
                    "date": {"type": "string"}
                  },
                  "required": ["fileName"]
                }
              }
            ]
            </tools>

            When calling a tool, respond with:
            <call:toolName>
            {
              "arg1": "value1"
            }
            </call:toolName>

            $historyContext
            User: $userPrompt
            Assistant:
        """.trimIndent()

        val textResult = generateTextInternal(prompt, imageBitmap, audioBytes)
        if (textResult.isFailure) {
            return@withContext Result.failure(textResult.exceptionOrNull() ?: Exception("Gemma 4 model file not configured."))
        }
        val text = textResult.getOrThrow()

        val (cleanText, extractedTools) = extractGemmaToolCalls(text)
        val inferredTool = tryInferToolCall(userPrompt)

        val finalTools = if (extractedTools.isNotEmpty()) extractedTools else if (inferredTool != null) listOf(inferredTool) else emptyList()

        Result.success(
            AiResponse(
                messageText = cleanText.ifBlank { "Action executed successfully." },
                toolCalls = finalTools
            )
        )

    }

    private fun extractGemmaToolCalls(rawText: String): Pair<String, List<AiToolCall>> {
        val toolCalls = mutableListOf<AiToolCall>()
        var cleanText = rawText

        // 1. Google Official Gemma Function Call Format: <call:toolName>{ "arg": "val" }</call:toolName>
        val callRegex = Regex("""<call:(\w+)>\s*(\{.*?\})\s*</call:\1>""", RegexOption.DOT_MATCHES_ALL)
        callRegex.findAll(rawText).forEach { match ->
            val toolName = match.groupValues[1]
            val argsJson = match.groupValues[2]
            val argsMap = mutableMapOf<String, String>()
            try {
                val json = JSONObject(argsJson)
                json.keys().forEach { k -> argsMap[k] = json.optString(k) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            toolCalls.add(AiToolCall(toolName, argsMap))
        }
        cleanText = rawText.replace(callRegex, "").trim()

        // 2. [TOOLS][{...}][/TOOLS] format
        if (cleanText.contains("[TOOLS]") && cleanText.contains("[/TOOLS]")) {
            val toolsStr = cleanText.substringAfter("[TOOLS]").substringBefore("[/TOOLS]").trim()
            cleanText = cleanText.substringBefore("[TOOLS]").trim()
            try {
                val jsonArray = JSONArray(toolsStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("toolName", obj.optString("name"))
                    val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                    val argsMap = mutableMapOf<String, String>()
                    argsObj.keys().forEach { k -> argsMap[k] = argsObj.optString(k) }
                    if (name.isNotBlank()) toolCalls.add(AiToolCall(name, argsMap))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair(cleanText, toolCalls)
    }

    override suspend fun generateItinerary(
        destination: String,
        daysCount: Int,
        preferences: String,
        enableSearchGrounding: Boolean
    ): Result<StructuredItineraryPlan> = withContext(Dispatchers.IO) {
        val prompt = """
            Create a $daysCount-day itinerary for $destination focusing on $preferences.
            Return strict JSON array of items with schema:
            [
              {
                 "dayNumber": 1,
                 "date": "2026-03-24",
                 "items": [
                   {
                     "title": "string",
                     "locationName": "string",
                     "startTime": "09:00",
                     "description": "string",
                     "type": "ACTIVITY | MEAL | TRANSIT | ACCOMMODATION",
                     "vegOptions": "string",
                     "nonVegOptions": "string"
                   }
                 ]
              }
            ]
        """.trimIndent()

        val jsonOutput = generateTextInternal(prompt).getOrDefault("")

        try {
            val daysList = mutableListOf<DayPlan>()
            
            // Try array parsing first
            val arrayStr = when {
                jsonOutput.contains("[") && jsonOutput.contains("]") -> jsonOutput.substring(jsonOutput.indexOf("["), jsonOutput.lastIndexOf("]") + 1)
                else -> ""
            }

            if (arrayStr.isNotBlank()) {
                try {
                    val array = JSONArray(arrayStr)
                    val baseDate = java.time.LocalDate.now().plusDays(1)
                    for (i in 0 until array.length()) {
                        val dayObj = array.getJSONObject(i)
                        val dayNumber = dayObj.optInt("dayNumber", i + 1)
                        val dayDate = dayObj.optString("date", baseDate.plusDays(i.toLong()).toString())
                        val itemsArray = dayObj.optJSONArray("items") ?: JSONArray()
                        val planItems = mutableListOf<PlanItem>()

                        for (j in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(j)
                            planItems.add(
                                PlanItem(
                                    title = itemObj.optString("title", "Visit Spot ${j + 1}"),
                                    locationName = itemObj.optString("locationName", destination),
                                    startTime = itemObj.optString("startTime", "${9 + (j * 3)}:00"),
                                    description = itemObj.optString("description", "Suggested by Explorer AI"),
                                    type = itemObj.optString("type", "ACTIVITY").uppercase(),
                                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=${itemObj.optString("locationName", destination).replace(" ", "+")}",
                                    vegOptions = itemObj.optString("vegOptions").takeIf { it.isNotBlank() },
                                    nonVegOptions = itemObj.optString("nonVegOptions").takeIf { it.isNotBlank() }
                                )
                            )
                        }

                        if (planItems.isNotEmpty()) {
                            daysList.add(DayPlan(dayNumber = dayNumber, date = dayDate, items = planItems))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback generation if LLM output was empty or invalid JSON
            if (daysList.isEmpty()) {
                val baseDate = java.time.LocalDate.now().plusDays(1)
                for (d in 1..daysCount) {
                    val currentDate = baseDate.plusDays((d - 1).toLong()).toString()
                    val fallbackItems = listOf(
                        PlanItem(
                            title = "Morning Exploration of $destination",
                            locationName = "$destination City Center",
                            startTime = "09:30",
                            description = "Explore top attractions and cultural highlights in $destination.",
                            type = "ACTIVITY",
                            googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=${destination.replace(" ", "+")}"
                        ),
                        PlanItem(
                            title = "Local Culinary Feast",
                            locationName = "$destination Local Dining Spot",
                            startTime = "13:00",
                            description = "Savor authentic traditional dishes and local street food.",
                            type = "MEAL",
                            googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=${destination.replace(" ", "+")}"
                        ),
                        PlanItem(
                            title = "Evening Sightseeing & Relaxation",
                            locationName = "$destination Waterfront & Night Market",
                            startTime = "17:30",
                            description = "Enjoy scenic evening views, night markets, and vibrant local atmosphere.",
                            type = "ACTIVITY",
                            googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=${destination.replace(" ", "+")}"
                        )
                    )
                    daysList.add(DayPlan(dayNumber = d, date = currentDate, items = fallbackItems))
                }
            }

            Result.success(
                StructuredItineraryPlan(
                    tripTitle = "$daysCount-Day $destination Exploration Plan",
                    destination = destination,
                    days = daysList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTextFromFile(file: File, mimeType: String): String {
        return when {
            mimeType.contains("pdf") || file.extension.lowercase() == "pdf" -> extractTextFromPdf(file)
            mimeType.contains("image") || listOf("jpg", "jpeg", "png").contains(file.extension.lowercase()) -> {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
                extractTextFromBitmapSync(bitmap)
            }
            else -> ""
        }
    }

    private fun extractTextFromPdf(file: File): String {
        val textBuilder = StringBuilder()
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val pageCount = minOf(renderer.pageCount, 3)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                textBuilder.append(extractTextFromBitmapSync(bitmap)).append("\n")
                page.close()
            }
            renderer.close()
            descriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return textBuilder.toString()
    }

    private fun extractTextFromBitmapSync(bitmap: Bitmap): String {
        var text = ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val lock = java.lang.Object()
        var isDone = false

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                text = visionText.text
                synchronized(lock) {
                    isDone = true
                    lock.notifyAll()
                }
            }
            .addOnFailureListener {
                synchronized(lock) {
                    isDone = true
                    lock.notifyAll()
                }
            }

        synchronized(lock) {
            if (!isDone) {
                lock.wait(5000)
            }
        }
        return text
    }

    private fun parseJsonObject(input: String): JSONObject {
        val jsonStr = when {
            input.contains("{") && input.contains("}") -> input.substring(input.indexOf("{"), input.lastIndexOf("}") + 1)
            else -> input
        }
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun tryInferToolCall(userPrompt: String): AiToolCall? {
        val lower = userPrompt.lowercase()
        val isExplicitAction = lower.contains("add") || lower.contains("record") ||
                lower.contains("log") || lower.contains("save") || lower.contains("schedule") ||
                lower.contains("create") || lower.contains("spent") || lower.contains("paid") ||
                lower.contains("insert") || lower.contains("put") || lower.contains("book") ||
                lower.contains("itinerary") || lower.contains("activity") || lower.contains("visit")

        if (!isExplicitAction) return null

        val amountMatch = Regex("(\\d+(?:\\.\\d+)?)").find(userPrompt)?.groupValues?.get(1)

        if (lower.contains("expense") || lower.contains("spent") || lower.contains("paid") || lower.contains("cost") || lower.contains("rm") || lower.contains("myr") || lower.contains("inr")) {
            val category = when {
                lower.contains("food") || lower.contains("dinner") || lower.contains("lunch") || lower.contains("meal") || lower.contains("eat") -> "Food & Dining"
                lower.contains("flight") || lower.contains("taxi") || lower.contains("bus") || lower.contains("grab") || lower.contains("transit") || lower.contains("ticket") -> "Transport"
                lower.contains("hotel") || lower.contains("resort") || lower.contains("stay") || lower.contains("airbnb") -> "Accommodation"
                else -> "Shopping & Activities"
            }
            return AiToolCall(
                toolName = "addExpense",
                arguments = mapOf(
                    "amount" to (amountMatch ?: "50.0"),
                    "currency" to (if (lower.contains("inr") || lower.contains("rs")) "INR" else "MYR"),
                    "category" to category,
                    "date" to java.time.LocalDate.now().plusDays(1).toString(),
                    "note" to userPrompt
                )
            )
        }

        if (lower.contains("document") || lower.contains("passport") || lower.contains("boarding pass") || lower.contains("pdf")) {
            return AiToolCall(
                toolName = "addDocument",
                arguments = mapOf(
                    "fileName" to userPrompt.take(30) + ".pdf",
                    "type" to "TICKET",
                    "date" to java.time.LocalDate.now().plusDays(1).toString()
                )
            )
        }

        val cleanTitle = userPrompt.replace(Regex("(?i)(add|save|schedule|record|create|to my itinerary|to itinerary|for tomorrow|for today)"), "").trim().take(50)
        val title = if (cleanTitle.length >= 3) cleanTitle else userPrompt.take(40)
        val location = userPrompt.split(" at ", " in ", " near ").lastOrNull()?.trim()?.take(30) ?: "Malaysia"
        val type = if (lower.contains("dinner") || lower.contains("lunch") || lower.contains("breakfast") || lower.contains("food") || lower.contains("meal") || lower.contains("restaurant") || lower.contains("eat")) "MEAL" else "ACTIVITY"

        return AiToolCall(
            toolName = "addItineraryItem",
            arguments = mapOf(
                "title" to title,
                "location" to location,
                "date" to java.time.LocalDate.now().plusDays(1).toString(),
                "startTime" to "10:00",
                "type" to type,
                "description" to "Added via Explorer AI"
            )
        )
    }

    private fun isZipArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(4)
                val bytesRead = input.read(buffer, 0, 4)
                bytesRead == 4 &&
                    buffer[0] == 'P'.code.toByte() &&
                    buffer[1] == 'K'.code.toByte() &&
                    buffer[2] == 0x03.toByte() &&
                    buffer[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun packageRawBinaryToMediaPipeTaskZip(rawFile: File, outputFile: File): Boolean {
        val tempOutputFile = File(outputFile.parentFile, "temp_${outputFile.name}")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        var isSuccess = false
        try {
            java.util.zip.ZipOutputStream(tempOutputFile.outputStream().buffered()).use { zipOut ->
                zipOut.setMethod(java.util.zip.ZipOutputStream.STORED)

                // 1. Entry: METADATA (STORED uncompressed)
                val metadataBytes = "MediaPipe LlmInference Model Bundle Metadata".toByteArray(Charsets.UTF_8)
                val metadataCrc = java.util.zip.CRC32().apply { update(metadataBytes) }
                val metadataEntry = java.util.zip.ZipEntry("METADATA").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = metadataBytes.size.toLong()
                    compressedSize = metadataBytes.size.toLong()
                    setCrc(metadataCrc.value)
                }
                zipOut.putNextEntry(metadataEntry)
                zipOut.write(metadataBytes)
                zipOut.closeEntry()

                // 2. Entry: model.tflite (STORED uncompressed for mmap)
                val modelBytesSize = rawFile.length()
                val crc32 = java.util.zip.CRC32()
                rawFile.inputStream().buffered().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        crc32.update(buffer, 0, bytesRead)
                    }
                }

                val modelEntry = java.util.zip.ZipEntry("model.tflite").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = modelBytesSize
                    compressedSize = modelBytesSize
                    setCrc(crc32.value)
                }
                zipOut.putNextEntry(modelEntry)
                rawFile.inputStream().buffered().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        zipOut.write(buffer, 0, bytesRead)
                    }
                }
                // 3. Entry: tokenizer.model / TOKENIZER_MODEL (STORED uncompressed)
                try {
                    val tokenizerBytes = context.assets.open("tokenizer.model").use { it.readBytes() }
                    val tokCrc32 = java.util.zip.CRC32().apply { update(tokenizerBytes) }

                    listOf("tokenizer.model", "TOKENIZER_MODEL", "spm.model").forEach { tokName ->
                        val tokEntry = java.util.zip.ZipEntry(tokName).apply {
                            method = java.util.zip.ZipEntry.STORED
                            size = tokenizerBytes.size.toLong()
                            compressedSize = tokenizerBytes.size.toLong()
                            setCrc(tokCrc32.value)
                        }
                        zipOut.putNextEntry(tokEntry)
                        zipOut.write(tokenizerBytes)
                        zipOut.closeEntry()
                    }
                } catch (tokError: Exception) {
                    tokError.printStackTrace()
                }

                // 4. Entry: task.json (STORED uncompressed)
                val taskJsonBytes = "{\"task_name\":\"LlmInference\",\"version\":\"1\"}".toByteArray(Charsets.UTF_8)
                val taskCrc32 = java.util.zip.CRC32().apply { update(taskJsonBytes) }
                val taskJsonEntry = java.util.zip.ZipEntry("task.json").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = taskJsonBytes.size.toLong()
                    compressedSize = taskJsonBytes.size.toLong()
                    setCrc(taskCrc32.value)
                }
                zipOut.putNextEntry(taskJsonEntry)
                zipOut.write(taskJsonBytes)
                zipOut.closeEntry()

                zipOut.finish()
            }

            isSuccess = tempOutputFile.exists() && tempOutputFile.length() > 50 * 1024 * 1024L && isZipArchive(tempOutputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            isSuccess = false
        }

        if (isSuccess) {
            if (outputFile.exists()) outputFile.delete()
            val renamed = tempOutputFile.renameTo(outputFile)
            if (renamed) {
                if (rawFile.exists() && rawFile.absolutePath != outputFile.absolutePath) {
                    try { rawFile.delete() } catch (ignored: Exception) {}
                }
                return true
            }
        }

        if (tempOutputFile.exists()) tempOutputFile.delete()
        return false
    }

    private fun isRawTfliteFlatbuffer(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(8)
                val read = input.read(buffer, 0, 8)
                read == 8 && (
                    buffer[0] == 0x1C.toByte() ||
                    (buffer[4] == 'T'.code.toByte() && buffer[5] == 'F'.code.toByte() && buffer[6] == 'L'.code.toByte() && buffer[7] == '3'.code.toByte())
                )
            }
        } catch (e: Exception) {
            false
        }
    }
}
