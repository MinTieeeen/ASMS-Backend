# Project Structure

## 1. Layout: layer first, then module

The code is organized **by technical layer first** (`controller -> service -> repository -> entity / dto / mapper`), and **each layer is split into one sub-package per business module**. Cross-cutting infrastructure sits at the root.

```
src/main/java/com/asms/
├── AsmsBackendApplication.java
│
├── config/                 # Spring configuration: AppProperties (app.*), JPA auditing, OpenAPI, Clock
├── security/               # SecurityConfig, SecurityUtils; later JwtService, group permission checker
├── constant/               # ApiPaths and other shared constants
├── exception/              # ErrorCode, BusinessException (+ subclasses), GlobalExceptionHandler
├── util/                   # Stateless helpers: DateTimeUtils, RandomCodeGenerator
│
├── controller/
│   ├── auth/               AuthController
│   ├── group/              GroupController, GroupMemberController, JoinRequestController
│   └── task/ ...           TaskController, CommentController
├── service/
│   ├── auth/               AuthService, JwtTokenService
│   ├── group/              GroupService, GroupMemberService, GroupPermissionService
│   └── task/ ...
├── repository/
│   ├── group/              GroupRepository, GroupMemberRepository
│   └── task/ ...
├── entity/
│   ├── base/               BaseEntity, SoftDeletableEntity
│   ├── group/              Group, GroupMember, GroupRole (enum), GroupStatus (enum)
│   └── task/ ...
├── dto/
│   ├── common/             PageResponse
│   ├── group/              CreateGroupRequest, UpdateGroupRequest, GroupResponse, GroupSummaryResponse
│   └── task/ ...
├── mapper/
│   ├── group/              GroupMapper
│   └── task/ ...
│
├── event/<module>/         # (when needed) internal application events, e.g. MemberJoinedEvent
├── listener/<module>/      # (when needed) @TransactionalEventListener handlers
└── scheduler/              # (when needed) @Scheduled jobs, e.g. DeadlineReminderScheduler (UC13)

src/main/resources/
├── application.yml, application-{local,prod}.yml
├── db/migration/           # Flyway V{n}__{description}.sql
└── templates/email/        # Thymeleaf email templates

src/test/java/com/asms/     # Mirrors the main tree: service/group/GroupServiceTest.java ...
```

Empty module folders are kept with a `.gitkeep`; delete the `.gitkeep` once the folder has real code. Do not create extra layer folders (`event/`, `scheduler/`...) until they are needed.

## 2. Modules

The same module names are used in every layer:

| Module | Requirement | Scope |
| --- | --- | --- |
| `auth` | M01 | Register, login (email, Google), refresh token, password reset, email verification |
| `user` | M01 | Profile, personal settings |
| `group` | M02, M03 | Groups, subject/semester info, members, invites, join requests, charter |
| `phase` | M04 | Phases, closing a phase, retrospectives |
| `task` | M05 | Tasks, subtasks, checklist, labels, comments, attachments, dependencies |
| `milestone` | M06 | Submission milestones and recorded results |
| `deliverable` | M06 | Versioned deliverables per milestone |
| `review` | M08 | Peer review rounds and aggregated scores (BR11, BR12) |
| `notification` | M10 | In-app notifications, emails, deadline reminders (UC13) |
| `activity` | M11 | Append-only group activity log |
| `catalog` | M12 | Suggested schools, semesters, subjects, phase and review templates |
| `admin` | M12 | User management, group monitoring, reports, system config, admin audit log |

Adding a new module: create `<module>` sub-packages in the layers it needs and add it to this table.

## 3. Layer responsibilities

| Layer | Does | Must not |
| --- | --- | --- |
| `controller` | Map HTTP to Java: path, `@Valid` request DTO, call **one** service method, return DTO + status, OpenAPI annotations | Contain business logic, call repositories, return entities, catch exceptions to build error responses |
| `service` | Business logic, `@Transactional` boundaries, business rule (BRxx) and group permission checks, orchestration, publishing events | Know about HTTP (`HttpServletRequest`, `ResponseEntity`), return entities to controllers |
| `repository` | Spring Data interfaces, derived queries, `@Query` / `@EntityGraph` | Contain business rules |
| `entity` | JPA mapping, invariants of a single aggregate (small domain methods like `markDeleted`) | Depend on services, DTOs or Spring beans |
| `dto` | Immutable `record`s with Jakarta Validation annotations | Contain logic beyond trivial factory methods |
| `mapper` | MapStruct interfaces entity <-> DTO | Call repositories or services |

## 4. Dependency rules

- Direction is one way: `controller -> service -> repository -> entity`. `dto` and `mapper` are used by controller and service only.
- Root infrastructure packages (`config`, `security`, `exception`, `util`, `constant`, `entity/base`, `dto/common`) **never** import from module sub-packages.
- A service may call **services** of another module, never its repositories. Example: `service/task/TaskService` asks `service/group/GroupMemberService` whether a user is a member; it does not use `GroupMemberRepository`.
- Side effects in another module (activity log, notifications) are published as application events and handled by a listener, to avoid circular dependencies between services.
- No circular dependencies between modules. If two services need each other, extract the shared logic into a third service or use events.
