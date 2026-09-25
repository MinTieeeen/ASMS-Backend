-- Shared extensions. gen_random_uuid() is built in since PostgreSQL 13, so pgcrypto is not needed.
-- Author: MinhTien | Created: 2026-09-26
CREATE EXTENSION IF NOT EXISTS unaccent; -- Accent-insensitive Vietnamese search (section 9.4)
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- Fuzzy search
