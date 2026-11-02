'use client';

import { Plus, Trash2 } from 'lucide-react';
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
import { errorMessage } from '@/lib/api/client';
import { type Order, type PoStatus, type Supplier, PAY_METHODS, purchasingApi, useSuppliers } from '@/lib/api/purchasing';
import { useOrgMutation } from '@/lib/api/queries';
import { useRetailShops } from '@/lib/api/sso';
import { usd } from '@/lib/money';
import { cn } from '@/lib/utils';

const NONE = 'none';
const alert = (msg: string | null) => msg && <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{msg}</p>;
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Phnom_Penh' }).format(new Date());

const TONE: Record<PoStatus, string> = {
  DRAFT: 'bg-muted text-foreground',
  ORDERED: 'bg-sky-50 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
  PARTIAL: 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  RECEIVED: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  CANCELLED: 'bg-muted text-muted-foreground line-through',
};

export function PoBadge({ status }: { status: PoStatus }) {
  return <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[status])}>{tr(`retail.po_${status}`)}</span>;
}

/** Owners and Admins: adds or edits a supplier. */
export function SupplierDialog({ org, supplier, onClose }: { org: string; supplier: Supplier | 'new'; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const s = supplier === 'new' ? null : supplier;
  const [v, setV] = useState({
    name: s?.name ?? '', contactName: s?.contactName ?? '', email: s?.email ?? '', phone: s?.phone ?? '', address: s?.address ?? '',
    taxId: s?.taxId ?? '', paymentTermsDays: String(s?.paymentTermsDays ?? 30), notes: s?.notes ?? '',
  });
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => purchasingApi.saveSupplier(org, s?.id ?? null, body));
  const field = (key: keyof typeof v, label: string, type = 'text') => (
    <div className="space-y-1.5"><Label htmlFor={`sup-${key}`}>{label}</Label><Input id={`sup-${key}`} type={type} value={v[key]} onChange={(e) => setV({ ...v, [key]: e.target.value })} /></div>
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{s ? t('retail.editSupplier') : t('retail.newSupplier')}</DialogTitle></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          {field('name', t('retail.supplierName'))}
          <div className="grid grid-cols-2 gap-3">
            {field('contactName', t('retail.contactName'))}
            {field('phone', t('retail.phone'), 'tel')}
            {field('email', t('retail.email'), 'email')}
            {field('taxId', t('retail.taxId'))}
            {field('paymentTermsDays', t('retail.paymentTerms'), 'number')}
          </div>
          {field('address', t('retail.address'))}
          <div className="space-y-1.5"><Label htmlFor="sup-notes">{t('retail.notes')}</Label><Textarea id="sup-notes" rows={2} maxLength={2000} value={v.notes} onChange={(e) => setV({ ...v, notes: e.target.value })} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!v.name.trim()) { setErr(t('retail.errSupplierName')); return; }
            if (!/^\d{1,3}$/.test(v.paymentTermsDays) || Number(v.paymentTermsDays) > 365) { setErr(t('retail.errTerms')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({
                name: v.name.trim(), contactName: v.contactName || null, email: v.email || null, phone: v.phone || null, address: v.address || null,
                taxId: v.taxId || null, paymentTermsDays: Number(v.paymentTermsDays), notes: v.notes || null,
              });
              toast.success(t('retail.supplierSaved'));
              onClose();
              if (!s) router.push(`/${org}/business/suppliers/${saved.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type Line = { description: string; sku: string; quantity: string; unitCost: string };

/** Owners and Admins: a new purchase order, or a draft's changes: supplier, shop, expected date and the lines. */
export function OrderDialog({ org, order, supplierId, onClose }: { org: string; order?: Order; supplierId?: string; onClose: () => void }) {
  const { t } = useT();
  const router = useRouter();
  const suppliers = useSuppliers(org);
  const shops = useRetailShops(org);
  const [supplier, setSupplier] = useState(order?.summary.supplierId ?? supplierId ?? NONE);
  const [shop, setShop] = useState(order?.shopBusinessId ?? NONE);
  const [expected, setExpected] = useState(order?.summary.expectedDate ?? '');
  const [notes, setNotes] = useState(order?.notes ?? '');
  const [lines, setLines] = useState<Line[]>(order?.lines.map((l) => ({ description: l.description, sku: l.sku ?? '', quantity: String(Number(l.quantity)), unitCost: Number(l.unitCost).toFixed(2) }))
    ?? [{ description: '', sku: '', quantity: '1', unitCost: '' }]);
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => purchasingApi.saveOrder(org, order?.summary.id ?? null, body));
  const total = lines.reduce((sum, l) => sum + (Number(l.quantity) || 0) * (Number(l.unitCost) || 0), 0);
  const set = (i: number, patch: Partial<Line>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader><DialogTitle>{order ? t('retail.editOrder') : t('retail.newOrder')}</DialogTitle><DialogDescription>{t('retail.orderBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="po-supplier">{t('retail.supplier')}</Label>
              <Select value={supplier} onValueChange={setSupplier}>
                <SelectTrigger id="po-supplier" className="w-full"><SelectValue placeholder={t('retail.chooseSupplier')} /></SelectTrigger>
                <SelectContent>{(suppliers.data ?? []).map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="po-shop">{t('retail.forShop')}</Label>
              <Select value={shop} onValueChange={setShop}>
                <SelectTrigger id="po-shop" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value={NONE}>{t('retail.noShop')}</SelectItem>{(shops.data?.shops ?? []).map((s) => <SelectItem key={s.businessId} value={s.businessId}>{s.businessName}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5"><Label htmlFor="po-expected">{t('retail.expected')}</Label><Input id="po-expected" type="date" value={expected} onChange={(e) => setExpected(e.target.value)} /></div>
          </div>
          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-medium">{t('retail.lines')}</legend>
            <div className="hidden grid-cols-[1fr_7rem_5rem_6rem_2.25rem] gap-2 text-xs text-muted-foreground sm:grid">
              <span>{t('retail.item')}</span><span>{t('retail.sku')}</span><span>{t('retail.qty')}</span><span>{t('retail.unitCost')}</span><span />
            </div>
            {lines.map((l, i) => (
              <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-[1fr_7rem_5rem_6rem_2.25rem]">
                <Input className="col-span-2 sm:col-span-1" aria-label={t('retail.item')} placeholder={t('retail.item')} maxLength={300} value={l.description} onChange={(e) => set(i, { description: e.target.value })} />
                <Input aria-label={t('retail.sku')} placeholder={t('retail.sku')} maxLength={60} value={l.sku} onChange={(e) => set(i, { sku: e.target.value })} />
                <Input aria-label={t('retail.qty')} inputMode="decimal" value={l.quantity} onChange={(e) => set(i, { quantity: e.target.value })} />
                <Input aria-label={t('retail.unitCost')} inputMode="decimal" placeholder="0.00" value={l.unitCost} onChange={(e) => set(i, { unitCost: e.target.value })} />
                <Button type="button" variant="ghost" size="icon" className="size-9" aria-label={t('common.remove')} disabled={lines.length <= 1} onClick={() => setLines(lines.filter((_, j) => j !== i))}><Trash2 className="size-4" /></Button>
              </div>
            ))}
            <div className="flex items-center justify-between">
              <Button type="button" variant="outline" size="sm" onClick={() => setLines([...lines, { description: '', sku: '', quantity: '1', unitCost: '' }])}><Plus className="size-4" />{t('retail.addLine')}</Button>
              <span className="text-sm">{t('retail.total')}: <span className="font-semibold tabular">{usd(total)}</span></span>
            </div>
          </fieldset>
          <div className="space-y-1.5"><Label htmlFor="po-notes">{t('retail.notes')}</Label><Textarea id="po-notes" rows={2} maxLength={4000} value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (supplier === NONE) { setErr(t('retail.chooseSupplier')); return; }
            const bad = lines.some((l) => !l.description.trim() || !/^\d{1,6}(\.\d{1,3})?$/.test(l.quantity.trim()) || Number(l.quantity) <= 0 || !/^\d{1,10}(\.\d{1,2})?$/.test(l.unitCost.trim()));
            if (bad) { setErr(t('retail.errLines')); return; }
            setErr(null);
            try {
              const saved = await save.mutateAsync({
                supplierId: supplier, shopBusinessId: shop === NONE ? null : shop, expectedDate: expected || null, notes: notes || null,
                lines: lines.map((l) => ({ description: l.description.trim(), sku: l.sku.trim() || null, quantity: l.quantity.trim(), unitCost: l.unitCost.trim() })),
              });
              toast.success(t('retail.orderSaved'));
              onClose();
              if (!order) router.push(`/${org}/business/purchase-orders/${saved.summary.id}`);
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Records a delivery: what arrived of each line still to come. */
export function ReceiveDialog({ org, order, onClose }: { org: string; order: Order; onClose: () => void }) {
  const { t } = useT();
  const open = order.lines.filter((l) => Number(l.received) < Number(l.quantity));
  const [qty, setQty] = useState<Record<string, string>>(Object.fromEntries(open.map((l) => [l.id, String(Number(l.quantity) - Number(l.received))])));
  const [on, setOn] = useState(today());
  const [note, setNote] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => purchasingApi.receive(org, order.summary.id, body));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader><DialogTitle>{t('retail.receiveTitle')}</DialogTitle><DialogDescription>{t('retail.receiveBody')}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="space-y-3">
          {open.map((l) => (
            <div key={l.id} className="grid grid-cols-[1fr_6rem] items-center gap-3">
              <Label htmlFor={`rcv-${l.id}`} className="font-normal">
                <span className="block text-sm">{l.description}</span>
                <span className="block text-xs text-muted-foreground">{t('retail.stillToCome', { n: Number(l.quantity) - Number(l.received) })}{l.sku ? ` · ${l.sku}` : ''}</span>
              </Label>
              <Input id={`rcv-${l.id}`} inputMode="decimal" value={qty[l.id] ?? ''} onChange={(e) => setQty({ ...qty, [l.id]: e.target.value })} />
            </div>
          ))}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5"><Label htmlFor="rcv-on">{t('retail.receivedOn')}</Label><Input id="rcv-on" type="date" max={today()} value={on} onChange={(e) => setOn(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="rcv-note">{t('retail.note')}</Label><Input id="rcv-note" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} /></div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (Object.values(qty).some((q) => q.trim() !== '' && !/^\d{1,6}(\.\d{1,3})?$/.test(q.trim()))) { setErr(t('retail.errQty')); return; }
            setErr(null);
            try {
              await save.mutateAsync({ receivedOn: on || null, note: note || null, lines: open.map((l) => ({ lineId: l.id, quantity: qty[l.id]?.trim() || '0' })) });
              toast.success(t('retail.received'));
              onClose();
            } catch (e) { setErr(errorMessage(e)); }
          }}>{t('retail.recordDelivery')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Records a payment to the supplier. */
export function PayDialog({ org, order, onClose }: { org: string; order: Order; onClose: () => void }) {
  const { t } = useT();
  const [amount, setAmount] = useState(Number(order.summary.balance).toFixed(2));
  const [on, setOn] = useState(today());
  const [method, setMethod] = useState<(typeof PAY_METHODS)[number]>('BANK_TRANSFER');
  const [reference, setReference] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (body: unknown) => purchasingApi.pay(org, order.summary.id, body));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('retail.payTitle', { supplier: order.summary.supplierName })}</DialogTitle><DialogDescription>{t('retail.leftToPay', { amount: usd(order.summary.balance) })}</DialogDescription></DialogHeader>
        {alert(err)}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5"><Label htmlFor="pay-amount">{t('retail.amount')}</Label><Input id="pay-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /></div>
          <div className="space-y-1.5"><Label htmlFor="pay-on">{t('retail.paidOn')}</Label><Input id="pay-on" type="date" value={on} onChange={(e) => setOn(e.target.value)} /></div>
          <div className="space-y-1.5">
            <Label htmlFor="pay-method">{t('retail.method')}</Label>
            <Select value={method} onValueChange={(m) => setMethod(m as typeof method)}>
              <SelectTrigger id="pay-method" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>{PAY_METHODS.map((m) => <SelectItem key={m} value={m}>{t(`retail.method_${m}`)}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5"><Label htmlFor="pay-ref">{t('retail.reference')}</Label><Input id="pay-ref" maxLength={120} value={reference} onChange={(e) => setReference(e.target.value)} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending} onClick={async () => {
            if (!/^\d{1,12}(\.\d{1,2})?$/.test(amount.trim()) || Number(amount) <= 0) { setErr(t('retail.errAmount')); return; }
            setErr(null);
            try { await save.mutateAsync({ amount: amount.trim(), paidOn: on, method, reference: reference || null }); toast.success(t('retail.paymentRecorded')); onClose(); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('retail.recordPayment')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Cancels an order nothing has arrived for, with the reason. */
export function CancelOrderDialog({ org, order, onClose }: { org: string; order: Order; onClose: () => void }) {
  const { t } = useT();
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const save = useOrgMutation(org, (why: string) => purchasingApi.cancel(org, order.summary.id, why));
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('retail.cancelTitle', { number: order.summary.number ?? '' })}</DialogTitle></DialogHeader>
        {alert(err)}
        <Label htmlFor="cancel-why">{t('retail.cancelReason')}</Label>
        <Textarea id="cancel-why" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="destructive" disabled={save.isPending} onClick={async () => {
            if (!reason.trim()) { setErr(t('retail.errReason')); return; }
            try { await save.mutateAsync(reason.trim()); toast.success(t('retail.orderCancelled')); onClose(); } catch (e) { setErr(errorMessage(e)); }
          }}>{t('retail.cancelOrder')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
