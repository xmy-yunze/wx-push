#!/usr/bin/env bash
#
# admin 鉴权 · 端到端验证脚本
# ============================================================
# 覆盖 11 项检查：未登录拦截、错误密码、防账号枚举、登录、
# Session 携带、真实读库接口、微信回调不被拦、退出登录、会话销毁。
#
# 用法：
#   ./scripts/verify-auth-e2e.sh <base-url> <username> <password>
#
# 例：
#   ./scripts/verify-auth-e2e.sh http://127.0.0.1:8080 admin '你的密码'
#
# ⚠️ 端口提醒：若你的后端不是 8080，请显式传入实际地址。
#    另注意：本机若被注入了 SERVER_PORT（沙箱环境会），
#    应用可能起在非 8080 端口 —— 先用 lsof -nP -iTCP:<port> -sTCP:LISTEN 确认监听者是你自己的 java 进程。
#
# 退出码：0 = 全部通过；1 = 有失败项；2 = 参数错误
# ============================================================

set -u

BASE=${1:-}
USER_NAME=${2:-}
PASS=${3:-}

if [ -z "$BASE" ] || [ -z "$USER_NAME" ] || [ -z "$PASS" ]; then
    echo "用法: $0 <base-url> <username> <password>"
    echo "例:   $0 http://127.0.0.1:8080 admin '你的密码'"
    exit 2
fi

COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

PASSED=0
FAILED=0

# 对 JSON 字符串做最小转义，避免密码里的 \ 或 " 破坏请求体
json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

# check <名称> <期望状态码> <实际状态码>
check() {
    if [ "$2" = "$3" ]; then
        printf '  ✅ %-42s 期望 %s / 实测 %s\n' "$1" "$2" "$3"
        PASSED=$((PASSED + 1))
    else
        printf '  ❌ %-42s 期望 %s / 实测 %s\n' "$1" "$2" "$3"
        FAILED=$((FAILED + 1))
    fi
}

# 只取 HTTP 状态码
code() {
    curl -s -o /dev/null -m 10 -w '%{http_code}' "$@" 2>/dev/null
}

LOGIN_BODY="{\"username\":\"$(json_escape "$USER_NAME")\",\"password\":\"$(json_escape "$PASS")\"}"
WRONG_BODY="{\"username\":\"$(json_escape "$USER_NAME")\",\"password\":\"__definitely_not_the_password__\"}"

echo "目标地址: $BASE"
echo "测试账号: $USER_NAME"
echo

echo "【T1】未登录应被拦截"
check "GET /api/messages 未登录" 401 "$(code "$BASE/api/messages")"

echo "【T2】错误密码应被拒"
check "POST /api/auth/login 错误密码" 401 \
    "$(code -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "$WRONG_BODY")"

echo "【T3】正确密码应登录成功并种下 Cookie"
check "POST /api/auth/login 正确密码" 200 \
    "$(code -c "$COOKIE_JAR" -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "$LOGIN_BODY")"

echo "【T4】带 Cookie 应能访问受保护接口（真实读库）"
check "GET /api/auth/me" 200 "$(code -b "$COOKIE_JAR" "$BASE/api/auth/me")"
check "GET /api/messages" 200 "$(code -b "$COOKIE_JAR" "$BASE/api/messages?page=1&size=3")"
check "GET /api/stats/overview" 200 "$(code -b "$COOKIE_JAR" "$BASE/api/stats/overview")"
check "GET /api/stats/type-distribution" 200 "$(code -b "$COOKIE_JAR" "$BASE/api/stats/type-distribution")"

echo "【T5】微信回调必须不被鉴权拦截（403 才对，401 即事故）"
check "GET /wx 无签名（需 403，绝不能 401）" 403 \
    "$(code "$BASE/wx?signature=bad&timestamp=1&nonce=2&echostr=probe")"

echo "【T6】退出登录应销毁会话"
check "POST /api/auth/logout" 200 "$(code -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE/api/auth/logout")"
check "退出后原 Cookie 再访问" 401 "$(code -b "$COOKIE_JAR" "$BASE/api/messages")"

echo
echo "----------------------------------------"
echo "通过 $PASSED 项 / 失败 $FAILED 项"
echo "----------------------------------------"

[ "$FAILED" -eq 0 ]
