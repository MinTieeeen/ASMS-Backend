# Workflow, Commits and Definition of Done

## 1. Working process for a feature

1. Read the related use case, business rules, permission matrix and tables in the specification. If anything is unclear or contradictory, **ask** before coding.
2. Implement bottom-up:
   1. Flyway migration
   2. `entity/<module>`
   3. `repository/<module>`
   4. `dto/<module>` + `mapper/<module>`
   5. `service/<module>` + unit tests
   6. `controller/<module>` + web tests
3. Add dependencies only when the feature needs them and they are listed in requirement section 9.3; otherwise justify the new library in the PR.
4. Keep changes focused: no unrelated refactors or reformatting in a feature commit.

## 2. Branches

`main` is always releasable. Use short-lived branches: `feature/<code>-<short-name>` (`feature/F03.01-create-group`), `fix/<short-name>`, `chore/<short-name>`.

## 3. Commit messages (Conventional Commits, English)

```
<type>(<module>): <imperative summary> [(Fxx.yy|UCxx|BRxx)]

<optional body: why the change was needed>
```

- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`, `ci`.
- Scope: the business module (`group`, `task`...) or `config` / `security` / `exception`.
- Examples:
  - `feat(group): create group and generate join code (F03.01)`
  - `fix(task): prevent non-assignee from moving task to review (BR07)`
  - `test(review): cover anonymity threshold (BR12)`

## 4. Definition of done

- [ ] Behaviour matches the specification (UC flows, BR checks, permission matrix)
- [ ] File headers added / updated (`@version`, `@modified`) per code-comments.md
- [ ] Unit and web tests written and passing; BR and permission paths covered
- [ ] `./mvnw spotless:apply` run; `./mvnw verify` passes
- [ ] OpenAPI annotations present; API contract changes communicated to the frontend
- [ ] No `System.out`, commented-out code, secrets, or TODOs without a feature code

## 5. Checklist for a new endpoint

- [ ] Path built from `ApiPaths`, REST conventions respected
- [ ] Request DTO validated; response is a DTO, never an entity
- [ ] System role **and** group role checked
- [ ] Related business rules enforced in the service with proper `ErrorCode`s
- [ ] `@Operation` summary and documented error responses
- [ ] Service unit tests + MockMvc tests for success, validation error and forbidden cases
