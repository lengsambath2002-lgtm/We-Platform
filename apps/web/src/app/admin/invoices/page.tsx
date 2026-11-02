'use client';

import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, Receipt, Wallet } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { ConfirmAction, ConsoleHeader, Facts, ListPanel, ListRow, Segmented } from '@/components/platform/ui';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type Invoice, invoiceApi, usePlatformInvoices } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { cn } from '@/lib/utils';

const METHODS = ['BANK_TRANSFER', 'KHQR', 'ABA_PAYWAY', 'CASH', 'OTHER'] as const;

function PaymentDialog({ invoice, onClose }: { invoice: Invoice; onClose: () => void }) {
  const { t } = useT();
  const qc = useQueryClient();
  const [method, setMethod] = useState<string>(invoice.claimedAt ? 'BANK_TRANSFER' : 'KHQR');
  const [reference, setReference] = useState(invoice.claimReference ?? '');
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('platform.recordPayment')}</DialogTitle><DialogDescription>{t('platform.recordPaymentBody', { number: invoice.number, amount: usd(invoice.amount), org: invoice.orgName })}</DialogDescription></DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5"><Label htmlFor="pm">{t('platform.paymentMethod')}</Label>
            <Select value={method} onValueChange={setMethod}><SelectTrigger id="pm" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>{METHODS.map((m) => <SelectItem key={m} value={m}>{tr(`platform.method_${m}` as MessageKey)}</SelectItem>)}</SelectContent></Select></div>
          <div className="space-y-1.5"><Label htmlFor="pr">{t('platform.paymentReference')}</Label><Input id="pr" maxLength={120} value={reference} onChange={(e) => setReference(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={busy} onClick={async () => {
            setBusy(true);
            try { await invoiceApi.record(invoice.id, method, reference.trim() || null); await qc.invalidateQueries({ queryKey: ['platform'] }); toast.success(t('platform.paymentRecorded')); onClose(); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
          }}>{t('platform.recordPayment')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Super admins: invoices to organisations; confirm the payments they say they made. */
export default function InvoicesAdmin() {
  const { t } = useT();
  const qc = useQueryClient();
  const [status, setStatus] = useState('OPEN');
  const { data, error, isPending, refetch } = usePlatformInvoices(status === 'ALL' ? '' : status);
  const open = usePlatformInvoices('OPEN');
  const [paying, setPaying] = useState<Invoice | null>(null);
  const openList = open.data ?? [];
  const summary = [
    { label: t('platform.invToCollect'), value: usd(openList.reduce((s, i) => s + Number(i.amount), 0)), icon: Wallet },
    { label: t('platform.inv_OPEN'), value: openList.length.toLocaleString(), icon: Receipt },
    { label: t('platform.invOverdue'), value: openList.filter((i) => i.overdue).length.toLocaleString(), icon: AlertTriangle, urgent: openList.some((i) => i.overdue) },
    { label: t('platform.attnClaims'), value: openList.filter((i) => i.claimedAt).length.toLocaleString(), icon: CheckCircle2 },
  ];
  return (
    <>
      <ConsoleHeader title={t('platform.navInvoices')} subtitle={t('platform.invoicesLead')} />
      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {summary.map((x) => (
          <div key={x.label} className="rounded-xl border bg-card p-4 shadow-xs">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm text-muted-foreground">{x.label}</p>
              <x.icon className={cn('size-4', x.urgent ? 'text-amber-600 dark:text-amber-400' : 'text-muted-foreground')} aria-hidden />
            </div>
            <p className="mt-2 text-2xl font-semibold tracking-tight tabular">{open.data ? x.value : '—'}</p>
          </div>
        ))}
      </div>
      <div className="mb-4">
        <Segmented label={t('platform.show')} value={status} onChange={setStatus}
          options={['OPEN', 'PAID', 'VOID', 'ALL'].map((s) => ({ value: s, label: tr(`platform.inv_${s}` as MessageKey) }))} />
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : data.length === 0 ? (
        <EmptyState icon={Receipt} title={t('platform.invNone')} />
      ) : (
        <ListPanel>
          {data.map((i) => (
            <ListRow key={i.id}
              lead={<span className="grid size-9 shrink-0 place-items-center rounded-lg border bg-muted/60 text-muted-foreground"><Receipt className="size-4" aria-hidden /></span>}
              title={<span className="flex items-center gap-2"><span className="font-mono">{i.number}</span><span className="text-muted-foreground">·</span><span className="truncate">{i.orgName}</span></span>}
              sub={<>{i.planCode} · {i.seats} · {i.claimedAt ? t('platform.claimedRef', { reference: i.claimReference ?? '' }) : i.paidAt ? t('platform.paidRef', { date: day(i.paidAt) }) : i.status === 'VOID' ? (i.voidReason ?? tr('platform.inv_VOID')) : '—'}</>}
              meta={<Facts items={[{ label: t('platform.invoiceDue'), value: <span className={i.overdue ? 'text-destructive' : undefined}>{day(i.dueAt)}</span> }]} />}
              aside={<>
                {i.overdue && <Badge className="border-destructive/20 bg-destructive/10 text-destructive">{t('platform.invOverdue')}</Badge>}
                {i.status !== 'OPEN' && <Badge variant="outline">{tr(`platform.inv_${i.status}` as MessageKey)}</Badge>}
                <span className="w-24 text-right text-base font-semibold tabular">{usd(i.amount)}</span>
                {i.status === 'OPEN' && (
                  <div className="flex gap-1">
                    <Button size="sm" onClick={() => setPaying(i)}>{t('platform.recordPayment')}</Button>
                    <ConfirmAction destructive trigger={<Button size="sm" variant="ghost" className="text-muted-foreground">{t('platform.void')}</Button>}
                      title={t('platform.voidTitle', { number: i.number })} body={t('platform.voidBody')}
                      reason={{ label: t('platform.voidReason'), hint: t('platform.voidReasonHint') }} confirm={t('platform.void')}
                      run={async (reason) => { await invoiceApi.void(i.id, reason); await qc.invalidateQueries({ queryKey: ['platform'] }); toast.success(t('platform.voided', { number: i.number })); }} />
                  </div>
                )}
              </>} />
          ))}
        </ListPanel>
      )}
      {paying && <PaymentDialog invoice={paying} onClose={() => setPaying(null)} />}
    </>
  );
}
