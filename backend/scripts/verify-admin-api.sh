#!/usr/bin/env bash
#
# admin 接口验证脚本 —— 启动服务 → 逐个打接口 → 断言 → 关闭服务
#
# 用法：
#   cd backend
#   ./scripts/verify-admin-api.sh          # 默认 8080 端口
#   ./scripts/verify-admin-api.sh 9090     # 指定端口
#
# 前置条件：
#   backend/application-local.yml 里配好数据库密码（该文件已被 .gitignore 排除，不进 Git）
#
# 与 tests/ 里的单元测试的区别：
#   单元测试用假的 Mapper，验证的是「逻辑对不对」；
#   本脚本连真实的 MySQL，验证的是「SQL 写得对不对、数据取得对不对」——
#   比如 GROUP BY 的别名、LIMIT/OFFSET 的翻页、日期区间的左右边界，
#   这些只有打到真实数据库才暴露得出来。

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

PORT="${1:-8080}"
BASE="http://localhost:${PORT}/api"
JAR="build/libs/wx-push-backend-0.0.1-SNAPSHOT.jar"
LOG="/tmp/wx-admin-verify.log"
PY="/usr/bin/python3"

PASS=0
FAIL=0

# 断言：请求 URL，用 python 表达式判断响应是否满足期望（变量 d = 解析后的整个响应体）
assert() {
  local url="$1" expr="$2" desc="$3" out
  out=$(curl -s "$url" 2>/dev/null | $PY -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print('PARSE_ERROR（响应不是合法 JSON）'); raise SystemExit
print('PASS' if ($expr) else 'FAIL -> ' + json.dumps(d, ensure_ascii=False)[:160])
" 2>/dev/null)
  if [ "$out" = "PASS" ]; then
    PASS=$((PASS + 1)); echo "  ✓ $desc"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $desc  $out"
  fi
}

echo "════════ 启动服务 ════════"
pkill -f "wx-push-backend-0.0.1" 2>/dev/null
sleep 1
rm -f "$LOG"

# 用 local profile 启动：数据库密码来自 application-local.yml，不出现在命令行里
nohup env -i HOME="$HOME" PWD="$PWD" PATH="/usr/bin:/bin:/usr/sbin:/sbin" TMPDIR=/tmp \
  SPRING_PROFILES_ACTIVE=local WX_TOKEN=wxToken123 \
  /opt/homebrew/opt/openjdk@17/bin/java -jar "$JAR" --server.port="$PORT" > "$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 45); do nc -z 127.0.0.1 "$PORT" 2>/dev/null && break; sleep 1; done

if ! grep -q "Tomcat started on port" "$LOG"; then
  echo "启动失败，日志末尾："
  tail -15 "$LOG"
  exit 1
fi
grep -oE "Tomcat started on port [0-9]+" "$LOG"

if grep -q "CannotGetJdbcConnectionException" "$LOG"; then
  echo "⚠️  检测到数据库连接失败，请检查 application-local.yml 里的密码"
fi

echo
echo "════════ 消息接口 ════════"
assert "$BASE/messages" "d['code']==0 and d['data']['total']==5" "total 应为 5（数据库实际行数）"
assert "$BASE/messages" "d['data']['page']==1 and d['data']['size']==20" "默认分页 page=1 size=20"
assert "$BASE/messages" "len(d['data']['list'])==5" "默认页返回 5 条"
assert "$BASE/messages" "d['data']['list'][0]['id']==6" "按 id 倒序，首条 id=6"
assert "$BASE/messages" "d['data']['list'][0]['msgId'] is None" "事件消息 msgId 为 null"
assert "$BASE/messages" "d['data']['list'][0]['createdAt']=='2026-09-20 21:55:09'" "时间格式为 yyyy-MM-dd HH:mm:ss"

echo
echo "════════ 分页与筛选 ════════"
assert "$BASE/messages?page=2&size=2" "d['data']['total']==5 and len(d['data']['list'])==2" "size=2 第 2 页返回 2 条"
assert "$BASE/messages?page=2&size=2" "[x['id'] for x in d['data']['list']]==[3,2]" "第 2 页 id 应为 [3,2]"
assert "$BASE/messages?page=0&size=9999" "d['data']['page']==1 and d['data']['size']==100" "非法分页参数被校正为 page=1 size=100"
assert "$BASE/messages?msgType=event&event=subscribe" "d['data']['total']==2 and all(x['event']=='subscribe' for x in d['data']['list'])" "按 event=subscribe 筛选出 2 条"
assert "$BASE/messages?msgType=text" "d['data']['total']==1 and d['data']['list'][0]['content']=='你好'" "按 msgType=text 筛选出 1 条且内容正确"
assert "$BASE/messages?keyword=%E4%BD%A0%E5%A5%BD" "d['data']['total']==1" "关键词搜索「你好」命中 1 条"
assert "$BASE/messages?startDate=2026-09-20&endDate=2026-09-20" "d['data']['total']==5" "日期区间 9-20 当天含全部 5 条（验证左闭右开）"
assert "$BASE/messages?startDate=2026-09-21" "d['data']['total']==0" "区间起点设为明天应为 0 条"

echo
echo "════════ 消息详情 ════════"
assert "$BASE/messages/1" "d['code']==0 and d['data']['id']==1 and d['data']['content']=='你好'" "id=1 返回裸对象（非分页结构）"
assert "$BASE/messages/999" "d['code']==404" "id=999 返回 code=404"
echo -n "  · id=999 的 HTTP 状态码（期望 404）："
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/messages/999"

echo
echo "════════ 统计接口 ════════"
assert "$BASE/stats/overview" "d['data']=={'totalMessages':5,'todayMessages':5,'totalSubscribe':2,'totalUnsubscribe':2,'netGrowth':0,'activeUsers':1}" "概览 6 项指标与库内实际数据吻合"
assert "$BASE/stats/trend?days=7" "len(d['data'])==7" "趋势返回 7 个点"
assert "$BASE/stats/trend?days=7" "d['data'][6]['total']==5 and d['data'][0]['total']==0" "今天 5 条、最早一天补 0"
assert "$BASE/stats/type-distribution" "sorted([(x['name'],x['value']) for x in d['data']])==[('event',4),('text',1)]" "类型分布 event=4 text=1"
assert "$BASE/stats/trend?days=9999" "len(d['data'])==90" "days 超上限截断为 90"

echo
echo "════════════════════════════════"
echo "  通过 $PASS 项，失败 $FAIL 项"
echo "════════════════════════════════"

kill $PID 2>/dev/null
exit $FAIL
