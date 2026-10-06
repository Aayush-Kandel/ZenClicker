# Zen Clicker

A clean, 37 KB auto clicker for Android with zero ads and zero tracking.

Most auto clickers on the Play Store are 30+ MB, full of full-screen video ads, demand sketchy permissions, or lag your phone by buffering hundreds of clicks even after you try to pause. 

I built Zen Clicker to be lightweight, simple, and completely offline. It does one job and gets out of your way.

[**Download ZenClicker.apk**](https://github.com/Aayush-Kandel/ZenClicker/raw/main/ZenClicker.apk) (Direct APK download, ~37 KB)

---

## What makes it different

- **No ads or trackers:** Doesn't even request internet access. 100% offline.
- **Tiny footprint:** Built with pure Android APIs (no heavy libraries or frameworks). The entire app is under 40 KB.
- **Pinned mode:** One of the most annoying things about mobile auto clickers is accidentally moving the target while playing. By default, the controls and target reticle are locked in place. You have to tap the Move button to reposition them.
- **Instant pause:** Clicks don't backlog in a queue. When you hit pause, it stops immediately.
- **Physical emergency stop:** If you have clicks running so fast that your screen isn't registering your taps, just press either volume button to kill the clicking immediately. It also stops automatically whenever your screen turns off.
- **Speed options:** Quick presets from 50ms (20 clicks/second) to 1 second, plus a custom slider.

---

## The Controls

When you start the service, a small 3-button bar and a target dot appear on your screen:

- **Play / Pause (▶ / ⏸):** Starts or stops clicking at the target dot.
- **Lock / Move (🔒 / ✥):** Toggles between pinned and draggable mode. Keep it on 🔒 while clicking so you don't nudge the target by mistake. Switch to ✥ when you want to drag the bar or dot somewhere else.
- **Close (✕):** Hides the floating controls.

---

## How to install and use

1. Download the latest [`ZenClicker.apk`](https://github.com/Aayush-Kandel/ZenClicker/raw/main/ZenClicker.apk) and install it on your device.
2. Open the app and enable Accessibility Service (this is how Android allows simulated taps).
   - **Note for Android 13 and 14:** Android often blocks sideloaded apps from enabling accessibility by default ("Restricted setting"). To fix this:
     1. Open your phone's **Settings -> Apps -> Zen Clicker**.
     2. Tap the **three dots (⋮)** in the top right corner.
     3. Tap **Allow restricted settings**.
     4. Go back to the app and turn on Accessibility.
3. Pick your desired click speed.
4. Tap **Show Floating Controls**.
5. Tap the **Move button (✥)**, drag the target dot over the button you want to click, then tap the **Lock button (🔒)**.
6. Tap **Play (▶)**. To stop, tap Pause or press any volume button.

---

## Troubleshooting & FAQ

**Why does the app require Accessibility permission?**  
Android restricts apps from touching other apps for security reasons. The Accessibility API is the only official, non-root mechanism Android provides to dispatch touch events. Zen Clicker does not have the `INTERNET` permission declared in its manifest, so it cannot send any screen content or keystroke data anywhere.

**Why does the service turn off after some time?**  
Aggressive battery managers on certain phones (Xiaomi/MIUI/HyperOS, Samsung One UI, OnePlus/Oppo) kill background accessibility services when idle. To fix this, open **App Info** for Zen Clicker, set **Battery Usage** to **Unrestricted** (or "No restrictions"), and turn on **Autostart** if your device has it.

**What if clicks are happening so fast I can't tap the pause button?**  
Press either physical **Volume Up** or **Volume Down** button on your device. Zen Clicker intercepts the key press and cuts the click loop instantly.

---

## Building from source

You don't need Android Studio or Gradle installed. The project compiles directly using Android command-line tools (`javac`, `d8`, `aapt2`).

**On Windows:**
```cmd
build.bat
```

**On Linux / macOS:**
```bash
bash build.sh
```

Build takes about 4 seconds and outputs `ZenClicker.apk` in the root folder.

---

## License

[MIT](LICENSE)
