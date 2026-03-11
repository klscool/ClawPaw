# ClawPaw

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)  
**中文**: [README.md](README.md)

**Note: The app UI and in-app copy are currently in Chinese only. English localization is planned for a future release.**

ClawPaw is an Android **device node** app that turns your phone into a controllable node for [OpenClaw](https://openclaw.ai) or for local HTTP/ADB-based integrations.

---

## Overview

- **OpenClaw integration**
  - As a **Node**: read device state, notifications, contacts, steps, etc., and run remote actions (tap, swipe, type).
  - **WebChat**: Chat with the OpenClaw assistant from the phone; same session as on desktop, no extra channel.
- **Non-OpenClaw use**: Talk to ClawPaw via **local HTTP** (default port 8765) for custom agents or other assistants.
- **SSH tunnel**: Built-in SSH client with **local SOCKS5 proxy** and **port forwarding** (forward/reverse); usable as a standalone tunnel without OpenClaw.
- **Extensibility**: Implement custom Skills; example Skills and scenarios will be provided later.

Open source; Issues and PRs welcome.

---

## Connection

| Method | Description |
|--------|-------------|
| **Direct** | Same network: set Gateway host:port in the app, or use phone IP:8765 for HTTP. |
| **SSH tunnel** | When only SSH is available: configure tunnel (SOCKS5 or port mappings), then set Gateway to `127.0.0.1`. |
| **Auth** | OpenClaw Gateway: token or password; first use has a guide; device must be approved on the host. |

---

## Control

| Method | Description |
|--------|-------------|
| **WebSocket (OpenClaw Node)** | Connect to OpenClaw Gateway; commands via `openclaw nodes invoke` or Control UI; WebChat over the same connection. |
| **HTTP** | Local server (default 8765): GET layout/screenshot, POST to run commands. **Recommended for non-OpenClaw agents.** |
| **ADB** | Broadcast a single command; for development and testing. |

---

## Capabilities (summary)

- **Accessibility / UI**: layout, screenshot, click, swipe, long-press, input text, back, open app/schema (accessibility service required).
- **Device state**: location, WiFi, screen on/off, battery, device info, health, permissions.
- **Hardware**: vibrate, camera (front/rear), wake screen.
- **Notifications**: list, dismiss, local push (notification listener required).
- **Data**: contacts, calendar, photos, volume, file read, step count, motion, Bluetooth/WiFi.
- **Chat**: Multi-session OpenClaw chat (chat.send / chat.history).

Full command list and parameters: see the Chinese [使用说明](使用说明.md) (User guide). An English doc is planned.

---

## Build and run

- **Requirements**: JDK 11+, [Android Studio](https://developer.android.com/studio), device or emulator (Android 10+, minSdk 29).
- **Steps**: Clone, open project root in Android Studio, sync Gradle, run on device.

```bash
git clone https://github.com/<your-username>/ClawPaw.git
cd ClawPaw
# Open ClawPaw in Android Studio and Run
```

---

## Docs

- [使用说明](使用说明.md) (Chinese): Command list, Node/HTTP/ADB examples, debugging and config.

---

## Future directions

- **English UI and copy** in the app (currently Chinese only).
- **Onboarding and scenarios**: First-screen value statement, step-by-step guide, scenario cards for non-technical users.
- **Wording and help**: Plainer terminology, short descriptions for connection/settings, about/help and “someone set this up for me” guidance.

---

## License

[Apache License 2.0](LICENSE)
