# 星游调度

荣耀游戏模式助手，由番星制作。目标设备为荣耀 X50 / MagicOS 9（Android 15）。包名：`cn.fanxing.starplay`。

目标是首次授权后在手机独立运行，不需要常驻 ADB、Shizuku 或连接电脑。应用独立安装，不能冒充荣耀签名覆盖系统管家。

> 当前版本为 **0.2.0 开发预览版**。用户于 2026-10-23 要求停止继续测试并推送仓库；新增监测和电池联动有尚未完成的真机验证，详见[行动记录](docs/SESSION-2026-10-23.md)。不要把候选设置写入成功当作游戏性能改善。

## 功能与验证状态

| 功能 | 当前状态 |
| --- | --- |
| 游戏库、逐游戏策略、启动游戏 | 初版真机通过 |
| 游戏/均衡模式设置读写 | 初版真机通过；实际调度收益未证实 |
| 原值持久保存、异常退出恢复、外部改值冲突保护 | 35 项 JVM 断言；初版真机通过 |
| 可拖动悬浮入口、通知恢复 | 初版真机通过 |
| 游戏内展开工具面板 | 新版已显示；不再强制跳回主界面 |
| 系统可用内存、网络状态/全设备网速、电量、电池温度、CPU 频率 | 新版真机已显示 |
| 设备 CPU 占用 | 已接入 DUMP + 使用情况权限路径，补充授权后未完成复核 |
| 游戏 FPS | 已实现游戏图层呈现时间戳解析；真实游戏有效读数未完成验证，失败显示不可用 |
| 清理运行内存 | 打开荣耀系统管家，由系统执行；不是第三方一键杀进程 |
| 游戏模式联动系统电池性能模式 | 已实现可选辅助功能适配；自动开启/恢复闭环尚未验证通过 |
| 拔线独立操作、重启后权限保留 | 设计目标，尚未单独完成验收 |

## 安装与一次性授权

先安装自己构建的 APK，再执行：

```sh
adb shell pm grant cn.fanxing.starplay android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant cn.fanxing.starplay android.permission.DUMP
adb shell pm grant cn.fanxing.starplay android.permission.PACKAGE_USAGE_STATS
adb shell appops set cn.fanxing.starplay GET_USAGE_STATS allow
```

App 设置页可进入通知、悬浮窗、使用情况访问权和可选辅助功能授权页面。ADB 只是初始授权手段，App 不使用 adb/su 常驻代理。

DUMP 和使用情况权限用于系统诊断采集，不会自动消除 SELinux、厂商服务权限或图层数据限制。数据不可用时界面会如实显示。

初次安装可用 `./install-adb.sh /path/to/starplay-0.2.0.apk`。脚本拒绝覆盖已有安装，以免未处理会话记录被忽略；升级时应先结束会话，保留同一签名，使用 `adb install -r`。

## 使用

1. 添加游戏，选择默认或单个游戏策略。
2. 均衡/游戏模式创建手动结束的会话；跟随系统只启动游戏，不创建监测会话。
3. 在设置开启悬浮入口。游戏中点击入口展开面板，可查看指标、切换策略、打开系统清理和结束会话。
4. 结束会话时恢复进入前设置。若检测到外部改值，用户自行选择恢复或保留当前值。
5. 可选开启“游戏模式同时启用系统性能模式”，并授权限定范围的辅助功能。切换会短暂打开系统电池页面，不是后台直接写一串性能参数。
6. 电池恢复失败时保留独立记录和恢复入口。卸载、清数据前先处理所有恢复记录。

## 构建

原生 Java 界面，无第三方运行时库；无网络访问权限。需要 Python 3.9+、JDK 17+ 和官方 Android SDK Platform 35 / Build Tools 35.0.0。无需 Android Studio 或 Gradle。

```sh
python3 tools/setup_sdk.py
python3 build.py
```

默认工作输出在 `/home/fanxing/codex-workspace/game-assistant/`。其他电脑可指定：

```sh
export STARPLAY_WORK="$PWD/.work"
python3 tools/setup_sdk.py
python3 build.py
```

`.work/` 已加入忽略规则。也可用 `STARPLAY_SDK` 指向已有 SDK。构建运行 35 项事务断言、17 项监测解析断言，再执行 aapt2 / javac / D8 / zipalign / apksigner。

本机更新签名位于工作目录 `signing/starplay-local.p12`，不在仓库内。其他电脑首次构建会生成新的本地签名，不能覆盖这台手机上不同签名的安装。不要卸载来绕过签名冲突，否则会丢失恢复记录；需在安全的私有渠道保管原签名。

GitHub Actions 仅在手动触发时运行 JVM 测试（本次推送不自动测试），不生成可覆盖用户安装的随机签名版本，不代表真机或性能验证。

## 继续维护

- [下次工作入口](docs/RESUME.md)
- [完整行动与证据记录](docs/SESSION-2026-10-23.md)
- [架构、权限和状态恢复](docs/ARCHITECTURE.md)
- [仓库维护约定](AGENTS.md)

原厂 APK、手机截图/完整诊断输出、设备序列号及签名私钥均不提交仓库。
