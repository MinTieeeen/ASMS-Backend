# API Conventions

## 1. Resources and paths

- Prefix `/api/v1`, declared through `ApiPaths` constants - no hard-coded paths in `@RequestMapping`.
- Plural nouns in kebab-case, nested under the owning resource when scoped: `/api/v1/groups/{groupId}/join-requests`.
- Path variables are UUIDs named after the resource: `{groupId}`, `{taskId}`.
- Non-CRUD actions are sub-resources with `POST`: `POST /tasks/{taskId}/approve`, `POST /phases/{phaseId}/close`.

## 2. HTTP methods and status codes

| Operation | Method | Success status |
| --- | --- | --- |
| Read one / list | `GET` | `200` |
| Create | `POST` | `201` + created resource |
| Full / partial update | `PUT` / `PATCH` | `200` + updated resource |
| Delete | `DELETE` | `204` |
| Action | `POST` | `200` (with body) or `204` |

Error statuses: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` business conflict (e.g. `GROUP_FULL`), `429` rate limited, `500` unexpected.

## 3. Requests and responses

- Request and response bodies are DTO `record`s; entities are never exposed.
- Validate input with Jakarta Validation (`@NotBlank`, `@Size`, `@Email`...) and `@Valid` in the controller. Cross-field and business validation happens in the service.
- JSON field names are `camelCase`.
- Time: `Instant` (ISO 8601, UTC) for points in time, `LocalDate` for date-only values (BR13). Never `LocalDateTime` in the API.
- Pagination: `page`, `size`, `sort` query parameters bound to `Pageable`; return `PageResponse<T>`. Enforce a maximum page size.
- Filtering: bind query parameters to an `XxxFilter` record.

## 4. Errors

- Every error is an RFC 9457 `ProblemDetail` produced by `GlobalExceptionHandler`, with:
  - `code`: an `ErrorCode` name (`GROUP_FULL`, `VALIDATION_FAILED`...) - the frontend translates by this code;
  - `errors`: list of `{ field, message }` for validation errors.
- Services throw `BusinessException(ErrorCode.X[, detail])` or a subclass (`ResourceNotFoundException`, `ForbiddenException`). Controllers never build error responses.
- New error codes are added to `ErrorCode`, grouped by module, referencing the BR code when relevant.

## 5. OpenAPI (contract with the frontend)

- Every controller has `@Tag(name = "...")`; every endpoint has `@Operation(summary = "...")` and documents non-obvious error responses with `@ApiResponse`.
- Keep method names stable: the frontend generates hook names from `operationId`.
- Any change to paths, DTOs or status codes is a contract change: mention it in the PR so the frontend runs `npm run api:gen`. Breaking changes need coordination (or a new `/api/v2` route).
