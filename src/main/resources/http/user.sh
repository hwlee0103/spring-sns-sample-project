#!/bin/bash

# User API 통합 테스트 스크립트.
# 전제: ./gradlew bootRun 실행 중 (dev 프로필).
# 흐름: 회원가입(검증) → 중복가입(409) → 조회/페이징 → 로그인 → 수정 → IDOR 방어 → 비밀번호 변경 → 삭제 → cascade 확인

BASE_URL="http://localhost:8080"
OWNER_JAR=$(mktemp)
ATTACKER_JAR=$(mktemp)
PASS=0
FAIL=0

cleanup() { rm -f "$OWNER_JAR" "$ATTACKER_JAR"; }
trap cleanup EXIT

check() {
  local step="$1"
  local method="$2"
  local url="$3"
  local req_body="$4"
  local expected="$5"
  local actual="$6"
  local res_body="$7"

  echo "--- Request ---"
  echo "$method $url"
  if [ -n "$req_body" ]; then
    echo "$req_body" | jq . 2>/dev/null || echo "$req_body"
  fi

  echo "--- Response ---"
  if [ "$actual" = "$expected" ]; then
    echo "[PASS] $step (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] $step (expected HTTP $expected, got HTTP $actual)"
    FAIL=$((FAIL + 1))
  fi
  if [ -n "$res_body" ]; then
    echo "$res_body" | jq . 2>/dev/null || echo "$res_body"
  fi
  echo ""
}

get_csrf() {
  awk '$6=="XSRF-TOKEN"{print $7}' "$1" | tail -1
}

echo "========================================"
echo " User API Test"
echo "========================================"
echo ""

SUFFIX=$(date +%s)
OWNER_EMAIL="user_owner_${SUFFIX}@example.com"
ATTACKER_EMAIL="user_attacker_${SUFFIX}@example.com"
PASSWORD="password123"

# 1. 회원가입 — owner
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"owner_%s"}' "$OWNER_EMAIL" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$OWNER_JAR" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "1. owner 회원가입" "POST" "$BASE_URL/api/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"
OWNER_ID=$(echo "$BODY" | jq -r '.id // empty')

# 2. 이메일 형식 오류 → 400 (Bean Validation)
REQ_BODY='{"email":"not-email","password":"password123","nickname":"invalid"}'
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. 이메일 형식 오류 (400)" "POST" "$BASE_URL/api/user" "$REQ_BODY" "400" "$HTTP_CODE" "$BODY"

# 3. 비밀번호 길이 부족 → 400
REQ_BODY=$(printf '{"email":"short_%s@example.com","password":"12345","nickname":"short_%s"}' "$SUFFIX" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. 비밀번호 길이 부족 (400)" "POST" "$BASE_URL/api/user" "$REQ_BODY" "400" "$HTTP_CODE" "$BODY"

# 4. 중복 이메일 → 409
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"dup_%s"}' "$OWNER_EMAIL" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "4. 중복 이메일 (409)" "POST" "$BASE_URL/api/user" "$REQ_BODY" "409" "$HTTP_CODE" "$BODY"

# 5. 중복 닉네임 → 409
REQ_BODY=$(printf '{"email":"diff_%s@example.com","password":"%s","nickname":"owner_%s"}' "$SUFFIX" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "5. 중복 닉네임 (409)" "POST" "$BASE_URL/api/user" "$REQ_BODY" "409" "$HTTP_CODE" "$BODY"

# 6. 회원가입 — attacker
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"attacker_%s"}' "$ATTACKER_EMAIL" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$ATTACKER_JAR" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "6. attacker 회원가입" "POST" "$BASE_URL/api/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"

# 7. ID 로 사용자 조회 (permitAll — public)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/$OWNER_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "7. ID 조회 (id=$OWNER_ID)" "GET" "$BASE_URL/api/user/$OWNER_ID" "" "200" "$HTTP_CODE" "$BODY"

# 8. 미존재 사용자 조회 → 404
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/99999999")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "8. 미존재 사용자 (404)" "GET" "$BASE_URL/api/user/99999999" "" "404" "$HTTP_CODE" "$BODY"

# 9. 닉네임으로 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/by-nickname/owner_$SUFFIX")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "9. 닉네임 조회 (owner_$SUFFIX)" "GET" "$BASE_URL/api/user/by-nickname/owner_$SUFFIX" "" "200" "$HTTP_CODE" "$BODY"

# 10. 페이징 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user?page=0&size=5&sort=id,desc")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "10. 페이징 조회 (size=5)" "GET" "$BASE_URL/api/user?page=0&size=5" "" "200" "$HTTP_CODE" "$BODY"

# 11. 비인증 수정 시도 → 401
REQ_BODY='{"nickname":"hacked"}'
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/api/user/$OWNER_ID" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "11. 비인증 수정 (401)" "PUT" "$BASE_URL/api/user/$OWNER_ID" "$REQ_BODY" "401" "$HTTP_CODE" "$BODY"

# 12. owner 로그인
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$OWNER_EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "12. owner 로그인" "POST" "$BASE_URL/api/auth/login" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 13. owner 본인 정보 수정 (인증 + 본인 리소스)
CSRF=$(get_csrf "$OWNER_JAR")
REQ_BODY=$(printf '{"nickname":"owner_updated_%s"}' "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X PUT "$BASE_URL/api/user/$OWNER_ID" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "13. 본인 정보 수정" "PUT" "$BASE_URL/api/user/$OWNER_ID" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 14. attacker 로그인
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$ATTACKER_EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$ATTACKER_JAR" -c "$ATTACKER_JAR" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "14. attacker 로그인" "POST" "$BASE_URL/api/auth/login" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 15. IDOR 방어 — attacker 가 owner 정보 수정 시도 → 403
CSRF=$(get_csrf "$ATTACKER_JAR")
REQ_BODY='{"nickname":"pwned"}'
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$ATTACKER_JAR" -c "$ATTACKER_JAR" -X PUT "$BASE_URL/api/user/$OWNER_ID" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "15. IDOR 방어 수정 (403)" "PUT" "$BASE_URL/api/user/$OWNER_ID" "$REQ_BODY" "403" "$HTTP_CODE" "$BODY"

# 16. IDOR 방어 — attacker 가 owner 삭제 시도 → 403
CSRF=$(get_csrf "$ATTACKER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$ATTACKER_JAR" -c "$ATTACKER_JAR" -X DELETE "$BASE_URL/api/user/$OWNER_ID" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "16. IDOR 방어 삭제 (403)" "DELETE" "$BASE_URL/api/user/$OWNER_ID" "" "403" "$HTTP_CODE" "$BODY"

# 17. 비밀번호 변경 — 현재 비밀번호 틀림 → 401
CSRF=$(get_csrf "$OWNER_JAR")
REQ_BODY=$(printf '{"currentPassword":"wrong_password","newPassword":"%s"}' "newpassword456")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X PUT "$BASE_URL/api/user/$OWNER_ID/password" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "17. 비번 변경 - 현재비번 오류 (401)" "PUT" "$BASE_URL/api/user/$OWNER_ID/password" "$REQ_BODY" "401" "$HTTP_CODE" "$BODY"

# 18. 비밀번호 변경 — 새 비번이 현재와 동일 → 400
CSRF=$(get_csrf "$OWNER_JAR")
REQ_BODY=$(printf '{"currentPassword":"%s","newPassword":"%s"}' "$PASSWORD" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X PUT "$BASE_URL/api/user/$OWNER_ID/password" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "18. 비번 변경 - 동일 비번 (400)" "PUT" "$BASE_URL/api/user/$OWNER_ID/password" "$REQ_BODY" "400" "$HTTP_CODE" "$BODY"

# 19. 비밀번호 변경 성공
CSRF=$(get_csrf "$OWNER_JAR")
NEW_PASSWORD="newpassword456"
REQ_BODY=$(printf '{"currentPassword":"%s","newPassword":"%s"}' "$PASSWORD" "$NEW_PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X PUT "$BASE_URL/api/user/$OWNER_ID/password" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "19. 비번 변경 성공" "PUT" "$BASE_URL/api/user/$OWNER_ID/password" "$REQ_BODY" "204" "$HTTP_CODE" "$BODY"

# 20. 새 비번으로 본인 세션 유지 확인 (token_version 증가에도 현재 세션은 refresh 되었어야 함)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X GET "$BASE_URL/api/auth/me")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "20. 비번 변경 후 본인 세션 유지 (200)" "GET" "$BASE_URL/api/auth/me" "" "200" "$HTTP_CODE" "$BODY"

# 21. 본인 계정 삭제
CSRF=$(get_csrf "$OWNER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -c "$OWNER_JAR" -X DELETE "$BASE_URL/api/user/$OWNER_ID" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "21. 본인 계정 삭제" "DELETE" "$BASE_URL/api/user/$OWNER_ID" "" "204" "$HTTP_CODE" "$BODY"

# 22. 삭제 후 세션 무효화 확인 → 401
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$OWNER_JAR" -X GET "$BASE_URL/api/auth/me")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "22. 삭제 후 세션 무효화 (401)" "GET" "$BASE_URL/api/auth/me" "" "401" "$HTTP_CODE" "$BODY"

# 23. 삭제된 계정 조회 → 404
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/$OWNER_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "23. 삭제된 계정 조회 (404)" "GET" "$BASE_URL/api/user/$OWNER_ID" "" "404" "$HTTP_CODE" "$BODY"

echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then
  echo " ALL TESTS PASSED"
else
  echo " SOME TESTS FAILED"
fi
echo "========================================"

exit "$FAIL"
