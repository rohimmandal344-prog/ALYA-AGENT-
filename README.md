# Alya Assistant (Alisa Mikhailovna Kujou) 🌸

A native Android application featuring **Alisa Mikhailovna Kujou (Alya)** powered by Google's **Gemini Multimodal Live API** with bidirectional real-time audio, user-owned Gemini API key management, local persistent long-term memory, and honest Android device automation.

Developed by **Rohim Mandal** | **SBSM35G-Z STUDIO**

---

## 🌟 Key Features

1. **User-Owned Gemini API Key**
   - Enter, update, or remove your personal Google Gemini API key securely in the Settings screen.
   - Encrypted at rest using **Android KeyStore** with **AES-256 GCM**. Keys are never logged or exposed in plaintext.
   - Real-time API key validation with clear error feedback (invalid key, quota exceeded, or network issues).

2. **Gemini Live API - Real-Time Voice Conversation**
   - Bidirectional real-time voice streaming with Gemini Live Multimodal API powered by `gemini-3.8-flash` / `gemini-3.8-live` voice generation.
   - High-fidelity `AudioManager` implementing native `AudioRecord` and `AudioTrack` with 16kHz PCM encoding.
   - Hardware Acoustic Echo Cancellation (`AcousticEchoCanceler`) & `NoiseSuppressor` on 16kHz PCM audio capture.
   - Thread-safe generational buffer queue preventing overlapping audio segments.
   - **Glitch-free barge-in / interruption**: Instantly flushes audio track buffers when user speaks or server sends an interrupt signal, preventing overlapping speech.
   - Explicit lifecycle connection states: `IDLE` → `CONNECTING` → `CONNECTED` → `LISTENING` → `THINKING` → `SPEAKING` → `INTERRUPTED` → `RECONNECTING` → `ERROR` → `DISCONNECTED`.
   - Real-time audio waveform visualizer and optional live transcripts/subtitles.

3. **Character Persona: Alisa Mikhailovna Kujou (Alya)**
   - 18-year-old student, September 2008.
   - Intelligent, confident, playful, caring, witty, and slightly tsundere.
   - Speaks fluent, natural conversational **Hindi** when addressed in Hindi, and seamless **English** or Japanese when requested.
   - Never fabricates fake memories or device states.

4. **Long-Term Memory System**
   - Offline, local persistent storage using **Room Database**.
   - Stores user-approved facts, preferences, and conversation summaries.
   - Complete control: view, manually add, or delete individual memories, or clear all.
   - Automatically injected into Alya's live instructions so she genuinely remembers what you tell her.

5. **Honest Device Automation**
   - Flashlight / Torch toggle via Android `CameraManager`.
   - Battery capacity and charging status via `BatteryManager`.
   - Media volume controls (Up, Down, Mute, Unmute) via `AudioManager`.
   - Safe app shortcuts via official Android `Intent`s (YouTube, Camera, Calculator, Maps, Settings, Clock).
   - Haptic vibration pulses via `Vibrator` / `VibratorManager`.
   - System clock and timezone reporting.
   - Never claims unsupported root/full OS takeover.

---

## 🚀 Setup & API Key Guide

1. Obtain a Google Gemini API Key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Open the **Alya Assistant** app on your Android device.
3. Tap the **Settings** icon (gear in top right of Chat screen).
4. Paste your API key into the secure input field and tap **Save Key**.
5. Tap **Test Key** to verify your key and quota against the Gemini API.
6. Open **Permissions** (or tap grant when prompted) to enable Microphone access for Live Voice.
7. Tap the **Live Voice Call** button to speak with Alya in real-time!

---

## 🔒 Required Permissions

- `android.permission.RECORD_AUDIO`: Captures microphone input at 16,000Hz PCM for Gemini Live real-time streaming.
- `android.permission.INTERNET`: Connects to Google Gemini API and WebSocket endpoints.
- `android.permission.MODIFY_AUDIO_SETTINGS`: Configures voice communication audio routing for echo cancellation.
- `android.permission.CAMERA`: Optional hardware permission strictly required for torch/flashlight control via Camera2 API (no images are captured).
- `android.permission.VIBRATE`: Provides haptic feedback pulses.
