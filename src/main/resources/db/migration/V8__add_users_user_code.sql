-- Purpose: users sign in with a User ID instead of their email. For a student the User ID is the MSSV, for an Admin a
--          code such as ADMIN. It replaces the optional student_code, becomes required and is stored uppercase so
--          that sign-in ignores case.
-- Author: MinhTien | Created: 2026-10-01
ALTER TABLE users RENAME COLUMN student_code TO user_code;
ALTER TABLE users RENAME CONSTRAINT ck_users_student_code_format TO ck_users_user_code_format;
DROP INDEX uq_users_student_code;

-- Existing accounts: normalize the codes they have, give one to those without (ADMIN, ADMIN2..., USER1, USER2...)
UPDATE users SET user_code = upper(btrim(user_code)) WHERE user_code IS NOT NULL;

WITH missing AS (
    SELECT id
        ,  system_role
        ,  row_number() OVER (PARTITION BY system_role ORDER BY created_at, id) AS n
    FROM users
    WHERE user_code IS NULL
)
UPDATE users u
SET user_code = CASE
        WHEN m.system_role = 'ADMIN' AND m.n = 1 THEN 'ADMIN'
        WHEN m.system_role = 'ADMIN' THEN 'ADMIN' || m.n
        ELSE 'USER' || m.n
    END
FROM missing m
WHERE u.id = m.id;

ALTER TABLE users
    ALTER COLUMN user_code SET NOT NULL,
    ADD CONSTRAINT uq_users_user_code UNIQUE (user_code),
    ADD CONSTRAINT ck_users_user_code_normalized CHECK (user_code = upper(user_code));
