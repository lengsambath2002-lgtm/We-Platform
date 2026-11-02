'use client';

import { parseISO } from 'date-fns';
import {
  ArrowUpRight, Ban, Blocks, ChevronLeft, ChevronRight, CreditCard, Eye, FileX, KeyRound, LogOut, type LucideIcon, PlayCircle, Receipt,
  RotateCcw, Search, ShieldCheck, ShieldOff, Smartphone, Tags, UserCheck, UserX,
} from 'lucide-react';
import Link from 'next/link';
import { type ReactNode, useState } from 'react';

import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { moduleName } from '@/components/shell/nav';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type AccountStatus, type OrgStatus, type PlatformAuditEntry, usePlatformAudit, usePlatformStats } from '@/lib/api/platform';
import { ago, fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

const TONE: Record<OrgStatus | AccountStatus, string> = {
  ACTIVE: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-300',
  SUSPENDED: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300',
  LOCKED: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300',
  DELETED: 'border-border bg-muted text-muted-foreground',
  DISABLED: 'border-destructive/20 bg-destructive/10 text-destructive',
};
const DOT: Record<OrgStatus | AccountStatus, string> = {
  ACTIVE: 'bg-emerald-500', SUSPENDED: 'bg-amber-500', LOCKED: 'bg-amber-500', DELETED: 'bg-muted-foreground', DISABLED: 'bg-destructive',
};

export function StatusBadge({ status }: { status: OrgStatus | AccountStatus }) {
  const { t } = useT();
  return (
    <Badge className={cn('gap-1.5 font-medium', TONE[status])}>
      <span className={cn('size-1.5 rounded-full', DOT[status])} aria-hidden />{t(`platform.status${status}`)}
    </Badge>
  );
}

export function SuperAdminBadge() {
  const { t } = useT();
  return <Badge variant="outline" className="gap-1"><ShieldCheck />{t('platform.superAdmin')}</Badge>;
}

/** A date, with "3 days ago" on hover; "Never" when there is none. */
export function When({ at, pattern = 'd MMM yyyy' }: { at: string | null; pattern?: string }) {
  const { t } = useT();
  if (!at) return <span className="text-muted-foreground">{t('platform.never')}</span>;
  return <time dateTime={at} title={ago(at)} className="whitespace-nowrap tabular">{fmt(parseISO(at), pattern)}</time>;
}

/** "Showing 26–50 of 1,243" and previous / next. With `total` and `size`, the range; otherwise "Page 2 of 50". */
export function Pager({ page, pages, onPage, total, size, className }: {
  page: number; pages: number; onPage: (p: number) => void; total?: number; size?: number; className?: string;
}) {
  const { t } = useT();
  if (pages <= 1 && total === undefined) return null;
  const label = total !== undefined && size
    ? t('platform.showing', { from: total === 0 ? 0 : (page * size + 1).toLocaleString(), to: Math.min(total, (page + 1) * size).toLocaleString(), total: total.toLocaleString() })
    : t('collab.pageOf', { page: page + 1, pages });
  return (
    <nav className={cn('flex items-center justify-between gap-3 px-4 py-3', className)} aria-label={t('collab.pageOf', { page: page + 1, pages })}>
      <span className="text-sm text-muted-foreground tabular">{label}</span>
      {pages > 1 && (
        <div className="flex items-center gap-2">
          <span className="hidden text-sm text-muted-foreground tabular sm:inline">{t('collab.pageOf', { page: page + 1, pages })}</span>
          <Button variant="outline" size="icon" className="size-8" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label={t('collab.newer')}><ChevronLeft className="size-4" /></Button>
          <Button variant="outline" size="icon" className="size-8" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)} aria-label={t('collab.older')}><ChevronRight className="size-4" /></Button>
        </div>
      )}
    </nav>
  );
}

/** A console page's title, what it is for, and its main actions. */
export function ConsoleHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="truncate text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

/** A white panel; with `title`, a header row (title, lead, action on the right). */
export function Panel({ title, lead, action, children, className, flush }: {
  title?: ReactNode; lead?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; flush?: boolean;
}) {
  return (
    <section className={cn('overflow-hidden rounded-xl border bg-card shadow-xs', className)}>
      {title && (
        <div className={cn('flex items-start justify-between gap-3 px-5 pt-5', flush ? 'pb-4' : 'pb-1')}>
          <div className="min-w-0">
            <h2 className="text-base font-semibold">{title}</h2>
            {lead && <p className="mt-0.5 text-sm text-muted-foreground">{lead}</p>}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </div>
      )}
      <div className={cn(!flush && 'p-5', !flush && title && 'pt-3')}>{children}</div>
    </section>
  );
}

/** A headline number with an icon; links to the list behind it. */
export function StatCard({ label, value, icon: Icon, hint, href, footer }: {
  label: string; value: number; icon: LucideIcon; hint?: ReactNode; href?: string; footer?: ReactNode;
}) {
  const body = (
    <>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-muted-foreground">{label}</p>
        <span className="grid size-8 place-items-center rounded-lg bg-muted text-muted-foreground transition-colors group-hover:bg-primary/10 group-hover:text-primary">
          <Icon className="size-4" aria-hidden />
        </span>
      </div>
      <p className="mt-3 text-3xl font-semibold tracking-tight tabular">{value.toLocaleString()}</p>
      {hint && <div className="mt-1.5 text-xs text-muted-foreground">{hint}</div>}
      {footer && <div className="mt-3">{footer}</div>}
    </>
  );
  const cls = 'group relative block rounded-xl border bg-card p-5 shadow-xs transition-all';
  return href ? (
    <Link href={href} className={cn(cls, 'hover:-translate-y-px hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring')}>
      {body}
      <ArrowUpRight className="absolute right-5 bottom-5 size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" aria-hidden />
    </Link>
  ) : <div className={cls}>{body}</div>;
}

/** "+12" in green: growth over a period. */
export function Delta({ children }: { children: ReactNode }) {
  return <span className="inline-flex items-center rounded-md bg-emerald-50 px-1.5 py-0.5 font-medium text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">{children}</span>;
}

/** Search box with an icon; the label is for screen readers. */
export function SearchField({ id, label, placeholder, value, onChange }: {
  id: string; label: string; placeholder: string; value: string; onChange: (v: string) => void;
}) {
  return (
    <div className="relative w-full min-w-0 sm:w-auto sm:max-w-sm sm:flex-1">
      <label htmlFor={id} className="sr-only">{label}</label>
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input id={id} type="search" className="h-9 bg-card pl-9" placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

/** One-of-several filter as a segmented control (radio group). */
export function Segmented<T extends string>({ label, value, options, onChange }: {
  label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex h-9 items-center gap-0.5 overflow-x-auto rounded-lg border bg-muted/60 p-0.5">
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={value === o.value} onClick={() => onChange(o.value)}
          className={cn('h-full shrink-0 rounded-md px-3 text-sm font-medium whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground',
            value === o.value && 'bg-card text-foreground shadow-xs')}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

/** A list in a panel: rows separated by hairlines, the pager in its footer. */
export function ListPanel({ children, footer, className }: { children: ReactNode; footer?: ReactNode; className?: string }) {
  return (
    <div className={cn('overflow-hidden rounded-xl border bg-card shadow-xs', className)}>
      <ul className="divide-y">{children}</ul>
      {footer && <div className="border-t bg-muted/20">{footer}</div>}
    </div>
  );
}

/**
 * One row of a list: a leading mark, the title and a line under it, details on the right. With `href`, the whole row is
 * one link (so nothing inside may be a link) with a chevron.
 */
export function ListRow({ href, lead, title, sub, meta, metaClassName, aside, className }: {
  href?: string; lead?: ReactNode; title: ReactNode; sub?: ReactNode; meta?: ReactNode; metaClassName?: string; aside?: ReactNode; className?: string;
}) {
  const body = (
    <>
      {lead}
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium text-foreground">{title}</div>
        {sub && <div className="mt-0.5 truncate text-xs text-muted-foreground">{sub}</div>}
        {meta && <div className="mt-2 md:hidden">{meta}</div>}
      </div>
      {meta && <div className={cn('hidden min-w-0 shrink-0 md:block', metaClassName)}>{meta}</div>}
      {aside && <div className="flex shrink-0 items-center gap-3">{aside}</div>}
      {href && <ChevronRight className="size-4 shrink-0 text-muted-foreground/60 transition-transform group-hover:translate-x-0.5 group-hover:text-muted-foreground" aria-hidden />}
    </>
  );
  const cls = cn('flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3.5 sm:flex-nowrap sm:px-5', className);
  return (
    <li>
      {href ? <Link href={href} className={cn(cls, 'group transition-colors hover:bg-muted/40 focus-visible:bg-muted/40 focus-visible:outline-none')}>{body}</Link> : <div className={cls}>{body}</div>}
    </li>
  );
}

/** Small "label value" facts in a row, e.g. "12 members · Joined 2 Nov". */
export function Facts({ items }: { items: { label: string; value: ReactNode }[] }) {
  return (
    <dl className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs">
      {items.map((f) => (
        <div key={f.label} className="flex items-baseline gap-1.5">
          <dt className="text-muted-foreground">{f.label}</dt>
          <dd className="font-medium text-foreground tabular">{f.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** An organisation's initials in a rounded square. */
export function OrgMark({ name, className }: { name: string; className?: string }) {
  const letters = name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]?.toUpperCase()).join('');
  return <span className={cn('grid size-9 shrink-0 place-items-center rounded-lg border bg-muted text-xs font-semibold text-foreground/80', className)} aria-hidden>{letters}</span>;
}

/** Module names as chips: the first few, then "+N". */
export function ModuleChips({ names, max = 3 }: { names: string[]; max?: number }) {
  const { t } = useT();
  if (names.length === 0) return <span className="text-sm text-muted-foreground">{t('common.none')}</span>;
  const shown = names.slice(0, max);
  const rest = names.slice(max);
  return (
    <div className="flex flex-wrap gap-1">
      {shown.map((n) => <span key={n} className="inline-flex items-center rounded-md border bg-background px-1.5 py-0.5 text-xs whitespace-nowrap text-foreground/80">{n}</span>)}
      {rest.length > 0 && <span className="inline-flex items-center rounded-md bg-muted px-1.5 py-0.5 text-xs text-muted-foreground" title={rest.join(', ')}>+{rest.length}</span>}
    </div>
  );
}

/** A label and its value, for property lists. */
export function Prop({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2.5 text-sm">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 truncate text-right font-medium">{children}</dd>
    </div>
  );
}

/**
 * Confirms a console action. With `reason`, a reason must be typed and is passed to `run`. Errors from the API
 * show in the dialog; it closes when `run` succeeds.
 */
export function ConfirmAction({ trigger, title, body, confirm, busy, destructive, reason, run }: {
  trigger: ReactNode; title: string; body: string; confirm: string; busy?: string; destructive?: boolean;
  reason?: { label: string; hint: string }; run: (reason: string) => Promise<unknown>;
}) {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const change = (v: boolean) => { setOpen(v); if (!v) { setText(''); setError(null); } };
  return (
    <>
      <span onClick={() => setOpen(true)} className="contents">{trigger}</span>
      <AlertDialog open={open} onOpenChange={change}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{title}</AlertDialogTitle>
            <AlertDialogDescription>{body}</AlertDialogDescription>
          </AlertDialogHeader>
          {reason && (
            <div className="space-y-2">
              <Label htmlFor="platform-reason">{reason.label}</Label>
              <Textarea id="platform-reason" value={text} maxLength={500} placeholder={reason.hint} onChange={(e) => setText(e.target.value)} />
            </div>
          )}
          {error && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <Button variant={destructive ? 'destructive' : 'default'} disabled={pending} onClick={async () => {
              if (reason && !text.trim()) { setError(t('platform.reasonRequired')); return; }
              setError(null);
              setPending(true);
              try { await run(text.trim()); change(false); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); }
            }}>{pending && busy ? busy : confirm}</Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

const ACTIONS: Record<string, MessageKey> = {
  'org.suspended': 'platform.act_org_suspended',
  'org.reactivated': 'platform.act_org_reactivated',
  'org.restored': 'platform.act_org_restored',
  'org.module.enabled': 'platform.act_org_module_enabled',
  'org.module.disabled': 'platform.act_org_module_disabled',
  'user.disabled': 'platform.act_user_disabled',
  'user.enabled': 'platform.act_user_enabled',
  'user.signed_out': 'platform.act_user_signed_out',
  'user.password_reset_sent': 'platform.act_user_password_reset_sent',
  'user.two_step_reset': 'platform.act_user_two_step_reset',
  'user.super_admin.granted': 'platform.act_user_super_admin_granted',
  'user.super_admin.revoked': 'platform.act_user_super_admin_revoked',
  'org.viewed': 'platform.act_org_viewed',
  'user.viewed': 'platform.act_user_viewed',
  'org.subscription.set': 'platform.act_org_subscription_set',
  'plan.saved': 'platform.act_plan_saved',
  'invoice.paid': 'platform.act_invoice_paid',
  'invoice.voided': 'platform.act_invoice_voided',
};

const ACTION_ICON: Record<string, LucideIcon> = {
  'org.suspended': Ban, 'org.reactivated': PlayCircle, 'org.restored': RotateCcw, 'org.module.enabled': Blocks, 'org.module.disabled': Blocks,
  'user.disabled': UserX, 'user.enabled': UserCheck, 'user.signed_out': LogOut, 'user.password_reset_sent': KeyRound, 'user.two_step_reset': Smartphone,
  'user.super_admin.granted': ShieldCheck, 'user.super_admin.revoked': ShieldOff, 'org.viewed': Eye, 'user.viewed': Eye,
  'org.subscription.set': CreditCard, 'plan.saved': Tags, 'invoice.paid': Receipt, 'invoice.voided': FileX,
};
const DANGER = new Set(['org.suspended', 'user.disabled', 'user.super_admin.revoked', 'invoice.voided']);

/** Audit entries of one day. */
export interface PlatformAuditEntryGroup { key: string; label: string; items: PlatformAuditEntry[] }

/** The icon of an audit action, in a circle. */
export function ActionIcon({ action }: { action: string }) {
  const Icon = ACTION_ICON[action] ?? Eye;
  return (
    <span className={cn('grid size-8 shrink-0 place-items-center rounded-full border bg-card',
      DANGER.has(action) ? 'border-destructive/20 text-destructive' : 'text-muted-foreground')} aria-hidden>
      <Icon className="size-3.5" />
    </span>
  );
}

/** One audit entry as a timeline row: icon, sentence, time. */
export function AuditRow({ e, moduleName, pattern = 'd MMM HH:mm' }: { e: PlatformAuditEntry; moduleName: (key: string) => string; pattern?: string }) {
  return (
    <li className="relative flex gap-3 pb-5 last:pb-0 [&:not(:last-child)]:before:absolute [&:not(:last-child)]:before:top-9 [&:not(:last-child)]:before:bottom-1 [&:not(:last-child)]:before:left-4 [&:not(:last-child)]:before:w-px [&:not(:last-child)]:before:bg-border">
      <ActionIcon action={e.action} />
      <div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-x-3 gap-y-0.5 pt-1.5 text-sm">
        <AuditSentence e={e} moduleName={moduleName} />
        <span className="shrink-0 text-xs text-muted-foreground"><When at={e.at} pattern={pattern} /></span>
      </div>
    </li>
  );
}

/** "Sok suspended Angkor Coffee": who did what to which organisation or account, with links. */
export function AuditSentence({ e, moduleName }: { e: PlatformAuditEntry; moduleName: (key: string) => string }) {
  const { t } = useT();
  const href = e.targetType === 'organisation' ? `/admin/organisations/${e.targetId}` : `/admin/users/${e.targetId}`;
  const target = '\u0000';
  const key = ACTIONS[e.action];
  const text = key ? t(key, { target, module: moduleName(String(e.data.module ?? '')) }) : `${e.action} ${target}`;
  const [before, after] = text.split(target);
  return (
    <span className="break-words">
      {e.actor ? <Link href={`/admin/users/${e.actor.id}`} className="font-medium hover:underline">{e.actor.name}</Link> : t('platform.someone')}{' '}
      {before}
      <Link href={href} className="text-primary hover:underline">{e.targetName ?? t('platform.gone')}</Link>
      {after}
      {typeof e.data.reason === 'string' && <span className="mt-0.5 block text-xs text-muted-foreground">“{e.data.reason}”</span>}
    </span>
  );
}

/** The platform actions about one organisation or account, newest first. */
export function TargetHistory({ target }: { target: string }) {
  const { t } = useT();
  const [page, setPage] = useState(0);
  const { data, error } = usePlatformAudit(target, page, 10);
  const stats = usePlatformStats();
  const name = (key: string) => {
    const m = stats.data?.modules.find((x) => x.key === key);
    return m ? moduleName(m) : key;
  };
  return (
    <Panel title={t('platform.history')}>
      {error ? <ErrorState error={error} /> : !data ? <ListSkeleton rows={2} /> : data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('platform.noHistory')}</p>
      ) : (
        <>
          <ul>{data.items.map((e) => <AuditRow key={e.id} e={e} moduleName={name} pattern="d MMM yyyy HH:mm" />)}</ul>
          <Pager className="mt-3 border-t px-0 pb-0" page={page} pages={Math.max(1, Math.ceil(data.total / 10))} onPage={setPage} />
        </>
      )}
    </Panel>
  );
}
