#!/bin/bash

# Follow API 통합 테스트 스크립트.
# 전제: ./gradlew bootRun 실행 중 (dev 프로필).
# 흐름: 두 사용자(follower/target) 가입 → follower 로그인 → 팔로우 → 상태/카운트/목록 확인 → 언팔로우 → 재팔로우(soft delete 복원)

BASE_URL="http://localhost:8080"
FOLLOWER_JAR=$(mktemp)
TARGET_JAR=$(mktemp)
PASS=0
FAIL=0

cleanup() { rm -f "$FOLLOWER_JAR" "$TARGET_JAR"; }
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
echo " Follow API Test"
echo "========================================"
echo ""

SUFFIX=$(date +%s)
FOLLOWER_EMAIL="follower_${SUFFIX}@example.com"
TARGET_EMAIL="target_${SUFFIX}@example.com"
PASSWORD="password123"

# 1. follower 회원가입
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"follower_%s"}' "$FOLLOWER_EMAIL" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "1. follower 회원가입" "POST" "$BASE_URL/api/v1/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"
FOLLOWER_ID=$(echo "$BODY" | jq -r '.id // empty')

# 2. target 회원가입
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"target_%s"}' "$TARGET_EMAIL" "$PASSWORD" "$SUFFIX")
RESPONSE=$(curl -s -w "\n%{http_code}" -c "$TARGET_JAR" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. target 회원가입" "POST" "$BASE_URL/api/v1/user" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"
TARGET_ID=$(echo "$BODY" | jq -r '.id // empty')

# 3. follower 로그인
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$FOLLOWER_EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. follower 로그인" "POST" "$BASE_URL/api/v1/auth/login" "$REQ_BODY" "200" "$HTTP_CODE" "$BODY"

# 4. 팔로우 전 카운트 확인 (permitAll)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/user/$TARGET_ID/follow/count")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "4. 팔로우 전 카운트 (followers=0)" "GET" "$BASE_URL/api/v1/user/$TARGET_ID/follow/count" "" "200" "$HTTP_CODE" "$BODY"

# 5. 팔로우
CSRF=$(get_csrf "$FOLLOWER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/user/$TARGET_ID/follow" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "5. 팔로우 (target=$TARGET_ID)" "POST" "$BASE_URL/api/v1/user/$TARGET_ID/follow" "" "201" "$HTTP_CODE" "$BODY"

# 6. 중복 팔로우 → 409
CSRF=$(get_csrf "$FOLLOWER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/user/$TARGET_ID/follow" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "6. 중복 팔로우 (409)" "POST" "$BASE_URL/api/v1/user/$TARGET_ID/follow" "" "409" "$HTTP_CODE" "$BODY"

# 7. 셀프 팔로우 → 400
CSRF=$(get_csrf "$FOLLOWER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/user/$FOLLOWER_ID/follow" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "7. 셀프 팔로우 (400)" "POST" "$BASE_URL/api/v1/user/$FOLLOWER_ID/follow" "" "400" "$HTTP_CODE" "$BODY"

# 8. 팔로우 후 카운트
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/user/$TARGET_ID/follow/count")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "8. 팔로우 후 카운트 (followers=1)" "GET" "$BASE_URL/api/v1/user/$TARGET_ID/follow/count" "" "200" "$HTTP_CODE" "$BODY"

# 9. 팔로우 상태 확인 (인증 필요)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -X GET "$BASE_URL/api/v1/user/$TARGET_ID/follow/status")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "9. 팔로우 상태 (isFollowing=true)" "GET" "$BASE_URL/api/v1/user/$TARGET_ID/follow/status" "" "200" "$HTTP_CODE" "$BODY"

# 10. 팔로워 목록 (target 의 팔로워)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/user/$TARGET_ID/followers")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "10. 팔로워 목록 (target=$TARGET_ID)" "GET" "$BASE_URL/api/v1/user/$TARGET_ID/followers" "" "200" "$HTTP_CODE" "$BODY"

# 11. 팔로잉 목록 (follower 의 팔로잉)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/user/$FOLLOWER_ID/followings")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "11. 팔로잉 목록 (follower=$FOLLOWER_ID)" "GET" "$BASE_URL/api/v1/user/$FOLLOWER_ID/followings" "" "200" "$HTTP_CODE" "$BODY"

# 12. 언팔로우
CSRF=$(get_csrf "$FOLLOWER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X DELETE "$BASE_URL/api/v1/user/$TARGET_ID/follow" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "12. 언팔로우" "DELETE" "$BASE_URL/api/v1/user/$TARGET_ID/follow" "" "204" "$HTTP_CODE" "$BODY"

# 13. 언팔로우 후 카운트 (0 으로 감소)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/user/$TARGET_ID/follow/count")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "13. 언팔로우 후 카운트 (followers=0)" "GET" "$BASE_URL/api/v1/user/$TARGET_ID/follow/count" "" "200" "$HTTP_CODE" "$BODY"

# 14. 재팔로우 (soft delete 복원)
CSRF=$(get_csrf "$FOLLOWER_JAR")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$FOLLOWER_JAR" -c "$FOLLOWER_JAR" -X POST "$BASE_URL/api/v1/user/$TARGET_ID/follow" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "14. 재팔로우 (soft deleted 복원)" "POST" "$BASE_URL/api/v1/user/$TARGET_ID/follow" "" "201" "$HTTP_CODE" "$BODY"

# 15. 비인증 팔로우 시도 → 401
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/user/$TARGET_ID/follow")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "15. 비인증 팔로우 (401)" "POST" "$BASE_URL/api/v1/user/$TARGET_ID/follow" "" "401" "$HTTP_CODE" "$BODY"

echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then
  echo " ALL TESTS PASSED"
else
  echo " SOME TESTS FAILED"
fi
echo "========================================"

exit "$FAIL"
