#!/usr/bin/env bash
# demo/run.sh — 부록 B 위촉 E2E를 curl로 밟는 실연 스크립트 (설계서 부록 B, Phase 8).
#
# 전제(3개 프로세스 + 시드):
#   1) docker compose up -d oracle
#   2) ./gradlew :insuhr-api:bootRun            # 8080 — 온라인 API(Flyway migrate 주체)
#   3) ./gradlew :insuhr-relay:bootRun          # Outbox → 웹훅 릴레이
#   4) python3 demo/webhook_receiver.py         # 9099 — 로컬 웹훅 수신 덤프
#   5) docker exec -i insuhr-oracle sqlplus -S insuhr/insuhr@localhost:1521/FREEPDB1 < demo/seed.sql
# 그리고: ./demo/run.sh
#
# 흐름: 로그인 → 웹훅 구독자 등록 → 조직/후보 → 자격·교육·보증 → 위촉 → 협회등록(ACTIVE, agent.appointed)
#       → 계좌 복호화 → 변경분 Pull. 위촉/활성 이벤트가 릴레이를 거쳐 수신 덤프에 찍히는 걸 눈으로 본다.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}/api/v1"
RECEIVER="${RECEIVER:-http://localhost:9099/hook}"
say() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
post() { curl -s -X POST "$BASE$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$2"; }
get()  { curl -s "$BASE$1" -H "Authorization: Bearer $TOKEN"; }

say "0) 로그인 (demo / demo1234!)"
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"loginId":"demo","password":"demo1234!"}' | jq -r '.data.accessToken')
[ "$TOKEN" != "null" ] && [ -n "$TOKEN" ] || { echo "로그인 실패 — seed.sql 실행했나요?"; exit 1; }
echo "  토큰 확보 (${TOKEN:0:16}…)"

say "1) 웹훅 구독자 등록 (영업시스템 역할, 로컬 수신 덤프로)"
post /admin/subscribers "{\"systemCd\":\"DEMO_SALES\",\"systemNm\":\"영업시스템(데모)\",\"deliveryTypeCd\":\"WEBHOOK\",\"endpointUrl\":\"$RECEIVER\",\"secret\":\"whsec-demo\",\"topicFilterJson\":null}" | jq -c '.data // .error'

say "2) 조직 생성 (지점 → 영업소)"
post /orgs '{"orgCd":"BR-DEMO","orgNm":"데모지점","orgType":"BRANCH","sortOrd":1}' | jq -c '.data // .error'
ORG_ID=$(post /orgs '{"orgCd":"OF-DEMO","orgNm":"데모영업소","orgType":"OFFICE","upOrgCd":"BR-DEMO","sortOrd":1}' | jq -r '.data.orgId')
echo "  영업소 orgId=$ORG_ID"

say "3) 설계사 후보 등록"
AGENT_ID=$(post /agents/candidates "{\"personNm\":\"김설계\",\"rrn\":\"900202-2345678\",\"birthDt\":\"1990-02-02\",\"gender\":\"F\",\"channel\":\"FC\",\"orgId\":$ORG_ID}" | jq -r '.data.agentId')
echo "  agentId=$AGENT_ID (상태 CANDIDATE)"

say "4) 생보 판매자격 등록 (VALID)"
post "/agents/$AGENT_ID/licenses" '{"type":"LIFE","licenseNo":"L-DEMO-1","examPassDt":"2026-06-01","regDt":"2026-07-01"}' | jq -c '.data // .error'

say "5) 등록교육 이수"
post "/agents/$AGENT_ID/educations" '{"type":"REG","eduNm":"신규등록교육","completeDt":"2026-07-10","eduHours":20,"providerNm":"생명보험협회"}' | jq -c '.data // .error'

say "6) 재정보증 등록 (보증보험 1년)"
post "/agents/$AGENT_ID/guarantees" '{"grntTypeCd":"SURETY_INS","grntAmt":30000000,"issuerNm":"서울보증보험","policyNo":"G-DEMO-1","startDt":"2026-07-15","endDt":"2027-07-14"}' | jq -c '.data // .error'

say "7) 위촉 실행 (요건검증 PASS → PENDING_ASSOC, 위촉계약+지급계좌 생성, agent.status.changed)"
post "/agents/$AGENT_ID/appoint" '{"appointDt":"2026-08-01","contractTypeCd":"FC_STD","commRuleVer":"2026-1","bankCd":"004","account":"110234567890","accountHolderNm":"김설계"}' >/dev/null
echo "  → 상태 $(get "/agents/$AGENT_ID" | jq -r '.data.statusCd') (요건검증 통과)"

say "8) 협회 등록번호 입력 (→ ACTIVE, agent.appointed 발행 → 영업시스템 웹훅 수신)"
post "/agents/$AGENT_ID/assoc-registrations" '{"regDt":"2026-08-02","assoc":"LIFE_ASSOC","assocRegNo":"LA-DEMO-1"}' >/dev/null
echo "  → 상태 $(get "/agents/$AGENT_ID" | jq -r '.data.statusCd') (영업시스템 웹훅으로 전파됨)"

say "9) 지급계좌 복호화 (사유 필수 → 원문 + 즉석 마스킹, 접근로그 기록)"
post "/agents/$AGENT_ID/account" '{"purpose":"수수료 정산 대사"}' | jq -c '.data // .error'

say "10) 변경분 Pull (수수료시스템 대사용 커서 API)"
get "/sync/changes?aggType=AGENT&cursor=0&size=20" | jq -c '.data'
echo "  (방금 만든 변경은 워터마크 지연 SYNC_WATERMARK_SECONDS(기본 5초) 안이라 잠시 뒤에 커서에 잡힌다 — 시퀀스 갭 함정 방지)"

say "완료. 릴레이가 2초 주기로 폴링하므로 잠시 후 webhook_receiver 창에 agent.status.changed·agent.appointed가 찍힙니다."
echo "  (부록 B의 SUSPENDED 전이는 배치가 잡는다:"
echo "   java -jar insuhr-batch/build/libs/insuhr-batch-*.jar --spring.batch.job.name=eligibilityRefreshJob targetDate=2028-08-02 run.id=1"
echo "   또는 제재 등록(POST /agents/$AGENT_ID/sanctions, recruitBlock:true)으로 즉시 SUSPENDED 확인.)"
