-- 사용자 이메일 UNIQUE 제약조건
ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);

-- 계정 잠금 기본값
ALTER TABLE users
    ALTER COLUMN locked SET DEFAULT FALSE;

-- 계정 상태 기본값
ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- 사용자 권한 제한
ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'));

-- 계정 상태 제한
ALTER TABLE users
    ADD CONSTRAINT chk_users_status
        CHECK (status IN (
                          'ACTIVE',
                          'DEACTIVATED',
                          'DELETED'
            ));
