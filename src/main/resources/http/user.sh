#!/bin/bash

BASE_URL="http://localhost:8080"
PASS=0
FAIL=0

check() {
  local step="$1"
  local expected="$2"
  local actual="$3"
  local body="$4"

  if [ "$actual" = "$expected" ]; then
    echo "[PASS] $step (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] $step (expected HTTP $expected, got HTTP $actual)"
    FAIL=$((FAIL + 1))
  fi
  if [ -n "$body" ]; then
    echo "$body" | jq . 2>/dev/null || echo "$body"
  fi
  echo ""
}

echo "========================================"
echo " User API Test"
echo "========================================"
echo ""

# 1. 회원가입
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/user" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","nickname":"테스트유저"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "1. 회원가입" "201" "$HTTP_CODE" "$BODY"

# 2. 전체 사용자 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "2. 전체 사용자 조회" "200" "$HTTP_CODE" "$BODY"

# 3. ID로 사용자 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/1")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "3. ID로 사용자 조회 (id=1)" "200" "$HTTP_CODE" "$BODY"

# 4. 사용자 업데이트
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/api/user/1" \
  -H "Content-Type: application/json" \
  -d '{"nickname":"수정된유저","password":"newpass456"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "4. 사용자 업데이트 (id=1)" "200" "$HTTP_CODE" "$BODY"

# 5. 업데이트 확인 조회
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/1")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "5. 업데이트 확인 조회 (id=1)" "200" "$HTTP_CODE" "$BODY"

# 6. 사용자 삭제
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/api/user/1")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "6. 사용자 삭제 (id=1)" "204" "$HTTP_CODE" "$BODY"

# 7. 삭제 확인 (존재하지 않는 사용자 → 400)
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/user/1")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
check "7. 삭제 확인 - 조회 실패 (id=1)" "400" "$HTTP_CODE" "$BODY"

# 결과 요약
echo "========================================"
echo " 결과: ${PASS} passed, ${FAIL} failed (total $((PASS + FAIL)))"
if [ "$FAIL" -eq 0 ]; then
  echo " ALL TESTS PASSED"
else
  echo " SOME TESTS FAILED"
fi
echo "========================================"

exit "$FAIL"
