# Scam Shield – Native Android Companion Application

Native Kotlin + Jetpack Compose companion app for **Scam Shield**, built strictly inside `android/` while integrating with the existing FastAPI backend and Firebase Authentication project (`scam-shield-51260`).

## Key Capabilities & Privacy Architecture

1. **Local Trusted Contact Identification (`ContactsContract` + Room)**
   - Reads saved mobile contacts locally using `READ_CONTACTS`.
   - Normalizes Indian 10-digit mobile numbers (`+91`, `0`, `0091`, punctuation, `tel:`/`sip:` URIs) and international numbers via `PhoneNumberNormalizer`.
   - Every saved contact is **TRUSTED** by default and bypassed during incoming call screening.
   - **Privacy guarantee**: Saved contacts are matched strictly on-device in SQLite/Room and are **never uploaded** to the backend or analyzed by AI.
   - If Contacts permission is denied, the app displays a clear notice explaining that trusted-contact exclusion cannot be guaranteed.

2. **Mode A – Live Incoming Call Screening (`CallScreeningService`)**
   - Uses Android's official `android.telecom.CallScreeningService` (`ScamCallScreeningService`) and `RoleManager.ROLE_CALL_SCREENING`.
   - Bypasses saved trusted contacts completely.
   - Checks unknown/unavailable callers locally within Android's 5-second screening budget, never blocks calls automatically by default, and displays an `Unknown Caller` alert (or elevates locally if that number was previously analyzed and flagged).
   - Never claims speech was analyzed automatically on cellular calls.

3. **Mode B – User-Authorized Live Audio Analysis (`ForegroundService` + `AudioRecord` + Faster-Whisper)**
   - Android restricts third-party apps from capturing both parties of private cellular voice streams internally.
   - Mode B provides explicit user consent, requests `RECORD_AUDIO`, shows a persistent foreground microphone indicator, captures valid 16kHz PCM RIFF `.wav` chunks from supported microphone input (designed for Speakerphone calls or direct nearby speech), supports immediate stop, and allows optional Media3 (`ExoPlayer`) local segment review before deletion.
   - Sends supported audio chunks to `/api/audio/upload` and `/api/analyze/audio`, and text transcripts to `/api/analyze/text`.

4. **AI Scam Categories & Risk Tiers**
   - Supports all 8 backend detection categories: `authority_impersonation`, `threats_intimidation`, `urgency`, `credential_harvesting`, `account_suspension`, `secrecy_isolation`, `fear_manipulation`, and `financial_pressure`.
   - Displays backend risk tiers (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) verbatim along with the backend's heuristic disclaimer.

## Setup Instructions

### 1. Backend Connection
- **Android Emulator**: Default debug backend URL is `http://10.0.2.2:8000` (routes to `127.0.0.1:8000` on host Windows/macOS/Linux).
- **Physical Android Phone (LAN)**: Run the existing FastAPI server bound to LAN (`uvicorn backend.main:app --host 0.0.0.0 --port 8000`) and change the URL inside **Settings -> FastAPI Backend Connection** to `http://<your-computer-ip>:8000` (or HTTPS reverse proxy).

### 2. Firebase Authentication Setup
- The app initializes `FirebaseApp` automatically if `android/app/google-services.json` is present.
- If `google-services.json` is not present locally, `FirebaseBootstrapper` falls back to public project coordinates for `scam-shield-51260` (overridable in `android/local.properties` via `firebase.apiKey`, `firebase.appId`, `firebase.projectId`, `firebase.storageBucket`).
- For production release builds, register package `com.scamshield.android` in Firebase Console and place `android/app/google-services.json` cleanly (gitignored by `android/.gitignore`).

### 3. Enabling Call Screening on Device
- Complete Onboarding and tap **Set as Call Screener** (`RoleManager.ROLE_CALL_SCREENING`), or go to Android **Settings -> Apps -> Default apps -> Caller ID & spam app -> Scam Shield**.
- Manufacturer note (Xiaomi/MIUI, Samsung One UI, Oppo/Realme/OnePlus): Ensure Scam Shield is exempt from aggressive background battery killing so the OS can bind `ScamCallScreeningService` reliably on incoming calls.

## Build & Test Commands

Run inside `D:\ScamShield\android`:

```powershell
# Run JVM unit tests & Robolectric + Compose UI tests
.\gradlew.bat testDebugUnitTest

# Build debug APK
.\gradlew.bat assembleDebug

# Compile instrumented Android tests
.\gradlew.bat compileDebugAndroidTestKotlin
```

