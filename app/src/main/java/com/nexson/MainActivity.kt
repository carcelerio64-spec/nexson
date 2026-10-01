package com.nexson

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var mediaManager: NexonMediaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaManager = NexonMediaManager(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF2979FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexonDashboard(mediaManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaManager.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexonDashboard(mediaManager: NexonMediaManager) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("NexonSettings", Context.MODE_PRIVATE)
    }

    var endpoint by remember {
        mutableStateOf(sharedPreferences.getString("endpoint_url", "") ?: "")
    }
    var apiKey by remember {
        mutableStateOf(sharedPreferences.getString("api_key", "") ?: "")
    }
    var promptInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isConfigured by remember { mutableStateOf(endpoint.isNotBlank()) }

    // Media states linked to our mediaManager
    var isPlaying by remember { mutableStateOf(mediaManager.isPlaying()) }
    var currentTrack by remember { mutableStateOf(mediaManager.currentTrack) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "NEXON AI DASHBOARD",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // SECTION 1: Status & Setup State Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConfigured) Color(0xFF1B5E20) else Color(0xFFC62828)
                ),
                modifier = Modifier.fillMaxWidth()
            ) { 
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status Icon",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isConfigured) "AI Inference Backend: Online" else "AI Inference Backend: Offline",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isConfigured) "Ready to query: $endpoint" else "Please configure your custom HTTPS endpoint below to enable AI capability.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SECTION 2: Media Control Center
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACTIVE MEDIA STREAM",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Track visualization or disk
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFF2C2C2C), shape = RoundedCornerShape(50))
                            .border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Album Art Placeholder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentTrack.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = currentTrack.artist,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Media Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            mediaManager.previous()
                            currentTrack = mediaManager.currentTrack
                            isPlaying = mediaManager.isPlaying()
                        }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Previous Track",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (isPlaying) {
                                    mediaManager.pause()
                                } else {
                                    mediaManager.play()
                                }
                                isPlaying = mediaManager.isPlaying()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Menu else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(32.dp),
                                tint = Color.Black
                            )
                        }

                        IconButton(onClick = {
                            mediaManager.next()
                            currentTrack = mediaManager.currentTrack
                            isPlaying = mediaManager.isPlaying()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next Track",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // SECTION 3: Nexon AI Assistant Query
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "NEXON INTUITIVE INTERFACE",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Describe your desired playback adjustments or inquire details about artists, related genres, or context of active content.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text("Ask Nexon Assistant or request media change") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        ),
                        singleLine = false,
                        maxLines = 3,
                        placeholder = { Text("e.g. Find context about '${currentTrack.artist}'") }
                    )

                    Button(
                        onClick = {
                            if (!isConfigured) {
                                aiResponse = "ERROR: Assistant requests are disabled because no runtime HTTPS endpoint is configured. Go to 'Runtime Backend Configuration' section below to connect a model."
                            } else {
                                isLoading = true
                                aiResponse = null
                                coroutineScope.launch {
                                    val promptToSend = "Current Track: '${currentTrack.title}' by ${currentTrack.artist}. Request: $promptInput"
                                    val result = queryBackend(endpoint, apiKey, promptToSend)
                                    aiResponse = result
                                    isLoading = false

                                    // Act on intent extracted via simple instructions
                                    val lowerRes = result.lowercase()
                                    if (lowerRes.contains("play") || lowerRes.contains("resume")) {
                                        mediaManager.play()
                                        isPlaying = mediaManager.isPlaying()
                                    } else if (lowerRes.contains("pause") || lowerRes.contains("stop")) {
                                        mediaManager.pause()
                                        isPlaying = mediaManager.isPlaying()
                                    } else if (lowerRes.contains("next") || lowerRes.contains("skip")) {
                                        mediaManager.next()
                                        currentTrack = mediaManager.currentTrack
                                        isPlaying = mediaManager.isPlaying()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Query Nexon AI")
                        }
                    }

                    if (aiResponse != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2C), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "Response:",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = aiResponse ?: "",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } 
                        }
                    }
                }
            }

            // SECTION 4: Runtime Backend Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                var expanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RUNTIME BACKEND CONFIGURATION",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.Settings,
                                contentDescription = "Toggle config panel"
                            )
                        } 
                    }

                    if (expanded || !isConfigured) {
                        Text(
                            text = "Provide a fully qualified HTTPS URL for any AI completion API endpoint (e.g. your custom backend or standard compatible service) along with optional authentication.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("HTTPS Endpoint URL") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://your-ai-backend.com/api/chat") }
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Bearer / Authorization Key (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("sk-proj-...") }
                        )

                        Button(
                            onClick = {
                                sharedPreferences.edit()
                                    .putString("endpoint_url", endpoint)
                                    .putString("api_key", apiKey)
                                    .apply()
                                isConfigured = endpoint.isNotBlank()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun queryBackend(endpointUrl: String, apiKey: String, prompt: String): String {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val json = JSONObject()
        try {
            json.put("model", "nexon-ai-model")
            val messageArray = org.json.JSONArray()
            val messageObj = JSONObject()
            messageObj.put("role", "user")
            messageObj.put("content", prompt)
            messageArray.put(messageObj)
            json.put("messages", messageArray)

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(body)

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                parseResponse(bodyString)
            } else {
                "HTTP Error: Code ${response.code} - ${response.message}"
            }
        } catch (e: Exception) {
            "Error querying backend: ${e.localizedMessage ?: "Unknown network exception"}"
        }
    }
}

private fun parseResponse(jsonString: String): String {
    return try {
        val obj = JSONObject(jsonString)
        if (obj.has("choices")) {
            val choices = obj.getJSONArray("choices")
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                if (choice.has("message")) {
                    return choice.getJSONObject("message").getString("content")
                }
            }
        }
        jsonString
    } catch (e: Exception) {
        jsonString
    }
}
