-- V2: follows 테이블 부분 인덱스 (PostgreSQL 전용)
--
-- Soft delete 패턴에서 대부분의 쿼리는 deleted = false 행만 대상으로 한다.
-- 부분 인덱스(partial index)를 사용하면:
--   1. 인덱스 크기 축소 — deleted = true 행은 인덱스에서 제외
--   2. INSERT 비용 감소 — soft delete 행이 쌓여도 인덱스 비대화 방지
--   3. 쿼리 성능 향상 — 활성 행만 대상으로 인덱스 스캔
--
-- 기존 전체 인덱스(V1)와 공존 가능하지만, 활성 팔로우 쿼리에서 PostgreSQL
-- 옵티마이저가 부분 인덱스를 자동 선택한다 (WHERE deleted = false 매칭).

-- 팔로워 목록: WHERE following_id = ? AND deleted = false ORDER BY created_at DESC
CREATE INDEX idx_follows_following_active
    ON follows (following_id, created_at DESC)
    WHERE deleted = false;

-- 팔로잉 목록: WHERE follower_id = ? AND deleted = false ORDER BY created_at DESC
CREATE INDEX idx_follows_follower_active
    ON follows (follower_id, created_at DESC)
    WHERE deleted = false;

-- 팔로우 여부 확인: WHERE follower_id = ? AND following_id = ? AND deleted = false
CREATE INDEX idx_follows_pair_active
    ON follows (follower_id, following_id)
    WHERE deleted = false;
