#!/bin/bash

# Post API 통합 테스트 — 4종 게시글 + 좋아요 + soft delete
# 전제: ./gradlew bootRun 실행 중 (dev 프로필)

BASE_URL="http://localhost:8080"
COOKIE_JAR=$(mktemp)
PASS=0
FAIL=0

cleanup() { rm -f "$COOKIE_JAR"; }
trap cleanup EXIT

check() {
  local step="$1" method="$2" url="$3" req_body="$4" expected="$5" actual="$6" res_body="$7"
  echo "--- Request ---"
  echo "$method $url"
  [ -n "$req_body" ] && (echo "$req_body" | jq . 2>/dev/null || echo "$req_body")
  echo "--- Response ---"
  if [ "$actual" = "$expected" ]; then
    echo "[PASS] $step (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] $step (expected HTTP $expected, got HTTP $actual)"
    FAIL=$((FAIL + 1))
  fi
  [ -n "$res_body" ] && (echo "$res_body" | jq . 2>/dev/null || echo "$res_body")
  echo ""
}

get_csrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$COOKIE_JAR" | tail -1; }

echo "========================================"
echo " Post API Test (4종 + Like)"
echo "========================================"
echo ""

SUFFIX=$(date +%s)
EMAIL="post_${SUFFIX}@example.com"
EMAIL2="post2_${SUFFIX}@example.com"
PASSWORD="password123"

# 1. 회원가입 + 로그인
REQ_BODY=$(printf '{"email":"%s","password":"%s","nickname":"poster_%s"}' "$EMAIL" "$PASSWORD" "$SUFFIX")
curl -s -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" -d "$REQ_BODY" > /dev/null
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "1. 로그인" "POST" "/api/v1/auth/login" "" "200" "$HTTP_CODE" ""

# 2. 일반 게시글 생성
CSRF=$(get_csrf)
REQ_BODY='{"content":"일반 게시글입니다."}'
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. 일반 게시글 생성" "POST" "/api/v1/post" "$REQ_BODY" "201" "$HTTP_CODE" "$BODY"
POST_ID=$(echo "$BODY" | jq -r '.id // empty')

# 3. 게시글 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post/$POST_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. 게시글 단건 조회" "GET" "/api/v1/post/$POST_ID" "" "200" "$HTTP_CODE" "$BODY"

# 4. 피드 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post?page=0&size=5")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "4. 피드 조회" "GET" "/api/v1/post" "" "200" "$HTTP_CODE" ""

# 5. 답글 생성
CSRF=$(get_csrf)
REQ_BODY=$(printf '{"content":"답글입니다.","parentId":%s}' "$POST_ID")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "5. 답글 생성" "POST" "/api/v1/post (reply)" "" "201" "$HTTP_CODE" ""

# 6. 답글 목록 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post/$POST_ID/replies")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "6. 답글 목록" "GET" "/api/v1/post/$POST_ID/replies" "" "200" "$HTTP_CODE" ""

# 7. 인용 생성
CSRF=$(get_csrf)
REQ_BODY=$(printf '{"content":"이 글 공감합니다.","quoteId":%s}' "$POST_ID")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "7. 인용 생성" "POST" "/api/v1/post (quote)" "" "201" "$HTTP_CODE" ""

# 8. 인용 목록
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post/$POST_ID/quotes")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "8. 인용 목록" "GET" "/api/v1/post/$POST_ID/quotes" "" "200" "$HTTP_CODE" ""

# 9. 리포스트 (두 번째 사용자)
REQ_BODY2=$(printf '{"email":"%s","password":"%s","nickname":"poster2_%s"}' "$EMAIL2" "$PASSWORD" "$SUFFIX")
curl -s -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" -d "$REQ_BODY2" > /dev/null
REQ_BODY2=$(printf '{"email":"%s","password":"%s"}' "$EMAIL2" "$PASSWORD")
curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" -d "$REQ_BODY2" > /dev/null
CSRF=$(get_csrf)
REQ_BODY=$(printf '{"repostId":%s}' "$POST_ID")
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "9. 리포스트 생성" "POST" "/api/v1/post (repost)" "" "201" "$HTTP_CODE" ""

# 10. 게시글 수정 (원본 작성자 재로그인)
REQ_BODY=$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD")
curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" -d "$REQ_BODY" > /dev/null
CSRF=$(get_csrf)
REQ_BODY='{"content":"수정된 게시글입니다."}'
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X PUT "$BASE_URL/api/v1/post/$POST_ID" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" -d "$REQ_BODY")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "10. 게시글 수정" "PUT" "/api/v1/post/$POST_ID" "" "200" "$HTTP_CODE" ""

# 11. 좋아요
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post/$POST_ID/like" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "11. 좋아요" "POST" "/api/v1/post/$POST_ID/like" "" "201" "$HTTP_CODE" ""

# 12. 중복 좋아요 → 409
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/post/$POST_ID/like" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "12. 중복 좋아요 (409)" "POST" "/api/v1/post/$POST_ID/like" "" "409" "$HTTP_CODE" ""

# 13. 좋아요 상태
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -X GET "$BASE_URL/api/v1/post/$POST_ID/like/status")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "13. 좋아요 상태 (liked=true)" "GET" "/api/v1/post/$POST_ID/like/status" "" "200" "$HTTP_CODE" ""

# 14. 좋아요 취소
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X DELETE "$BASE_URL/api/v1/post/$POST_ID/like" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "14. 좋아요 취소" "DELETE" "/api/v1/post/$POST_ID/like" "" "204" "$HTTP_CODE" ""

# 15. 좋아요 목록 (공개)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post/$POST_ID/likes")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "15. 좋아요 목록" "GET" "/api/v1/post/$POST_ID/likes" "" "200" "$HTTP_CODE" ""

# 16. 게시글 삭제 (soft delete)
CSRF=$(get_csrf)
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X DELETE "$BASE_URL/api/v1/post/$POST_ID" \
  -H "X-XSRF-TOKEN: $CSRF")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "16. 게시글 삭제" "DELETE" "/api/v1/post/$POST_ID" "" "204" "$HTTP_CODE" ""

# 17. 삭제된 게시글 조회 (스레드용 200 + deleted=true)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/v1/post/$POST_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "17. 삭제된 게시글 조회 (200)" "GET" "/api/v1/post/$POST_ID" "" "200" "$HTTP_CODE" ""

# 18. 비인증 생성 → 401
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/post" \
  -H "Content-Type: application/json" -d '{"content":"unauthorized"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
check "18. 비인증 생성 (401)" "POST" "/api/v1/post" "" "401" "$HTTP_CODE" ""

echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then echo " ALL TESTS PASSED"; else echo " SOME TESTS FAILED"; fi
echo "========================================"
exit "$FAIL"
