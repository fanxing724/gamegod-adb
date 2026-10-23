#!/usr/bin/env bash
set -euo pipefail
apk="${1:-/home/fanxing/codex-workspace/game-assistant/releases/starplay-0.2.0.apk}"
if [[ ! -f "$apk" ]]; then echo '找不到 APK，请把路径作为第一个参数传入。' >&2; exit 1; fi
mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#devices[@]} -ne 1 ]]; then echo '请只连接一台已允许 USB 调试的手机。' >&2; exit 1; fi
serial="${devices[0]}"
# Never replace an existing install that might contain an active recovery record.
if adb -s "$serial" shell pm path cn.fanxing.starplay | tr -d '\r' | grep -q '^package:'; then
 echo '手机已有星游调度。请先在 App 中结束会话，再手动执行 adb install -r 更新；此脚本只用于首次安装。' >&2
 exit 1
fi
adb -s "$serial" install "$apk"
adb -s "$serial" shell pm grant cn.fanxing.starplay android.permission.WRITE_SECURE_SETTINGS
adb -s "$serial" shell pm grant cn.fanxing.starplay android.permission.DUMP
adb -s "$serial" shell pm grant cn.fanxing.starplay android.permission.PACKAGE_USAGE_STATS
adb -s "$serial" shell appops set cn.fanxing.starplay GET_USAGE_STATS allow
adb -s "$serial" shell am start -n cn.fanxing.starplay/.MainActivity
printf '%s\n' '已安装并执行授权。请在 App 确认权限状态；确认后可拔线。实际调度效果仍需验证。'
