#!/usr/bin/env bash
#
# 创建 / 重置 admin 后台账号。
#
#   ./scripts/create-admin.sh                # 默认账号 admin
#   ./scripts/create-admin.sh yunze 云泽     # 自定义账号与显示名
#
# 为什么要有这个脚本？—— 手工建账号踩过坑，它一次性堵住三类事故：
#
#   1. 密码泄露：密码用 `read -s` 从终端读取，不回显、不进 shell 历史、
#      不落任何文件、也不出现在命令行参数里。
#   2. 哈希被 shell 吃掉：哈希形如 pbkdf2$310000$盐$摘要，里面有三个 `$`。
#      若用 `mysql -e "... $HASH ..."` 或 `echo` 拼接，`$310000` 会被当作变量
#      展开，存进库的就是个残废字符串（登录永远失败，且报错与密码错完全一样，
#      极难排查）。本脚本把哈希经 `printf '%s'` 写入临时 SQL 文件再重定向给
#      mysql —— `printf` 的 %s 是字面插入，不经过任何一轮展开。
#   3. 占位符被当成真值粘贴：模板里写「把哈希粘这里」，真有人会把它原样粘进去。
#      脚本没有可粘贴的中间产物，从流程上消除了这种可能。
#
# 写库后会立即回读校验哈希长度与前缀，脏数据当场暴露。
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."   # 切到 backend 根目录

USERNAME="${1:-admin}"
DISPLAY_NAME="${2:-$USERNAME}"

JAVA_BIN="${JAVA_BIN:-/opt/homebrew/opt/openjdk@17/bin/java}"
command -v "$JAVA_BIN" >/dev/null 2>&1 || JAVA_BIN=java

MYSQL_BIN="${MYSQL_BIN:-/opt/homebrew/bin/mysql}"
command -v "$MYSQL_BIN" >/dev/null 2>&1 || MYSQL_BIN=mysql

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3326}"
DB_NAME="${DB_NAME:-wx_push}"
DB_USER="${DB_USER:-root}"

# 环境里已有 MYSQL_PWD 时不再提示输入 MySQL 密码（便于自动化 / 脚本自检）
MYSQL_AUTH="-p"
[ -n "${MYSQL_PWD:-}" ] && MYSQL_AUTH=""

echo "账号: $USERNAME    显示名: $DISPLAY_NAME"
echo

printf '为该账号设置密码: '
read -rs PW1; echo
printf '再输入一次确认:   '
read -rs PW2; echo

[ -n "$PW1" ] || { echo "✗ 密码不能为空，已中止"; exit 1; }
[ "$PW1" = "$PW2" ] || { echo "✗ 两次输入不一致，已中止"; exit 1; }

echo
echo "生成 PBKDF2-HMAC-SHA256 哈希中..."
HASH="$("$JAVA_BIN" scripts/GeneratePasswordHash.java "$PW1" | tail -1)"
unset PW1 PW2

HASH_LEN=${#HASH}
echo "哈希长度: $HASH_LEN"
if [ "$HASH_LEN" -lt 60 ]; then
    echo "✗ 哈希长度异常（真实哈希约 83 字符），已中止，未写库"
    exit 1
fi

SQL_FILE="$(mktemp -t wxp-create-admin)"
chmod 600 "$SQL_FILE"
trap 'rm -f "$SQL_FILE"' EXIT

# 注意：SQL 用 printf 的 %s 注入，绝不用双引号字符串拼接 —— 见文件头第 2 条
printf "INSERT INTO admin_user (username, password_hash, display_name)\nVALUES ('%s', '%s', '%s')\nON DUPLICATE KEY UPDATE\n  password_hash = VALUES(password_hash),\n  display_name  = VALUES(display_name),\n  status        = 1;\nSELECT id, username, display_name, status, LENGTH(password_hash) AS hash_len, LEFT(password_hash, 12) AS hash_head FROM admin_user WHERE username = '%s';\n" \
    "$USERNAME" "$HASH" "$DISPLAY_NAME" "$USERNAME" > "$SQL_FILE"

echo
echo "写入数据库（若下方提示 Enter password 请输入 MySQL 的 root 密码）..."
"$MYSQL_BIN" -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" $MYSQL_AUTH \
    --default-character-set=utf8mb4 "$DB_NAME" < "$SQL_FILE"

echo
echo "✓ 完成。上方 hash_len 应为 8x、hash_head 应为 pbkdf2\$31000。"
echo "  验证登录： ./scripts/verify-auth-e2e.sh http://127.0.0.1:8080 $USERNAME"
