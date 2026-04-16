#!/bin/bash

# Post API 통합 테스트 스크립트.
# 전제: ./gradlew bootRun 실행 중 (dev 프로필).
# 흐름: 가입 → 로그인 → 게시글 생성 → 조회 → 피드 → 수정 → 삭제

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

get_csrf() {
  awk '$6=="XSRF-TOKEN"{print $7}' "$COOKIE_JAR" | tail -1
}

echo "========================================"
echo " Post API Test"
echo "========================================"
echo ""

EMAIL="post_test_$(date +%s)@example.com"
PASSWORD="password123"
NICKNAME="post테스트_$(date +%s)"

# 1. 회원가입
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"%s"}' "$EMAIL" "$PASSWORD" "$NICKNAME")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "1. 회원가입" "POST" "$BASE_URL/api/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"

# 2. 로그인
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. 로그인" "POST" "$BASE_URL/api/auth/login" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 3. 비인증 게시글 생성 시도 → 401 (별도 세션)
REQ_BODY='{"content":"비인증 게시글"}'
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/post" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. 비인증 게시글 생성 (401)" "POST" "$BASE_URL/api/post" "$REQ_BODY" "401" "$HTTP_CODE" "$BODY"

# 4. 인증 게시글 생성
CSRF=$(get_csrf)
REQ_BODY='{"content":"첫 번째 게시글입니다."}'
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/post" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "4. 게시글 생성" "POST" "$BASE_URL/api/post" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"
POST_ID=$(echo "$BODY" | jq -r '.id // empty')

# 5. 게시글 단건 조회 (permitAll)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/post/$POST_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "5. 게시글 단건 조회 (id=$POST_ID)" "GET" "$BASE_URL/api/post/$POST_ID" "" "200" "$HTTP_CODE" "$BODY"

# 6. 피드 조회 (permitAll)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/post?page=0&size=5")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "6. 피드 조회 (page=0,size=5)" "GET" "$BASE_URL/api/post?page=0&size=5" "" "200" "$HTTP_CODE" "$BODY"

# 7. 게시글 수정
CSRF=$(get_csrf)
REQ_BODY='{"content":"수정된 게시글입니다."}'
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X PUT "$BASE_URL/api/post/$POST_ID" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "7. 게시글 수정 (id=$POST_ID)" "PUT" "$BASE_URL/api/post/$POST_ID" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 8. 게시글 삭제
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X DELETE "$BASE_URL/api/post/$POST_ID" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "8. 게시글 삭제 (id=$POST_ID)" "DELETE" "$BASE_URL/api/post/$POST_ID" "" "204" "$HTTP_CODE" "$BODY"

# 9. 삭제 확인 → 400 (또는 404)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/post/$POST_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "9. 삭제 확인 조회 (id=$POST_ID → 404)" "GET" "$BASE_URL/api/post/$POST_ID" "" "404" "$HTTP_CODE" "$BODY"

echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then
  echo " ALL TESTS PASSED"
else
  echo " SOME TESTS FAILED"
fi
echo "========================================"

exit "$FAIL"
