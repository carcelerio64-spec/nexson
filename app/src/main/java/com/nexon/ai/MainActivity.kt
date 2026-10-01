package com.nexon.ai

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Song(val id: Long, val title: String, val artist: String, val uri: android.net.Uri)
data class ChatMessage(val sender: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                NexonMainScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexonMainScreen(context: Context) {
    var currentTab by remember { mutableStateOf(0) }

    // Config State
    var backendUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("gpt-3.5-turbo") }
    var isConfigured by remember { mutableStateOf(false) }

    // Chat State
    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>(
        ChatMessage("System", "Welcome to Nexon AI. Configure your API backend in settings to begin real inference.")
    )) }
    var isGenerating by remember { mutableStateOf(false) }

    // Media State
    var songList by remember { mutableStateOf(listOf<Song>()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Load local music
    LaunchedEffect(Unit) {
        val songs = mutableListOf<Song>()
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown"
                    val artist = it.getString(artistCol) ?: "Unknown"
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs.add(Song(id, title, artist, uri))
                }
            }
        }
        songList = songs
    }

    fun playSong(song: Song) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, song.uri)
                prepare()
                start()
                setOnCompletionListener { isPlaying = false }
            }
            currentSong = song
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            } else {
                it.start()
                isPlaying = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexon AI") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("AI") },
                    label = { Text("Assistant") },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("Media") },
                    label = { Text("Playback") },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 }
                )
                NavigationBarItem(
                    icon = { Text("Config") },
                    label = { Text("Settings") },
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 }
                )
            }
        }
    ) { paddingVals ->
        Box(modifier = Modifier.padding(paddingVals).fillMaxSize()) {
            when (currentTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        if (!isConfigured) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Backend Not Configured", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Please set your HTTPS backend URL and API Key in the Settings tab to enable real inference.")
                                }
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(chatMessages) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(text = it.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(text = it.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ask Nexon AI...") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (chatInput.isBlank()) return@Button
                                    val prompt = chatInput
                                    chatInput = ""
                                    chatMessages = chatMessages + ChatMessage("User", prompt)

                                    if (!isConfigured) {
                                        chatMessages = chatMessages + ChatMessage("Nexon AI", "Error: Backend is unconfigured. Please provide an HTTPS inference endpoint in settings.")
                                        return@Button
                                    }

                                    isGenerating = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val client = OkHttpClient.Builder()
                                                .connectTimeout(30, TimeUnit.SECONDS)
                                                .readTimeout(30, TimeUnit.SECONDS)
                                                .build()

                                            val jsonBody = JSONObject().apply {
                                                put("model", modelName)
                                                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                                            }

                                            val request = Request.Builder()
                                                .url(backendUrl)
                                                .addHeader("Authorization", "Bearer $apiKey")
                                                .addHeader("Content-Type", "application/json")
                                                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                                                .build()

                                            val response = client.newCall(request).execute()
                                            val responseBody = response.body?.string() ?: ""

                                            if (response.isSuccessful) {
                                                val respJson = JSONObject(responseBody)
                                                val choices = respJson.getJSONArray("choices")
                                                val messageObj = choices.getJSONObject(0).getJSONObject("message")
                                                val answer = messageObj.getString("content")

                                                withContext(Dispatchers.Main) {
                                                    chatMessages = chatMessages + ChatMessage("Nexon AI", answer.trim())
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    chatMessages = chatMessages + ChatMessage("Nexon AI", "API Error (${response.code}): $responseBody")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                chatMessages = chatMessages + ChatMessage("Nexon AI", "Connection Error: ${e.localizedMessage}")
                                            }
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                },
                                enabled = !isGenerating
                            ) {
                                Text(if (isGenerating) "..." else "Send")
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text("Media Library", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(songList) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { playSong(it) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = it.title, style = MaterialTheme.typography.titleMedium)
                                        Text(text = it.artist, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        currentSong?.let {
                            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "Now Playing: ${it.title}", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(onClick = { togglePlayPause() }) {
                                            Text(if (isPlaying) "Pause" else "Play")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Inference Configuration", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            label = { Text("HTTPS Backend URL (e.g. OpenAI/Ollama Endpoint)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text(
                                "Model Name"
                            ) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isConfigured = backendUrl.isNotBlank()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Configuration")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Note: API keys and configuration values are strictly kept in memory at runtime and are never embedded into source files or APK binaries.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}