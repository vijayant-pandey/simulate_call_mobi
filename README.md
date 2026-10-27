# Simulate Call (Android)

An Android app that rings your phone with a simulated incoming call after a delay you choose.

- Add a caller photo, pick a contact from your phonebook, or type a name and number with a country code.
- Choose when it rings: 5 sec, 10 sec, 15 sec, 20 sec, 30 sec, 45 sec, 1 min, 2 min or 5 min.
- The phone rings with your real ringtone and vibration, even when it's locked.
- Every call is added to your phone's call history:
  - **Answered**: an incoming call, with the time you stayed on the call
  - **Declined**: a declined call
  - **Not answered within 30 seconds**: a missed call

---

## How to run it on your phone (first time)

### 1. Install Android Studio (one time)
1. Install it with `brew install --cask android-studio`, or download it from https://developer.android.com/studio.
2. Open it and click **Next** through the setup wizard with the default options.
   The Android SDK goes in `~/Library/Android/sdk`.

### 2. Open this project
1. In Android Studio choose **File → Open…** and select this `mobi` folder.
2. Wait for the bar at the bottom to finish ("Gradle sync"). The first time, this takes a few minutes.

### 3. Get your phone ready (one time)
1. On your Android phone, open **Settings → About phone** and tap **Build number** 7 times.
   You'll see "You are now a developer".
2. Go to **Settings → System → Developer options** and turn on **USB debugging**.
3. Connect the phone to your Mac with a USB cable and tap **Allow** on the phone.

### 4. Run
At the top of Android Studio, pick your phone from the device list and press the green ▶ **Run** button.
The app installs and opens on your phone.

### 5. Allow permissions
When the app opens, allow:
- **Notifications**: so it can ring while the app is closed
- **Call logs**: so calls appear in your phone's call history
- **Full-screen calls** (Android 14 and newer): so the call screen shows over the lock screen

---

## Things to know

- **Android only.** iPhones don't let apps write to the call history, so this can't be made for iPhone.
- **Not allowed on the Google Play Store.** Google only lets the phone's own dialer app use call-log access.
  Install the app directly, as described above. To share it with friends, use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**,
  then send them the `.apk` file.
- **Which name shows in the call history** is decided by your Phone app. If the number is saved in your contacts,
  that contact's name is shown. Otherwise most phones show just the number.
- Some phone brands (for example Xiaomi, Oppo, Vivo) aggressively stop background apps. If the call doesn't come
  when the app is closed, go to the app's settings and set battery usage to **Unrestricted**.

---

## Where things are in the code

| File | What it does |
|---|---|
| `app/src/main/java/com/fakecall/app/SetupScreen.kt` | The main screen: photo, contact, number, delay, Schedule button |
| `.../CallService.kt` | Waits for the delay, rings, tracks the call, writes the call log |
| `.../CallActivity.kt` | The incoming call and in-call screens |
| `.../CallLogWriter.kt` | Adds the entry to the phone's call history |
| `.../Notifications.kt` | The incoming, ongoing and missed call notifications |
| `.../Contacts.kt` | Reading a picked contact, the country code list |
| `app/src/main/AndroidManifest.xml` | Permissions and app screens |
