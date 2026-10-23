# 下次继续工作的入口

## 用户目标

开发自己的荣耀游戏助手，能切换游戏策略、显示游戏内工具面板，并可选联动系统电池性能模式。用户明确不希望一直连电脑或维持 ADB 会话。

远程仓库：`git@github.com:fanxing724/gamegod-adb.git`。应用名：星游调度，包名：`cn.fanxing.starplay`。

本机源码：`/home/fanxing/repos/starplay/`。请先 `git status`，不要覆盖未提交改动。

## 本机文件与边界

| 位置 | 用途 | 是否入库 |
| --- | --- | --- |
| repos/starplay | 自有源码、说明、测试 | 是 |
| codex-workspace/game-assistant/releases | 已签 APK、源码 ZIP、校验文件 | APK 不入 Git；按需附件交付 |
| codex-workspace/game-assistant/signing | 本地更新签名身份 | 否，禁止上传 |
| codex-workspace/game-assistant/toolchain | 官方 SDK 平台及构建工具 | 否 |
| codex-workspace/game-assistant/apk-review | 用户提供的原游戏管家分析 | 否 |
| codex-workspace/game-assistant/device-validation | 初版真机证据，含个人信息 | 否 |
| codex-workspace/game-assistant/v020-validation | 系统管家分析与新版真机证据 | 否 |

## 继续前先做的只读检查

```sh
adb devices -l
adb shell settings --user current get secure game_power_save_mode
adb shell settings --user current get system SmartModeStatus
adb shell settings --user current get global low_power
adb shell dumpsys activity services cn.fanxing.starplay
adb shell dumpsys package cn.fanxing.starplay
```

不要把设备标识和完整输出贴到公开 Issue。没有设备时可以构建与测试；不能宣称已安装或真机通过。

初始检查发现系统游戏管家被 Hail 禁用，原包版本 18.0.21.319。本次未改变其禁用状态。不要擅自解冻/删除。

2026-10-23 这次测试前的系统值：游戏模式 `2`，电池 `SmartModeStatus=5`，原生性能开关开启，`low_power=0`。这是历史基线，下次必须重读，不要直接当作当前值。

## 授权和构建

```sh
python3 tools/setup_sdk.py
python3 build.py
adb install -r /path/to/starplay-0.2.0.apk
adb shell pm grant cn.fanxing.starplay android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant cn.fanxing.starplay android.permission.DUMP
adb shell pm grant cn.fanxing.starplay android.permission.PACKAGE_USAGE_STATS
adb shell appops set cn.fanxing.starplay GET_USAGE_STATS allow
```

悬浮窗、通知与可选辅助功能可在 App 设置进入系统页面授权。辅助功能只适配荣耀系统管家的性能开关；不要用 ADB 整体覆盖用户现有的辅助服务名单。

DUMP 权限不能绕过 SELinux/厂商限制。若界面显示 CPU 或 FPS 不可用，应保留这个事实，分析原因，不填入占位数值。

## 验证与恢复

- 从游戏库启动才能关联游戏图层；手动会话没有游戏包名，FPS 不采集。
- 退出会话后核对两种策略恢复情况。游戏模式恢复完成不代表电池恢复也完成。
- 电池策略异常有独立恢复卡片，关闭辅助功能后仍保留记录。
- 不在活动会话中卸载或清数据。
- 当前手动结束会话；自动识别游戏退出未实现。
- 单元测试或设置读回不能证明稳帧、降温或节电。重启保留与断线独立使用需单独验收。

## 最终状态与待办

看 `docs/SESSION-2026-10-23.md` 的最新验证表；避免把旧版本的测试结果套用到新版本。远程发布、Actions、真机安装状态分别核实。

用户最后明确要求停止测试并推送仓库。下次不要自动恢复未完成测试，先确认用户新的工作意图。电池最终状态未复核，优先参照行动记录的停止状态边界。
