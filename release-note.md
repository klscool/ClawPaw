# ClawPaw 更新说明

## v1.0.2（versionName 1.0.2 / versionCode 3）

- **Gateway 连接与文案**：界面文案统一为「Gateway 配对」「Gateway 设置」；连接状态入口改为「Gateway设置」；可多选说明改为「Gateway 配对 和/或 启用 HTTP 服务」。
- **引导页连接方式**：改为「扫码或配对码连接」+「手动配置」两个可折叠卡片；扫码/配对码内容放入第一张卡片内，手动配置保留地址/端口/持久化 Token 并恢复密码；仅卡片顶部标题行可点击展开/收起；键盘弹出时不再上移整页（adjustNothing）。
- **配对与 Token**：扫码/配对码后弹窗选择注册为 Node 或 Operator，分别写入对应 Token；说明 OpenClaw 3.12 起单 token 仅能注册一种角色；持久化 Token 优先、无持久化时才使用 Node/Operator Token；一次 reload 内取 token 与来源，避免误发 bootstrapToken；兼容旧版单 token（legacy gateway_token 视为原 token）。
- **Gateway 设置页**：仅「持久化 Token」可编辑，Node/Operator Token 以只读状态展示；恢复密码输入框（含显示/隐藏）；标题改为「OpenClaw Gateway设置」。
- **其他**：配对码/扫码区域去掉下方说明文案；HTTP 服务在关闭「常驻通知」时可去掉前台通知；SSH 密码改为密码形式输入并支持显示/隐藏；QR 扫码 CameraX 与权限/生命周期修复。

---

## v1.0.1（versionName 1.0.1 / versionCode 2）

- **连接保活**：Gateway 服务常驻前台，不再因关闭常驻通知而断连；SSH 使用 Session 保活 API，减少 3–5 分钟断线；刷新时若已开 HTTP 服务则自动拉起。
- **对话**：顶栏按布局高度自动展开/收起，收起键盘后自动展开；标题 5 次点击无点击效果。
- **Release**：开启代码与资源压缩；ProGuard 补充 JSch/CameraX 等规则；忽略 app/release 构建产物。
