package com.carcelerio64.nexson

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AiAssistantScreen()
            }
        }
    }
}

data class Message(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen() {
    var inputMessage by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            Message("Hello! I am Nexson, your AI Assistant for Factory testing. How can I help you today?", false)
        )
    }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexson AI Assistant", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) {
                    message ->
                    ChatBubble(message = message)
                }
                if (isGenerating) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Nexson is thinking...",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputMessage.isNotBlank() && !isGenerating) {
                                val userText = inputMessage
                                messages.add(Message(userText, true))
                                inputMessage = ""
                                isGenerating = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size)
                                    delay(1200)
                                    val aiResponse = getAiResponse(userText)
                                    messages.add(Message(aiResponse, false))
                                    isGenerating = false
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val backgroundBubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = backgroundBubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                modifier = Modifier.padding(16.dp),
                fontSize = 16.sp
            )
        }
    }
}

fun getAiResponse(query: String): String {
    val lower = query.lowercase()
    return when {
        lower.contains("status") -> "All Factory end-to-end testing systems are fully operational."
        lower.contains("test") -> "Running simulated diagnostic test... Success! All modules verified."
        lower.contains("hello" ) || lower.contains("hi") -> "Hello there! Ready to execute factory verification protocols."
        else -> "Nexson processed your query: '$query'. Systems look stable and ready for production."
    }
}
