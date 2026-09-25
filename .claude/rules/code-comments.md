# Code Comments and File Headers

## 1. Language

All comments, Javadoc, identifiers and log messages are written in **English**. User-facing text is the only exception (e.g. `ErrorCode` fallback messages and email templates, which may be Vietnamese).

## 2. Standard file header

Every hand-written Java type (class, interface, enum, record, annotation) has a Javadoc header **directly above the top-level type declaration** (after the imports):

```java
/**
 * One-sentence summary of what this type is responsible for.
 *
 * <p>Optional extra paragraphs: important rules (BRxx, UCxx), usage notes, caveats.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Service
@RequiredArgsConstructor
public class GroupService { ... }
```

| Tag | Meaning | Rule |
| --- | --- | --- |
| summary | What the type does | First sentence, ends with a period, describes responsibility - not implementation |
| `@author` | Developer who created the file | Value of `git config user.name` (team display name). One tag per person; the original author stays first. Add another `@author` line only when someone rewrites a significant part of the file |
| `@version` | Version of this file | Semantic versioning, starts at `1.0.0` (see section 3) |
| `@since` | Creation date | `yyyy-MM-dd`, **never changes** after creation |
| `@modified` | Date of the last meaningful change | `yyyy-MM-dd`, updated together with `@version` |

Other header forms:

- **Flyway SQL migrations** - line comments at the top:
  ```sql
  -- Purpose: create groups and group_members tables (F03.01)
  -- Author: MinhTien | Created: 2026-09-26
  ```
- **YAML / XML config** - a one-line `#` / `<!-- -->` comment describing the file's purpose is enough; no version tags.

Exempt from the header: generated code (MapStruct output, `target/`), `package-info.java`, test classes (header optional; recommended for shared test infrastructure such as `TestcontainersConfiguration`).

> `@modified` is a custom tag. If Javadoc HTML generation is added later, register it in `maven-javadoc-plugin` (`<tags><tag><name>modified</name><placement>a</placement><head>Modified:</head></tag></tags>`).

## 3. Version and date update rules

Update `@version` and `@modified` in the **same commit** as the change:

| Change | Bump | Example |
| --- | --- | --- |
| Breaking change to the type's public contract (renamed/removed public method, changed signature or semantics) | MAJOR | `1.4.2 -> 2.0.0` |
| New behaviour that stays backward compatible (new public method, new endpoint, new rule check) | MINOR | `1.4.2 -> 1.5.0` |
| Bug fix, refactor or performance change without contract change | PATCH | `1.4.2 -> 1.4.3` |
| Formatting only, import reordering, comment typo | none | keep version and date |

- `@since` never changes. Do not keep a change log inside the file - Git history is the change log.
- When **Claude or another AI assistant** writes code, the author is the developer who requested the change (from `git config user.name`), never "Claude". New files get today's date for both `@since` and `@modified`.

## 4. Javadoc for members

- Public methods of services, utilities and anything reused across modules get Javadoc when the name alone does not explain the behaviour, parameters, thrown `BusinessException`s or the business rule enforced.
- Do not write Javadoc that only repeats the method name (`/** Gets the id. */`).
- Reference requirement codes: `/** Transfers leadership to another member (UC05, BR01). */`.
- Use `{@code ...}` for code, `{@link ...}` for types, `<p>` to start new paragraphs.

## 5. Inline comments

- Explain **why**, not what. If a comment explains what the code does, rename or extract a method instead.
- Put the comment on the line above the code it describes; keep it short.
- Task markers must reference a feature code and be actionable:
  - `// TODO(F05.10): support task dependencies`
  - `// FIXME(BR15): reminder may be sent twice when two instances run`
- Never commit commented-out code, `System.out.println`, or personal notes.
- Keep comments true: when code changes, update or delete the related comments.
