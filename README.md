🌀 PGravitation – Icon Physics for Android Launchers

---

PGravitation adds real-time gravity and physics simulation to launcher icons on Android.
Tilt your phone — icons move, bounce, and collide like small objects in a physical world.

It’s a proof-of-concept LSPosed module built and tested on Google Pixel devices using **NexusLauncher**.
Other **Launcher3-based** launchers may work as well.

---

### ⚙️ What It Does

PGravitation hooks into the launcher workspace and transforms static icons into physical particles.
Each icon reacts to device tilt using the rotation sensor, gaining velocity, damping, and collisions with other icons and screen boundaries. The result is a fluid, dynamic home screen where icons drift and slide according to real-time motion.

---

### 🧩 How It Works

* Hooks the launcher’s workspace through LSPosed.
* Collects visible icons (`BubbleTextView`) on the active page.
* Reads rotation data from the **TYPE_ROTATION_VECTOR** sensor.
* Computes gravity force from pitch/roll values.
* Applies velocity, damping, and boundary collisions to each icon.
* Resolves inter-icon overlaps through iterative collision solving.
* Smoothly restores all icons when the effect is disabled.

The simulation runs continuously while the module is enabled, updating positions every sensor tick.

---

### 🔧 Configuration

All physics parameters can be tuned via **XposedPrefs**:

| Key                   | Description              |
| --------------------- | ------------------------ |
| `GRAVITY_SCALE`       | Gravity strength         |
| `MAX_VELOCITY`        | Velocity clamp           |
| `LINEAR_DAMPING`      | Friction per frame       |
| `NORMAL_VEL_DAMP`     | Bounce damping           |
| `HITBOX_SCALE`        | Collision radius scaling |
| `SEPARATION_STRENGTH` | Icon separation force    |

These allow you to control how “heavy”, “bouncy”, or “loose” the motion feels.

---

### 📱 Environment

* Android 12+ (tested only Android 16)
* Google Pixel devices (tested only Pixel 7)
* LSPosed
* NexusLauncher (works)
* Launcher3 variants (untested)

---

### 🚧 Status

This project is POC — physics is basic but functional.
Expect minor jank or FPS drops with many icons on screen.

---

### ⚡ Installation

1. Compile and install this application
2. Enable module in LSPosed for your launcher
3. Reboot system or restart your launcher
4. Tilt your phone and watch the icons move

---

### ✨ Inspiration

Inspired by the Gravitation tweak on iOS — reimagined and built from scratch for Android.
No ports, no reuse — just the same idea, adapted to Pixel Launcher through LSPosed.

---

### 🪐 License

**MIT License** — free to use, modify, and build upon.
