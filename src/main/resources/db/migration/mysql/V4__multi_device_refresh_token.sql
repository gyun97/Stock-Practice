-- 멀티 기기 세션 지원을 위한 refresh_tokens 테이블 재구성
-- PK를 userId(rt_key) → UUID 기반 세션 ID(id)로 변경하여 1명이 여러 기기에서 동시 로그인 가능

DROP TABLE IF EXISTS refresh_tokens;

CREATE TABLE refresh_tokens (
    id         VARCHAR(36)  NOT NULL COMMENT '세션 UUID (기기별 고유 식별자)',
    user_id    BIGINT       NOT NULL COMMENT '유저 PK',
    rt_value   VARCHAR(500) NOT NULL COMMENT '리프레시 토큰 값',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 일시',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_rt_value (rt_value(255))
);
