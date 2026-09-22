#!/usr/bin/env bash
# =============================================================================
#  wx-push · 本地联调启动脚本
#
#  作用：在 Mac 上按「内网穿透联调」所需的姿势起后端：
#        1. 只监听 127.0.0.1:18080 —— 不对外暴露，公网入口全部交给穿透隧道
#        2. 走 local profile  → 读 backend/application-local.yml（含 DB 密码，不进 Git）
#        3. 注入 WX_TOKEN     → 必须与公众平台后台「服务器配置」的 Token 逐字一致
#
#  用法：
#         WX_TOKEN=你的真实Token ./deploy/scripts/run-local.sh
#         不带 WX_TOKEN 时用默认 wxToken123（只够跑本地自检，联调必须传真实值）
#
#  为什么端口固定 18080：
#         本机会注入 SERVER_PORT，WorkBuddy 客户端会在该端口挂代理，
#         固定 18080 并显式绑定 127.0.0.1 可以绕开它。
#
#  配套：另开一个终端跑 ./deploy/scripts/tunnel.sh 起公网隧道。
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND="${ROOT}/backend"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export SPRING_PROFILES_ACTIVE=local
export WX_TOKEN="${WX_TOKEN:-wxToken123}"

if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "✗ 找不到 JDK 17：${JAVA_HOME}/bin/java" >&2
    echo "  请显式指定，例如：JAVA_HOME=\$(/usr/libexec/java_home -v 17) $0" >&2
    exit 1
fi

if [[ ! -f "${BACKEND}/application-local.yml" ]]; then
    echo "✗ 缺少 ${BACKEND}/application-local.yml（本地数据库等配置，不进 Git）" >&2
    exit 1
fi

if [[ "${WX_TOKEN}" == "wxToken123" ]]; then
    echo "⚠️  正在使用默认 Token（wxToken123）。"
    echo "    本地自检够用；真机联调必须与公众平台后台的 Token 一致："
    echo "       WX_TOKEN=你的真实Token $0"
    echo
fi

echo "→ profile : ${SPRING_PROFILES_ACTIVE}（DB 配置来自 backend/application-local.yml）"
echo "→ 监听    : 127.0.0.1:18080（不对外，公网入口交给隧道）"
echo "→ Token   : ${WX_TOKEN:0:3}***（不回显全文）"
echo

cd "${BACKEND}"
# exec：让 gradle 进程取代当前 shell，Ctrl+C 能干净地传到 JVM
exec ./gradlew bootRun --console=plain --args='--server.port=18080 --server.address=127.0.0.1'
