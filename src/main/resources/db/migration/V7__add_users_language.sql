-- Purpose: preferred language of each user, used for emails and the UI (NFR14: Vietnamese by default, English allowed)
-- Author: MinhTien | Created: 2026-09-26
ALTER TABLE users
    ADD COLUMN language varchar(5) NOT NULL DEFAULT 'VI',
    ADD CONSTRAINT ck_users_language CHECK (language IN ('VI', 'EN'));
