package com.example.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val appColors = LocalAppThemeColors.current

    val language by viewModel.language.collectAsStateWithLifecycle()
    val chatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.aiChatLoading.collectAsStateWithLifecycle()
    val speechLangFlow by viewModel.aiSpeechLanguage.collectAsStateWithLifecycle()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()

    // Add state flow collectors for Gemini and Search Grounding modes
    val thinkingMode by viewModel.aiThinkingMode.collectAsStateWithLifecycle()
    val searchGrounding by viewModel.aiSearchGrounding.collectAsStateWithLifecycle()
    val imageGenMode by viewModel.aiImageGenMode.collectAsStateWithLifecycle()

    // Real-Time Voice Call state management
    var isVoiceCallActive by remember { mutableStateOf(false) }
    var voiceConnectionState by remember { mutableStateOf("Ready") } // Ready, Listening to you..., Thinking..., Speaking...
    var voiceTranscribedText by remember { mutableStateOf("") }
    var lastSpokenMessageId by remember { mutableStateOf("") }

    // Align talking language state with either custom preference or fall back to overall app language
    val isTamilTalk = remember(speechLangFlow, language) {
        if (speechLangFlow.isNotBlank()) {
            speechLangFlow == "Tamil"
        } else {
            language == "Tamil"
        }
    }

    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Initialize TextToSpeech engine
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Background continuous speech recognizer for uninterrupted real-time duplex talking (no popup typing dialog overlays!)
    var backgroundSpeechRecognizer by remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }

    fun startBackgroundListening() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            (context as? Activity)?.runOnUiThread {
                try {
                    if (backgroundSpeechRecognizer == null) {
                        val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                            override fun onReadyForSpeech(params: android.os.Bundle?) {
                                voiceConnectionState = "Listening to you..."
                            }
                            override fun onBeginningOfSpeech() {
                                voiceConnectionState = "Listening to you..."
                            }
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {
                                voiceConnectionState = "Thinking..."
                            }
                            override fun onError(error: Int) {
                                if (!isVoiceCallActive) return
                                val errorDesc = when (error) {
                                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Speak now."
                                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out. Speak now."
                                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Retrying..."
                                    else -> "Microphone ready."
                                }
                                voiceConnectionState = errorDesc
                                // Automatically rebuild and restart listening session on timeout
                                if (error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == android.speech.SpeechRecognizer.ERROR_NO_MATCH) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        if (isVoiceCallActive) {
                                            startBackgroundListening()
                                        }
                                    }, 1500)
                                }
                            }
                            override fun onResults(results: android.os.Bundle?) {
                                if (!isVoiceCallActive) return
                                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                val spokenText = matches?.firstOrNull()
                                if (!spokenText.isNullOrBlank()) {
                                    voiceTranscribedText = spokenText
                                    voiceConnectionState = "Thinking..."
                                    viewModel.sendChatMessageToEaswar(spokenText)
                                } else {
                                    startBackgroundListening()
                                }
                            }
                            override fun onPartialResults(partialResults: android.os.Bundle?) {
                                if (!isVoiceCallActive) return
                                val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                val spokenText = matches?.firstOrNull()
                                if (!spokenText.isNullOrBlank()) {
                                    voiceTranscribedText = spokenText
                                }
                            }
                            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                        })
                        backgroundSpeechRecognizer = recognizer
                    }

                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (isTamilTalk) "ta-IN" else "en-US")
                        putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                    backgroundSpeechRecognizer?.startListening(intent)
                    voiceConnectionState = "Listening to you..."
                } catch (e: Exception) {
                    voiceConnectionState = "Mic active."
                }
            }
        } else {
            voiceConnectionState = "Requires Mic Permission"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startBackgroundListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for Real-Time Call", Toast.LENGTH_LONG).show()
            isVoiceCallActive = false
        }
    }

    // Speech recognizer activity launcher (STT backup for standard text input typing)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenList?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                chatInput = spokenText
            }
        }
    }

    // TextToSpeech initialization with progress listener for voice call loops
    DisposableEffect(isTamilTalk, isVoiceCallActive) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val loc = if (isTamilTalk) Locale("ta", "IN") else Locale.US
                ttsInstance?.language = loc
                ttsInstance?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (isVoiceCallActive) {
                            voiceConnectionState = "Speaking..."
                            // Mute speech recognizer microphone while bot speaks to prevent audio echoing feedback
                            backgroundSpeechRecognizer?.stopListening()
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (isVoiceCallActive && utteranceId == "EaswarVoiceCall") {
                            // Automatically resume speech capturing once speaking finishes (fluent back-and-forth duplex)
                            (context as? Activity)?.runOnUiThread {
                                startBackgroundListening()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        if (isVoiceCallActive) {
                            voiceConnectionState = "Call Idle"
                            (context as? Activity)?.runOnUiThread {
                                startBackgroundListening()
                            }
                        }
                    }
                })
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            backgroundSpeechRecognizer?.let {
                it.stopListening()
                it.cancel()
                it.destroy()
            }
            backgroundSpeechRecognizer = null
        }
    }

    // Scroll to the end when messages change
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Capture user voice calls and speak aloud replies automatically
    LaunchedEffect(chatMessages.size, isVoiceCallActive) {
        if (isVoiceCallActive && chatMessages.isNotEmpty()) {
            val lastMsg = chatMessages.last()
            if (!lastMsg.isUser && lastMsg.id != lastSpokenMessageId) {
                lastSpokenMessageId = lastMsg.id
                voiceConnectionState = "Speaking..."
                // Prevent microphoning feedback issues by shutting down recorder
                backgroundSpeechRecognizer?.stopListening()
                backgroundSpeechRecognizer?.cancel()
                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EaswarVoiceCall")
                }
                ttsInstance?.speak(lastMsg.text, TextToSpeech.QUEUE_FLUSH, params, "EaswarVoiceCall")
            }
        }
    }

    // Automatically trigger initial speech when voice call opens up
    LaunchedEffect(isVoiceCallActive) {
        if (isVoiceCallActive) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startBackgroundListening()
            } else {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = translate("Easwar AI Assistant", language),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (offlineModeEnabled) {
                                translate("Offline mode active", language)
                            } else {
                                translate("Online microfinance companion", language)
                            },
                            fontSize = 11.sp,
                            color = if (offlineModeEnabled) Color(0xFFFDA4AF) else Color(0xFF6EE7B7)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Voice Call Button!
                    IconButton(
                        onClick = {
                            isVoiceCallActive = true
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startBackgroundListening()
                            } else {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("ai_chat_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call with Easwar",
                            tint = Color.White
                        )
                    }

                    // Language switcher button inside Chat Toolbar
                    IconButton(
                        onClick = {
                            val newLang = if (isTamilTalk) "English" else "Tamil"
                            viewModel.setAiSpeechLanguage(newLang)
                            Toast.makeText(
                                context,
                                "Voice set to: $newLang",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(
                            text = if (isTamilTalk) "அ" else "En",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Clear Chat History Button
                    IconButton(
                        onClick = {
                            viewModel.clearAiChat()
                            Toast.makeText(context, "History Cleared!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("ai_chat_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorSlateDark)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Horizontal settings chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thinking Mode Toggle Chip
                @OptIn(ExperimentalMaterial3Api::class)
                FilterChip(
                    selected = thinkingMode == "High Thinking",
                    onClick = {
                        val newMode = if (thinkingMode == "High Thinking") "Low Latency" else "High Thinking"
                        viewModel.setAiThinkingMode(newMode)
                        Toast.makeText(context, "Mode set to: $newMode", Toast.LENGTH_SHORT).show()
                    },
                    label = {
                        Text(
                            text = if (thinkingMode == "High Thinking") "🧠 High Thinking" else "⚡ Low Latency",
                            fontSize = 12.sp,
                            color = ColorSlateDark
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (thinkingMode == "High Thinking") Icons.Default.Psychology else Icons.Default.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (thinkingMode == "High Thinking") appColors.primaryAccent else Color.Gray
                        )
                    }
                )

                // Google Grounding Toggle Chip
                @OptIn(ExperimentalMaterial3Api::class)
                FilterChip(
                    selected = searchGrounding,
                    onClick = {
                        viewModel.setAiSearchGrounding(!searchGrounding)
                        Toast.makeText(context, if (!searchGrounding) "Google Search Grounding Enabled" else "Search Grounding Disabled", Toast.LENGTH_SHORT).show()
                    },
                    label = {
                        Text(
                            text = "🌐 Web Grounding",
                            fontSize = 12.sp,
                            color = ColorSlateDark
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (searchGrounding) Color(0xFF047857) else Color.Gray
                        )
                    }
                )

                // Image Generator Toggle Chip
                @OptIn(ExperimentalMaterial3Api::class)
                FilterChip(
                    selected = imageGenMode,
                    onClick = {
                        viewModel.setAiImageGenMode(!imageGenMode)
                        Toast.makeText(context, if (!imageGenMode) "Image Creator Mode Active" else "Image Creator Mode Deactivated", Toast.LENGTH_SHORT).show()
                    },
                    label = {
                        Text(
                            text = "🎨 Image Creator",
                            fontSize = 12.sp,
                            color = ColorSlateDark
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (imageGenMode) Color(0xFFB45309) else Color.Gray
                        )
                    }
                )
            }

            // Chat Log
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatMessages) { message ->
                    val isUser = message.isUser
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            // Easwar avatar circle
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp, top = 2.dp)
                                    .size(32.dp)
                                    .background(ColorSlateDark, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "E",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 2.dp,
                                    bottomEnd = if (isUser) 2.dp else 16.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) appColors.primaryAccent else Color.White
                                ),
                                border = if (isUser) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val bitmap = remember(message.base64Image) {
                                        try {
                                            if (message.base64Image != null) {
                                                val imageBytes = android.util.Base64.decode(message.base64Image, android.util.Base64.DEFAULT)
                                                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                            } else null
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "AI Generated Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .padding(bottom = 8.dp),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }

                                    if (message.text.isNotBlank()) {
                                        ChatMarkdownText(
                                            text = message.text,
                                            textColor = if (isUser) Color.White else ColorSlateDark
                                        )
                                    }
                                }
                            }

                            // Sub-actions block under bubble
                            if (!isUser) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                ) {
                                    // Speak aloud button (TTS)
                                    IconButton(
                                        onClick = {
                                            if (isTtsReady && ttsInstance != null) {
                                                ttsInstance?.speak(
                                                    message.text,
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "EaswarTTS"
                                                )
                                            } else {
                                                Toast.makeText(context, "TTS not ready", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("ai_chat_speak_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Speak Aloud",
                                            tint = ColorSlateDark.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Speak",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Typing feedback
            AnimatedVisibility(visible = isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = appColors.primaryAccent
                    )
                    Text(
                        text = translate("Easwar is analyzing data...", language),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Chat input bar
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Voice recognizer button
                    IconButton(
                        onClick = {
                            if (offlineModeEnabled) {
                                Toast.makeText(
                                    context,
                                    translate("Voice recognizer is not available offline", language),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isTamilTalk) "ta-IN" else "en-US")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (isTamilTalk) "ஈஸ்வரிடம் பேசத் தொடங்குங்கள்..." else "Speak to Easwar...")
                                    }
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Voice recognizer init failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .background(appColors.primaryAccent.copy(alpha = 0.12f), CircleShape)
                            .size(44.dp)
                            .testTag("ai_chat_microphone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Text Field
                    TextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        textStyle = LocalTextStyle.current.copy(color = Color.Black),
                        placeholder = {
                            Text(
                                text = translate("Talk to Easwar...", language),
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        },
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                        shape = RoundedCornerShape(22.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (chatInput.isNotBlank()) {
                                viewModel.sendChatMessageToEaswar(chatInput.trim())
                                chatInput = ""
                                keyboardController?.hide()
                            }
                        })
                    )

                    // Send Button
                    IconButton(
                        onClick = {
                            if (chatInput.isNotBlank()) {
                                viewModel.sendChatMessageToEaswar(chatInput.trim())
                                chatInput = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier
                            .background(appColors.primaryAccent, CircleShape)
                            .size(44.dp)
                            .testTag("ai_chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (isVoiceCallActive) {
            val lastBotMessage = remember(chatMessages) {
                chatMessages.findLast { !it.isUser }?.text ?: ""
            }
            VoiceCallOverlay(
                connectionState = voiceConnectionState,
                transcribedText = voiceTranscribedText,
                lastBotMessageText = lastBotMessage,
                isTamil = isTamilTalk,
                onTapToListen = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startBackgroundListening()
                    } else {
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                onEndCall = {
                    isVoiceCallActive = false
                    ttsInstance?.stop()
                    voiceConnectionState = "Ready"
                }
            )
        }
    }
}

@Composable
fun VoiceCallOverlay(
    connectionState: String,
    transcribedText: String,
    lastBotMessageText: String,
    isTamil: Boolean,
    onTapToListen: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // dark ambient background, cosmic slate
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = if (isTamil) "ஈஸ்வர் குரல் அழைப்பு" else "EASWAR AI VOICE ACTIVE",
                    fontSize = 12.sp,
                    color = Color(0xFF10B981), // glowing emerald
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = connectionState,
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            // Visualizer Ring in center and Text transcription
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing central orb
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            color = when (connectionState) {
                                "Listening to you..." -> Color(0xFF10B981).copy(alpha = 0.2f)
                                "Thinking..." -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                "Speaking..." -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                else -> Color.White.copy(alpha = 0.1f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = when (connectionState) {
                                    "Listening to you..." -> Color(0xFF10B981)
                                    "Thinking..." -> Color(0xFF3B82F6)
                                    "Speaking..." -> Color(0xFF8B5CF6)
                                    else -> Color.White.copy(alpha = 0.3f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                "Speaking..." -> Icons.Default.VolumeUp
                                "Thinking..." -> Icons.Default.Psychology
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Active Icon",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Transcribed or spoken text container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (transcribedText.isNotBlank()) {
                            Text(
                                text = "“ $transcribedText ”",
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        if (lastBotMessageText.isNotBlank()) {
                            Text(
                                text = lastBotMessageText,
                                fontSize = 16.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 5,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (isTamil) "ஈஸ்வர் உங்களுக்காக கேட்கிறார்..." else "Easwar is listening to your command...",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Call actions at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                // Large Red disconnect button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Manual click to speak if voice times out
                    IconButton(
                        onClick = onTapToListen,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Listen",
                            tint = Color.White
                        )
                    }

                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .size(72.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Hang Up",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMarkdownText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                // Header 1, 2, 3: e.g. "# Heading" or "## Heading"
                trimmed.startsWith("#") -> {
                    val hashCount = trimmed.takeWhile { it == '#' }.length
                    val headerText = trimmed.drop(hashCount).trim()
                    if (headerText.isNotEmpty()) {
                        val headerSize = when (hashCount) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 15.sp
                        }
                        Text(
                            text = parseMarkdownToAnnotatedString(headerText),
                            color = textColor,
                            fontSize = headerSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                }
                // Bullet items starting with "*" or "-" followed by space
                (trimmed.startsWith("*") && trimmed.substringAfter("*").startsWith(" ")) ||
                (trimmed.startsWith("-") && trimmed.substringAfter("-").startsWith(" ")) -> {
                    val itemText = trimmed.substring(1).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = textColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownToAnnotatedString(itemText),
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                // Normal text line
                else -> {
                    if (line.isNotEmpty()) {
                        Text(
                            text = parseMarkdownToAnnotatedString(line),
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val boldIndex = text.indexOf("**", index)
            val italicIndex = text.indexOf("*", index)
            
            if (boldIndex != -1 && (italicIndex == -1 || boldIndex <= italicIndex)) {
                append(text.substring(index, boldIndex))
                val nextBold = text.indexOf("**", boldIndex + 2)
                if (nextBold != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(boldIndex + 2, nextBold))
                    pop()
                    index = nextBold + 2
                } else {
                    append("**")
                    index = boldIndex + 2
                }
            } else if (italicIndex != -1) {
                append(text.substring(index, italicIndex))
                val nextItalic = text.indexOf("*", italicIndex + 1)
                if (nextItalic != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Medium))
                    append(text.substring(italicIndex + 1, nextItalic))
                    pop()
                    index = nextItalic + 1
                } else {
                    append("*")
                    index = italicIndex + 1
                }
            } else {
                append(text.substring(index))
                break
            }
        }
    }
}

