#!/bin/bash

# Auth API 통합 테스트 스크립트.
# 전제: ./gradlew bootRun 실행 중 (dev 프로필).
# 흐름: 회원가입 → 비인증 me(401) → 로그인 → me(200) → 로그아웃 → me(401)

BASE_URL="http://localhost:8080"
COOKIE_JAR=$(mktemp)
PASS=0
FAIL=0

cleanup() { rm -f "$COOKIE_JAR"; }
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

# XSRF 토큰 추출 (cookie jar 에서)
get_csrf() {
  awk '$6=="XSRF-TOKEN"{print $7}' "$COOKIE_JAR" | tail -1
}

echo "========================================"
echo " Auth API Test"
echo "========================================"
echo ""

EMAIL="auth_test_$(date +%s)@example.com"
PASSWORD="password123"
NICKNAME="auth테스트_$(date +%s)"

# 1. 회원가입 (permitAll, CSRF 면제)
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"%s"}' "$EMAIL" "$PASSWORD" "$NICKNAME")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "1. 회원가입" "POST" "$BASE_URL/api/v1/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"

# 2. 비인증 me → 401
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/api/v1/auth/me")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. 비인증 me (401)" "GET" "$BASE_URL/api/v1/auth/me" "" "401" "$HTTP_CODE" "$BODY"

# 3. 로그인 (CSRF 면제)
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. 로그인" "POST" "$BASE_URL/api/v1/auth/login" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 4. 로그인 후 me → 200
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/api/v1/auth/me")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "4. 로그인 후 me (200)" "GET" "$BASE_URL/api/v1/auth/me" "" "200" "$HTTP_CODE" "$BODY"

# 5. 잘못된 비밀번호 → 401
REQ_BODY=$(printf '{"email":"%s","password":"wrong_password"}' "$EMAIL")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "5. 잘못된 비밀번호 (401)" "POST" "$BASE_URL/api/v1/auth/login" "$REQ_BODY" "401" "$HTTP_CODE" "$BODY"

# 6. 로그아웃 (CSRF 필요)
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/logout" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "6. 로그아웃" "POST" "$BASE_URL/api/v1/auth/logout" "" "204" "$HTTP_CODE" "$BODY"

# 7. 로그아웃 후 me → 401
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X GET "$BASE_URL/api/v1/auth/me")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "7. 로그아웃 후 me (401)" "GET" "$BASE_URL/api/v1/auth/me" "" "401" "$HTTP_CODE" "$BODY"

echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then
  echo " ALL TESTS PASSED"
else
  echo " SOME TESTS FAILED"
fi
echo "========================================"

exit "$FAIL"
