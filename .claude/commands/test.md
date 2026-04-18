---
description: Run test on modified files against Spring Boot project standards
allowed-tools: Read, Grep, Glob, Edit, Write, Bash
---

## Task

tester agent 를 실행하여 QA test 해.
반드시 테스트 설계서를 참고해서 테스트 진행해.
현재 변경된 파일이 없을 경우 전체 소스 대상으로 TEST 진행해.
User journey를 포함해서 제대로 작동하는지 테스트 진행해.
지금까지 만든 전체 서비스에서 캐시, 쿠키, 토큰, 에러 처리 네 가지 관점에서 민감 정보 노출 가능한 곳 찾고 테스트 내용으로 추가해.
(수정, 개선이 필요한 부분, 권장사항 포함)
성능 테스트 관점에서도 테스트를 진행해줘.

리뷰 진행 후 `.claude/test-history/` 디렉토리 내 **월 단위 파일**(`YYYY-MM.md`)을 다음 규칙에 따라 갱신해줘.

### 파일 명명 규칙
- 테스트 결과 파일은 일자별이 아닌 **월 단위**로 관리한다. (예: `2026-04.md`, `2026-05.md`)
- 신규 파일이 필요하면 동일한 표 형식과 상태값 정의 섹션을 포함하여 생성한다.
