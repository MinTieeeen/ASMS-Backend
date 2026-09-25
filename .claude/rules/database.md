# Database Rules

## 1. Migrations (Flyway)

- The schema changes **only** through Flyway (`spring.jpa.hibernate.ddl-auto=validate`).
- File name: `V{n}__{snake_case_description}.sql` in `src/main/resources/db/migration`, e.g. `V2__create_users.sql`. Use the next free number.
- **Never edit a migration that has been merged**; write a new migration instead.
- One logical change per migration (a table with its indexes and constraints, or one alteration).
- Every migration starts with the header comment (see code-comments.md).

## 2. Naming

| Object | Convention | Example |
| --- | --- | --- |
| Table | `snake_case`, plural | `group_members` |
| Column | `snake_case` | `due_at`, `created_by` |
| Primary key | `id uuid DEFAULT gen_random_uuid()` | |
| Foreign key column | `{referenced_singular}_id` | `group_id` |
| Foreign key constraint | `fk_{table}_{column}` | `fk_tasks_phase_id` |
| Unique constraint | `uk_{table}_{columns}` | `uk_group_members_group_id_user_id` |
| Index | `idx_{table}_{columns}` | `idx_tasks_assignee_id_due_at` |
| Check constraint | `ck_{table}_{rule}` | `ck_phases_end_after_start` |

## 3. Standard columns

- Every table: `created_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL` (filled by JPA auditing).
- Soft-deletable tables (BR08): `deleted_at timestamptz NULL`; the entity extends `SoftDeletableEntity` and declares `@SQLRestriction("deleted_at IS NULL")`.
- Append-only tables (`activity_logs`, `admin_audit_logs`) are never updated or deleted by application code.
- All timestamps are `timestamptz` stored in UTC (BR13).

## 4. Types

- Enumerations: `varchar` + `CHECK (column IN (...))` instead of native PostgreSQL enums (easier to extend), mapped with `EnumType.STRING`.
- Flexible structured data: `jsonb` (`criteria`, `summary_snapshot`, `metadata`).
- Scores and percentages: `numeric(p,s)`, never floating point.

## 5. Indexes and performance

- Create the indexes from requirement section 7.4 in the same migration that creates the table.
- Index every foreign key used in filters or joins.
- Full-text search uses `tsvector` + `unaccent` (enabled in `V1__init_extensions.sql`).
- Check new queries for N+1 problems and missing indexes before merging.
