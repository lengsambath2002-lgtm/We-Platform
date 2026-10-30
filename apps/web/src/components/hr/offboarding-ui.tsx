'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { labels } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { usePeople } from '@/lib/api/hr';
import { LEAVE_KINDS, type LeaveKind, type OffDetail, type OffStatus, offboardingApi } from '@/lib/api/offboarding';
import { useOrgMutation } from '@/lib/api/queries';
import { cn } from '@/lib/utils';

export const KIND_LABEL = labels(LEAVE_KINDS, (k) => `offboarding.kind_${k}`);
const STATUSES: OffStatus[] = ['REQUESTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
export const STATUS_LABEL = labels(STATUSES, (s) => `offboarding.status_${s}`);
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Phnom_Penh' }).format(new Date());
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

const TONE: Record<OffStatus, string> = {
  REQUESTED: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  IN_PROGRESS: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  COMPLETED: 'bg-muted text-foreground',
  CANCELLED: 'bg-muted text-muted-foreground line-through',
};

export function OffBadge({ status }: { status: OffStatus }) {
  return <span className={cn('inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[status])}>{STATUS_LABEL[status]}</span>;
}

/** The person's own resignation: their proposed last day and why. */
export function ResignDialog({ org, onClose }: { org: string; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const [lastDay, setLastDay] = useState('');
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, () => offboardingApi.resign(org, { lastDay, reason: reason.trim() || null }));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('offboarding.resignTitle')}</DialogTitle><DialogDescription>{t('offboarding.resignBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-3">
          <div className="space-y-1.5"><Label htmlFor="rs-last">{t('offboarding.lastDay')}</Label><Input id="rs-last" type="date" min={today()} value={lastDay} onChange={(e) => setLastDay(e.target.value)} /></div>
          <div className="space-y-1.5"><Label htmlFor="rs-reason">{t('offboarding.reason')}</Label><Textarea id="rs-reason" rows={3} maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="destructive" disabled={save.isPending} onClick={async () => {
            if (!lastDay) { setErr(t('offboarding.errLastDay')); return; }
            setErr(null);
            try { const d = await save.mutateAsync(undefined); toast.success(t('offboarding.resigned')); router.push(`/${org}/hr/offboarding/${d.summary.id}`); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('offboarding.resign')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * HR: starts someone's departure ({@code person} preselected), or — given {@code detail} — accepts a resignation or
 * changes an open one.
 */
export function DepartureDialog({ org, person, detail, onClose }: { org: string; person?: string; detail?: OffDetail; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const people = usePeople(org);
  const accepting = detail?.summary.status === 'REQUESTED';
  const [userId, setUserId] = useState(person ?? '');
  const [kind, setKind] = useState<LeaveKind>(detail?.summary.kind ?? 'RESIGNATION');
  const [lastDay, setLastDay] = useState(detail?.summary.lastDay ?? '');
  const [reason, setReason] = useState(detail?.reason ?? '');
  const [rehire, setRehire] = useState(detail?.rehire ?? true);
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, () => {
    const body = { kind, lastDay, reason: reason.trim() || null, rehire };
    if (!detail) return offboardingApi.start(org, { ...body, userId });
    return accepting ? offboardingApi.accept(org, detail.summary.id, body) : offboardingApi.update(org, detail.summary.id, body);
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{!detail ? t('offboarding.startTitle') : accepting ? t('offboarding.acceptTitle') : t('offboarding.editTitle')}</DialogTitle>
          <DialogDescription>{!detail || accepting ? t('offboarding.startBody') : t('offboarding.editBody')}</DialogDescription>
        </DialogHeader>
        {alert(err)}
        <div className="space-y-3">
          {!detail && (
            <div className="space-y-1.5">
              <Label htmlFor="dp-person">{t('offboarding.person')}</Label>
              <Select value={userId} onValueChange={setUserId}>
                <SelectTrigger id="dp-person" className="w-full"><SelectValue placeholder={t('offboarding.choosePerson')} /></SelectTrigger>
                <SelectContent>{(people.data ?? []).map((p) => <SelectItem key={p.person.id} value={p.person.id}>{p.person.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="dp-kind">{t('offboarding.kind')}</Label>
            <Select value={kind} onValueChange={(v) => setKind(v as LeaveKind)}>
              <SelectTrigger id="dp-kind" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>{LEAVE_KINDS.map((k) => <SelectItem key={k} value={k}>{KIND_LABEL[k]}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5"><Label htmlFor="dp-last">{t('offboarding.lastDay')}</Label><Input id="dp-last" type="date" value={lastDay} onChange={(e) => setLastDay(e.target.value)} /></div>
          <div className="space-y-1.5"><Label htmlFor="dp-reason">{t('offboarding.reason')}</Label><Textarea id="dp-reason" rows={2} maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} /></div>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={rehire} onCheckedChange={(v) => setRehire(v === true)} />{t('offboarding.rehire')}</label>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!detail && !userId) { setErr(t('offboarding.errPerson')); return; }
            if (!lastDay) { setErr(t('offboarding.errLastDay')); return; }
            setErr(null);
            try {
              const d = await save.mutateAsync(undefined);
              toast.success(t(!detail ? 'offboarding.started' : accepting ? 'offboarding.accepted' : 'offboarding.saved'));
              onClose();
              if (!detail) router.push(`/${org}/hr/offboarding/${d.summary.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{accepting ? t('offboarding.accept') : t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
