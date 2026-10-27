# Simulate Call: App Details

**Simulate Call** is an Android app that makes your phone receive a realistic incoming call after a delay you choose. You set who is calling (name, number, photo) and, if you like, a message the caller speaks once you answer. When the call ends, it is saved in your phone's real call history.

| | |
|---|---|
| Platform | Android 8.0 (API 26) and newer |
| App ID | `com.fakecall.app` |
| Version | 1.0 |
| Tested on | Samsung Galaxy M51 (SM-M515F), Android 12 |

---

## Features

### 1. Caller Photo
- Tap the round picture (or **Add photo**) to choose an image with Android's built-in photo picker.
- The image is copied into the app's private storage, so it is still available when the call rings, even if you lock the phone or close the app.
- **Remove photo** clears it.
- Older photos are deleted only after a new one has been saved successfully, so a failed copy never removes a photo you already chose.

### 2. Photo Display Style
Appears once a photo is added and sets how the photo appears on the call screen:
- **Circle:** a round photo above the caller's name.
- **Full screen:** the photo fills the whole call screen. The top and bottom are darkened slightly so the name and buttons stay readable. The photo is loaded at a higher resolution in this mode so it stays sharp.

### 3. Contact Picker
- **Choose from contacts** opens your phonebook and fills in the contact's name and number.
- No contacts permission is needed: Android shares only the one contact you pick.
- The app also tries to use the contact's picture, but Android usually blocks this without full contacts permission, so in most cases you add a photo yourself.

### 4. Caller Name
- Free-text name shown on the call screen and in notifications.
- If left empty, the phone number is shown instead.

### 5. Phone Number with Country Code
- A country code dropdown with flags, covering 33 countries.
- The default country is detected automatically from the SIM card, then the mobile network, then the phone's language region (e.g. 🇮🇳 +91 in India).
- A number typed with `+` or `00` keeps its own country code. Otherwise the selected code is added and any leading `0` is removed. Example: `09876543210` with +91 becomes `+919876543210`.
- An error is shown if no number is entered.

### 6. Voice Message
An optional message the caller "speaks" once you answer.
- **Text box:** what the caller should say.
- **Female / Male:** the voice's gender.
- **Voice 1 / 2 / 3 / 4:** choose between the phone's different voices of that gender (up to 4), in the phone's language.
- **Pitch slider in hertz:** set the exact voice pitch in 5 Hz steps. The current value is shown, e.g. "220 Hz".
  - **Female:** 150–320 Hz (default 220 Hz). This covers Indian women's voices, which typically average about 180–250 Hz, with room either side.
  - **Male:** 85–180 Hz (default 125 Hz).
  - Under the slider, the app shows the typical range for that gender and the chosen voice's own natural pitch.
  - Each voice has a different natural pitch, so the app works out the right adjustment for the chosen voice. 220 Hz sounds like 220 Hz on every voice.
  - The natural pitches of the Google English (India and US) voices were measured on the test phone: the three Indian female voices are about 237, 276 and 182 Hz, and the Indian male voice about 139 Hz. Tests rendered at 150, 220 and 300 Hz came out within about 5% of the target.
- **Instant preview:** ▶ plays the message on the loudspeaker. Changing the gender, voice or pitch also plays it immediately, so voices are easy to compare.
- Uses the phone's built-in text-to-speech: free, and works offline.
- Google's speech engine is used when installed, because it includes both male and female voices. Some phone engines, such as Samsung's, only include a female voice.
- Voices are matched to male or female by their names. The Google English (US and India) voices were checked by measuring their pitch on the test phone.
- If no matching voice exists, the default voice is pitch-shifted instead.
- Leave the box empty for a silent call.

### 7. Ring Delay
Choose how long until the phone rings: **5 sec, 10 sec, 15 sec, 20 sec, 30 sec, 45 sec, 1 min, 2 min or 5 min**.

### 8. Scheduled Call Countdown
- After you tap **Schedule call**, a card shows a live countdown, e.g. "Mom calls in 8 s", and a **Cancel** button.
- A low-priority notification ("Call scheduled") also offers **Cancel**.
- You can lock the phone or switch apps while waiting.

### 9. Realistic Incoming Call
- **Over the lock screen:** opens a full-screen incoming call screen and turns the screen on, even when the phone is locked.
- **Ringtone:** plays your phone's real ringtone and follows your ringer mode: normal rings, vibrate only vibrates, silent stays silent.
- **Vibration:** in the normal ring-and-pause pattern.
- **While using another app:** shows a call notification at the top of the screen with **Answer** and **Decline** buttons, as real calls do.
- **Accept and Decline buttons:** large buttons, with the Accept button gently pulsing.
- **Missed after 30 seconds:** if not answered, the call counts as missed.

### 10. In-Call Screen
- **Call timer:** a running timer (mm:ss) replaces "Incoming call".
- **Speaker:** really switches the voice message between the earpiece and the loudspeaker.
- **Mute and Keypad:** only change their look; they have no effect.
- **End:** hangs up.
- **Ongoing notification:** shows the call timer and a Hang up button, so you can return to the call from anywhere.

### 11. In-Call Voice Playback
- About 1 second after you answer, the voice message plays, pauses for 2.5 seconds, and repeats until the call ends.
- It plays through the **earpiece**, like a real call, so you hold the phone to your ear.
- The phone's volume buttons adjust the call volume.

### 12. Proximity Screen-Off
While on a call with the speaker off, the screen turns off when the phone is near your face, so your cheek can't press any buttons. It turns back on when you move the phone away.

### 13. Call Log Integration
Every call is written to the phone's real call history (Phone app → Recents):

| What happened | Logged as | Duration |
|---|---|---|
| Answered | Incoming call | Time you stayed on the call |
| Declined | Declined call | 0 s |
| Not answered within 30 s | Missed call (marked as new/unread) | 0 s |

The phone's dialer decides which name to display: if the number is saved in your contacts, the contact's name appears. Otherwise most phones show just the number.

### 14. Missed Call Notification
A missed call also shows a **"Missed call"** notification with the caller's name and photo. Tapping it opens the phone's call history.

### 15. Permission Setup Card
If anything is missing, a **Finish setup** card at the top of the main screen lists it with an **Allow** button:
- **Notifications:** needed to ring while the app is closed.
- **Call history:** lets calls appear in the phone's call log.
- **Full-screen calls** (Android 14+): shows the call screen over the lock screen.

If Android stops showing the permission popup (after it was denied twice), **Allow** opens the app's Settings page instead.

### 16. Reliable Background Timing
The countdown runs in a foreground service and keeps the processor awake, so the call rings on time even when the screen is off or the app is closed.

### 17. App Look and Branding
- App name **Simulate Call**, with a green phone launcher icon.
- Material You design: matches your wallpaper colours on Android 12+ and supports dark mode.
- Dark-themed call screen with a blue-to-navy background.

---

## Permissions Used

| Permission | Why |
|---|---|
| `WRITE_CALL_LOG` | Add calls to the phone's call history |
| `POST_NOTIFICATIONS` | Ring, plus the incoming, ongoing and missed call notifications |
| `USE_FULL_SCREEN_INTENT` | Show the call screen over the lock screen |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the countdown and call running in the background |
| `WAKE_LOCK` | Keep the countdown on time while the screen is off, and turn the screen off near your face |
| `VIBRATE` | Vibrate while ringing |
| `MODIFY_AUDIO_SETTINGS` | Play the voice through the earpiece and switch to the speaker |

The app also declares a **query for text-to-speech engines**, so it can find Google's speech engine.

---

## Limitations

- **Android only.** iPhones do not allow apps to write to the call history, and cannot ring on a timer in the background without a push-notification server.
- **Not allowed on Google Play.** Google only lets the phone's default dialer use `WRITE_CALL_LOG`. Install the app directly from Android Studio, or share the `.apk` file.
- **Battery restrictions.** Some brands (Xiaomi, Oppo, Vivo and others) stop background apps aggressively. If the call doesn't ring while the app is closed, set the app's battery usage to **Unrestricted**.
- **Call log name.** The dialer chooses which name to show (see Feature 13).
- **Contact photos.** Usually not available without full contacts permission (see Feature 3).
- **Voices.** The voices available depend on what is installed on the phone. Some voices are listed as downloaded but actually fall back to the default voice; on the test phone, this happened with the UK English voices.

---

## Changes So Far

| Change | Details |
|---|---|
| Initial build | All core features: caller details, delays, incoming and in-call screens, call log |
| Build tools updated | Moved to Gradle 9.8.2 and Android Gradle Plugin 9.4.1, which work with the Java 25 bundled with Android Studio |
| **Fix:** photo missing on call screen | Choosing a contact used to delete the chosen photo when Android blocked the contact's picture |
| Photo Display Style | Added the Circle / Full screen choice |
| Renamed | "Fake Call" became **Simulate Call** |
| Voice Message | Added the text message, Female/Male voice, earpiece playback, working Speaker button, proximity screen-off |
| Better male voice | Switched to Google's speech engine, because Samsung's engine only has a female voice |
| Voice choice and pitch | Added Voice 1–4 and the Deeper/Higher pitch slider, after feedback that the female voice should sound more feminine |
| Pitch in hertz | The pitch slider now sets the exact pitch in Hz: female 150–320 Hz, covering Indian women's voices; male 85–180 Hz. Each voice's natural pitch was measured so the result is accurate |

---

## Technical Overview

| | |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose with Material 3 (Compose BOM 2026.09.00) |
| Build | Gradle 9.8.2, Android Gradle Plugin 9.4.1 |
| Compile / target SDK | 37 |
| Minimum SDK | 26 (Android 8.0) |

### Project Structure

| File | Purpose |
|---|---|
| `app/src/main/java/com/fakecall/app/MainActivity.kt` | App entry point and permission handling |
| `.../SetupScreen.kt` | Main screen: photo, contact, number, voice message, ring delay, schedule |
| `.../CallService.kt` | Runs each call: countdown, ringing, answer/end, audio routing, call log |
| `.../CallActivity.kt` | Incoming call and in-call screens |
| `.../CallState.kt` | Shared call data (caller, phase, timings, speaker state) |
| `.../VoicePlayer.kt` | Text-to-speech: voice selection, preview, repeating playback |
| `.../CallLogWriter.kt` | Writes entries to the phone's call history |
| `.../Notifications.kt` | Scheduled, incoming, ongoing and missed call notifications |
| `.../PhotoStore.kt` | Saves and loads the caller photo |
| `.../Contacts.kt` | Contact picker, country list, phone number formatting |
| `.../Ui.kt` | App theme and the round photo component |
| `app/src/main/AndroidManifest.xml` | Permissions, screens and the background service |

### Build and Install

From Android Studio, select the phone and press ▶ **Run**. Or from the terminal:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug        # build and install on the connected phone
./gradlew assembleDebug       # build only: app/build/outputs/apk/debug/app-debug.apk
```

See `README.md` for first-time setup (installing Android Studio and preparing the phone).
