#!/bin/bash
# [v133] APK 装机前三查——防 AGP 9 坏包与依赖版本劈叉（历史实例见 AGENTS.md 坑区第 5 条）
#
# 用法: scripts/verify_apk.sh [apk路径]   # 缺省取 app/build/outputs/apk/debug/ 最新一个
#
# 三查:
#   1. 条目数基线（历史残包只剩 3 个文件/1 个小 dex；正常 460+）
#   2. 跨 dex 重复类定义（dex 合并错乱会同一类打进两个 dex，运行时加载顺序不定）
#   3. foundation-layout FlowRow 签名金丝雀（BOM 2024.09.00 期签名是
#      `(Modifier,Horizontal,Vertical,int,int,FlowRowOverflow,...)`；若出现
#      `Alignment$Vertical` 说明 1.9.x 混进打包——v133 崩溃即此因，升 BOM 时需同步改此断言）
set -euo pipefail
export LC_ALL=C

APK="${1:-$(ls -t app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then echo "[FAIL] 找不到 APK: $APK"; exit 1; fi
echo "目标: $APK"

DEXDUMP=""
for d in ~/Library/Android/sdk/build-tools/*/dexdump; do DEXDUMP="$d"; done
[ -x "$DEXDUMP" ] || { echo "[FAIL] 找不到 dexdump（需 Android SDK build-tools）"; exit 1; }

TMP=$(mktemp -d /tmp/verify_apk.XXXXXX)
trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" 'classes*.dex' -d "$TMP"

# 查 1: 条目数
ENTRIES=$(unzip -l "$APK" | tail -1 | awk '{print $2}')
if [ "$ENTRIES" -lt 400 ]; then echo "[FAIL] 条目数 $ENTRIES < 400（残包）"; exit 1; fi
echo "[OK] 条目数 $ENTRIES"

# 查 2: 跨 dex 重复类
DUPES=""
for d in "$TMP"/classes*.dex; do "$DEXDUMP" -l plain "$d" 2>/dev/null; done \
  | awk '/^ *Class descriptor *:/{gsub(/\x27/,"",$NF); print $NF}' | sort | uniq -d > "$TMP/dupes.txt"
if [ -s "$TMP/dupes.txt" ]; then
  echo "[FAIL] 跨 dex 重复类定义:"; head -5 "$TMP/dupes.txt"; exit 1
fi
echo "[OK] 无跨 dex 重复类"

# 查 3: FlowRow 签名金丝雀（依赖 BOM 2024.09.00 期签名）
SIGFILE="$TMP/sig.txt"
for d in "$TMP"/classes*.dex; do
  "$DEXDUMP" -l plain "$d" 2>/dev/null | awk -v out="$SIGFILE" '
    /Class descriptor .*FlowLayoutKt/ {flag=1; next}
    /Class descriptor/ {flag=0}
    flag && /name *: .FlowRow./ {getline; print >> out}
  '
done
if [ ! -s "$SIGFILE" ]; then echo "[FAIL] 未找到 FlowLayoutKt（compose 未打包？）"; exit 1; fi
if awk '/Alignment\$Vertical/' "$SIGFILE" | grep -q .; then
  echo "[FAIL] FlowRow 签名含 Alignment\$Vertical → 1.9.x foundation-layout 混入打包（编译/运行劈叉）"
  cat "$SIGFILE"; exit 1
fi
echo "[OK] FlowRow 签名与 BOM 2024.09.00（foundation-layout 1.7.x）一致"
echo "全部通过 ✓"
