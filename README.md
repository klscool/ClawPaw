# ClawPaw

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)  
**English**: [README_EN.md](README_EN.md)（App UI 支持中文与英文，可在设置中切换语言）

ClawPaw 是运行在 Android 上的**设备节点应用**，可将手机作为可控节点接入 [OpenClaw](https://openclaw.ai) 或通过 HTTP/ADB 与自建服务通信。

---

## 功能概览

- **对接 OpenClaw**
  - 作为 Node：从手机**获取信息**（状态、通知、联系人、步数等）、**远程操控**（点击、滑动、输入等）。
  - **WebChat**：与 OpenClaw 助理对话，和电脑端同一会话，无需额外通道。
- **非 OpenClaw 场景**：通过**本地 HTTP**（默认端口 8765）与 ClawPaw 通信，适合自建 Agent 或其它智能助手。
- **SSH 隧道**：内置 SSH 客户端，支持**本地 SOCKS5 代理**与**端口映射**（正向/反向），不连 OpenClaw 时可单独用作端口转发。
- **扩展**：可自行实现 Skill；后续将提供示例 Skill 与典型场景。

开源项目，欢迎 Issue 与 PR。

---

## 连接方式

与 ClawPaw 建立通道的几种方式（用于连接 OpenClaw 或访问本地 HTTP 服务）。

| 方式 | 说明 |
|------|------|
| **直连** | 手机与主机同网时，在 App 中配置 Gateway 地址（Host:Port），或通过本机 IP:8765 访问 HTTP 服务。 |
| **SSH 隧道** | 手机与主机仅能通过 SSH 互通时使用。支持**本地 SOCKS5 代理**与**端口映射**（正向：本机端口 → 远程主机:端口；反向：远程端口 → 本机 host:port）。配置后 Gateway 地址填 `127.0.0.1`，流量经隧道转发。 |
| **认证** | 连接 OpenClaw Gateway 时支持 Token 或密码；首次使用有引导，设备需在主机端 approve。 |

---

## 控制方式

下发命令的三种途径，同一套能力在三种方式下均可使用（需先建立连接或可访问到设备）。

| 方式 | 说明 |
|------|------|
| **WebSocket（OpenClaw Node）** | 连接 OpenClaw Gateway，由 `openclaw nodes invoke` 或 Control UI 下发命令；与 OpenClaw 对话（WebChat）也走此连接。 |
| **HTTP** | 本机 HTTP 服务（默认 8765），GET 布局/截图、POST 执行命令；**非 OpenClaw 的智能助手或自建 Agent 建议使用此方式**。 |
| **ADB** | 通过广播执行单条命令，用于开发调试与能力验收。 |

---

## 能力概览

| 类别 | 能力说明 |
|------|----------|
| **无障碍 / 界面** | 布局获取、截屏、点击、滑动、长按、输入文字、返回键、按 schema/包名打开应用等（需开启无障碍服务）。 |
| **设备状态** | 定位、WiFi、屏幕亮灭、电量、设备信息、健康与权限状态（不依赖无障碍）。 |
| **硬件** | 震动、前后置拍照、唤醒屏幕。 |
| **通知** | 通知列表、dismiss 操作、本地推送（需通知监听权限）。 |
| **数据** | 联系人、日历、照片、音量、文件读写、传感器步数、运动与蓝牙/WiFi 信息。 |
| **对话** | 与 OpenClaw 多会话对话（chat.send / chat.history），支持多会话切换。 |

完整命令列表与参数见 [使用说明](使用说明.md)。

---

## 使用与扩展

- 可**自行实现 Skill**，按需扩展设备侧能力。
- 后续将提供**经典 Skill** 与**典型场景**使用示例。

---

## 构建与运行

- **环境**：JDK 11+，[Android Studio](https://developer.android.com/studio)，真机或模拟器（Android 10+，minSdk 29）。
- **步骤**：克隆仓库后，用 Android Studio 打开项目根目录，同步 Gradle，连接设备后 Run。

```bash
git clone https://github.com/<你的用户名>/ClawPaw.git
cd ClawPaw
# 使用 Android Studio 打开 ClawPaw 目录并运行
```

---

## 文档

- [使用说明](使用说明.md)：命令列表、Node/HTTP/ADB 调用示例、调试与配置说明。

---

## 后续计划（优化清单）

- [x] **英语适配**：应用内界面与文案的英文支持；设置内可切换语言。
- [ ] **引导与场景**：首屏价值说明与三步引导、场景卡片，便于非技术用户理解与上手。
- [ ] **术语与帮助**：界面用语更白话、连接状态与设置页简短说明、关于/帮助页与「别人帮我装的」说明。

---

## 许可证

[Apache License 2.0](LICENSE)
