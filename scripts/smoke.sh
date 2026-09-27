#!/usr/bin/env bash
# 端到端冒烟验证：覆盖 预约→批次→称重→复核→入柜→异常→交接→追溯→兑换 全链路
# 用法: scripts/smoke.sh [base_url]   （默认 http://localhost:8080）
set -euo pipefail

BASE="${1:-http://localhost:8080}"
SUF="$(date +%s%N | tail -c 7)"   # 唯一后缀，避免封袋号/用户名冲突
PASS=0; FAIL=0

say()  { echo -e "\n\033[1;36m== $* ==\033[0m"; }
ok()   { PASS=$((PASS+1)); echo "  ✅ $*"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ $*"; }
check(){ # check <描述> <实际值> <期望值>
  if [ "$2" == "$3" ]; then ok "$1 ($2)"; else bad "$1 期望[$3] 实际[$2]"; fi
}

req() { # req <method> <path> <token> <json-body>
  local m="$1" p="$2" t="${3:-}" b="${4:-}"
  local args=(-s -X "$m" "$BASE$p" -H "Content-Type: application/json")
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$b" ] && args+=(-d "$b")
  curl "${args[@]}"
}
token_of() { req POST /api/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}" | jq -r '.data.token'; }

say "0. 健康检查与登录"
curl -sf "$BASE/api/public/community-points" >/dev/null && ok "服务可达" || { echo "服务不可达: $BASE"; exit 1; }
ADMIN=$(token_of admin admin123)
COLLECTOR=$(token_of collector01 collector123)
RESIDENT=$(token_of resident01 resident123)
[ "$ADMIN" != "null" ] && [ -n "$ADMIN" ] && ok "admin 登录" || { echo "登录失败"; exit 1; }
[ "$COLLECTOR" != "null" ] && ok "collector01 登录"
[ "$RESIDENT" != "null" ] && ok "resident01 登录"

say "1. 居民预约（普通药 + 含处方药）"
A1=$(req POST /api/resident/appointments "$RESIDENT" \
  "{\"drugType\":\"感冒灵\",\"quantity\":2,\"communityPointId\":1,\"hasPrescription\":false,\"isLiquid\":false,\"isInjection\":false,\"hasPersonalInfo\":false}" \
  | jq -r '.data.appointmentNo')
A2=$(req POST /api/resident/appointments "$RESIDENT" \
  "{\"drugType\":\"安定片\",\"quantity\":1,\"communityPointId\":1,\"hasPrescription\":true,\"isLiquid\":false,\"isInjection\":false,\"hasPersonalInfo\":true,\"note\":\"药盒有处方单\"}" \
  | jq -r '.data.appointmentNo')
[[ "$A1" == APT* ]] && ok "预约1 $A1" || bad "预约1失败"
[[ "$A2" == APT* ]] && ok "预约2 $A2（含处方药）" || bad "预约2失败"

say "2. 回收员开批次 + 扫码登记"
BATCH=$(req POST /api/collector/batches "$COLLECTOR" '{"communityPointId":1}' | jq -r '.data.id')
BATCH_NO=$(req GET "/api/collector/batches/$BATCH" "$COLLECTOR" | jq -r '.data.batchNo')
ok "批次 $BATCH_NO (id=$BATCH)"
ST=$(req POST "/api/collector/batches/$BATCH/checkin" "$COLLECTOR" "{\"appointmentNo\":\"$A1\"}" | jq -r '.data.status')
check "预约1登记" "$ST" "CHECKED_IN"

say "3. 分类称重封袋（低风险即时积分 / 高风险进复核）"
W1=$(req POST "/api/collector/batches/$BATCH/weigh" "$COLLECTOR" \
  "{\"appointmentNo\":\"$A1\",\"category\":\"TABLET\",\"weightGrams\":500,\"sealBagNo\":\"BAG-$SUF-1\",\"photoUrls\":[\"pkg1.jpg\",\"seal1.jpg\"]}")
check "普通片剂积分" "$(echo "$W1" | jq -r '.data.pointsCredited')" "50"
W2=$(req POST "/api/collector/batches/$BATCH/weigh" "$COLLECTOR" \
  "{\"appointmentNo\":\"$A2\",\"category\":\"PSYCHOTROPIC\",\"weightGrams\":100,\"sealBagNo\":\"BAG-$SUF-2\",\"photoUrls\":[\"pkg2.jpg\"]}")
check "精神类进复核" "$(echo "$W2" | jq -r '.data.highRisk')" "true"
TICKET=$(echo "$W2" | jq -r '.data.reviewTicketId')
BAL=$(req GET /api/resident/points "$RESIDENT" | jq -r '.data.balance')
check "复核前余额" "$BAL" "50"

say "4. 管理员复核通过 → 发放积分"
req POST "/api/admin/reviews/$TICKET/decision" "$ADMIN" '{"approved":true,"comment":"核验无误"}' >/dev/null
BAL=$(req GET /api/resident/points "$RESIDENT" | jq -r '.data.balance')
check "复核后余额(50+50)" "$BAL" "100"

say "5. 入暂存柜"
S1=$(req POST "/api/collector/batches/$BATCH/store" "$COLLECTOR" "{\"sealBagNo\":\"BAG-$SUF-1\",\"cabinetId\":1}")
check "袋1入柜" "$(echo "$S1" | jq -r '.data.status')" "STORED"
req POST "/api/collector/batches/$BATCH/store" "$COLLECTOR" "{\"sealBagNo\":\"BAG-$SUF-2\",\"cabinetId\":1}" >/dev/null
ok "袋2入柜"

say "6. 称重争议异常 → 未解决时阻止交接 → 复称调积分"
BAG1=$(req GET "/api/collector/batches/$BATCH/bags" "$COLLECTOR" | jq -r ".data[] | select(.sealBagNo==\"BAG-$SUF-1\") | .id")
EX=$(req POST "/api/collector/batches/$BATCH/exceptions" "$COLLECTOR" \
  "{\"type\":\"WEIGHT_DISPUTE\",\"description\":\"居民对称重有异议\",\"bagId\":$BAG1}" | jq -r '.data.id')
[ "$EX" != "null" ] && ok "异常已记录 #$EX"
HTMP=$(req POST /api/admin/handovers "$ADMIN" \
  "{\"communityPointId\":1,\"envCompany\":\"绿源环保\",\"batchIds\":[$BATCH]}")
check "未解决异常阻止交接" "$(echo "$HTMP" | jq -r '.code')" "40000"
req POST "/api/admin/exceptions/$EX/resolve" "$ADMIN" \
  '{"resolution":"复称确认为520g","finalWeightGrams":520}' >/dev/null
BAL=$(req GET /api/resident/points "$RESIDENT" | jq -r '.data.balance')
check "复称后余额(100+2)" "$BAL" "102"

say "7. 环保交接（含精神类 → 双人交接）"
H=$(req POST /api/admin/handovers "$ADMIN" \
  "{\"communityPointId\":1,\"envCompany\":\"绿源环保\",\"batchIds\":[$BATCH]}")
HID=$(echo "$H" | jq -r '.data.id')
check "双人交接标记" "$(echo "$H" | jq -r '.data.dualRequired')" "true"
check "交接总重(520+100)" "$(echo "$H" | jq -r '.data.totalWeightGrams')" "620"
SINGLE=$(req POST "/api/admin/handovers/$HID/complete" "$ADMIN" '{"receiverName":"赵环保"}')
check "单人签收被拒" "$(echo "$SINGLE" | jq -r '.code')" "40000"
req POST "/api/admin/handovers/$HID/complete" "$ADMIN" \
  '{"receiverName":"赵环保","secondReceiverName":"钱监督","photoUrls":["handover.jpg"]}' >/dev/null
ok "双人签收完成"
RPT=$(req GET "/api/admin/handovers/$HID/report" "$ADMIN")
check "报告去向" "$(echo "$RPT" | jq -r '.data.destination')" "绿源环保"
check "报告积分发放" "$(echo "$RPT" | jq -r '.data.totalPointsIssued')" "102"

say "8. 居民追溯批次去向"
TR=$(req GET "/api/resident/trace/$BATCH_NO" "$RESIDENT")
check "批次状态" "$(echo "$TR" | jq -r '.data.batchStatus')" "HANDED_OVER"
check "去向公司" "$(echo "$TR" | jq -r '.data.bags[0].destination')" "绿源环保"

say "9. 积分兑换（成功 + 峰值失败兜底）"
R1=$(req POST /api/resident/points/redeem "$RESIDENT" '{"item":"环保袋","points":30}')
check "兑换30分" "$(echo "$R1" | jq -r '.data.status')" "SUCCESS"
R2=$(req POST /api/resident/points/redeem "$RESIDENT" '{"item":"大米","points":99999}')
check "余额不足兑换失败" "$(echo "$R2" | jq -r '.data.status')" "FAILED"
EXC=$(req GET "/api/admin/exceptions?status=OPEN" "$ADMIN" | jq '[.data[] | select(.type=="POINTS_EXCHANGE_FAILED")] | length')
[ "$EXC" -ge 1 ] && ok "兑换失败已记录异常" || bad "兑换失败未记录异常"

say "10. 活动日大批量称重 + 看板"
DAY=$(req POST /api/admin/activity-days "$ADMIN" \
  '{"name":"周末集中回收日","activityDate":"2026-10-01","communityPointId":1}' | jq -r '.data.id')
B2=$(req POST /api/collector/batches "$COLLECTOR" "{\"communityPointId\":1,\"activityDayId\":$DAY}" | jq -r '.data.id')
ITEMS=$(for i in 1 2 3; do
  AN=$(req POST /api/resident/appointments "$RESIDENT" \
    "{\"drugType\":\"批量药$i\",\"quantity\":1,\"communityPointId\":1,\"activityDayId\":$DAY}" | jq -r '.data.appointmentNo')
  echo "{\"appointmentNo\":\"$AN\",\"category\":\"EXTERNAL\",\"weightGrams\":100,\"sealBagNo\":\"BLK-$SUF-$i\"}"
done | jq -s '.')
BW=$(req POST "/api/collector/batches/$B2/weigh/batch" "$COLLECTOR" "{\"items\":$ITEMS}")
check "批量称重成功数" "$(echo "$BW" | jq -r '.data.succeeded')" "3"
DASH=$(req GET "/api/admin/dashboard?communityPointId=1" "$ADMIN")
echo "$DASH" | jq -r '.data | "  看板: 暂存\(.storedBagCount)袋/\(.storedTotalWeightGrams)g 上门量\(.pickupThresholdGrams)g 超期\(.overdueBagCount) 待复核\(.pendingReviewCount) 未解决异常\(.openExceptionCount)"'
[ "$(echo "$DASH" | jq -r '.code')" == "0" ] && ok "看板查询"

say "结果"
echo "  通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] && echo "  🎉 SMOKE OK" || { echo "  ⚠️ 存在失败项"; exit 1; }
