#!/usr/bin/env bash
# 把 -javaagent 写进 HMCL 的 .sh 启动器头部（纯文本头 + zip 尾，只改头部，zip 不动）。
# 用法: ./inject-sh.sh <HMCL-*.sh> <region-agent.jar> [输出文件]
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "用法: $0 <HMCL-*.sh> <region-agent.jar> [输出文件]" >&2
  exit 2
fi

src=$1
agent=$2
out=${3:-${src%.sh}-agent.sh}

[ -f "$src" ] || { echo "找不到 $src" >&2; exit 1; }
[ -f "$agent" ] || { echo "找不到 $agent" >&2; exit 1; }

# 头部用的是绝对路径，避免从别的目录启动时找不到 agent
agent=$(cd "$(dirname "$agent")" && pwd)/$(basename "$agent")

marker='_HMCL_VM_OPTIONS="-XX:MinHeapFreeRatio=5'
line=$(grep -abo -- "$marker" "$src" | head -1 || true)
if [ -z "$line" ]; then
  echo "没找到 _HMCL_VM_OPTIONS 行，可能不是预期的 HMCL 版本" >&2
  exit 1
fi
offset=${line%%:*}
marker_len=${#marker}

head -c "$offset" "$src" > "$out"
printf '%s' "${marker/-XX/-javaagent:$agent -XX}" >> "$out"
tail -c "+$((offset + marker_len + 1))" "$src" >> "$out"
chmod +x "$out"

# .sh 是 "脚本头 + zip" 的 polyglot，unzip 会报 "extra bytes" 并以 1 退出，只看结论行
check=$(unzip -t "$out" 2>&1 || true)
case "$check" in
  *"No errors detected"*) ;;
  *) echo "改完后 zip 校验失败，已保留 $out 供排查" >&2; exit 1 ;;
esac
echo "已生成: $out"
echo "检查: head -c 4k '$out' | grep -n javaagent"
