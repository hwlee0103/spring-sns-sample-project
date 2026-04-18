-- V4: Post 엔티티 재설계 — 4종 통합 (ORIGINAL/REPLY/QUOTE/REPOST) + 비정규화 카운트

-- 1. 참조 ID 컬럼 추가
ALTER TABLE posts ADD COLUMN parent_id BIGINT;
ALTER TABLE posts ADD COLUMN quote_id BIGINT;
ALTER TABLE posts ADD COLUMN repost_id BIGINT;
ALTER TABLE posts ADD COLUMN media_id BIGINT;

-- 2. 비정규화 카운트 컬럼 추가
ALTER TABLE posts ADD COLUMN reply_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN repost_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN share_count BIGINT NOT NULL DEFAULT 0;

-- 3. content nullable 변경 (리포스트/삭제 시 null)
ALTER TABLE posts ALTER COLUMN content DROP NOT NULL;

-- 4. content 길이 확장 500 → 1000
ALTER TABLE posts ALTER COLUMN content TYPE VARCHAR(1000);

-- 5. 인덱스 추가
CREATE INDEX idx_posts_author_created ON posts (author_id, created_at DESC);
CREATE INDEX idx_posts_parent_id ON posts (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_posts_parent_created ON posts (parent_id, created_at DESC) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_posts_quote_id ON posts (quote_id) WHERE quote_id IS NOT NULL;
CREATE INDEX idx_posts_repost_id ON posts (repost_id) WHERE repost_id IS NOT NULL;
