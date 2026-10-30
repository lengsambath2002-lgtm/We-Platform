'use client';

import { parseISO } from 'date-fns';
import { ChevronLeft, ChevronRight, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { type ReactNode, useState } from 'react';

import {
  AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { ErrorState, ListSkeleton } from '@/components/common/states';
import { moduleName } from '@/components/shell/nav';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type AccountStatus, type OrgStatus, type PlatformAuditEntry, usePlatformAudit, usePlatformStats } from '@/lib/api/platform';
import { ago, fmt } from '@/lib/format';
import { cn } from '@/lib/utils';

const TONE: Record<OrgStatus | AccountStatus, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  SUSPENDED: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  LOCKED: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  DELETED: 'bg-muted text-muted-foreground',
  DISABLED: 'bg-destructive/10 text-destructive',
};

export function StatusBadge({ status }: { status: OrgStatus | AccountStatus }) {
  const { t } = useT();
  return <Badge className={cn('font-medium', TONE[status])}>{t(`platform.status${status}`)}</Badge>;
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

export function Pager({ page, pages, onPage }: { page: number; pages: number; onPage: (p: number) => void }) {
  const { t } = useT();
  if (pages <= 1) return null;
  return (
    <nav className="mt-4 flex items-center justify-between gap-2" aria-label={t('collab.pageOf', { page: page + 1, pages })}>
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}><ChevronLeft className="size-4" />{t('collab.newer')}</Button>
      <span className="text-sm text-muted-foreground tabular">{t('collab.pageOf', { page: page + 1, pages })}</span>
      <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>{t('collab.older')}<ChevronRight className="size-4" /></Button>
    </nav>
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
  'user.super_admin.granted': 'platform.act_user_super_admin_granted',
  'user.super_admin.revoked': 'platform.act_user_super_admin_revoked',
  'org.viewed': 'platform.act_org_viewed',
  'user.viewed': 'platform.act_user_viewed',
};

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
    <section className="mt-4 rounded-lg border bg-card p-4 sm:p-5">
      <h2 className="mb-3 text-base font-semibold">{t('platform.history')}</h2>
      {error ? <ErrorState error={error} /> : !data ? <ListSkeleton rows={2} /> : data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('platform.noHistory')}</p>
      ) : (
        <>
          <ul className="divide-y">
            {data.items.map((e) => (
              <li key={e.id} className="flex items-start justify-between gap-3 py-2.5 text-sm">
                <AuditSentence e={e} moduleName={name} />
                <span className="shrink-0 text-xs text-muted-foreground"><When at={e.at} pattern="d MMM yyyy HH:mm" /></span>
              </li>
            ))}
          </ul>
          <Pager page={page} pages={Math.max(1, Math.ceil(data.total / 10))} onPage={setPage} />
        </>
      )}
    </section>
  );
}
