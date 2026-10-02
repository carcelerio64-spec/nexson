# Nexon AI v3 Pro - Advanced Hands-Free Voice Assistant

Nexon AI v3 is an ultra-fast on-device speech processing framework for Android built on Jetpack Compose and optimized for highly responsive hands-free device management, speech-to-text transcription, media controls, and browser search actions.

## Runtime Configuration & Limits

To run this application with full Cloud intelligence, configuration of an HTTPS backend is required:

1. **Local & System Actions (Offline Mode):**
   - Commands such as `"play"`, `"pause"`, `"skip"`, or `"search for [term]"` are analyzed locally on-device for immediate response and executed without any external cloud delay.

2. **Cloud AI Backend Path (HTTPS Gateway):**
   - Click on the **Settings gear icon** in the top right corner.
   - Enter your HTTPS backend URL (e.g., standard OpenAI compatibility completion endpoint, Ollama server, or custom prompt routing backend).
   - Provide an optional API bearer token if required by your gateway security policies.
   - Specify your model name.
   - The application securely dispatches complex queries to your endpoint and returns fully executed mobile voice controls.

3. **Required Permissions:**
   - Microphone/Audio recording (`android.permission.RECORD_AUDIO`) is required to transcribe speaking input. Permission can be granted on launch.

4. **Limits:**
   - Continuous offline keyword wake-word triggers depend on system level Assistant bindings. For hands-free testing on the simulator, trigger via the mic action or the quick prompt text command entry box.
