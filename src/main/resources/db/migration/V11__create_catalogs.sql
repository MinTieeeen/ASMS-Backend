-- Purpose: catalogs configured by Admins: schools users pick from (BR-USER-03) and system-wide holidays (UC-USER-13)
-- Author: MinhTien | Created: 2026-10-04
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE schools (
        id          uuid         NOT NULL DEFAULT gen_random_uuid()
    ,   code        varchar(20)  NOT NULL
    ,   name        varchar(150) NOT NULL
    ,   name_search varchar(150) NOT NULL
    ,   short_name  varchar(30)
    ,   is_active   boolean      NOT NULL DEFAULT true
    ,   created_by  uuid
    ,   updated_by  uuid
    ,   created_at  timestamptz  NOT NULL DEFAULT now()
    ,   updated_at  timestamptz  NOT NULL DEFAULT now()
    ,   version     integer      NOT NULL DEFAULT 0,

    -- Constraint --
    CONSTRAINT pk_schools
        PRIMARY KEY (id),
    CONSTRAINT uq_schools_code
        UNIQUE (code),
    CONSTRAINT uq_schools_name_search
        UNIQUE (name_search),
    CONSTRAINT ck_schools_code_format
        CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    CONSTRAINT ck_schools_name_length
        CHECK (char_length(name) BETWEEN 2 AND 150),
    CONSTRAINT ck_schools_short_name_length
        CHECK (char_length(short_name) <= 30),
    -- created_by is NULL only for the rows seeded below: a fresh database has no Admin yet
    CONSTRAINT fk_schools_created_by
        FOREIGN KEY (created_by)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schools_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users (id)
        ON DELETE SET NULL
);

CREATE INDEX ix_schools_active_name ON schools (is_active, name);
CREATE INDEX ix_schools_name_search_trgm ON schools USING gin (name_search gin_trgm_ops);

CREATE TABLE holidays (
        id             uuid         NOT NULL DEFAULT gen_random_uuid()
    ,   name           varchar(100) NOT NULL
    ,   start_date     date         NOT NULL
    ,   end_date       date         NOT NULL
    ,   kind           varchar(20)  NOT NULL DEFAULT 'PUBLIC_HOLIDAY'
    ,   repeats_yearly boolean      NOT NULL DEFAULT false
    ,   note           varchar(300)
    ,   created_by     uuid
    ,   updated_by     uuid
    ,   created_at     timestamptz  NOT NULL DEFAULT now()
    ,   updated_at     timestamptz  NOT NULL DEFAULT now()
    ,   version        integer      NOT NULL DEFAULT 0,

    -- Constraint --
    CONSTRAINT pk_holidays
        PRIMARY KEY (id),
    CONSTRAINT ck_holidays_name_length
        CHECK (char_length(name) BETWEEN 2 AND 100),
    CONSTRAINT ck_holidays_range
        CHECK (end_date >= start_date AND end_date - start_date <= 30),
    CONSTRAINT ck_holidays_kind
        CHECK (kind IN ('PUBLIC_HOLIDAY', 'COMPENSATORY', 'OTHER')),
    CONSTRAINT ck_holidays_note_length
        CHECK (char_length(note) <= 300),
    CONSTRAINT ck_holidays_same_year
        CHECK (extract(year FROM start_date) = extract(year FROM end_date) OR kind = 'PUBLIC_HOLIDAY'),
    CONSTRAINT ex_holidays_no_overlap
        EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&),
    CONSTRAINT fk_holidays_created_by
        FOREIGN KEY (created_by)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_holidays_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users (id)
        ON DELETE SET NULL
);

CREATE INDEX ix_holidays_range ON holidays (start_date, end_date);

-- Users pick their school from the catalog (replaces the free-text school column of the earlier spec)
ALTER TABLE users
    ADD COLUMN school_id uuid,
    ADD CONSTRAINT fk_users_school
        FOREIGN KEY (school_id)
        REFERENCES schools (id)
        ON DELETE RESTRICT;

CREATE INDEX ix_users_school_id ON users (school_id) WHERE school_id IS NOT NULL;

-- Seed data; Admins edit it on SCR-USER-09 and SCR-USER-10
INSERT INTO schools (code, name, short_name, name_search)
SELECT code, name, short_name, btrim(regexp_replace(lower(unaccent(name)), '\s+', ' ', 'g'))
FROM (VALUES
      ('HCMUT', 'Trường Đại học Bách khoa, ĐHQG-HCM', 'ĐH Bách khoa')
    , ('HCMUS', 'Trường Đại học Khoa học Tự nhiên, ĐHQG-HCM', 'ĐH KHTN')
    , ('UIT', 'Trường Đại học Công nghệ Thông tin, ĐHQG-HCM', 'ĐH CNTT')
    , ('HCMUTE', 'Trường Đại học Sư phạm Kỹ thuật TP.HCM', 'ĐH SPKT')
    , ('UEH', 'Trường Đại học Kinh tế TP.HCM', 'ĐH Kinh tế')
    , ('FPTU', 'Trường Đại học FPT', 'ĐH FPT')
    , ('HUST', 'Đại học Bách khoa Hà Nội', 'ĐHBK Hà Nội')
    , ('UET', 'Trường Đại học Công nghệ, ĐHQG Hà Nội', 'ĐH Công nghệ')
) AS seed (code, name, short_name);

-- Holidays of 2026: lunar ones are entered every year, fixed ones repeat
INSERT INTO holidays (name, start_date, end_date, kind, repeats_yearly) VALUES
      ('Tết Dương lịch', DATE '2026-01-01', DATE '2026-01-01', 'PUBLIC_HOLIDAY', true)
    , ('Tết Nguyên đán', DATE '2026-02-14', DATE '2026-02-22', 'PUBLIC_HOLIDAY', false)
    , ('Giỗ Tổ Hùng Vương', DATE '2026-04-26', DATE '2026-04-26', 'PUBLIC_HOLIDAY', false)
    , ('Nghỉ bù Giỗ Tổ Hùng Vương', DATE '2026-04-27', DATE '2026-04-27', 'COMPENSATORY', false)
    , ('Ngày Giải phóng miền Nam', DATE '2026-04-30', DATE '2026-04-30', 'PUBLIC_HOLIDAY', true)
    , ('Ngày Quốc tế Lao động', DATE '2026-05-01', DATE '2026-05-01', 'PUBLIC_HOLIDAY', true)
    , ('Quốc khánh', DATE '2026-09-01', DATE '2026-09-02', 'PUBLIC_HOLIDAY', true);
