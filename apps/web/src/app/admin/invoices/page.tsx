'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { tr } from '@/i18n/core';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type Invoice, invoiceApi, usePlatformInvoices } from '@/lib/api/billing';
import { errorMessage } from '@/lib/api/client';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';

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
  const [paying, setPaying] = useState<Invoice | null>(null);
  return (
    <>
      <div className="mb-4"><h1 className="text-xl font-semibold">{t('platform.navInvoices')}</h1><p className="text-sm text-muted-foreground">{t('platform.invoicesLead')}</p></div>
      <Tabs value={status} onValueChange={setStatus}><TabsList className="mb-4">
        {['OPEN', 'PAID', 'VOID', 'ALL'].map((s) => <TabsTrigger key={s} value={s}>{tr(`platform.inv_${s}` as MessageKey)}</TabsTrigger>)}
      </TabsList></Tabs>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton /> : (
        <div className="overflow-x-auto rounded-lg border bg-card">
          <Table>
            <TableHeader><TableRow><TableHead>{t('platform.invoice')}</TableHead><TableHead>{t('platform.colOrg')}</TableHead><TableHead>{t('platform.invoiceDue')}</TableHead><TableHead className="text-right">{t('platform.invoiceAmount')}</TableHead><TableHead>{t('platform.invoiceClaim')}</TableHead><TableHead className="w-40" /></TableRow></TableHeader>
            <TableBody>{data.map((i) => (
              <TableRow key={i.id}>
                <TableCell><span className="font-medium">{i.number}</span><span className="block text-xs text-muted-foreground">{i.planCode} · {i.seats}</span></TableCell>
                <TableCell>{i.orgName}</TableCell>
                <TableCell className={i.overdue ? 'text-destructive' : undefined}>{day(i.dueAt)}</TableCell>
                <TableCell className="text-right tabular">{usd(i.amount)}</TableCell>
                <TableCell className="text-sm">{i.claimedAt ? t('platform.claimedRef', { reference: i.claimReference ?? '' }) : i.paidAt ? t('platform.paidRef', { date: day(i.paidAt) }) : '—'}</TableCell>
                <TableCell className="text-right">{i.status === 'OPEN' && <>
                  <Button size="sm" onClick={() => setPaying(i)}>{t('platform.recordPayment')}</Button>
                  <Button size="sm" variant="ghost" onClick={async () => {
                    const reason = window.prompt(t('platform.voidReason'));
                    if (!reason) return;
                    try { await invoiceApi.void(i.id, reason); await qc.invalidateQueries({ queryKey: ['platform'] }); } catch (e) { toast.error(errorMessage(e)); }
                  }}>{t('platform.void')}</Button>
                </>}</TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        </div>
      )}
      {paying && <PaymentDialog invoice={paying} onClose={() => setPaying(null)} />}
    </>
  );
}
