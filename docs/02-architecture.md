# Business Platform — Step 2: Architecture

Status: **Proposed, 2026-10-27.** Working name "Business Platform" (no "We-" names). Follows the
owner's master prompt §12. Inputs: `01-discovery.md`. Constraint from the owner: **WeGroupware is not
modified**; existing apps are integrated later only through their current APIs.

---

## 1. Existing application inventory

See `01-discovery.md` §2. Summary: WeGroupware (HR, My Profile, Approval, Board, Calendar, System)
and VirtualBiz (marketplace, seller POS / catalogue / orders / inventory, provider bookings).

## 2. Reuse / build / retire matrix (platform view)

| Capability | Platform decision | Existing system role |
|---|---|---|
| Identity & access (accounts, sessions, MFA, recovery) | **Build** in the platform (one account system) | WeGroupware sign-in = design reference; federation later (its RS256 tokens can be accepted with its public key, no change to it) |
| Organisations, branches, memberships, roles | **Build** | WeGroupware tenant → company → department model = reference |
| Module registry & enablement | **Build** (table-driven, not hard-coded) | — |
| Projects & tasks | **Build** (MVP) | Board TASK posts = reference only |
| Calendar & scheduling | **Build** platform calendar (events + deadlines) | WeGroupware Calendar shown later through its API |
| Approval & workflow engine | **Build** a generic engine (any module, any record, request changes) | WeGroupware Approval keeps serving HR; reference for features |
| Documents | **Build** (after MVP) | — |
| Notifications | **Build** (in-app + email; push for mobile) | Both systems = reference |
| Reporting kit | **Build** shared table / filter / chart / export | — |
| Audit / activity | **Build** (one activity log, per record) | WeGroupware `audit_log` = reference |
| Retail & Commerce module | **Integrate** VirtualBiz through its API (later) | VirtualBiz unchanged |
| HR & Workforce module | **Integrate** WeGroupware HR through its current API (later), build missing parts (recruitment, payroll …) in the platform | WeGroupware unchanged |
| Service & Operations | **Integrate** VirtualBiz bookings (later); **build** tickets, work orders, assets | |
| General Business, Education | **Build** (after MVP) | |
| Retire | Nothing in existing systems (they are not touched) | |

## 3. Shared core vs industry modules

```
┌──────────────────────────── Shared core (platform-owned) ───────────────────────────┐
│ identity · organisation (orgs, branches, members, teams, roles, permissions)         │
│ module registry · project & task · workflow (approvals) · calendar · notification   │
│ activity & audit · file storage · document (later) · reporting kit                  │
└──────────────────────────────────────────────────────────────────────────────────────┘
   ▲ services, not business rules                ▲ events (TaskCompleted, ApprovalDecided …)
┌──────────┬──────────┬────────────────┬─────────────────┬────────────────────┐
│ General  │Education │ Retail &       │ HR & Workforce  │ Service &          │
│ Business │          │ Commerce       │                 │ Operations         │
│ (build)  │ (build)  │ (VirtualBiz    │ (WeGroupware HR │ (VirtualBiz        │
│          │          │  adapter+build)│  adapter+build) │  bookings+build)   │
└──────────┴──────────┴────────────────┴─────────────────┴────────────────────┘
```

Rules: a module may depend on the core, never on another module; modules talk through core events
and record references (`module`, `recordType`, `recordId`). Each module owns its tables (no generic
"object" table).

## 4. Sitemap (web)

```
/sign-in · /sign-up · /forgot-password · /invite/[token]
/[org]/overview                    Overview dashboard
/[org]/my-tasks                    My Tasks
/[org]/projects                    Project list ─ /[org]/projects/[key] (board · list · timeline later · members · settings)
/[org]/projects/[key]/tasks/[n]    Task detail (drawer on desktop, page on phone)
/[org]/calendar                    Calendar (month / week / agenda)
/[org]/teams                       Teams & members
/[org]/approvals                   Inbox · Sent · All (admins)
/[org]/documents                   (after MVP)
/[org]/reports                     (after MVP)
/[org]/m/business/...              General Business module
/[org]/m/education/...             Education module
/[org]/m/retail/...                Retail & Commerce module
/[org]/m/hr/...                    HR & Workforce module
/[org]/m/service/...               Service & Operations module
/[org]/admin/organisation · members · roles · modules · workflows · audit
/account (profile, security, notifications, appearance, language)
/admin                             Platform console (super admins): overview · organisations · users · audit log
```

`[org]` is the organisation's slug; switching organisation changes the URL, so links are shareable.

## 5. Roles and permissions

Three layers, all checked on the server, plus the people who run the platform:

**Super admin** (platform, not organisation): a flag on the account. The first ones are the emails in
`PLATFORM_SUPER_ADMINS`; super admins then grant and revoke it in the console (never their own). In `/admin`
they see every organisation (members, owners, modules, last activity) and every account, and they suspend or
reactivate an organisation, restore one its owner deleted, switch its modules, disable or enable an account,
sign it out everywhere and send it a password reset; every action goes to the platform audit log. Being a super
admin is **not** membership: an organisation's own data (tasks, documents, invoices …) stays closed to them. A
suspended organisation answers `ORG_SUSPENDED` to its members and disappears from single sign-on and shop
links; a disabled account answers `ACCOUNT_DISABLED` on sign-in and its sessions end at once.

| Layer | Roles | Grants |
|---|---|---|
| Organisation | **Owner** (one or more), **Admin**, **Member**, **Guest** (outside collaborator) | Owner/Admin: members, roles, modules, workflows, audit. Member: everything their module and project roles allow. Guest: only projects they are added to |
| Module | per module, e.g. Projects: **Manager**, **Contributor**, **Viewer**; HR: HR officer, Payroll officer (sensitive), Manager, Employee | Module features |
| Project | **Lead**, **Member**, **Viewer** | Project data |

Permission codes are strings (`projects.project.create`, `projects.task.update`, `approvals.decide`,
`org.members.manage` …) resolved per request from the three layers; the UI hides what the server would
refuse, never the other way round.

| Action | Owner | Admin | Member | Guest |
|---|---|---|---|---|
| Invite / remove members, change roles | ✅ | ✅ (not owners) | — | — |
| Enable / disable modules | ✅ | ✅ | — | — |
| Create project | ✅ | ✅ | ✅ (if Projects Manager or Contributor) | — |
| Manage project members / settings | ✅ | ✅ | Project Lead | — |
| Create / edit tasks | ✅ | ✅ | Project Lead, Member | Project Member |
| View project | ✅ | ✅ | project members (+ org-visible projects) | project members |
| Decide approval | the step's approver(s) | the step's approver(s) | the step's approver(s) | — |
| Configure workflows | ✅ | ✅ | — | — |
| View audit log | ✅ | ✅ | — | — |

### General Business permissions (built)

`business.view` for Owners, Admins and Members with the module on; `business.manage` for Owners and Admins.
Guests get nothing. The API enforces every row (`com.businessplatform.business.service.BusinessAccess`).

| Action | Owner / Admin | Member |
|---|---|---|
| See customers, products, quotes, invoices, the business home | ✅ | ✅ |
| Add / edit customers | ✅ | ✅ |
| Archive / restore customers; add / edit products and prices | ✅ | — |
| Create quotes and invoices; edit, delete, send, accept / decline, convert, issue, record payments | anyone's | their own |
| Void an issued invoice (with a reason, no payments on it); remove a payment | ✅ | — |
| Record expenses and submit them for approval | ✅ (own) | ✅ (own) |
| See expenses | everyone's | their own |
| Decide expenses | the workflow's approvers (default "Expense approval": an Admin) | — |
| Mark an approved expense paid; expense categories; business settings | ✅ | — |
| Invoice due dates on the calendar | ✅ | — |

Money is USD `NUMERIC(12,2)`, computed only by the API (`Totals`: each step rounds half-up to cents; VAT 10%
once on the document's taxable sum). Quotes are numbered Q-0001 at creation, invoices INV-0001 when issued, from
a per-organisation counter taken with one atomic upsert (`biz_sequence`), so numbers never repeat. Screens show
riel beside USD at 1 USD = 4,100 KHR, rounded to 100 (`apps/web/src/lib/money.ts`).

### Service & Operations permissions (built)

Module roles live in the module (`svc_staff`): **Dispatcher** or **Technician**, added by Admins. Owners and Admins get
`service.view`, `service.manage`, `service.admin`; dispatchers `service.view` + `service.manage`; technicians
`service.view` + `service.technician` (through the core's `PermissionSource` hook). Members without a role and guests
get nothing (FORBIDDEN). The API enforces every row (`com.businessplatform.ops.service.OpsAccess`; the package is `ops`
because `..service..` would match every module's service layer in the architecture tests).

| Action | Owner / Admin | Dispatcher | Technician |
|---|---|---|---|
| Dashboard | everyone's | everyone's | their own tickets and work orders |
| Customers, sites, assets, maintenance plans, reports | ✅ | ✅ | — |
| Take requests, triage, edit, assign, close and reopen tickets | ✅ | ✅ | — |
| See, comment on, add files to, and move tickets (in progress, waiting, resolved) | ✅ | ✅ | the ones assigned to them |
| Create, edit, schedule and cancel work orders | ✅ | ✅ | — |
| Start, hold, tick the checklist, record parts / labour, complete with sign-off | ✅ | ✅ | their own work orders only |
| Decide work order approvals (default "Work order approval": an Admin) | the workflow's approvers | — | — |
| Service staff, SLA targets, approval threshold, labour rate | ✅ | — | — |

Tickets are TCK-0001 and work orders WO-0001, from a per-organisation counter taken with one atomic upsert
(`svc_sequence`). SLA due and warn (75%) times are set from the priority's policy; breached / at risk are derived on
the server. Work order totals are computed only by the API (each line half-up to cents); a draft whose total is above
the threshold is scheduled only after approval. Maintenance plans generate their next work order `leadDays` before it
is due, hourly (`platform.service.maintenance-cron`) or with "Generate now", at most once per plan and due date.
Bookings stay in VirtualBiz: the Bookings page links to the organisation's shops through Retail & Commerce.

**Retail purchasing** (`retail` package, `retail_*` tables): suppliers (payment terms), purchase orders (draft →
ordered → partly received / received, or cancelled; numbered PO-0001 per organisation), deliveries and supplier
payments. Received quantities never exceed what was ordered and payments never exceed the order total. Stock stays in
VirtualBiz (one stock count): a delivery links to VirtualBiz inventory through SSO and is ticked "In stock" once it is
recorded there. VirtualBiz is not modified; automatic stock sync would need a VirtualBiz change and is not built.

## 6. Core user journeys (MVP first)

1. **Sign up → create organisation** (name, slug, branch optional) → lands on Overview with the Projects module on.
2. **Invite members** by email → they accept → appear in Teams.
3. **Manager creates a project**, adds members (Lead / Member / Viewer), creates and assigns tasks with priority, due date, subtasks.
4. **Member updates a task** (status by drag on the board or the status menu), comments with @mentions, attaches files; everything lands in the task's activity history and notifies the right people.
5. **Member requests approval** on a task (e.g. budget sign-off) using a workflow; the **approver approves, rejects or requests changes**; the task's history and the requester's notifications update; requested changes reopen the request for resubmission.
6. **Deadlines** of my tasks and projects appear on the **Calendar** and **Overview**.

Later journeys (master prompt §11): HR attendance & leave, student registration, teacher attendance & results, POS sale with stock update, work order to technician — each in its module phase.

## 7. Entity relationships (core + MVP)

```
user ─< membership >─ organisation ─< branch
  │            │            ├─< team ─< team_member >─ membership
  │            └─ role      ├─< org_module (module_key, enabled)
  │                         ├─< project ─< project_member >─ membership
  │                         │      ├─< task ─< task (subtask, parent_id)
  │                         │      │     ├─< task_comment, task_attachment ─ file
  │                         │      │     └─< task_watcher
  │                         │      └─< project_milestone (later)
  │                         ├─< workflow_definition ─< workflow_step_def
  │                         ├─< approval_request (module, record_type, record_id) ─< approval_step ─< approval_action
  │                         ├─< calendar_event ─< calendar_attendee
  │                         ├─< activity (module, record_type, record_id, verb, data)
  │                         └─< notification ─ user
  └─< session (refresh tokens), user_preference
```

## 8. Database table inventory (MVP)

| Area | Tables |
|---|---|
| Identity | `app_user` (email, password hash, name, locale, avatar), `user_session` (refresh token hash, device, expiry), `password_reset`, `user_preference` |
| Organisation | `organisation` (slug, name, plan), `branch`, `membership` (user, org, role, status ACTIVE / INVITED / SUSPENDED), `invitation` (token hash, email, role, expiry), `team`, `team_member` |
| Modules | `module` (key, name, status — registry), `org_module` (org, module, enabled, enabled_by, at) |
| Projects | `project` (org, key, name, description, status, visibility ORG / PRIVATE, lead, start, due), `project_member` (role), `task` (project, number, title, description, status, priority, assignee, reporter, due_date, start_date, parent_id, position, completed_at), `task_comment`, `task_attachment`, `task_watcher` |
| Workflow | `workflow_definition` (org, module, name, active), `workflow_step_def` (order, approver type USER / ROLE / PROJECT_LEAD, approver ref), `approval_request` (org, module, record_type, record_id, title, requester, status PENDING / CHANGES_REQUESTED / APPROVED / REJECTED / WITHDRAWN, current_step), `approval_step` (request, order, approver, status), `approval_action` (request, actor, action, comment, at) |
| Calendar | `calendar_event` (org, title, start, end, all_day, owner, project?), `calendar_attendee` |
| Cross-cutting | `activity` (org, module, record_type, record_id, actor, verb, data JSONB, at), `notification` (user, org, type, payload, read_at), `stored_file` (org, owner, name, type, size, storage key) |

Every org-scoped table has `org_id` NOT NULL with an index; ids are UUID; timestamps `TIMESTAMPTZ`.

## 9. API endpoint inventory (MVP, REST + OpenAPI at `/api/docs`)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/sign-up`, `/sign-in`, `/refresh`, `/sign-out`, `/forgot-password`, `/reset-password`; `GET /api/me` |
| Orgs | `GET/POST /api/orgs`, `GET/PATCH /api/orgs/{org}`, `GET /api/orgs/{org}/context` (my roles, permissions, enabled modules, nav) |
| Members | `GET /api/orgs/{org}/members`, `POST /api/orgs/{org}/invitations`, `POST /api/invitations/{token}/accept`, `PATCH /api/orgs/{org}/members/{id}` (role, status) |
| Teams | `GET/POST /api/orgs/{org}/teams`, `PUT /api/orgs/{org}/teams/{id}/members` |
| Modules | `GET /api/modules`, `PUT /api/orgs/{org}/modules/{key}` (enable / disable) |
| Projects | `GET/POST /api/orgs/{org}/projects`, `GET/PATCH/DELETE /api/orgs/{org}/projects/{key}`, `GET/PUT /api/orgs/{org}/projects/{key}/members` |
| Tasks | `GET/POST /api/orgs/{org}/projects/{key}/tasks`, `GET/PATCH/DELETE /api/orgs/{org}/tasks/{id}`, `POST /api/orgs/{org}/tasks/{id}/move` (status + position), `GET/POST .../comments`, `POST .../attachments`, `GET /api/orgs/{org}/my-tasks` |
| Workflow | `GET/POST /api/orgs/{org}/workflows`, `POST /api/orgs/{org}/approvals` (module, recordType, recordId, workflowId), `GET /api/orgs/{org}/approvals?box=inbox|sent|all`, `POST /api/orgs/{org}/approvals/{id}/{approve|reject|request-changes|resubmit|withdraw}` |
| Calendar | `GET /api/orgs/{org}/calendar?from&to` (events + task deadlines), `POST/PATCH/DELETE /api/orgs/{org}/events/{id}` |
| Activity, notifications | `GET /api/orgs/{org}/activity?module&recordType&recordId`, `GET /api/notifications`, `POST /api/notifications/read` |
| Overview | `GET /api/orgs/{org}/overview` (my tasks, deadlines, project progress, pending approvals, recent activity, events) |
| Files | `POST /api/orgs/{org}/files`, `GET /api/files/{id}` |
| HR attendance | `GET/POST /api/orgs/{org}/hr/schedules`, `PUT/DELETE .../schedules/{id}`, `PUT .../hr/people/{userId}/schedule`, `GET .../hr/attendance/today`, `POST .../attendance/{clock-in|clock-out}`, `GET .../attendance/timesheet?userId&month` (self, manager, HR), `GET .../attendance/board?date` (HR), `PUT .../attendance/{userId}/{date}` (HR correction with a reason), `GET .../overtime/mine`, `GET .../overtime?status` (HR), `POST .../overtime`, `POST .../overtime/{id}/withdraw` (overtime is decided through the approval engine, like leave) |
| HR payroll (HR managers; payslips also to their person once paid) | `GET/PUT /api/orgs/{org}/hr/payroll/settings` (Tax on Salary brackets, dependant allowance, NSSF, overtime rate), `GET .../payroll/people`, `PUT .../payroll/people/{userId}` (salary USD, dependants, NSSF, bank), `GET/POST .../payroll/runs`, `GET/PUT/DELETE .../payroll/runs/{id}`, `POST .../payroll/runs/{id}/{recalculate|approve|reopen|pay}`, `GET .../payroll/runs/{id}/export.csv`, `PUT .../payroll/payslips/{id}/adjustments`, `GET .../payroll/payslips/mine`, `GET .../payroll/payslips/{id}` |
| HR recruitment (HR; a job's hiring manager; interviewers for their candidates) | `GET/POST /api/orgs/{org}/hr/recruitment/jobs`, `GET/PUT .../jobs/{id}`, `GET/POST .../jobs/{id}/candidates`, `GET/PUT/DELETE .../candidates/{id}`, `POST .../candidates/{id}/{stage|hire|interviews}`, `POST/GET .../candidates/{id}/cv`, `DELETE .../interviews/{id}`, `PUT .../interviews/{id}/feedback` (others' feedback shown after giving your own), `GET .../interviews/mine`, `GET .../hires`, `POST .../candidates/{id}/onboarding`, `PUT/DELETE .../onboarding/{id}`, `GET/PUT .../onboarding-template` |
| HR reviews and training | `GET/POST /api/orgs/{org}/hr/review-cycles`, `GET/PUT .../review-cycles/{id}`, `POST .../review-cycles/{id}/{launch|close}` (HR), `GET .../reviews/{mine|to-review}`, `GET .../reviews/{id}`, `PUT .../reviews/{id}/{self|review|reviewer}`, `POST .../reviews/{id}/acknowledge`; `GET/POST .../training/courses`, `GET/PUT .../training/courses/{id}`, `POST .../training/courses/{id}/{enrol|enrolments}`, `PUT .../training/enrolments/{id}`, `GET .../training/mine` |
| HR insights | `GET /api/orgs/{org}/hr/dashboard` (HR), `GET .../hr/org-chart`, `GET .../hr/reports/people.csv`, `GET .../hr/reports/attendance.csv?month`, `GET .../hr/reports/leave.csv?year` (HR; UTF-8 with BOM, formula-like cells neutralised) |
| Business CRM and finance | `GET/POST /api/orgs/{org}/business/deals` (pipeline with totals, weighted forecast, win rate), `GET/PUT/DELETE .../deals/{id}`, `POST .../deals/{id}/{stage|activities|convert|quote}`; `GET .../business/reports?year` and `GET .../business/reports/{months|aging|customers|expenses}.csv?year` (Owners and Admins) |
| Platform (super admins; NOT_FOUND for others) | `GET /api/platform/stats`, `GET /api/platform/orgs?q&status`, `GET /api/platform/orgs/{id}`, `POST /api/platform/orgs/{id}/{suspend|reactivate|restore}`, `PUT /api/platform/orgs/{id}/modules/{key}`, `GET /api/platform/users?q&status`, `GET /api/platform/users/{id}`, `POST /api/platform/users/{id}/{disable|enable|sign-out|password-reset}`, `PUT /api/platform/users/{id}/super-admin`, `GET /api/platform/audit?target` |

Envelope `{status, data, error}`; errors as codes (`NOT_FOUND`, `FORBIDDEN`, `VALIDATION_FAILED` with field details); pagination `page`, `size`.

## 10. Integration architecture

```
apps/web (Next.js) ──/api/**──► apps/api (Spring Boot, Java 21) ──► PostgreSQL (business_platform)
apps/mobile (Expo)  ──/api/**──┘          │
                                         ├─ integrations/virtualbiz  (later: Retail & Commerce read/write through VirtualBiz REST, unchanged)
                                         └─ integrations/wegroupware (later: HR read through WeGroupware REST with the user's own token, unchanged)
```

- The platform API is a modular monolith: packages `identity`, `org`, `module`, `project`, `workflow`, `calendar`, `notification`, `activity`, `file`, and later one package per industry module. ArchUnit-style tests keep modules from depending on each other.
- Integrations sit behind adapter interfaces; until an adapter exists, its module shows "not connected" — never fake data.
- Domain events (in-process, after commit) drive activity, notifications and approval effects; an outbox table can make them durable later.

## 11. Web and mobile routing

- **Web**: Next.js App Router, routes as in §4; `(auth)` group for sign-in pages, `[org]` group with the app shell (sidebar + header), `account` outside orgs. Server-side data via TanStack Query on the client with typed API functions generated from OpenAPI.
- **Mobile (Expo Router)**: `(auth)/sign-in`; `(app)/(tabs)/{overview, my-tasks, approvals, calendar, more}`; stack screens `project/[key]`, `task/[id]`, `notifications`, `org-switcher`. Same API, same permission codes; phone-first patterns (bottom tabs, sheets, swipe actions), not shrunken desktop screens.

## 12. Design system and component inventory

Tailwind + shadcn/ui (Radix) + Lucide; tokens as CSS variables (light and dark). Components: AppShell (sidebar expanded / collapsed, header, org switcher, module switcher, search, notifications, user menu, breadcrumbs), DataTable (sort, filter, pagination, row actions, empty / loading / error rows), FilterBar, Form fields (RHF + Zod), Dialog, Drawer / Sheet, ConfirmDialog, StatusBadge, PriorityBadge, Avatar / AvatarStack, Tabs, Pagination, DatePicker, KanbanBoard, TaskCard, ActivityTimeline, CommentBox (mentions), FileDrop, EmptyState, Skeletons, PermissionDenied, ErrorState, Toast, StatCard, Chart wrapper. Details in `03-design-system.md` (step 3).

## 13. Implementation roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| 0 | Repo, stack, CI, OpenAPI, database, design tokens | Builds, lint, tests green |
| 1 — MVP | Identity, organisations, members, roles, module registry, app shell, Overview, Projects & Tasks (board, list, detail, comments, attachments, activity), My Tasks, Calendar (events + deadlines), Approvals (generic engine, request changes), notifications in-app | Journeys 1–6 pass end to end on desktop and phone browsers; cross-organisation isolation tests |
| 2 | Mobile app (Expo): sign-in, org switch, My Tasks, task updates, comments, approvals, calendar, notifications | Same journeys on the phone app |
| 3 | Documents, reporting kit, email + push notifications | |
| 4 | Retail & Commerce via VirtualBiz adapter; Service & Operations (bookings via VirtualBiz, tickets / work orders / assets built) | |
| 5 | HR & Workforce (WeGroupware HR read via its API; recruitment, payroll, reviews built), General Business (CRM, finance, invoices), Education | One module at a time |

## 14. Testing and migration strategy

- **Backend**: unit tests for rules (permissions, workflow transitions); integration tests on a real PostgreSQL (`business_platform_test`, Flyway-migrated) for every endpoint; **isolation tests** proving one organisation never reads another's data; workflow tests for every transition.
- **Frontend**: Vitest for logic and components; Playwright for the journeys on desktop and phone viewports; accessibility checks (axe) on main screens.
- **Migrations**: Flyway, timestamped files, never edited after merge; the same migrations run in every environment (no hand-applied SQL).
- **Existing systems**: nothing migrated out of them in the MVP. Later adapters read through their APIs; any data copy (e.g. HR employees into the platform) will be a separate, reviewed migration plan with reconciliation reports.
