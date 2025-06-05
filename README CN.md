# Beam Klipper - 适用于安卓的 Klipper!

Beam Klipper 允许在任何支持 OTG 的 Android 5.0+ 设备上运行 [Klipper](https://github.com/KevinOConnor/klipper) 主机软件（Klippy）。

Telegram 频道: https://t.me/ytkab0bp_channel

Boosty（Patreon 平替）: https://boosty.to/ytkab0bp

K3D Chat 讨论和支持（仅俄语）: https://t.me/K_3_D

# 快速开始

1. 从 [这里](https://github.com/utkabobr/klipper/tree/prebuilt-v0.12.0) 下载并安装 firmware.bin（或从 [此仓库](https://github.com/utkabobr/klipper) 构建您自己的版本以确保版本兼容性）
2. 从 [发布页面](https://github.com/utkabobr/BeamKlipper/releases/latest) 下载 APK
3. 允许所有必需的权限
4. 添加打印机实例（如果您的打印机不兼容已有配置，请点击 generic-***.cfg）
5. 点击启动
6. 访问网络服务器`http://IP:8888/`
7. 在编辑器的 "Devices" 选项卡中配置串行端口（1.0.1+ 会自动配置，如果使用单打印机设置）
8. 您已完成！

# 安装 Beam Klipper 后，我的设备还能正常使用吗？

**是的！** 绝对可以！

Beam Klipper 不会对您的 Android 系统执行任何操作，它作为常规 Android 应用在用户空间运行

# 什么是 IP:port？

它显示在任何实例运行时的主页面上。

Web 服务器 URL 是：`http://IP:8888/`

摄像头 URL 是：
- /webcam/?action=stream => `http://IP:8889/`
- /webcam/?action=snapshot => `http://IP:8889/snapshot`

推荐的摄像头配置是 mjpeg-**stream**（不是自适应 mjpeg）用于 Fluidd 和 UV4L-MJPEG 用于 Mainsail

# 里面有什么？

Beam Klipper 打包了：
- [Klipper](https://github.com/KevinOConnor/klipper)
- [Moonraker](https://github.com/Arksine/moonraker)
- [Fluidd](https://github.com/fluidd-core/fluidd)
- [Mainsail](https://github.com/mainsail-crew/mainsail)
- [Happy Hare](https://github.com/moggieuk/Happy-Hare)
- [Klipper TMC Autotune](https://github.com/andrewmcgr/klipper_tmc_autotune)
- [Moonraker-timelapse](https://github.com/mainsail-crew/moonraker-timelapse)

# Beam 扩展

Beam Klipper 提供额外的扩展来控制一些内置功能。

### 摄像头

在 printer.cfg 中包含 `[beam_camera]`

`SET_CAMERA_FLASHLIGHT ENABLED=true/false` - 切换照明灯光

`SET_CAMERA_FOCUS AUTOFOCUS=true/false FOCUS_DISTANCE=0...?` - 设置摄像头自动对焦状态和对焦距离（如果自动对焦关闭）。`FOCUS_DISTANCE` 以屈光度表示，可能因设备而异

### 蜂鸣器

在您的 printer.cfg 中包含 `[include beam_beeper.cfg]`

使用 `M300` 宏 [如文档中定义](https://marlinfw.org/docs/gcode/M300.html)

# 自启动

您可以通过将所需的打印机设置为自启动、并将应用设置为默认启动器来实现自启动。

您**必须**移除加密设备的锁屏密码（大多数设备默认启用）

# 背景活动通知

一些制造商可能会限制应用的性能或后台进程。
您可以通过将应用设置为默认启动器并允许所有后台任务来绕过此限制

# 安卓电视是否支持？

是的。应该可以正常工作。但请注意，一些廉价电视盒子可能无法在不先禁用系统的情况下将 Beam Klipper 设置为启动器，使用 ADB 或 root 来禁用它

# 使用什么 USB 集线器？

我使用的是绿联 Type-c 集线器（并非赞助，但我正在等待您的邀请，绿联 :D），但任何应该都可以，只要它与您的设备兼容并同时提供充电

# 限制

- Web 服务器不能在默认端口运行，因为 Android/linux 不允许用户空间应用绑定到小于 1024 的端口，而我们希望 80 用于默认 `http://IP`
- 同时运行的实例最多为 4 个，因为 Android 要求开发者为每个服务单独声明进程。我不知道是否有人会使用更多，但没关系 ¯\\_(ツ)\_/¯
- 一些设备在固件重新启动时可能会重置设备路径，您应该在这种情况下使用 VID/PID 命名
- 没有 SSH（您将无法构建固件或运行任何额外的自动运行服务）
- 一些设备不支持 OTG 和充电同时进行，您必须在这种情况下直接焊接到电池引脚（或者使用不同的设备，这取决于您）
- 仅支持 250000 波特率（我不想将此设置转发到 Android USB 驱动程序，但没关系几乎所有配置都使用 250000）

# 编译

- 首先获取所有子模块！（`git clone --recursive`，不要下载项目为存档）
- 在 Android Studio 中导入项目并点击运行

# 贡献

欢迎提交 Pull Request，但我**不会**批准 Kotlin 源代码，因为我不在项目中使用它