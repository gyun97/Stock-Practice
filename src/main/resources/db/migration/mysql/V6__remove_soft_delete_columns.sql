-- 회원 탈퇴 하드 딜리트 전환에 따른 불필요 컬럼 제거
ALTER TABLE users DROP COLUMN is_deleted;
ALTER TABLE users DROP COLUMN withdrawal_at;
