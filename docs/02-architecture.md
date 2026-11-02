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
│ identity · organisation (orgs, structure, positions, members, teams, roles, perms)  │
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
/sign-in · /sign-up · /forgot-password · /verify-email · /invite/[token]
/[org]/overview                    Overview dashboard
/[org]/my-tasks                    My Tasks
/[org]/projects                    Project list ─ /[org]/projects/[key] (board · list · timeline later · members · settings)
/[org]/projects/[key]/tasks/[n]    Task detail (drawer on desktop, page on phone)
/[org]/calendar                    Calendar (month / week / agenda)
/[org]/structure                   Organisation structure (units, positions, who sits where)
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

### People import (built)

Admin › Members & roles › Import people takes a spreadsheet saved as CSV (parsed in the browser): one row per person
with email, name, role, unit path ("Mobile Center > Mobile") and unit types, position, manager and 2nd approver emails,
employment type, start date and unit head. `POST /people-import/preview` checks every row (bad values, duplicates,
unknown managers, manager loops) and lists the units and positions it would create; `POST /people-import` applies all
or nothing. People without an account get one (no usable password) and an email to choose one within 7 days; people
with an account elsewhere are invited and placed by importing again after they accept; members are updated.
Placements are made quietly and announced once (`StructureChanged`), and HR's details travel in `PeopleImported`.
Importing the same file twice changes nothing. A 129-person company imports in about 2 seconds.

### Platform operator and organisation roles (checked against real products)

Super admins run the platform (`/admin`): organisations, accounts, modules, suspension; they are not members of any
organisation and cannot open its data (like Google and Microsoft staff, who need the customer's approval). Opening an
organisation's or an account's details is itself logged (`org.viewed`, `user.viewed`), like Access Transparency.
Inside an organisation, as in Slack, one Owner is the primary owner (`organisation.primary_owner_id`): only they delete
the organisation or hand the role over (`POST /transfer-ownership`, the new one becomes an Owner too), and they can't
be demoted, suspended or leave before handing over. Admins have full rights as BambooHR's Full Admin does (payroll
included); a narrower Payroll or HR role is a custom role. Still to come with plans and billing: modules a company is
allowed (set by the platform) apart from those it switches on, and a billing role.

### Plans and subscriptions (built)

`billing` (core). `plan`: price per member (guests free) per month and per year, member limit, storage, included
modules, trial days, public or not; seeded Free (5 members, 1 GB, Projects and Business), Standard and Premium (every
module; 20 GB and 200 GB). `subscription`, one per organisation: TRIALING, ACTIVE, PAST_DUE, CANCELLED or COMPLIMENTARY.
New organisations get a 14-day Standard trial and move to Free when it ends (`SubscriptionJob`, hourly) unless they
choose a plan; a cancelled paid plan runs to the end of its period. The plan is enforced in the core: modules outside it
are locked (`ModuleEntitlements`; switched-on data is kept), invitations and imports stop at the member limit (pending
invitations count), uploads stop at the storage limit (`StorageQuota`). Owners and the billing role change the plan;
Admins see it. Super admins edit plans and set any organisation's subscription (contracts, complimentary, longer
trials). Organisations that existed before plans are complimentary Premium.

### Email verification (built)

Sign-up emails a "confirm your email" link (`EmailVerificationService`; `email_verification`, 3 days, single use,
only the token's hash stored; "send the link again" at most 3 times in 15 minutes). Until it's opened the account can
sign in and join organisations it's invited to, but can't create an organisation, invite people or import them
(`EMAIL_NOT_VERIFIED`), so a mistyped or borrowed address can't send email to others. Accepting an invitation or
resetting the password also proves the address. Accounts from before verification count as confirmed. The OIDC
`email_verified` claim is now real. `PLATFORM_AUTH_REQUIRE_VERIFIED_EMAIL=false` lifts the limits (local
development and the browser tests, which can't open emailed links).

### Two-step sign-in (built)

Optional per account (`TwoStepService`): on the account page, scan a QR code with an authenticator app (TOTP, RFC
6238: 30 s, 6 digits, ±1 step, each code works once), confirm with a first code, and keep 10 recovery codes (shown
once, single use, SHA-256 stored). From then on a right password answers only a challenge (`SessionResponse.twoStep`,
5 minutes, `two_step_challenge`) and `POST /api/auth/two-step` with a code or a recovery code completes the sign-in;
a password-reset link also ends at the code. Wrong codes count towards the 5-attempt lockout. Turning it off takes the
password and a code; a super admin can turn it off for someone who lost their phone and codes (audited, emailed).
The authenticator secret is encrypted (AES-GCM, `SecretBox`, key derived from `PLATFORM_JWT_SECRET`: changing that
secret means people set it up again). Not yet: an organisation setting that requires it for every member.

### Delete my account; export a company's data (built)

**Delete my account** (`AccountDeletionService`, `POST /api/me/delete`, account page): password (and a code with
two-step sign-in on); primary owners and only owners hand over or delete their organisations first
(`GET /api/me/delete/blockers`). The sign-in is erased (email freed for a new sign-up, password, photo, two-step,
devices, connected apps, notifications), `app_user.deleted_at` is set and every membership is suspended. As in Slack
or Google Workspace, organisations keep what they need as the employer (the name on tasks, HR and payroll records);
memberships are suspended, not deleted, because deleting one cascades to the HR profile.

**Export all data** (`OrgExportService`, `GET /api/orgs/{org}/export`, Settings, Owners only, 3 an hour, audited
`org.exported`): a streamed ZIP with one CSV per table (UTF-8 with BOM, formula-safe), found from the schema: every
table with `org_id` plus tables whose rows belong to one by a foreign key, so new modules are included without changes.
Also `people.csv` and the uploaded files. Columns named like hash, secret, token or password are left out.

### Invoices and payment (built)

`billing_invoice` (`PlatformInvoice`, `PlatformInvoiceService`): choosing a paid plan, and each renewal, issues an
invoice (`INV-yyyy-nnnnn`, seats = active non-guest members) with the seller's bank details and, when a Bakong account
is configured, a KHQR code for the exact amount (`billing/khqr/KhqrBuilder`, from VirtualBiz). Owners get it by email.
The Owner (or billing role) says "we paid" with the transfer reference; super admins confirm it (**Platform → Invoices**:
record payment or void). Unpaid after the due date the subscription is `PAST_DUE` (banner); after the grace period it
moves to Free and the invoice is voided. Payments are confirmed by a person: automatic Bakong / ABA PayWay confirmation
needs merchant credentials. See `docs/ops/billing.md`.

### Custom roles (built)

Owner, Admin, Member and Guest stay the base roles. On top of them, Owners and Admins create custom roles (`org_role`:
a name and permission codes from `RoleCatalogue`) and grant them to members (`org_role_grant`), never to guests;
Owners and Admins already hold every permission and only they manage roles, so a role can't be used to hand out more
access. Grantable: `org.structure.manage`, `documents.manage`, `activity.view`, `hr.manage`, `hr.payroll`,
`hr.recruit`, `business.manage`, `retail.manage`, `education.manage`, `service.manage` (a dispatcher). `hr.manage`
can be granted for one unit: it then covers the active members placed in that unit or below it (never the holder's
own record) for profiles and private details, leave balances, the attendance board and corrections (`hr.team` in the
client); moving people, leave requests, schedules, reviews, training, settings and the dashboard need it
organisation-wide. Payroll is its own permission, apart from managing people. `OrgAccess` resolves the caller's grants
into `OrgContext` (`can`, `canFor`, `canSome`, `scopeOf`), which every module's access helper checks.

### Approval line (built)

Workflow steps can ask the requester's manager (`MANAGER`) and, in HR workflows, their 2nd approver
(`SECOND_MANAGER`): the one set on their HR profile (`employee_profile.second_manager_id`), else their manager's
manager. When the line is built, someone already in it is not asked again (a 1st and 2nd approver who are the same
person approve once), a missing 2nd approver drops that level, and for leave and overtime an Owner with nobody above
them is approved at once (`AUTO_APPROVE` in the history) instead of going to the other Admins. Tested against a real
129-person company chart (centers → teams, 1st and 2nd managers): every line matched.

### Acting approvers, reminders and escalation (built)

`approval_delegation`: while someone is away (first and last day, Asia/Phnom_Penh), their delegate decides their
approval steps for them; people set their own, Admins and HR managers set anyone's, and one person has at most one
for any day. The delegate sees those requests in their inbox, sees the record as the approver would, and decides
"for" them (`on_behalf_of` on the step and in the history); it starts and stops by date, one hop only. A workflow may
remind the approver (and their delegate) after `remind_after_hours`, again every period, and escalate after
`escalate_after_hours`: the organisation's Admins are told and may then decide too (`ApprovalReminderJob`, hourly,
`platform.approvals.reminder-cron`).

### Employee records (built)

On each person's HR page: personal details (`employee_profile`: nickname, duty, category for everyone; gender, birth date,
nationality, ID or passport with expiry, NSSF number, address and personal contacts private to the person and their
HR managers), contracts (`employee_contract`: probation, fixed-term FDC with an end, unlimited UDC, internship; probation
end; signed file; salary only for payroll and the person), documents (`employee_document`: ID, contract, certificate,
medical, other; expiry; people file their own certificates, HR files IDs and contracts) and, for payroll, salary
history (`pay_change`, written on every salary change with an optional reason). HR managers of a unit manage these for
its people. The HR dashboard lists what ends within 30 days (and lately): contracts, probations, IDs, documents.

### Offboarding (built)

`offboarding`: people resign from their profile (REQUESTED; their manager and the Admins are told) or HR starts a
departure (termination, end of contract, retirement …). HR accepts a resignation (IN_PROGRESS) and works through a
checklist made from the organisation's `offboarding_template` (seeded with handover, exit interview, equipment, ID card
and keys, access, final pay, certificate of employment, NSSF; due dates relative to the last day). The page shows what
to hand over (direct reports, approvals waiting on them, units they head) and their leave balances for the final pay.
On or after the last day, with every step done, HR completes it: `employee_profile.left_on` is set and the membership
is suspended (access ends; owners must hand over first). HR managers of a unit handle its people; nobody their own.

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

### Organisation structure (built)

Units form a tree under the organisation: Branch, Division, Department, Center, Section and Team, in any nesting
(names unique among siblings, optional organisation-wide codes, a head who is an active member). Positions are jobs
with a unit (or organisation-wide), a grade, a "reports to" position and a planned headcount; filled and open seats
are counted from placements. Each member has at most one primary place (a unit, a position or both; a position in a
unit implies the unit) and up to ten additional ones. The primary position is the member's job title. HR's
department is derived, never typed: the database function `org_department_of` gives the nearest Department at or
above the primary unit (else that unit's name), and HR keeps `employee_profile.department` equal to it on every
change (`StructureChanged` event). Someone newly placed without a manager gets the holder of their position's
"reports to" position, else the nearest unit head above them. Everyone but guests reads the structure
(`org.structure.view`); Owners and Admins change it (`org.structure.manage`). Units and positions in use can only
be archived once emptied; only never-used ones can be deleted. The old `branch` table and free-text departments and
titles were migrated into units, positions and primary placements.

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
| Organisation | `organisation` (slug, name, plan), `org_unit` (branch / division / department / center / section / team tree, head, code), `org_position` (title, unit, grade, reports-to, headcount), `org_assignment` (member → unit and/or position, one primary), `membership` (user, org, role, status ACTIVE / INVITED / SUSPENDED), `invitation` (token hash, email, role, expiry), `team`, `team_member` |
| Modules | `module` (key, name, status — registry), `org_module` (org, module, enabled, enabled_by, at) |
| Projects | `project` (org, key, name, description, status, visibility ORG / PRIVATE, lead, start, due), `project_member` (role), `task` (project, number, title, description, status, priority, assignee, reporter, due_date, start_date, parent_id, position, completed_at), `task_comment`, `task_attachment`, `task_watcher` |
| Workflow | `workflow_definition` (org, module, name, active), `workflow_step_def` (order, approver type USER / ROLE / PROJECT_LEAD, approver ref), `approval_request` (org, module, record_type, record_id, title, requester, status PENDING / CHANGES_REQUESTED / APPROVED / REJECTED / WITHDRAWN, current_step), `approval_step` (request, order, approver, status), `approval_action` (request, actor, action, comment, at) |
| Calendar | `calendar_event` (org, title, start, end, all_day, owner, project?), `calendar_attendee` |
| Cross-cutting | `activity` (org, module, record_type, record_id, actor, verb, data JSONB, at), `notification` (user, org, type, payload, read_at), `stored_file` (org, owner, name, type, size, storage key) |

Every org-scoped table has `org_id` NOT NULL with an index; ids are UUID; timestamps `TIMESTAMPTZ`.

## 9. API endpoint inventory (MVP, REST + OpenAPI at `/api/docs`)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/sign-up`, `/sign-in`, `/refresh`, `/sign-out`, `/forgot-password`, `/reset-password`, `/verify-email`; `GET /api/me`; `POST /api/me/verify-email/resend`; `/api/auth/two-step`; `GET/POST /api/me/two-step` (`/setup`, `/enable`, `/disable`, `/recovery-codes`) |
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
