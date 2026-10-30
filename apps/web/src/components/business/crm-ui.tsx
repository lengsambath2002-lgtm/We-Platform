'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { tr } from '@/i18n/core';
import { useT } from '@/i18n/provider';
import { useCustomers } from '@/lib/api/business';
import { errorMessage } from '@/lib/api/client';
import { type Deal, type DealStage, DEAL_SOURCES, crmApi } from '@/lib/api/crm';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import { cn } from '@/lib/utils';

const NONE = 'none';
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;

const TONE: Record<DealStage, string> = {
  LEAD: 'bg-muted text-foreground',
  QUALIFIED: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  PROPOSAL: 'bg-violet-50 text-violet-700 dark:bg-violet-950 dark:text-violet-300',
  NEGOTIATION: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  WON: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  LOST: 'bg-destructive/10 text-destructive',
};

export function DealStageBadge({ stage }: { stage: DealStage }) {
  return <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[stage])}>{tr(`business.stage_${stage}`)}</span>;
}

/** The deal's customer, or the lead's company / contact. */
export const dealWho = (d: Pick<Deal, 'customer' | 'company' | 'contactName'>) => d.customer?.name ?? d.company ?? d.contactName ?? '';

/** Adds a deal (a lead or for a customer), or edits one. */
export function DealDialog({ org, deal, onClose }: { org: string; deal: Deal | 'new'; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const d = deal === 'new' ? null : deal;
  const customers = useCustomers(org, { size: 100 });
  const members = useMembers(org);
  const [v, setV] = useState({
    title: d?.title ?? '', forCustomer: d ? !!d.customer : false, customerId: d?.customer?.id ?? NONE,
    company: d?.company ?? '', contactName: d?.contactName ?? '', email: d?.email ?? '', phone: d?.phone ?? '',
    value: d ? Number(d.value).toFixed(2) : '', source: d?.source ?? 'REFERRAL', expectedClose: d?.expectedClose ?? '',
    ownerId: d?.owner.id ?? NONE, nextStep: d?.nextStep ?? '', nextStepDate: d?.nextStepDate ?? '', notes: d?.notes ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => crmApi.saveDeal(org, d?.id ?? null, body));
  const field = (key: 'title' | 'company' | 'contactName' | 'email' | 'phone' | 'value' | 'expectedClose' | 'nextStep' | 'nextStepDate', label: string, type = 'text') => (
    <div className="space-y-1.5"><Label htmlFor={`deal-${key}`}>{label}</Label><Input id={`deal-${key}`} type={type} value={v[key]} onChange={(e) => setV({ ...v, [key]: e.target.value })} /></div>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{d ? t('business.editDeal') : t('business.newDeal')}</DialogTitle><DialogDescription>{t('business.dealBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          {field('title', t('business.dealTitle'))}
          <div role="radiogroup" aria-label={t('business.dealFor')} className="grid grid-cols-2 gap-2">
            {[false, true].map((c) => (
              <button key={String(c)} type="button" role="radio" aria-checked={v.forCustomer === c} onClick={() => setV({ ...v, forCustomer: c })}
                className={cn('rounded-md border px-3 py-2 text-sm', v.forCustomer === c ? 'border-primary bg-primary/10 font-medium text-primary' : 'hover:bg-muted')}>
                {t(c ? 'business.dealForCustomer' : 'business.dealForLead')}
              </button>
            ))}
          </div>
          {v.forCustomer ? (
            <div className="space-y-1.5">
              <Label htmlFor="deal-customer">{t('business.customer')}</Label>
              <Select value={v.customerId} onValueChange={(x) => setV({ ...v, customerId: x })}>
                <SelectTrigger id="deal-customer" className="w-full"><SelectValue placeholder={t('business.chooseCustomer')} /></SelectTrigger>
                <SelectContent>{(customers.data?.items ?? []).filter((c) => !c.archived).map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {field('company', t('business.leadCompany'))}
              {field('contactName', t('business.leadContact'))}
              {field('email', t('business.leadEmail'), 'email')}
              {field('phone', t('business.leadPhone'), 'tel')}
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            {field('value', t('business.dealValue'))}
            <div className="space-y-1.5">
              <Label htmlFor="deal-source">{t('business.dealSource')}</Label>
              <Select value={v.source} onValueChange={(x) => setV({ ...v, source: x as typeof v.source })}>
                <SelectTrigger id="deal-source" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{DEAL_SOURCES.map((s) => <SelectItem key={s} value={s}>{t(`business.dealSource_${s}`)}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            {field('expectedClose', t('business.expectedClose'), 'date')}
            <div className="space-y-1.5">
              <Label htmlFor="deal-owner">{t('business.dealOwner')}</Label>
              <Select value={v.ownerId} onValueChange={(x) => setV({ ...v, ownerId: x })}>
                <SelectTrigger id="deal-owner" className="w-full"><SelectValue placeholder={t('business.me')} /></SelectTrigger>
                <SelectContent>{(members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST').map((m) => <SelectItem key={m.userId} value={m.userId}>{m.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            {field('nextStep', t('business.nextStep'))}
            {field('nextStepDate', t('business.nextStepDate'), 'date')}
          </div>
          <div className="space-y-1.5"><Label htmlFor="deal-notes">{t('business.notes')}</Label><Textarea id="deal-notes" rows={3} maxLength={4000} value={v.notes} onChange={(e) => setV({ ...v, notes: e.target.value })} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.title.trim()) { setErr(t('business.errDealTitle')); return; }
            if (!/^\d{1,12}(\.\d{1,2})?$/.test(v.value.trim())) { setErr(t('business.errDealValue')); return; }
            if (v.forCustomer && v.customerId === NONE) { setErr(t('business.chooseCustomer')); return; }
            if (!v.forCustomer && !v.company.trim() && !v.contactName.trim()) { setErr(t('business.errLead')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({
                title: v.title.trim(), customerId: v.forCustomer ? v.customerId : null,
                company: v.forCustomer ? null : v.company || null, contactName: v.forCustomer ? null : v.contactName || null,
                email: v.forCustomer ? null : v.email || null, phone: v.forCustomer ? null : v.phone || null,
                value: v.value.trim(), source: v.source, expectedClose: v.expectedClose || null, ownerId: v.ownerId === NONE ? null : v.ownerId,
                nextStep: v.nextStep || null, nextStepDate: v.nextStepDate || null, notes: v.notes || null,
              });
              toast.success(t('business.dealSaved'));
              onClose();
              if (!d) router.push(`/${org}/business/deals/${saved.deal.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Marks a deal lost, with the reason. */
export function LostDialog({ org, dealId, onClose }: { org: string; dealId: string; onClose: () => void }) {
  const { t } = useT();
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const move = useOrgMutation(org, (why: string) => crmApi.move(org, dealId, 'LOST', why));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('business.lostTitle')}</DialogTitle></DialogHeader>
        {alert(err)}
        <Label htmlFor="lost-why">{t('business.lostReason')}</Label>
        <Textarea id="lost-why" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t('business.lostReasonHint')} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="destructive" disabled={move.isPending} onClick={async () => {
            if (!reason.trim()) { setErr(t('business.errLostReason')); return; }
            try { await move.mutateAsync(reason.trim()); toast.success(t('business.dealLost')); onClose(); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('business.markLost')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
