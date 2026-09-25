# Security and Authorization

## 1. Authentication (NFR05, NFR06)

- Stateless: short-lived JWT access token (15 min) in the `Authorization: Bearer` header; refresh token in an `httpOnly`, `Secure`, `SameSite` cookie handled by the `auth` module.
- JWT claims: `sub` = user id (UUID), `roles` = system roles (`["ADMIN"]` -> authority `ROLE_ADMIN`).
- Passwords are hashed with Argon2 (`PasswordEncoder` bean); minimum 8 characters with letters and digits.
- Get the current user with `SecurityUtils.getCurrentUserId()`; never trust a user id sent in the request body.

## 2. Two independent authorization layers (NFR07)

1. **System role** (`user`, `admin`) - checked by `SecurityConfig` URL rules and `@PreAuthorize("hasRole('ADMIN')")`.
2. **Group role** (`leader`, `deputy`, `member`) - checked in the **service layer** (or `@PreAuthorize("@groupPermissionService.canManageTasks(#groupId)")` delegating to one shared service), following the permission matrix in requirement section 3.2.

- An Admin has **no** implicit group permissions.
- Every endpoint checks both layers server-side, even when the UI hides the action.
- Archived groups are read-only (BR09): reject writes with `GROUP_ARCHIVED`.

## 3. Privacy rules

- **BR11 / NFR10:** `peer_reviews` rows are read only by the score aggregation service. No endpoint - including admin endpoints - returns who reviewed whom. Comments are shown only when the reviewee has at least 2 reviews (BR12).
- Admin access to group content happens only while handling a report and is always written to `admin_audit_logs`.
- Never log passwords, tokens, reset links or peer review content.

## 4. Input and files (NFR08, NFR09)

- Sanitize user-provided Markdown/HTML (OWASP Java HTML Sanitizer) before storing it.
- Uploads: max 25 MB per file and 1 GB per group (BR14); detect the file type from content (Apache Tika), not the extension; store in private object storage and serve through presigned URLs valid for 15 minutes.
- Rate-limit login, registration and invitation endpoints (Bucket4j + Redis).
- Use parameter binding only (JPA / Spring Data); never concatenate SQL.

## 5. Secrets

- No secret is committed. Local defaults live only in `application-local.yml` and are obviously fake.
- Production reads secrets from environment variables (`APP_JWT_SECRET`, `DB_PASSWORD`, `MAIL_PASSWORD`...). The JWT secret must be at least 32 characters.
