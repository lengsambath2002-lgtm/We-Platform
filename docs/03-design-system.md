# Business Platform — Step 3: Design system

Status: **Proposed, 2026-10-27.** Implemented as Tailwind tokens (CSS variables) + shadcn/ui
components in `apps/web`. Light and dark themes.

## Principles

Modern enterprise SaaS: calm neutral surfaces, **one primary colour**, clear hierarchy, dense but
readable tables, actionable dashboards. Avoid gradients, glassmorphism, oversized cards, decorative
charts, inconsistent buttons and different layouts for the same purpose (master prompt §9).

## Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | `#F8FAFC` (slate-50) | `#0B1120` | App background |
| `--card` | `#FFFFFF` | `#111827` | Panels, tables, dialogs |
| `--foreground` | `#0F172A` | `#E5E7EB` | Text |
| `--muted-foreground` | `#64748B` | `#94A3B8` | Secondary text |
| `--border` | `#E2E8F0` | `#1F2937` | 1 px borders |
| `--primary` | `#2563EB` (blue-600) | `#3B82F6` | Primary actions, focus, selected nav |
| `--primary-foreground` | `#FFFFFF` | `#FFFFFF` | |
| `--destructive` | `#DC2626` | `#EF4444` | Delete, reject |
| `--success` | `#16A34A` | `#22C55E` | Done, approved |
| `--warning` | `#D97706` | `#F59E0B` | Due soon, changes requested |
| `--info` | `#0891B2` | `#22D3EE` | In review |
| `--radius` | 8 px (controls), 10 px (panels) | | No pill-shaped containers except badges |

Status colours are used **only** for status (badges, dots, chart series), never decoration.

## Typography

Inter (Latin) + Noto Sans Khmer (Khmer), system fallbacks. Scale: 12 (caption) · 13 (table, meta) ·
14 (body, controls) · 16 (section title) · 20 (page title) · 24 (dashboard numbers). Weights 400 /
500 / 600. Tabular numbers in tables and money.

## Spacing and layout

4 px grid (Tailwind default). Page padding 24 px desktop, 16 px phone. Sidebar 248 px expanded, 64 px
collapsed. Header 56 px. Content max width 1440 px for lists; forms 720 px. Breakpoints: `sm` 640,
`md` 768 (tablet: sidebar collapses), `lg` 1024 (sidebar expanded), `xl` 1280.

## Page conventions

- **List page**: page header (title, count, primary action) → filter bar (search, filters, saved views) → table or board → pagination.
- **Detail page**: header (breadcrumb, title, status, actions) → main column (content, activity) + side column (properties). On phones the side column moves below the title.
- **Dashboard**: a row of 3–4 compact stats that link somewhere → actionable lists (my tasks, approvals, deadlines) → one or two charts with business meaning.
- Every data region has **loading** (skeleton), **empty** (what will appear + next step), **error** (what failed + retry) and **no permission** states.

## Components (shadcn/ui based)

AppShell (Sidebar expanded / collapsed, Header, OrgSwitcher, ModuleSwitcher, CommandSearch, NotificationBell, UserMenu, Breadcrumbs) · PageHeader · DataTable · FilterBar · Form fields (RHF + Zod) · Dialog · Sheet (drawer) · ConfirmDialog · StatusBadge · PriorityBadge · Avatar / AvatarStack · Tabs · Pagination · DatePicker · KanbanBoard · TaskCard · ActivityTimeline · CommentBox · FileDrop · EmptyState · Skeleton · ErrorState · PermissionDenied · Toast (sonner) · StatCard · Chart (Recharts).

Icons: Lucide only, 16 px in tables and buttons, 18 px in navigation.

## Mobile app

Same tokens in React Native (NativeWind or a theme object). Bottom tabs (Overview, My Tasks,
Approvals, Calendar, More), sheets for create / edit, swipe actions on list rows, 44 pt touch targets.
