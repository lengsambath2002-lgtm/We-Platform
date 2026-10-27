# Business Platform — Step 1: Discovery

Status: **Discovery report, 2026-10-27.** No code was changed.

> **Owner decisions after this report (2026-10-27):** a **new platform repository** (this one) holds
> the platform. **WeGroupware is not modified** — not its code, database or process. The platform
> therefore owns its shared core (identity, organisations, roles, projects & tasks, workflow,
> calendar, notifications); the existing six apps and VirtualBiz are integrated later **only through
> their current APIs, unchanged**, and are never rewritten. Where §3 says "Extend" for a WeGroupware
> capability, read "reuse its design and rules as the reference; integrate through existing APIs".
> The architecture is in `02-architecture.md`. Everything below was read in the two
codebases; anything not confirmed is marked *unverified*. "Business Platform" is a placeholder name.

| Codebase | Path | What it is |
|---|---|---|
| **WeGroupware** | `/Users/mobile/Kosign/wehr` | The six existing apps: HR, My Profile, Approval, Board, Calendar, System. Multi-tenant. In production (uat / prod). |
| **VirtualBiz** | this repo | Marketplace + seller workspace (catalogue, POS, orders, inventory, promotions) + service-provider workspace (bookings, staff calendar). Public demo. |

---

## 1. Existing architecture

| | WeGroupware | VirtualBiz | Target (master prompt) |
|---|---|---|---|
| Backend | Spring Boot 3.5, **Java 17** toolchain (runtime image 21); modules `wgpp-api` (packages per app) + `wgpp-common` | Spring Boot 3.5, **Java 17**; `vbiz-api` (23 domains) + `vbiz-common` | Java 21 + Spring Boot 3 |
| Database | PostgreSQL, Flyway **only on local** — uat / prod migrations applied by hand (393 files) | PostgreSQL, Flyway everywhere (28 files) | PostgreSQL + Flyway |
| API | REST, springdoc; `openapi.json` committed; generated client folder empty | REST, springdoc | REST + OpenAPI |
| Frontend | Next.js 15, React 19, TS; **Tailwind 3**, hand-built UI kit on 7 Radix primitives, **Lucide**, TanStack Query + Table, Zustand, axios; RHF in 3 pages, Zod almost unused; light theme only | Next.js 15, React 19, TS; **CSS modules** (Tailwind present, little used), own components, TanStack Query, Zustand; no RHF / Zod / Lucide; light + dark | Tailwind + **shadcn/ui** + Lucide + TanStack Query + **RHF + Zod** |
| Mobile | Hybrid native shell (Android Kotlin, iOS Swift) around phone web pages; no React Native | Native iOS (SwiftUI) + Android (Compose) | **React Native + Expo** |
| i18n | en / ko / kh | en / kh | — (owner: en / kh) |
| Auth | Login ID or email + password, optional emailed code; RS256 JWT, stateless | Email / Google (Firebase) / email code / phone; two-step (TOTP) for everyone | One account, one auth system |
| Tenancy | Tenant group (family) → companies → departments; `company_code` Hibernate filter on opted-in services | Per-business checks only; no organisations | Organisations, branches, memberships, RBAC |
| Deploy | GitLab CI triggers an external pipeline; Docker files partly stale; no IaC | Vercel (web) + Render (API); Docker file | Docker + CI/CD |

---

## 2. The six existing applications

### HR Management (`/admin/hr/*`, `/api/hr/*`)
- **Has:** employee records (list, import / export CSV, bulk edit, manager line, accounts, retire / restore / erase), profile sections (career, skills, education, languages, family, military, custom fields), employee documents with HR review, profile change approvals, personnel orders, read-only org chart, **attendance** (records, terminals ZKTeco, phone scan with geofence, policies, exceptions, close-out schedulers), **work schedules**, holidays, **leave** (ledger with one writer, generation, policies), **overtime** (records, caps, balances, pay rates), 8 fixed reports, manager cockpit.
- **Missing:** recruitment / candidates, onboarding workflow, **payroll**, benefits / compensation, performance reviews, training enrolment (catalogue read-only), shift rostering.
- **Tables:** `employees`, `employee_*` profile tables, `attendance_*`, `work_schedules`, `employee_schedule_assignments`, `leave_balances` + `leave_transactions` (ledger), `overtime_*`, `ot_*`, `personnel_orders`, `employee_documents`.
- **Depends on:** Approval (leave / overtime / corrections / resignation are approval documents whose effects the approval engine applies), Calendar (holidays), System (departments, codes, audit), Notification.

### My Profile (`/my/*`, `/api/my/*`)
- **Has:** self-service home (balances, attendance, requests), punch in / out by phone, own profile with change requests, own documents, leave / overtime / requests lists, request form picker, directory with personal groups, manager cockpit, app launcher catalogue.
- **Missing:** payslips, benefits, goals / reviews, training.
- **Note:** almost no domain of its own — it is a self-service face of HR and Approval.

### Approval (`/ap/*`, `/admin/ap/*`, `/api/ap/*`)
- **Has:** a home-built workflow engine — forms (7 field types; ~33 hand-coded form renderers), approval lines by person / department / role, saved and default lines, two generations of routing rules, delegation with scope and amount limit, substitute approval after 3 days, approve / reject / hold (single and bulk), undo, cancel final approval, withdraw, re-draft, circulate, comments, attachments, CC, admin actions with history, inboxes, dashboard, notifications.
- **Missing for a platform engine:** a link to *any* module's record (no source module / entity reference), configurable after-approval effects (today hard-coded per HR form category), **request changes**, time-based escalation / SLA, parallel or quorum stages, conditional routing.

### Board (`/boards/*`, `/api/boards/*`)
- **Has:** boards (types, members by people or departments, admins, categories, archive, pin), posts (pin, private, read tracking, reactions, mentions, share, server drafts), threaded comments, attachments with Office → PDF preview, notice board with fan-out. **TASK posts** (status, dates, assignees, flat subtasks) exist in the API but **creating them in the UI was switched off** (2026-10-01).
- **Missing:** projects, kanban, task lists, dependencies, milestones.

### Calendar (`/cal/*`, `/api/cal/*`)
- **Has:** personal and shared events, recurrence (no end date, whole-series edits), alarms, meeting invitations, **meeting-room booking** (overlap-safe), Cambodian holiday sync, leave and overtime overlays, read-only team feed.
- **Missing:** user-created team / group calendars, task and project deadlines, recurrence exceptions; a Korean tax schedule needs removing or localising.

### System (`/admin/sys/*`, `/api/sys/*`)
- **Has:** first-run setup wizard, companies and tenant groups (owners, co-owners, company requests, cloning from head office), users, roles and permission matrix (per company, templates, overrides), departments (tree, unit types), cost centres, code tables (positions and titles are codes), **app entitlements per tenant** + per-user participation + menu toggles, per-app administrators, **audit log** (generic, CSV export), operator access with declared reason, client releases.
- **Missing for a platform:** organisation-level switch between industry modules beyond the six app codes (catalogue is hard-coded in ~5 places), branches as their own concept (companies / departments today), project-level permissions.

### Cross-cutting (both systems)
- **Notifications:** WeGroupware — inbox, SSE, email (36 templates), browser push, category + channel preferences. VirtualBiz — inbox, SSE, email, Firebase push, per-topic preferences.
- **Files:** WeGroupware — MinIO with per-domain attachment tables, no generic file table, malware scan interface unimplemented. VirtualBiz — one `stored_file` table, disk or database store.
- **Reporting:** no chart library in either; exports via SheetJS / exceljs (WeGroupware), CSV (VirtualBiz).
- **Documents library:** **none** in either system.

---

## 3. Reuse / extend / merge / build matrix

| Platform capability (master prompt §5–6) | Existing source | Decision | Why |
|---|---|---|---|
| **A. Identity & access** — login, recovery, sessions | WeGroupware auth | **Extend** | Production users live there; add TOTP / WebAuthn from VirtualBiz's two-step design, stronger password rule |
| Organisations, branches, memberships | WeGroupware tenant group → companies → departments | **Extend** | Already multi-tenant; branches = companies or department units (decide); relax "one company per person" for SaaS |
| Roles & permissions (RBAC) | WeGroupware roles + menu permission matrix (shadow mode in HR) | **Extend** | Make the matrix enforcing; add module- and project-level scopes |
| Invitations, user management | WeGroupware memberships (INVITED), SYS users | **Reuse** | |
| Module enablement per organisation | WeGroupware tenant app entitlements | **Extend** | Replace the hard-coded app catalogue with a module registry |
| **B. Projects & tasks** | Board TASK posts (hidden) | **Build new**, migrate TASK posts | Board tasks lack projects, kanban, dependencies; keep board for discussion |
| **C. Calendar & scheduling** | WeGroupware Calendar | **Extend** | Add team calendars and task / project deadlines; keep rooms |
| Bookings / appointments (service) | VirtualBiz provider calendar & bookings | **Reuse** in Service & Operations | Working booking engine with public booking |
| **D. Approval & workflow engine** | WeGroupware Approval | **Extend** (generalise) | Add source-module + entity reference, pluggable after-approval handlers, request-changes, escalation; HR forms keep working |
| **E. Document management** | — | **Build new** | Nothing exists; reuse storage + Office → PDF preview |
| **F. Notifications** | Both systems | **Merge** into one service | Same shape; keep WeGroupware inbox / SSE / email, add VirtualBiz's per-topic choices and mobile push |
| Announcements / discussion | Board | **Reuse** | |
| **G. Reporting** | HR reports, VirtualBiz reports | **Build** shared components; reuse queries | No chart library; one table / filter / export kit |
| Audit trail | WeGroupware `audit_log` | **Reuse** for all modules | |
| **Module 1 — General Business** (CRM, leads, finance, invoices, expenses) | — (approval has finance helper forms) | **Build new** | |
| **Module 2 — Education** | — | **Build new** | Student attendance separate from employee attendance |
| **Module 3 — Retail & Commerce** | VirtualBiz catalogue, variants, POS (barcode / camera scan), orders, stock movements, promotions, returns, customers, marketplace | **Reuse**; **build** warehouses + transfers, suppliers, purchase orders | Most of the module exists |
| **Module 4 — HR & Workforce** | WeGroupware HR + MY | **Reuse** + **build** recruitment, onboarding, payroll, compensation, reviews, training enrolment | Strong attendance / leave / overtime |
| **Module 5 — Service & Operations** | VirtualBiz bookings + staff calendar | **Reuse** bookings; **build** tickets, work orders, technicians, assets, maintenance, SLA | |
| Payments (partner-based) | VirtualBiz KHQR payments, payouts | **Extend** into a provider-agnostic layer | Licensing to verify |
| **Merge** candidates | HR Employee Detail vs MY Profile; two holiday tables; two routing-rule systems; two request front doors (MY / AP); two OT routes | **Merge** during module work | Duplicates found in discovery |
| **Retire** candidates | Hidden HR mock pages (`approver-flow`, `settings-attendance`), stale Docker files, Korean tax schedule, dead `@Audited` stubs | **Retire** after confirmation | No live callers found |

---

## 4. Proposed navigation and module hierarchy

```
Business Platform
├─ Header: organisation switcher · module switcher · global search · notifications · profile
├─ Global (everyone, by permission)
│   Overview · My Tasks · Projects · Calendar · Teams · Approvals · Documents · Reports
├─ Modules (only if the organisation enabled it AND the user has a role in it)
│   ├─ General Business — Dashboard · CRM pipeline · Customers · Projects portfolio · Finance · Invoices · Expenses · Reports
│   ├─ Education        — Dashboard · Students · Parents · Teachers · Academic structure · Classes & courses · Attendance · Exams · Gradebook · Fees · Timetable · Reports
│   ├─ Retail & Commerce — Dashboard · POS · Products · Inventory · Warehouses · Suppliers · Purchasing · Sales orders · Returns · Online store · Reports
│   ├─ HR & Workforce   — Dashboard · Employees · Org chart · Recruitment · Attendance · Schedules · Leave · Overtime · Payroll · Reviews · Training · Reports
│   └─ Service & Ops    — Dashboard · Service requests · Tickets · Bookings · Work orders · Technicians · Assets · Maintenance · Reports
└─ Administration (org admins) — Organisation & branches · Members & roles · Modules & plan · Workflows · Audit log · Settings
```

- Today's apps map in: HR + MY → **HR & Workforce** (MY becomes "My" self-service pages inside it and the mobile app); Approval → global **Approvals** + **Administration ▸ Workflows**; Calendar → global **Calendar**; Board → **Teams** (announcements, discussion); System → **Administration**; VirtualBiz seller / provider → **Retail & Commerce** / **Service & Ops**; the public marketplace stays a public site.
- Sidebar: expanded (icons + labels, module section grouped) and collapsed (icons, tooltips); on phones a bottom bar (Overview, My Tasks, Approvals, Calendar, More).

---

## 5. Recommended MVP

**Shared core + one end-to-end workflow** (master prompt §14 step 5):

1. **Platform shell** in the target stack (Tailwind + shadcn/ui + Lucide, RHF + Zod): sidebar (expanded / collapsed), header with organisation and module switchers, Overview dashboard, permission-aware navigation from a **module registry** (not a hard-coded list).
2. **Identity & organisation** from WeGroupware (sign-in, organisations, members, roles, module entitlements) exposed through a documented API the shell uses.
3. **Projects & Tasks** (new): project, members, kanban + list, task assignment, priority, due date, subtasks, comments, attachments, activity history, **My Tasks**, deadlines on the **Calendar**.
4. **Approval link**: a task (or any record) can request approval through the existing engine, generalised with a source-module / entity reference; approve / reject / **request changes** update the task's activity history.

Workflow proven end to end (journeys 1–3): *a manager creates a project, invites members and assigns tasks → a member updates status, comments, attaches a file → a manager approves a request and the history updates* — on web and phone browser, with loading / empty / error / no-permission states and backend-enforced permissions.

Why this first: Projects & Tasks and the generic approval link are in every module's navigation and do not exist yet; HR (WeGroupware) and Retail (VirtualBiz) already work and can join the shell next without being rewritten.

---

## 6. Unknowns and risks to verify

| # | Unknown / risk | Why it matters |
|---|---|---|
| 1 | **Where the platform code lives** — extend WeGroupware's repo, VirtualBiz's, or a new platform repo that calls both | Decides every later step |
| 2 | WeGroupware uat / prod **database state** (migrations applied by hand; some never recorded; baseline stops 2026-09-30) | Any schema change there must be scripted and verified |
| 3 | **Tenancy enforcement** is opt-in (`@TenantScoped` + base entity); Super Admin bypasses filters; native SQL unfiltered; no row-level security | A SaaS with outside organisations needs isolation that cannot be forgotten |
| 4 | **One company per person** rule (WeGroupware) vs SaaS members in several organisations | Membership model change |
| 5 | **Two user bases** (WeGroupware employees, VirtualBiz merchants + public customers) | Identity merge or single sign-on, account-linking rules |
| 6 | Stack gaps: Java 17 → 21, no shadcn/ui, little RHF / Zod, CSS modules in VirtualBiz | Upgrade path vs new shell only |
| 7 | **Mobile**: target React Native + Expo; both systems have native / hybrid clients in progress | Which ones stay, which are replaced |
| 8 | Who owns and deploys WeGroupware (other team, other AI agent, PO approval, GitLab) | Coordination and access to merge / deploy |
| 9 | Approval engine size (two service classes of 3k and 7k lines) and HR-specific effects | Generalising it must not break HR |
| 10 | Payment licensing in Cambodia; partners beyond Bakong KHQR | Before any live money |
| 11 | Languages: ko still in WeGroupware; owner asked en / kh | Translation scope |
| 12 | Legal text, hosting plan (free Render cannot send email or keep files) | Production readiness |

---

## 7. Prioritised plan

| # | Step (master prompt §14) | Output | Needs from owner |
|---|---|---|---|
| 1 | Decide repo & integration (risk 1, 5, 8) | ADR: platform repo layout, how the shell reaches WeGroupware and VirtualBiz APIs, identity strategy | **Decision** |
| 2 | Architecture deliverables (§12) | Sitemap, roles × permissions matrix, journeys, ERD for core + Projects & Tasks, table and API inventories, routing for web and mobile | Review |
| 3 | Design system (step 3) | Tokens (light / dark), typography, spacing, breakpoints, shadcn-based components (table, filters, forms, dialogs, badges, tabs, pagination, date picker, charts, timeline, empty / loading / error / no-permission) | Brand colour |
| 4 | UX (step 4) | Shell, Overview, Projects & Tasks, module navigation — clickable, phone and desktop | Approve screens |
| 5 | MVP build (step 5) | Shell + identity / org bridge + module registry + Projects & Tasks + approval link, tested end to end | — |
| 6 | Bring existing apps into the shell | HR & Workforce (WeGroupware HR + MY), Retail & Commerce (VirtualBiz) as modules — reused, not rewritten | — |
| 7 | Expansion (step 6) | Documents, Service & Ops, General Business, Education, payroll, payment layer — one at a time | Priorities |
| 8 | Validation (step 7) | Journey tests, accessibility, authorisation tests (cross-organisation isolation), existing suites green | — |

---

## Khmer summary (សង្ខេបជាភាសាខ្មែរ)

- **ការស្វែងយល់ (Discovery) ប៉ុណ្ណោះ** — មិនទាន់កែកូដទេ។ ប្រព័ន្ធពីរមានស្រាប់៖ **WeGroupware** (កម្មវិធីទាំង 6៖ HR, My Profile, Approval, Board, Calendar, System — មានអង្គភាពច្រើន ដំណើរការលើ production) និង **VirtualBiz** (ទីផ្សារ, POS, ស្តុក, ការកក់សេវាកម្ម)។
- **ប្រើឡើងវិញ / ពង្រីក៖** ការចូល, អង្គភាព, តួនាទី, ការអនុញ្ញាតម៉ូឌុល (WeGroupware); ម៉ាស៊ីនអនុម័ត (ត្រូវធ្វើឱ្យប្រើបានគ្រប់ម៉ូឌុល); ប្រតិទិន; HR ខ្លាំងលើវត្តមាន ច្បាប់ឈប់សម្រាក ម៉ោងបន្ថែម; Retail ភាគច្រើនមានក្នុង VirtualBiz; ការកក់សេវាកម្ម។
- **ត្រូវសាងសង់ថ្មី៖** គម្រោង និងការងារ (Projects & Tasks), បណ្ណាល័យឯកសារ, CRM និងហិរញ្ញវត្ថុ, ការអប់រំ, សំបុត្រ/បញ្ជាការងារ/ទ្រព្យសម្បត្តិ, ប្រាក់ខែ, ការជ្រើសរើសបុគ្គលិក។
- **MVP ដែលណែនាំ៖** សំបកវេទិកា (sidebar, ប្តូរអង្គភាព/ម៉ូឌុល, Overview) + អត្តសញ្ញាណ និងអង្គភាពពី WeGroupware + **Projects & Tasks** + ភ្ជាប់ការអនុម័ត — ដំណើរការពេញពីចុងដល់ចុង លើកុំព្យូទ័រ និងទូរស័ព្ទ។
- **ការសម្រេចចិត្តដំបូងដែលត្រូវការ៖** កូដវេទិការស់នៅទីណា (WeGroupware, VirtualBiz ឬឃ្លាំងថ្មី) និងរបៀបភ្ជាប់អត្តសញ្ញាណអ្នកប្រើពីរប្រព័ន្ធ។
