#!/usr/bin/env bash
# =============================================================================
#  wx-push · P0 冒烟测试
#
#  用途：不依赖微信服务器、不依赖数据库，验证「微信回调链路」是否正常工作。
#        脚本自己按微信规则算签名，再用 curl 直接打本地接口。
#
#  用法：./scripts/smoke-test.sh [token] [port]
#        默认  token=wxToken123  port=8080
#
#  前置：另开一个终端启动服务
#        WX_TOKEN=wxToken123 ./gradlew bootRun
# =============================================================================
set -uo pipefail

TOKEN="${1:-wxToken123}"
PORT="${2:-8080}"
BASE="http://localhost:${PORT}/wx"
TS="1700000000"
NONCE="abc123"

PASS=0
FAIL=0

green() { printf '\033[32m%s\033[0m\n' "$1"; }
red()   { printf '\033[31m%s\033[0m\n' "$1"; }
title() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# 按微信规则计算签名：token / timestamp / nonce 字典序排序 → 拼接 → SHA-1 小写十六进制
calc_signature() {
    printf '%s\n' "$1" "$2" "$3" | LC_ALL=C sort | tr -d '\n' | shasum -a 1 | cut -d' ' -f1
}

# check <描述> <实际值> <期望包含的子串>
check() {
    if [[ "$2" == *"$3"* ]]; then
        green "  ✓ $1"
        PASS=$((PASS + 1))
    else
        red "  ✗ $1"
        printf '    期望包含: %s\n' "$3"
        printf '    实际输出: %s\n' "$2"
        FAIL=$((FAIL + 1))
    fi
}

SIG="$(calc_signature "$TOKEN" "$TS" "$NONCE")"

title "wx-push P0 冒烟测试"
printf '  目标地址 : %s\n' "$BASE"
printf '  Token    : %s\n' "$TOKEN"
printf '  签名     : %s\n' "$SIG"

# ---------------------------------------------------------------- 连通性检查
title "[0] 服务连通性"
if ! curl -s -m 5 -o /dev/null "$BASE?signature=x&timestamp=1&nonce=1"; then
    red "  ✗ 无法连接 $BASE"
    echo "    请先另开终端执行：WX_TOKEN=${TOKEN} ./gradlew bootRun"
    exit 1
fi
green "  ✓ 服务可达"

# ------------------------------------------------------- [1] URL 验证（成功）
title "[1] GET /wx · URL 验证（签名正确）"
RESP="$(curl -s -m 5 "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}&echostr=echo_test_123")"
check "原样返回 echostr（不能加引号/不能包 JSON）" "$RESP" "echo_test_123"

# ------------------------------------------------------- [2] URL 验证（失败）
title "[2] GET /wx · URL 验证（签名错误 → 应 403）"
CODE="$(curl -s -m 5 -o /dev/null -w '%{http_code}' "$BASE?signature=wrongsig&timestamp=${TS}&nonce=${NONCE}&echostr=x")"
check "HTTP 状态码为 403" "$CODE" "403"

# --------------------------------------------------------------- [3] 文本消息
title "[3] POST /wx · 文本消息（核心：验证收发双方互换）"
REQ_XML='<xml><ToUserName><![CDATA[gh_abc123]]></ToUserName><FromUserName><![CDATA[oUserOpenid]]></FromUserName><CreateTime>1348831860</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[你好]]></Content><MsgId>1234567890123456</MsgId></xml>'
RESP="$(curl -s -m 5 -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" -H "Content-Type: text/xml" -d "$REQ_XML")"
check "回复的 ToUserName 是用户 openid（原发送者）" "$RESP" "<ToUserName><![CDATA[oUserOpenid]]></ToUserName>"
check "回复的 FromUserName 是公众号 ID（原接收者）" "$RESP" "<FromUserName><![CDATA[gh_abc123]]></FromUserName>"
check "回复内容为回声" "$RESP" "<Content><![CDATA[你说的是：你好]]></Content>"
check "报文以 <xml> 包裹" "$RESP" "<xml>"
check "回复 MsgType 为 text" "$RESP" "<MsgType><![CDATA[text]]></MsgType>"

# --------------------------------------------------------------- [4] 关注事件
title "[4] POST /wx · 关注事件"
REQ_XML='<xml><ToUserName><![CDATA[gh_abc123]]></ToUserName><FromUserName><![CDATA[oUserOpenid]]></FromUserName><CreateTime>1348831860</CreateTime><MsgType><![CDATA[event]]></MsgType><Event><![CDATA[subscribe]]></Event></xml>'
RESP="$(curl -s -m 5 -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" -H "Content-Type: text/xml" -d "$REQ_XML")"
check "关注后回复欢迎语" "$RESP" "欢迎关注"

# --------------------------------------------------------- [5] 取关事件不回复
title "[5] POST /wx · 取关事件（应不回复：空体）"
REQ_XML='<xml><ToUserName><![CDATA[gh_abc123]]></ToUserName><FromUserName><![CDATA[oUserOpenid]]></FromUserName><CreateTime>1348831860</CreateTime><MsgType><![CDATA[event]]></MsgType><Event><![CDATA[unsubscribe]]></Event></xml>'
RESP="$(curl -s -m 5 -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" -H "Content-Type: text/xml" -d "$REQ_XML")"
if [[ -z "$RESP" ]]; then
    green "  ✓ 返回空体（= 不回复）"
    PASS=$((PASS + 1))
else
    red "  ✗ 期望空体，实际: $RESP"
    FAIL=$((FAIL + 1))
fi

# ------------------------------------------------------- [6] 未签名请求拒绝
title "[6] POST /wx · 签名错误（应 403）"
CODE="$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE?signature=bad&timestamp=${TS}&nonce=${NONCE}" \
    -H "Content-Type: text/xml" -d '<xml><MsgType><![CDATA[text]]></MsgType></xml>')"
check "HTTP 状态码为 403" "$CODE" "403"

# --------------------------------------------------------- [7] 畸形报文容错
title "[7] POST /wx · 畸形报文（应 200 + 空体，不触发微信重试）"
RESP="$(curl -s -m 5 -w '|%{http_code}' -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" \
    -H "Content-Type: text/xml" -d 'this is not xml')"
check "HTTP 状态码为 200" "$RESP" "|200"

# ------------------------------------------------- [8] CLICK 菜单事件（自定义菜单）
# 这组用例的价值：click 菜单的「key」和「回复文案」分处两地时，一旦漏配，
# 现象是「用户点了菜单毫无反应」，而日志里一切正常 —— 属于最难发现的静默错位。
# 所以这里把 MenuCatalog 的 4 个 key 逐个点一遍。
title "[8] POST /wx · CLICK 菜单事件（4 个 key 逐个验证，必须都有文案）"
for CLICK_KEY in MENU_ABOUT MENU_HOWTO MENU_CONTACT MENU_PROGRESS; do
    REQ_XML="<xml><ToUserName><![CDATA[gh_abc123]]></ToUserName><FromUserName><![CDATA[oUserOpenid]]></FromUserName><CreateTime>1348831860</CreateTime><MsgType><![CDATA[event]]></MsgType><Event><![CDATA[CLICK]]></Event><EventKey><![CDATA[${CLICK_KEY}]]></EventKey></xml>"
    RESP="$(curl -s -m 5 -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" \
        -H "Content-Type: text/xml" -d "$REQ_XML")"
    if [[ "$RESP" == *"<MsgType><![CDATA[text]]></MsgType>"* ]]; then
        green "  ✓ ${CLICK_KEY} → 有文本回复"
        PASS=$((PASS + 1))
    else
        red "  ✗ ${CLICK_KEY} → 未收到文本回复（点了会「没反应」）"
        printf '    实际输出: %s\n' "${RESP:-（空体）}"
        FAIL=$((FAIL + 1))
    fi
done

title "[9] POST /wx · CLICK 未知 key（菜单被手工改过 → 应静默不回复）"
REQ_XML='<xml><ToUserName><![CDATA[gh_abc123]]></ToUserName><FromUserName><![CDATA[oUserOpenid]]></FromUserName><CreateTime>1348831860</CreateTime><MsgType><![CDATA[event]]></MsgType><Event><![CDATA[CLICK]]></Event><EventKey><![CDATA[MENU_NOT_EXIST]]></EventKey></xml>'
RESP="$(curl -s -m 5 -X POST "$BASE?signature=${SIG}&timestamp=${TS}&nonce=${NONCE}" -H "Content-Type: text/xml" -d "$REQ_XML")"
if [[ -z "$RESP" ]]; then
    green "  ✓ 返回空体（= 不回复，且不报错）"
    PASS=$((PASS + 1))
else
    red "  ✗ 期望空体，实际: $RESP"
    FAIL=$((FAIL + 1))
fi

# ------------------------------------------------------------------ 汇总
title "汇总"
printf '  通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
if [[ "$FAIL" -eq 0 ]]; then
    green "  全部通过 ✅"
    exit 0
else
    red "  存在失败项 ❌"
    exit 1
fi
