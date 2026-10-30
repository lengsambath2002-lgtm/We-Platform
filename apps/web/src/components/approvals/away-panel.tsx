'use client';

import { Plane, X } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { type Delegation, delegationsApi, useDelegations } from '@/lib/api/delegations';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import { useSession } from '@/lib/auth/session';
import { day } from '@/lib/format';

const ME = '__me__';
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Phnom_Penh' }).format(new Date());

function AwayDialog({ org, canManage, onClose }: { org: string; canManage: boolean; onClose: () => void }) {
  const { t } = useT();
  const { user } = useSession();
  const members = useMembers(org);
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST');
  const [who, setWho] = useState(ME);
  const [delegate, setDelegate] = useState('');
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState('');
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: Parameters<typeof delegationsApi.create>[1]) => delegationsApi.create(org, body));
  const owner = who === ME ? user?.id : who;
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('approvals.awayNew')}</DialogTitle><DialogDescription>{t('approvals.awayBody')}</DialogDescription></DialogHeader>
        {err && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{err}</p>}
        <div className="space-y-4">
          {canManage && (
            <div className="space-y-1.5">
              <Label htmlFor="away-who">{t('approvals.awayFor')}</Label>
              <Select value={who} onValueChange={setWho}>
                <SelectTrigger id="away-who" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value={ME}>{t('approvals.awayMe')}</SelectItem>{people.filter((p) => p.userId !== user?.id).map((p) => <SelectItem key={p.userId} value={p.userId}>{p.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="away-delegate">{t('approvals.awayDelegate')}</Label>
            <Select value={delegate} onValueChange={setDelegate}>
              <SelectTrigger id="away-delegate" className="w-full"><SelectValue placeholder={t('approvals.awayChoose')} /></SelectTrigger>
              <SelectContent>{people.filter((p) => p.userId !== owner).map((p) => <SelectItem key={p.userId} value={p.userId}>{p.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5"><Label htmlFor="away-from">{t('approvals.awayFrom')}</Label><Input id="away-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="away-to">{t('approvals.awayTo')}</Label><Input id="away-to" type="date" min={from} value={to} onChange={(e) => setTo(e.target.value)} /></div>
          </div>
          <div className="space-y-1.5"><Label htmlFor="away-reason">{t('approvals.awayReason')}</Label><Input id="away-reason" maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!delegate) { setErr(t('approvals.awayErrDelegate')); return; }
            if (!from || !to) { setErr(t('approvals.awayErrDates')); return; }
            setErr(null);
            try {
              await save.mutateAsync({ userId: who === ME ? null : who, delegateId: delegate, startsOn: from, endsOn: to, reason: reason.trim() || null });
              toast.success(t('approvals.awaySaved'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Rows({ org, items }: { org: string; items: Delegation[] }) {
  const { t } = useT();
  const end = useOrgMutation(org, (id: string) => delegationsApi.end(org, id));
  return (
    <ul className="divide-y rounded-md border">
      {items.map((d) => (
        <li key={d.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm">
          <span className={d.active ? 'rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary' : 'rounded-full bg-muted px-2 py-0.5 text-xs'}>{t(d.active ? 'approvals.awayActive' : 'approvals.awayUpcoming')}</span>
          <span className="min-w-0 flex-1">{t('approvals.awayRow', { delegate: d.delegate.name, user: d.user.name })}
            <span className="block text-xs text-muted-foreground">{t('approvals.awayDates', { from: day(d.startsOn), to: day(d.endsOn) })}{d.reason ? ` · ${d.reason}` : ''}</span></span>
          {d.canRevoke && <Button variant="ghost" size="sm" onClick={() => end.mutateAsync(d.id).then(() => toast.success(t('approvals.awayEnded'))).catch((e) => toast.error(errorMessage(e)))}><X className="size-4" />{t('approvals.awayEnd')}</Button>}
        </li>
      ))}
    </ul>
  );
}

/** "Away? Hand over your approvals": the caller's acting approvers, and (Admins, HR managers) everyone's. */
export function AwayPanel({ org }: { org: string }) {
  const { t } = useT();
  const { data } = useDelegations(org);
  const [open, setOpen] = useState(false);
  if (!data) return null;
  return (
    <section aria-labelledby="away-h" className="mt-8 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 id="away-h" className="flex-1 text-base font-semibold">{t('approvals.awayTitle')}</h2>
        <Button variant="outline" size="sm" onClick={() => setOpen(true)}><Plane className="size-4" />{t('approvals.awayNew')}</Button>
      </div>
      <p className="max-w-3xl text-sm text-muted-foreground">{t('approvals.awayBody')}</p>
      {data.mine.length === 0 ? <p className="text-sm text-muted-foreground">{t('approvals.awayNone')}</p> : <Rows org={org} items={data.mine} />}
      {data.canManage && data.all.length > 0 && (
        <>
          <h3 className="pt-2 text-sm font-semibold">{t('approvals.awayEveryone')}</h3>
          <Rows org={org} items={data.all} />
        </>
      )}
      {open && <AwayDialog org={org} canManage={data.canManage} onClose={() => setOpen(false)} />}
    </section>
  );
}
