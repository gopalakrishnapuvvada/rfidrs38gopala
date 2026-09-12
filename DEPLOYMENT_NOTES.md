# CipherLab RS38 RFID Scanner - Build & Deployment Guide

This document contains step-by-step instructions for building, installing, and deploying the **UHFSample** RFID application to the **CipherLab RS38** handheld scanner, along with detailed explanations of each command and networking mode.

---

## 1. Prerequisites & Environment

* **Hardware**: CipherLab RS38 Handheld Mobile Computer (with E310 UHF RFID Module) & USB-C Cable.
* **Computer**: Linux (Ubuntu/Debian).
* **SDK Location**: `~/Android/Sdk` (Installed via `setup_sdk.sh`).
* **ADB Binary**: `~/Android/Sdk/platform-tools/adb`.

> [!TIP]
> **Optional (Make `adb` global)**:
> To run `adb` from anywhere without typing `~/Android/Sdk/platform-tools/adb`, run:
> ```bash
> echo 'export PATH="$HOME/Android/Sdk/platform-tools:$PATH"' >> ~/.bashrc
> source ~/.bashrc
> ```

---

## 2. One-Time Handheld Setup (On the RS38)

Before connecting via USB, prepare the RS38 Android settings:

1. **Enable Developer Options**:
   * Go to **Settings** $\rightarrow$ **About phone**.
   * Scroll down to **Build number** and tap it **7 times** until you see *"You are now a developer!"*.
2. **Enable USB Debugging**:
   * Go to **Settings** $\rightarrow$ **System** $\rightarrow$ **Developer options**.
   * Turn ON **USB debugging**.
3. **Set Battery to Unrestricted** (Prevents Wi-Fi sleep in warehouse use):
   * Go to **Settings** $\rightarrow$ **Apps** $\rightarrow$ **UHFSample** $\rightarrow$ **Battery** $\rightarrow$ Select **Unrestricted**.

---

## 3. Step-by-Step Build & Deployment Workflow

### Step 1: Build the APK
Open your terminal in VS Code (or your system terminal):

```bash
# Navigate to the Android project root
cd ~/Desktop/rfidrs38gopala/UHFSample_forAS/UHFSample

# Compile the debug APK
./gradlew assembleDebug
```

* **What this does**: Gradle compiles your Java code (`MainActivity.java`), packages resources, and creates the `.apk` file.
* **Output APK location**:
  ```
  app/build/outputs/apk/debug/app-debug.apk
  ```

---

### Step 2: Verify Device Connection (`adb devices`)
Plug the RS38 into your computer using the USB cable, then run:

```bash
~/Android/Sdk/platform-tools/adb devices
```

#### What this command does:
Queries the Android Debug Bridge (ADB) daemon to detect all connected Android devices over USB.

#### What the output means:
* **`RS38XXXXXX    device`** $\rightarrow$  **Connected and ready!**
* **`RS38XXXXXX    unauthorized`** $\rightarrow$ Look at the RS38 screen. A prompt will ask *"Allow USB debugging?"*. Check *"Always allow from this computer"* and tap **Allow**.
* **Empty list** $\rightarrow$ Check that the USB cable is firmly connected and supports data (not a charge-only cable).

---

### Step 3: Install the App (`adb install -r`)
Install the fresh APK onto the RS38 with:

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### What this command does:
Pushes the APK package to the RS38 and invokes Android's `pm install` (Package Manager).

#### What the flags mean:
* **`-r` (Reinstall / Replace)**: Keeps all existing app settings, permissions, and `SharedPreferences` (like your saved Server URL) while replacing the compiled code with your newest build.

*(Optional: You can launch the app directly from your terminal using)*:
```bash
~/Android/Sdk/platform-tools/adb shell am start -n com.example.uhfsample/.MainActivity
```

---

## 4. Understanding Networking: USB Mode vs. Wi-Fi Mode

The scanner needs to send scanned tags to your FastAPI backend (`/post_fixed_rfid`). You can connect either via **USB** or **Wi-Fi**.

---

### Mode A: Tethered USB Testing (`adb reverse`)

When the RS38 is connected via USB cable:

```bash
~/Android/Sdk/platform-tools/adb reverse tcp:8000 tcp:8000
```

#### Why is `adb reverse` necessary?
* Normally on any phone, `http://127.0.0.1:8000` refers to **the phone itself** (localhost).
* Your FastAPI server is running on **your computer**, not on the phone.
* `adb reverse tcp:8000 tcp:8000` creates a **reverse network tunnel** over the USB cable. It tells the Android kernel:
  > *"Any request sent to `127.0.0.1:8000` on this handheld should be forwarded over USB to port 8000 on the connected computer."*

#### App Configuration for Mode A:
* **Server Endpoint on RS38 screen**: `http://127.0.0.1:8000/post_fixed_rfid`

---

### Mode B: Untethered Wireless Wi-Fi Testing (No USB Cable)

When you disconnect the USB cable and walk around with the RS38:

1. **Find your computer's local Wi-Fi IP address**:
   ```bash
   hostname -I
   ```
   *(Example result: `192.168.1.50`)*.

2. **Ensure your computer firewall allows port 8000**:
   ```bash
   sudo ufw allow 8000
   ```

3. **Configure the App on the RS38 Screen**:
   * In the **FastAPI Server Endpoint** field, change the URL to:
     ```
     http://<YOUR_COMPUTER_IP>:8000/post_fixed_rfid
     ```
     *(e.g., `http://192.168.1.50:8000/post_fixed_rfid`)*.
   * Tap **Save**.

4. **Pull the trigger**: Tags are transmitted wirelessly through your local Wi-Fi router directly to your computer.

---

## 5. Starting the Backend Server

Before scanning, make sure your receiver server is running on your computer.

### Using the Included Test Server:
```bash
python3 ~/Desktop/rfidrs38gopala/mock_fastapi_server.py
```

### Using Production FastAPI / Uvicorn:
Make sure your FastAPI server binds to `0.0.0.0` (all network interfaces), not just `127.0.0.1`:
```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 6. Daily Quick Reference Cheat Sheet

| Task | Command |
| :--- | :--- |
| **Rebuild APK** | `cd ~/Desktop/rfidrs38gopala/UHFSample_forAS/UHFSample && ./gradlew assembleDebug` |
| **Check Device** | `~/Android/Sdk/platform-tools/adb devices` |
| **Install App** | `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| **Enable USB Port Tunnel** | `~/Android/Sdk/platform-tools/adb reverse tcp:8000 tcp:8000` |
| **Get Computer IP** | `hostname -I` |
| **Start Mock Server** | `python3 ~/Desktop/rfidrs38gopala/mock_fastapi_server.py` |
| **View Live Android Logs** | `~/Android/Sdk/platform-tools/adb logcat -s RFID_RS38` |

