-- V3: 전 테이블에 공통 감사(audit) 컬럼 추가 — BaseEntity 매핑
--
-- createdAt, updatedAt, deletedAt 을 모든 엔티티에 공통 적용.
-- 기존에 일부 컬럼이 있는 테이블은 누락분만 추가한다.

-- =====================================================
-- 1. users — createdAt, updatedAt, deletedAt 전부 신규
-- =====================================================
ALTER TABLE users ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- 기존 행에 현재 시각 기본값 채우기
UPDATE users SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;

-- NOT NULL 제약 적용
ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;

-- =====================================================
-- 2. posts — updatedAt, deletedAt 신규 (createdAt 은 기존)
-- =====================================================
ALTER TABLE posts ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE posts ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE posts SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE posts ALTER COLUMN updated_at SET NOT NULL;

-- =====================================================
-- 3. follows — updatedAt 신규 (createdAt, deletedAt 기존)
-- =====================================================
ALTER TABLE follows ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE follows SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE follows ALTER COLUMN updated_at SET NOT NULL;

-- =====================================================
-- 4. follow_counts — createdAt, updatedAt, deletedAt 전부 신규
-- =====================================================
ALTER TABLE follow_counts ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE follow_counts ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE follow_counts ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE follow_counts SET created_at = NOW(), updated_at = NOW() WHERE created_at IS NULL;

ALTER TABLE follow_counts ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE follow_counts ALTER COLUMN updated_at SET NOT NULL;
