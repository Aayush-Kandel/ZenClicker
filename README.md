<div align="center">

# ⚡ Zen Clicker

**An ultra-minimalist, high-performance auto clicker for Android.**  
Zero ads. Zero telemetry. Zero bloated libraries. Just **~37 KB**.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Aayush-Kandel/ZenClicker)
[![Size](https://img.shields.io/badge/APK%20Size-~37%20KB-4F9DFF?style=flat-square)](https://github.com/Aayush-Kandel/ZenClicker/raw/main/ZenClicker.apk)
[![Root](https://img.shields.io/badge/Root-Not%20Required-brightgreen?style=flat-square)](https://github.com/Aayush-Kandel/ZenClicker)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%2F%20No%20Ads-success?style=flat-square)](https://github.com/Aayush-Kandel/ZenClicker)
[![License](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)](LICENSE)

<br />

<a href="https://github.com/Aayush-Kandel/ZenClicker/raw/main/ZenClicker.apk">
  <img src="https://img.shields.io/badge/⬇️%20DOWNLOAD%20APK-ZenClicker.apk%20(37%20KB)-4F9DFF?style=for-the-badge&logo=android&logoColor=white" height="42" alt="Download APK" />
</a>

<p><em>Click the button above to download the ready-to-install signed APK directly.</em></p>

</div>

---

## 🎛️ Floating Controller Dock

Zen Clicker features a compact, sleek 3-button floating dock and a precision reticle:

```
 ┌──────────────┬──────────────┬──────────────┐
 │    ▶ / ⏸     │    🔒 / ✥    │      ✕       │
 │  Play / Stop │   Move / Pin │    Close     │
 └──────────────┴──────────────┴──────────────┘
```

| Control | Mode | Behavior |
| :---: | :--- | :--- |
| **▶ / ⏸** | **Play / Pause** | Tap to begin clicking. Tap again to stop instantly (**0 ms touch-down stop**). |
| **🔒 / ✥** | **Move / Pin Toggle** | **PINNED (🔒 Default):** Both the pin and controller are completely locked in place. Accidental swipes or gameplay touches will never nudge them.<br>**MOVE (✥):** Unlocks the target pin and controller so you can drag and reposition them anywhere on screen. Tap again to re-lock. |
| **✕** | **Close** | One-tap dismiss to close the floating overlay immediately. |

---

## ✨ Key Highlights

- 🎯 **Pixel-Perfect Target Reticle:** High-contrast blue reticle with center crosshairs and a white pinpoint dot for precision aiming on dark games or bright screens.
- 🔒 **Rock-Solid Pinned Mode:** Prevents accidental dragging while gaming. Once positioned and locked, icons will never shift.
- ⚡ **Backlog-Free Gesture Dispatcher:** Clicks fire sequentially with system completion callbacks. Eliminates gesture queue congestion and runaway clicking at high CPS (up to 25 clicks/sec).
- 🛑 **Physical Volume Key Emergency Stop:** Press any physical Volume button on your phone to instantly cut clicking if your screen is busy.
- 🌙 **Screen-Off Safety Guard:** Automatically ceases all clicking the moment your device screen turns off.
- 🎚️ **Fine-Tuned Speed Controls:** Instant preset chips (**50ms [20 CPS]**, **100ms [10 CPS]**, **200ms [5 CPS]**, **500ms [2 CPS]**, **1.0s [1 CPS]**) plus a smooth slider from 40ms to 2.0s.
- 📳 **Tactile Haptic Feedback:** Gentle vibration ticks on start, stop, move toggle, and dismissal.
- 🛡️ **Android 13 & 14 Ready:** Built-in guidance for sideloaded "Restricted Settings" permissions.

---

## 📱 Quick Setup Guide

1. **Download & Install:** Download [`ZenClicker.apk`](https://github.com/Aayush-Kandel/ZenClicker/raw/main/ZenClicker.apk) and install it on your device.
2. **Grant Accessibility Permission:** Tap the card inside the app.
   > **Android 13 / 14 Note:** If Android shows *"Restricted setting"*, tap the in-app help link $\rightarrow$ **Open App Info** $\rightarrow$ tap the three dots (**⋮**) in the top-right corner $\rightarrow$ tap **"Allow restricted settings"**, then enable Accessibility.
3. **Select Speed:** Choose a preset chip or adjust the slider.
4. **Show Controls:** Tap **"Show Floating Controls"**.
5. **Aim & Lock:**
   - Tap the **Move button (✥)**.
   - Drag the target pin onto your game button.
   - Tap the **Lock button (🔒)** to pin everything in place.
6. **Click:** Tap **Play (▶)** to start tapping! Tap again or press any **Volume button** to stop.

---

## 🛠️ Build from Source

Everything is designed for zero external bloat — no Gradle, Android Studio, or external dependencies required.

### Windows (Command Prompt or PowerShell)
```cmd
build.bat
```

### Linux / macOS / Git Bash
```bash
bash build.sh
```

**Output:** `ZenClicker.apk` (Signed, zip-aligned, and ready to install in ~4 seconds).

---

## 📂 Project Architecture

```
├── ZenClicker.apk                # Production signed release binary (~37 KB)
├── app/src/main/
│   ├── AndroidManifest.xml       # Minimal permissions and exported service
│   ├── java/.../
│   │   ├── MainActivity.java     # Speed presets, CPS indicator, permission helper
│   │   └── AutoClickerService.java # Floating dock, pinned reticle, gesture engine
│   └── res/
│       ├── layout/activity_main.xml
│       ├── values/{colors,styles,strings}.xml
│       ├── drawable/{bg_card,bg_button,bg_chip,ic_app}.xml
│       └── xml/accessibility_service_config.xml
├── build.bat                     # Pure Windows native build script
├── build.sh                      # Unix/Bash build script
└── README.md
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
