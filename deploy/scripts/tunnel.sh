#!/usr/bin/env bash
# =============================================================================
#  wx-push · 内网穿透隧道（把本机 18080 借公网域名出去）
#
#  原理：隧道客户端由内向外主动建立长连接，所以即使你在热点/家宽后面、
#        没有公网 IP、没开任何入站端口，微信服务器也能访问到你的机器。
#
#  用法：
#         ./deploy/scripts/tunnel.sh              # 默认转发到 127.0.0.1:18080
#         PORT=18080 ./deploy/scripts/tunnel.sh   # 换本地端口
#
#  自动探测顺序：cpolar → cloudflared → frpc
#    · cpolar       国内节点，免费版给 80/443 上的随机域名（推荐）
#    · cloudflared  免注册 Quick Tunnel，一条命令出 trycloudflare.com 域名（备选）
#
#  ⚠️ 隧道起来后务必看它打印的 Forwarding 地址，那就是要填进
#     公众平台后台「服务器配置 → URL」的地址（后面加 /wx）。
# =============================================================================
set -uo pipefail

PORT="${PORT:-18080}"
LOCAL="http://127.0.0.1:${PORT}"

title() { printf '\n\033[1m%s\033[0m\n' "$1"; }
warn()  { printf '\033[33m%s\033[0m\n' "$1"; }

# ------------------------------------------------------------------ 前置检查
title "① 检查本地后端"
if ! curl -s -m 3 -o /dev/null "http://127.0.0.1:${PORT}/wx?signature=x&timestamp=1&nonce=1"; then
    warn "⚠️  127.0.0.1:${PORT} 没有响应。"
    echo "    请先在另一个终端启动后端： WX_TOKEN=你的Token ./deploy/scripts/run-local.sh"
    echo "    （仍然继续启动隧道，但你得先把后端起起来，否则微信那边会验证失败）"
else
    echo "✓ 后端可达：${LOCAL}"
fi

# ------------------------------------------------------------------ 出口 IP
title "② 当前出口公网 IP（微信「IP 白名单」要填的值）"
IPV4="$(curl -4 -s -m 8 ifconfig.me 2>/dev/null || true)"
if [[ -n "${IPV4}" ]]; then
    echo "   IPv4 : ${IPV4}   ← 公众平台「基本配置 → IP 白名单」填这个"
else
    warn "   取不到 IPv4（网络异常？）"
fi
warn "   ⚠️ 这个 IP 会随热点/宽带重连而变，变了就必须回公众平台重加。"

# ------------------------------------------------------------------ 启动隧道
title "③ 启动隧道"

if command -v cpolar >/dev/null 2>&1; then
    echo "使用 cpolar（国内节点）"
    echo "拿到的 Forwarding 地址形如 https://xxxxxxxx.r3.cpolar.cn  → 填 URL 时加 /wx"
    echo
    exec cpolar http "${PORT}"

elif command -v cloudflared >/dev/null 2>&1; then
    echo "使用 cloudflared Quick Tunnel（免注册）"
    echo "拿到的地址形如 https://xxx-xxx-xxx.trycloudflare.com  → 填 URL 时加 /wx"
    echo
    exec cloudflared tunnel --url "${LOCAL}"

elif command -v frpc >/dev/null 2>&1; then
    echo "检测到 frpc —— 需要你自己有公网服务器与 frps 配置，本脚本不代管。"
    echo "参考：frpc -c ./frpc.toml   （local_port=${PORT}）"
    exit 1

else
    warn "✗ 未检测到任何穿透工具。装一个再回来："
    echo
    echo "  【推荐】cpolar（国内节点，免费版给 80/443 上的随机域名）"
    echo "     1) 注册并登录 https://www.cpolar.com/  → 控制台「验证」页复制 authtoken"
    echo "     2) 安装：brew install cpolar    （或按官网 macOS 安装说明）"
    echo "     3) 绑定：cpolar authtoken <你的authtoken>"
    echo "     4) 回到本脚本重跑"
    echo
    echo "  【备选】cloudflared（免注册，但国内访问可能慢/不稳）"
    echo "     brew install cloudflared"
    echo
    exit 1
fi
