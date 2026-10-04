# ⚡ Zen Clicker

An ultra-minimalist, high-performance auto clicker for Android (Android 8.0+ / API 26–34).
Zero bloated dependencies, zero telemetry, dark aesthetic, and a tiny **~37 KB** footprint.

---

## ✨ Features

- **Draggable Precision Target Pin:** Place the reticle anywhere on screen with sub-pixel tap precision.
- **Dedicated Move / Lock Mode:** 
  - **PINNED 🔒 (Default):** Target pin and controls are completely locked in place! Swipes or taps near them will never accidentally move them during games.
  - **MOVE ✥:** One tap unlocks both the pin and the controller so you can reposition them anywhere on screen. Tap again to lock!
- **Instant Play/Pause:** Tap the prominent button to start or pause tapping with 0ms delay.
- **Physical Volume Key Emergency Stop:** Press any physical Volume button on your phone to instantly cut clicking.
- **Speed Presets & Fine Slider:** Quick chips (`50ms [20 CPS]`, `100ms [10 CPS]`, `200ms [5 CPS]`, `500ms [2 CPS]`, `1.0s [1 CPS]`) plus a fine-grained slider from 40ms to 2.0s.
- **Safety Emergency Stops & Failsafes:**
  - **Screen-Off Auto Stop:** Automatically ceases clicking if your phone screen turns off.
  - **Ongoing Notification:** Control, Pause, or Hide overlays directly from the notification shade.
- **Tactile Haptic Feedback:** Subtle vibration ticks on start, stop, move toggle, and overlay dismissal.
- **Android 13 & 14 Ready:** Includes built-in bypass instructions for "Restricted Settings".

---

## 📱 How to Use

1. **Install** `ZenClicker.apk` onto your Android phone.
2. **Grant Accessibility Permission:** Tap the card inside the app.
   - *Android 13/14 Note:* If Android displays *"Restricted setting"*, tap the help link $\rightarrow$ **Open App Info** $\rightarrow$ tap the three dots (**⋮**) in the top-right corner $\rightarrow$ tap **"Allow restricted settings"**, then turn on the service.
3. **Set Speed:** Select your desired interval or tap any preset chip.
4. **Tap "Show Floating Controls"** — the app minimizes.
5. **Positioning (Move Mode):**
   - Tap the **Move button (✥)** on the floating pill.
   - Drag the target pin to your game button or click spot.
   - Drag the floating bar to a comfortable area.
   - Tap the **Lock button (🔒)** — icons are now **pinned and immovable**!
6. **Start / Stop:**
   - Tap **Play (⏯)** to begin clicking.
   - Tap **Pause (⏯)** or press any physical **Volume button** to instantly stop.
7. **Close:** Tap the **✕** button on the bar to dismiss the controls.

---

## 🛠️ Build from Source

No Gradle or Android Studio required. Everything is pre-bundled in `tools/` with JDK 17 and Android SDK 34.

### Option 1: Windows CMD / PowerShell (Native)
```cmd
build.bat
```

### Option 2: Git Bash / Cygwin / WSL
```bash
bash build.sh
```

**Output:** `ZenClicker.apk` (Signed, zip-aligned, and ready to install).

---

## 📂 Project Structure

```
├── app/src/main/
│   ├── AndroidManifest.xml       # Permissions, exported service, and metadata
│   ├── java/.../
│   │   ├── MainActivity.java     # Speed presets, CPS indicator, permission helper
│   │   └── AutoClickerService.java # Floating dock, pinned reticle, gesture dispatcher
│   └── res/
│       ├── layout/activity_main.xml
│       ├── values/{colors,styles,strings}.xml
│       ├── drawable/{bg_card,bg_button,bg_chip,ic_app}.xml
│       └── xml/accessibility_service_config.xml
├── tools/                        # Bundled JDK 17 + Android SDK 34
├── build.bat                     # Windows native build script
├── build.sh                      # Shell build script
└── ZenClicker.apk                # Production-ready signed binary (~37 KB)
```
