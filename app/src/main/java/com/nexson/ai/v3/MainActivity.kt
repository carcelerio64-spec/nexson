package com.nexson.ai.v3

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var isPlayingMedia = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("nexon_prefs", Context.MODE_PRIVATE)
        textToSpeech = TextToSpeech(this, this)
        initSpeechRecognizer()

        setContent {
            NexonTheme {
                MainScreen(
                    sharedPreferences = sharedPreferences,
                    isPlayingMedia = isPlayingMedia,
                    onToggleMedia = { play ->
                        isPlayingMedia = play
                        speakText(if (play) "Playing music" else "Music paused")
                    },
                    onTriggerListening = { startSpeechListening() },
                    onExecuteCommand = { command -> processVoiceCommand(command) }
                )
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        }
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexon_utterance_id")
    }

    private fun startSpeechListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required for voice control", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateListeningState(true, "Listening...")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                _waveVolume.value = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                updateListeningState(false, "Processing speech...")
            }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side speech error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                    SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No voice match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server processing error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
                    else -> "Speech recognition failed"
                }
                updateListeningState(false, "Idle")
                addLogEntry("Speech error: $msg")
                speakText("I didn't catch that. Could you please repeat?")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    addLogEntry("Recognized voice: \"$recognizedText\"")
                    processVoiceCommand(recognizedText)
                } else {
                    addLogEntry("Speech recognition returned empty.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun updateListeningState(active: Boolean, status: String) {
        _isListening.value = active
        _statusMessage.value = status
    }

    private fun addLogEntry(text: String) {
        val currentLogs = _actionLogs.value.toMutableList()
        currentLogs.add(0, LogEntry(System.currentTimeMillis(), text))
        _actionLogs.value = currentLogs
    }

    private fun processVoiceCommand(command: String) {
        val endpoint = sharedPreferences.getString("api_endpoint", "") ?: ""
        val token = sharedPreferences.getString("api_token", "") ?: ""
        val customModel = sharedPreferences.getString("custom_model_name", "Nexon AI Engine v3") ?: "Nexon AI Engine v3"

        addLogEntry("Analyzing query: \"$command\"")

        val lowerCommand = command.lowercase(Locale.ROOT)
        var actionHandledOffline = false

        if (lowerCommand.contains("play") || lowerCommand.contains("resume") || lowerCommand.contains("start music")) {
            isPlayingMedia = true
            speakText("Resuming your playback")
            addLogEntry("Offline Action: Resuming playback")
            actionHandledOffline = true
        } else if (lowerCommand.contains("pause") || lowerCommand.contains("stop music") || lowerCommand.contains("freeze")) {
            isPlayingMedia = false
            speakText("Playback paused")
            addLogEntry("Offline Action: Paused media playback")
            actionHandledOffline = true
        } else if (lowerCommand.contains("skip") || lowerCommand.contains("next")) {
            speakText("Skipping to next track")
            addLogEntry("Offline Action: Skipped track")
            actionHandledOffline = true
        } else if (lowerCommand.contains("search for") || lowerCommand.contains("google") || lowerCommand.contains("find info on")) {
            val query = command.substringAfter("search for").substringAfter("google").substringAfter("find info on").trim()
            if (query.isNotEmpty()) {
                performSearch(query)
                speakText("Searching the web for $query")
                addLogEntry("Offline Action: Launching web browser for '$query'")
                actionHandledOffline = true
            } 
        }

        if (endpoint.isBlank()) {
            if (actionHandledOffline) {
                addLogEntry("Command completed locally (Backend URL empty/offline mode)")
            } else {
                addLogEntry("System Alert: Cloud intelligence fallback is not configured. Go to settings to enter your endpoint.")
                speakText("I performed local system matching, but the cloud AI backend is offline or unconfigured. Please check settings.")
            }
            return
        }

        addLogEntry("Forwarding command to AI Cloud: $endpoint")
        _statusMessage.value = "AI thinking..."

        thread {
            try {
                val client = OkHttpClient()
                val jsonBody = JSONObject().apply {
                    put("model", customModel)
                    put("prompt", "Analyze this mobile user voice query. The state is: isPlayingMedia=$isPlayingMedia. Action choices: PLAY, PAUSE, SKIP, PREVIOUS, SEARCH:<query>, TALK:<speech>. Return a JSON response format with keys: 'action', 'parameter', 'reply'. Command: $command")
                    put("max_tokens", 100)
                    put("temperature", 0.3)
                }

                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))

                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val parsedReply = parseResponse(jsonResponse)

                    runOnUiThread {
                        _statusMessage.value = "Idle"
                        addLogEntry("AI response received: ${parsedReply.reply}")
                        speakText(parsedReply.reply)
                        executeCloudAction(parsedReply.action, parsedReply.parameter)
                    }
                } else {
                    runOnUiThread {
                        _statusMessage.value = "Idle"
                        addLogEntry("HTTP Error: Code ${response.code}. Ensure URL matches your remote model provider's completion route.")
                        speakText("Cloud backend response failed with error code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    _statusMessage.value = "Idle"
                    addLogEntry("Network Exception: ${e.localizedMessage}")
                    speakText("Failed to establish a live connection to the AI backend")
                } 
            }
        }
    }

    private data class AiResult(val action: String, val parameter: String, val reply: String)

    private fun parseResponse(json: JSONObject): AiResult {
        return try {
            if (json.has("choices")) {
                val choice = json.getJSONArray("choices").getJSONObject(0)
                val text = if (choice.has("message")) {
                    choice.getJSONObject("message").getString("content")
                } else {
                    choice.getString("text")
                }
                parseRawAiText(text)
            } else if (json.has("response")) {
                val text = json.getString("response")
                parseRawAiText(text)
            } else if (json.has("reply")) {
                AiResult(
                    action = json.optString("action", "TALK"),
                    parameter = json.optString("parameter", ""),
                    reply = json.optString("reply", "Understood.")
                )
            } else {
                parseRawAiText(json.toString())
            }
        } catch (e: Exception) {
            AiResult("TALK", "", "I processed your request, but could not decode the backend structure.")
        }
    }

    private fun parseRawAiText(text: String): AiResult {
        try {
            val startIdx = text.indexOf("{")
            val endIdx = text.lastIndexOf("}")
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val innerJson = JSONObject(text.substring(startIdx, endIdx + 1))
                return AiResult(
                    action = innerJson.optString("action", "TALK"),
                    parameter = innerJson.optString("parameter", ""),
                    reply = innerJson.optString("reply", "Completed command")
                )
            }
        } catch (e: Exception) {}

        val lowerText = text.lowercase()
        return when {
            lowerText.contains("pause") -> AiResult("PAUSE", "", text)
            lowerText.contains("play") -> AiResult("PLAY", "", text)
            lowerText.contains("skip") -> AiResult("SKIP", "", text)
            lowerText.contains("search") -> {
                val keyword = text.substringAfter("search").trim()
                AiResult("SEARCH", keyword, "Searching for $keyword")
            }
            else -> AiResult("TALK", "", text)
        }
    }

    private fun executeCloudAction(action: String, parameter: String) {
        when (action.uppercase(Locale.ROOT)) {
            "PLAY" -> {
                isPlayingMedia = true
                addLogEntry("AI Cloud Action: Resumed playback")
            }
            "PAUSE" -> {
                isPlayingMedia = false
                addLogEntry("AI Cloud Action: Paused media")
            }
            "SKIP" -> {
                addLogEntry("AI Cloud Action: Skipped song")
            }
            "SEARCH" -> {
                if (parameter.isNotEmpty()) {
                    performSearch(parameter)
                    addLogEntry("AI Cloud Action: Executed web search for '$parameter'")
                }
            }
            else -> {}
        }
    }

    private fun performSearch(query: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            addLogEntry("Error: No browser available to search.")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    companion object {
        private val _actionLogs = mutableStateOf<List<LogEntry>>(emptyList())
        val actionLogs: State<List<LogEntry>> = _actionLogs

        private val _isListening = mutableStateOf(false)
        val isListening: State<Boolean> = _isListening

        private val _statusMessage = mutableStateOf("Idle")
        val statusMessage: State<String> = _statusMessage

        private val _waveVolume = mutableStateOf(0f)
        val waveVolume: State<Float> = _waveVolume
    }
}

data class LogEntry(
    val timestamp: Long,
    val message: String
)

@Composable
fun NexonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC5),
            background = Color(0xFF0D0E15),
            surface = Color(0xFF1E1F28),
            onBackground = Color(0xFFF1F1F4),
            onSurface = Color(0xFFE3E3E6)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedPreferences: SharedPreferences,
    isPlayingMedia: Boolean,
    onToggleMedia: (Boolean) -> Unit,
    onTriggerListening: () -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    val context = LocalContext.current
    var isSettingsOpen by remember { mutableStateOf(false) }

    var endpointUrl by remember { mutableStateOf(sharedPreferences.getString("api_endpoint", "") ?: "") }
    var apiToken by remember { mutableStateOf(sharedPreferences.getString("api_token", "") ?: "") }
    var customModel by remember { mutableStateOf(sharedPreferences.getString("custom_model_name", "Nexon AI Engine v3") ?: "Nexon AI Engine v3") }

    val logs by MainActivity.actionLogs
    val isListening by MainActivity.isListening
    val currentStatus by MainActivity.statusMessage
    val volumeLevel by MainActivity.waveVolume

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onTriggerListening()
        } else {
            Toast.makeText(context, "Microphone permission is required to process commands directly.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color.Red else Color.Green)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nexon AI v3 Pro",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSettingsOpen = !isSettingsOpen }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Backend Settings",
                            tint = if (isSettingsOpen) Color(0xFF03DAC5) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0E15)
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13141F))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isListening) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth()
                        ) {
                            val maxBarHeight = 35
                            repeat(8) { index ->
                                val scaledHeight = remember(volumeLevel) {
                                    val factor = (Math.sin(volumeLevel.toDouble() + index) * 10 + 15).coerceIn(4.0, maxBarHeight.toDouble())
                                    factor.dp
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .width(6.dp)
                                        .height(scaledHeight)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF03DAC5), Color(0xFF6200EE))
                                            )
                                        )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Tap circle & speak hands-free",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF6200EE), Color(0xFF0D0E15)),
                                    radius = 180f
                                )
                            )
                            .border(
                                width = 3.dp,
                                color = if (isListening) Color(0xFF03DAC5) else Color(0xFF6200EE),
                                shape = CircleShape
                            )
                            .clickable {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    onTriggerListening()
                                } else {
                                    voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Warning else Icons.Default.PlayArrow,
                            contentDescription = "Trigger Assistant",
                            modifier = Modifier.size(36.dp),
                            tint = if (isListening) Color(0xFF03DAC5) else Color.White
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF0D0E15)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) { 
            AnimatedVisibility(visible = isSettingsOpen) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F28)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI System Configuration",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF03DAC5)
                        )
                        Text(
                            text = "Provide custom API Gateway for remote intelligence path:",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = {
                                endpointUrl = it
                                sharedPreferences.edit().putString("api_endpoint", it).apply()
                            },
                            label = { Text("HTTPS Backend Endpoint") },
                            placeholder = { Text("https://your-model-host/v1/completions") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = {
                                apiToken = it
                                sharedPreferences.edit().putString("api_token", it).apply()
                            },
                            label = { Text("API Bearer Token (Optional)") },
                            placeholder = { Text("sk-proj-...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customModel,
                            onValueChange = {
                                customModel = it
                                sharedPreferences.edit().putString("custom_model_name", it).apply()
                            },
                            label = { Text("Model Tag Name") },
                            placeholder = { Text("gpt-4o / llama3") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                isSettingsOpen = false
                                Toast.makeText(context, "Settings saved securely.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply & Save")
                        }
                    }
                }
            }

            var typedCommand by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = typedCommand,
                    onValueChange = { typedCommand = it },
                    placeholder = { Text("Type prompt (e.g. play next, search news)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1F28),
                        unfocusedContainerColor = Color(0xFF11121A)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6200EE))
                        .clickable {
                            if (typedCommand.isNotBlank()) {
                                onExecuteCommand(typedCommand)
                                typedCommand = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send prompt",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F28))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Nexon Media State",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isPlayingMedia) "Status: STREAMING PLAYBACK" else "Status: PAUSED / IDLE",
                            fontSize = 11.sp,
                            color = if (isPlayingMedia) Color(0xFF03DAC5) else Color.LightGray
                        )
                    }

                    Row {
                        IconButton(onClick = { onToggleMedia(!isPlayingMedia) }) {
                            Icon(
                                imageVector = if (isPlayingMedia) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "PlayPause Toggle",
                                tint = Color(0xFF03DAC5)
                            )
                        } 
                    }
                }
            }

            Text(
                text = "SYSTEM ENGINE CONSOLE",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF08090E))
                    .border(1.dp, Color(0xFF232533), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No system logs. Speak or type a query above.\nSystem is ready.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(logs) { log ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = "[${android.text.format.DateFormat.format("HH:mm:ss", log.timestamp)}] ${log.message}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.message.startsWith("System Alert") || log.message.startsWith("Error")) Color(0xFFFF4D4D)
                                            else if (log.message.startsWith("AI response") || log.message.startsWith("AI Cloud Action")) Color(0xFF03DAC5)
                                            else if (log.message.startsWith("Offline Action")) Color(0xFFB39DDB)
                                            else Color.LightGray
                                )
                                Divider(color = Color(0xFF1E2030), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
