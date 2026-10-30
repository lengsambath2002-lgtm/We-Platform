'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { type ReactNode, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { PRIORITIES, PRIORITY_LABEL } from '@/components/common/badges';
import { applyServerErrors } from '@/components/forms/server-errors';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useT } from '@/i18n/provider';
import { ApiError, errorMessage } from '@/lib/api/client';
import { useMembers, useOrgMutation } from '@/lib/api/queries';
import {
  ASSET_STATUSES, type Asset, CATEGORIES, type Plan, type Site, type Staff, type SvcCustomer, type Ticket, svcApi, useAssets, useStaff,
  useSvcCustomer, useSvcCustomers,
} from '@/lib/api/service';

import { ASSET_LABEL, CATEGORY_LABEL } from './ui';

type T = ReturnType<typeof useT>['t'];
const NONE = 'none';
const PHONE = /^$|^[0-9+()\-. ]{6,30}$/;
const opt = (v: string) => (v.trim() ? v.trim() : null);
const sel = (v: string) => (v && v !== NONE ? v : null);

export function ErrorLine({ error }: { error: string | null }) {
  return error ? <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p> : null;
}

/** A labelled select with a "none" choice. */
function Pick({ id, label, value, onChange, options, none, disabled }: {
  id: string; label: ReactNode; value: string; onChange: (v: string) => void; options: { value: string; label: string }[]; none?: string; disabled?: boolean;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value || NONE} onValueChange={onChange} disabled={disabled}>
        <SelectTrigger id={id} className="w-full min-w-0"><SelectValue /></SelectTrigger>
        <SelectContent>
          {none && <SelectItem value={NONE}>{none}</SelectItem>}
          {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );
}

// ── Ticket ────────────────────────────────────────────────────────────────────────

const ticketSchema = (t: T) => z.object({
  title: z.string().trim().min(1, t('service.errTitle')).max(200),
  description: z.string().max(4000),
  category: z.string(),
  priority: z.string(),
  customerId: z.string(),
  siteId: z.string(),
  assetId: z.string(),
  requesterName: z.string().max(120),
  requesterPhone: z.string().refine((v) => PHONE.test(v.trim()), t('service.errPhone')),
  assigneeId: z.string(),
});
type TicketValues = z.infer<ReturnType<typeof ticketSchema>>;

/** Takes a service request (a new ticket) or edits one: what, who asked, where, how urgent, and who handles it. */
export function TicketDialog({ org, ticket, open, onOpenChange, onSaved }: {
  org: string; ticket?: Ticket; open: boolean; onOpenChange: (v: boolean) => void; onSaved?: (t: Ticket) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => ticketSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const x = ticket?.summary;
  const form = useForm<TicketValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: x?.title ?? '', description: ticket?.description ?? '', category: x?.category ?? 'REPAIR', priority: x?.priority ?? 'MEDIUM',
      customerId: x?.customer?.id ?? NONE, siteId: ticket?.site?.id ?? NONE, assetId: ticket?.asset?.id ?? NONE,
      requesterName: ticket?.requesterName ?? '', requesterPhone: ticket?.requesterPhone ?? '', assigneeId: x?.assignee?.id ?? NONE,
    },
  });
  const customers = useSvcCustomers(org, { size: 100 }, open);
  const customerId = sel(form.watch('customerId'));
  const detail = useSvcCustomer(org, customerId);
  const staff = useStaff(org, open && !ticket);
  const save = useOrgMutation(org, (v: TicketValues) => svcApi.saveTicket(org, ticket?.summary.id ?? null, {
    title: v.title.trim(), description: opt(v.description), category: v.category, priority: v.priority,
    customerId: sel(v.customerId), siteId: sel(v.siteId), assetId: sel(v.assetId), requesterName: opt(v.requesterName),
    requesterPhone: opt(v.requesterPhone), assigneeId: ticket ? null : sel(v.assigneeId),
  }));
  const submit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const saved = await save.mutateAsync(v);
      toast.success(ticket ? t('service.ticketSaved') : t('service.ticketCreated', { number: saved.summary.number }));
      onOpenChange(false);
      onSaved?.(saved);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const pick = (name: keyof TicketValues, label: string, options: { value: string; label: string }[], none?: string, onPick?: () => void) => (
    <FormField control={form.control} name={name} render={({ field }) => (
      <FormItem className="min-w-0">
        <FormLabel>{label}</FormLabel>
        <Select value={field.value || NONE} onValueChange={(v) => { field.onChange(v); onPick?.(); }}>
          <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue /></SelectTrigger></FormControl>
          <SelectContent>
            {none && <SelectItem value={NONE}>{none}</SelectItem>}
            {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
          </SelectContent>
        </Select>
        <FormMessage />
      </FormItem>
    )} />
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{ticket ? t('service.editTicket') : t('service.newRequest')}</DialogTitle>
          <DialogDescription>{t('service.requestBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            <FormField control={form.control} name="title" render={({ field }) => (
              <FormItem><FormLabel>{t('service.whatIsWrong')}</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <FormField control={form.control} name="description" render={({ field }) => (
              <FormItem><FormLabel>{t('service.details')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Textarea rows={3} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              {pick('category', t('service.category'), CATEGORIES.map((c) => ({ value: c, label: CATEGORY_LABEL[c] })))}
              {pick('priority', t('service.priority'), PRIORITIES.map((p) => ({ value: p, label: PRIORITY_LABEL[p] })))}
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              {pick('customerId', t('service.customer'), (customers.data?.items ?? []).map((c) => ({ value: c.id, label: c.name })), t('service.noCustomer'),
                () => { form.setValue('siteId', NONE); form.setValue('assetId', NONE); })}
              {pick('siteId', t('service.site'), (detail.data?.sites ?? []).map((s) => ({ value: s.id, label: s.name })), t('service.noSite'))}
              {pick('assetId', t('service.asset'), (detail.data?.assets ?? []).map((a) => ({ value: a.id, label: a.serial ? `${a.name} · ${a.serial}` : a.name })), t('service.noAsset'))}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="requesterName" render={({ field }) => (
                <FormItem><FormLabel>{t('service.requesterName')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="requesterPhone" render={({ field }) => (
                <FormItem><FormLabel>{t('service.requesterPhone')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></FormLabel><FormControl><Input type="tel" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            {!ticket && pick('assigneeId', t('service.assignee'), (staff.data ?? []).filter((s) => s.active).map((s) => ({ value: s.person.id, label: s.person.name })), t('common.unassigned'))}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={save.isPending}>{ticket ? t('common.save') : t('service.createTicket')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ── Customer ──────────────────────────────────────────────────────────────────────

const customerSchema = (t: T) => z.object({
  name: z.string().trim().min(1, t('service.errName')).max(200),
  contactName: z.string().max(120),
  phone: z.string().refine((v) => PHONE.test(v.trim()), t('service.errPhone')),
  email: z.string().max(160).refine((v) => !v.trim() || z.string().email().safeParse(v.trim()).success, t('service.errEmail')),
  address: z.string().max(500),
  notes: z.string().max(2000),
});
type CustomerValues = z.infer<ReturnType<typeof customerSchema>>;

export function CustomerDialog({ org, customer, open, onOpenChange, onSaved }: {
  org: string; customer?: SvcCustomer; open: boolean; onOpenChange: (v: boolean) => void; onSaved?: (c: SvcCustomer) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => customerSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<CustomerValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: customer?.name ?? '', contactName: customer?.contactName ?? '', phone: customer?.phone ?? '', email: customer?.email ?? '',
      address: customer?.address ?? '', notes: customer?.notes ?? '',
    },
  });
  const save = useOrgMutation(org, (v: CustomerValues) => svcApi.saveCustomer(org, customer?.id ?? null, {
    name: v.name.trim(), contactName: opt(v.contactName), phone: opt(v.phone), email: opt(v.email), address: opt(v.address), notes: opt(v.notes),
  }));
  const submit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const c = await save.mutateAsync(v);
      toast.success(t('service.customerSaved'));
      onOpenChange(false);
      onSaved?.(c);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const text = (name: keyof CustomerValues, label: string, optional = true, type = 'text') => (
    <FormField control={form.control} name={name} render={({ field }) => (
      <FormItem><FormLabel>{label}{optional && <span className="font-normal text-muted-foreground"> {t('common.optional')}</span>}</FormLabel><FormControl><Input type={type} {...field} /></FormControl><FormMessage /></FormItem>
    )} />
  );
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{customer ? t('service.editCustomer') : t('service.newCustomer')}</DialogTitle>
          <DialogDescription>{t('service.customerBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            {text('name', t('service.customerName'), false)}
            <div className="grid gap-4 sm:grid-cols-2">{text('contactName', t('service.contactName'))}{text('phone', t('service.phone'), true, 'tel')}</div>
            {text('email', t('service.email'), true, 'email')}
            {text('address', t('service.address'))}
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem><FormLabel>{t('service.notes')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={save.isPending}>{t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ── Site ──────────────────────────────────────────────────────────────────────────

export function SiteDialog({ org, customerId, site, open, onOpenChange }: {
  org: string; customerId: string; site?: Site; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const [name, setName] = useState(site?.name ?? '');
  const [address, setAddress] = useState(site?.address ?? '');
  const [error, setError] = useState<string | null>(null);
  const save = useOrgMutation(org, () => (site ? svcApi.updateSite(org, site.id, { name: name.trim(), address: opt(address) })
    : svcApi.addSite(org, customerId, { name: name.trim(), address: opt(address) })));
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{site ? t('service.editSite') : t('service.newSite')}</DialogTitle>
          <DialogDescription>{t('service.siteBody')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) { setError(t('service.errName')); return; }
          setError(null);
          try { await save.mutateAsync(undefined); toast.success(t('service.siteSaved')); onOpenChange(false); } catch (x) { setError(errorMessage(x)); }
        }}>
          <ErrorLine error={error} />
          <div className="space-y-2"><Label htmlFor="site-name">{t('service.siteName')}</Label><Input id="site-name" value={name} onChange={(e) => setName(e.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="site-address">{t('service.address')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label><Input id="site-address" value={address} onChange={(e) => setAddress(e.target.value)} /></div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={save.isPending}>{t('common.save')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Asset ─────────────────────────────────────────────────────────────────────────

const assetSchema = (t: T) => z.object({
  customerId: z.string().refine((v) => !!v && v !== NONE, t('service.errCustomer')),
  siteId: z.string(),
  name: z.string().trim().min(1, t('service.errName')).max(160),
  serial: z.string().max(80),
  model: z.string().max(120),
  manufacturer: z.string().max(120),
  installedOn: z.string(),
  warrantyEnd: z.string(),
  status: z.string(),
  notes: z.string().max(2000),
}).refine((v) => !v.installedOn || !v.warrantyEnd || v.warrantyEnd >= v.installedOn, { path: ['warrantyEnd'], message: t('service.errWarranty') });
type AssetValues = z.infer<ReturnType<typeof assetSchema>>;

export function AssetDialog({ org, asset, customerId, open, onOpenChange, onSaved }: {
  org: string; asset?: Asset; customerId?: string; open: boolean; onOpenChange: (v: boolean) => void; onSaved?: (a: Asset) => void;
}) {
  const { t } = useT();
  const schema = useMemo(() => assetSchema(t), [t]);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<AssetValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      customerId: asset?.customer?.id ?? customerId ?? NONE, siteId: asset?.site?.id ?? NONE, name: asset?.name ?? '', serial: asset?.serial ?? '',
      model: asset?.model ?? '', manufacturer: asset?.manufacturer ?? '', installedOn: asset?.installedOn ?? '', warrantyEnd: asset?.warrantyEnd ?? '',
      status: asset?.status ?? 'ACTIVE', notes: asset?.notes ?? '',
    },
  });
  const customers = useSvcCustomers(org, { size: 100 }, open);
  const detail = useSvcCustomer(org, sel(form.watch('customerId')));
  const save = useOrgMutation(org, (v: AssetValues) => svcApi.saveAsset(org, asset?.id ?? null, {
    customerId: v.customerId, siteId: sel(v.siteId), name: v.name.trim(), serial: opt(v.serial), model: opt(v.model), manufacturer: opt(v.manufacturer),
    installedOn: v.installedOn || null, warrantyEnd: v.warrantyEnd || null, status: v.status, notes: opt(v.notes),
  }));
  const submit = form.handleSubmit(async (v) => {
    setError(null);
    try {
      const a = await save.mutateAsync(v);
      toast.success(t('service.assetSaved'));
      onOpenChange(false);
      onSaved?.(a);
    } catch (e) { setError(applyServerErrors(form, e)); }
  });
  const text = (name: keyof AssetValues, label: string, optional = true, type = 'text') => (
    <FormField control={form.control} name={name} render={({ field }) => (
      <FormItem className="min-w-0"><FormLabel>{label}{optional && <span className="font-normal text-muted-foreground"> {t('common.optional')}</span>}</FormLabel><FormControl><Input type={type} {...field} /></FormControl><FormMessage /></FormItem>
    )} />
  );
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{asset ? t('service.editAsset') : t('service.newAsset')}</DialogTitle>
          <DialogDescription>{t('service.assetBody')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <ErrorLine error={error} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="customerId" render={({ field }) => (
                <FormItem className="min-w-0"><FormLabel>{t('service.customer')}</FormLabel>
                  <Select value={field.value} onValueChange={(v) => { field.onChange(v); form.setValue('siteId', NONE); }}>
                    <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue placeholder={t('service.chooseCustomer')} /></SelectTrigger></FormControl>
                    <SelectContent>{(customers.data?.items ?? []).map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}</SelectContent>
                  </Select><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="siteId" render={({ field }) => (
                <FormItem className="min-w-0"><FormLabel>{t('service.site')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent><SelectItem value={NONE}>{t('service.noSite')}</SelectItem>{(detail.data?.sites ?? []).map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}</SelectContent>
                  </Select><FormMessage /></FormItem>
              )} />
            </div>
            {text('name', t('service.assetName'), false)}
            <div className="grid gap-4 sm:grid-cols-3">{text('serial', t('service.serial'))}{text('model', t('service.model'))}{text('manufacturer', t('service.manufacturer'))}</div>
            <div className="grid gap-4 sm:grid-cols-3">
              {text('installedOn', t('service.installedOn'), true, 'date')}
              {text('warrantyEnd', t('service.warrantyEnd'), true, 'date')}
              <FormField control={form.control} name="status" render={({ field }) => (
                <FormItem className="min-w-0"><FormLabel>{t('service.status')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full min-w-0"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>{ASSET_STATUSES.map((s) => <SelectItem key={s} value={s}>{ASSET_LABEL[s]}</SelectItem>)}</SelectContent>
                  </Select></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem><FormLabel>{t('service.notes')}</FormLabel><FormControl><Textarea rows={2} {...field} /></FormControl><FormMessage /></FormItem>
            )} />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={save.isPending}>{t('common.save')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

// ── Staff ─────────────────────────────────────────────────────────────────────────

/** Adds a member to the service staff (Admins) or changes their role, skills, phone or whether they are active. */
export function StaffDialog({ org, staff, taken, open, onOpenChange }: {
  org: string; staff?: Staff; taken: string[]; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const members = useMembers(org);
  const [userId, setUserId] = useState(staff?.person.id ?? '');
  const [role, setRole] = useState<string>(staff?.role ?? 'TECHNICIAN');
  const [skills, setSkills] = useState((staff?.skills ?? []).join(', '));
  const [phone, setPhone] = useState(staff?.phone ?? '');
  const [active, setActive] = useState(staff?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const people = (members.data ?? []).filter((m) => m.status === 'ACTIVE' && m.role !== 'GUEST' && !taken.includes(m.userId));
  const save = useOrgMutation(org, () => svcApi.saveStaff(org, staff?.id ?? null, {
    userId: staff ? null : userId, role, skills: skills.split(',').map((x) => x.trim()).filter(Boolean), phone: opt(phone), active,
  }));
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{staff ? t('service.editStaff', { name: staff.person.name }) : t('service.addStaff')}</DialogTitle>
          <DialogDescription>{t('service.staffBody')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          if (!staff && !userId) { setError(t('service.errMember')); return; }
          setError(null);
          try { await save.mutateAsync(undefined); toast.success(t('service.staffSaved')); onOpenChange(false); } catch (x) { setError(errorMessage(x)); }
        }}>
          <ErrorLine error={error} />
          {!staff && <Pick id="staff-person" label={t('service.member')} value={userId} onChange={setUserId}
            options={people.map((m) => ({ value: m.userId, label: m.name }))} />}
          <Pick id="staff-role" label={t('service.role')} value={role} onChange={setRole}
            options={[{ value: 'TECHNICIAN', label: t('service.roleTechnician') }, { value: 'DISPATCHER', label: t('service.roleDispatcher') }]} />
          <div className="space-y-2"><Label htmlFor="staff-skills">{t('service.skills')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label>
            <Input id="staff-skills" value={skills} placeholder={t('service.skillsHint')} onChange={(e) => setSkills(e.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="staff-phone">{t('service.phone')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label>
            <Input id="staff-phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></div>
          {staff && <label className="flex items-center gap-2 text-sm"><Checkbox checked={active} onCheckedChange={(v) => setActive(v === true)} />{t('service.activeStaff')}</label>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={save.isPending}>{t('common.save')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── Maintenance plan ──────────────────────────────────────────────────────────────

export function PlanDialog({ org, plan, assetId, open, onOpenChange }: {
  org: string; plan?: Plan; assetId?: string; open: boolean; onOpenChange: (v: boolean) => void;
}) {
  const { t } = useT();
  const assets = useAssets(org, { size: 100 }, open);
  const staff = useStaff(org, open);
  const [asset, setAsset] = useState(plan?.asset?.id ?? assetId ?? '');
  const [title, setTitle] = useState(plan?.title ?? '');
  const [count, setCount] = useState(String(plan?.intervalCount ?? 3));
  const [unit, setUnit] = useState<string>(plan?.intervalUnit ?? 'MONTHS');
  const [nextDue, setNextDue] = useState(plan?.nextDue ?? '');
  const [lead, setLead] = useState(String(plan?.leadDays ?? 7));
  const [tech, setTech] = useState(plan?.technician?.id ?? NONE);
  const [checklist, setChecklist] = useState((plan?.checklist ?? []).join('\n'));
  const [active, setActive] = useState(plan?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const save = useOrgMutation(org, () => svcApi.savePlan(org, plan?.id ?? null, {
    assetId: asset, title: title.trim(), intervalCount: Number(count), intervalUnit: unit, nextDue, leadDays: Number(lead),
    technicianId: sel(tech), checklist: checklist.split('\n').map((x) => x.trim()).filter(Boolean), active,
  }));
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{plan ? t('service.editPlan') : t('service.newPlan')}</DialogTitle>
          <DialogDescription>{t('service.planBody')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          if (!asset || !title.trim() || !nextDue || !(Number(count) >= 1)) { setError(t('service.errPlan')); return; }
          setError(null);
          try { await save.mutateAsync(undefined); toast.success(t('service.planSaved')); onOpenChange(false); } catch (x) {
            setError(x instanceof ApiError && x.fields.length ? x.fields[0]!.message : errorMessage(x));
          }
        }}>
          <ErrorLine error={error} />
          <Pick id="plan-asset" label={t('service.asset')} value={asset} onChange={setAsset}
            options={(assets.data?.items ?? []).map((a) => ({ value: a.id, label: `${a.name}${a.serial ? ` · ${a.serial}` : ''} · ${a.customer?.name ?? ''}` }))} />
          <div className="space-y-2"><Label htmlFor="plan-title">{t('service.planTitle')}</Label><Input id="plan-title" value={title} onChange={(e) => setTitle(e.target.value)} /></div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2"><Label htmlFor="plan-count">{t('service.every')}</Label><Input id="plan-count" inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value)} /></div>
            <Pick id="plan-unit" label={t('service.unit')} value={unit} onChange={setUnit}
              options={[{ value: 'DAYS', label: t('service.unitDays') }, { value: 'MONTHS', label: t('service.unitMonths') }]} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2"><Label htmlFor="plan-due">{t('service.nextDue')}</Label><Input id="plan-due" type="date" value={nextDue} onChange={(e) => setNextDue(e.target.value)} /></div>
            <div className="space-y-2"><Label htmlFor="plan-lead">{t('service.leadDays')}</Label><Input id="plan-lead" inputMode="numeric" value={lead} onChange={(e) => setLead(e.target.value)} /></div>
          </div>
          <Pick id="plan-tech" label={t('service.technician')} value={tech} onChange={setTech} none={t('service.noTechnician')}
            options={(staff.data ?? []).filter((s) => s.active && s.role === 'TECHNICIAN').map((s) => ({ value: s.person.id, label: s.person.name }))} />
          <div className="space-y-2"><Label htmlFor="plan-check">{t('service.checklist')} <span className="font-normal text-muted-foreground">{t('service.onePerLine')}</span></Label>
            <Textarea id="plan-check" rows={3} value={checklist} onChange={(e) => setChecklist(e.target.value)} /></div>
          {plan && <label className="flex items-center gap-2 text-sm"><Checkbox checked={active} onCheckedChange={(v) => setActive(v === true)} />{t('service.planActive')}</label>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={save.isPending}>{t('common.save')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ── A reason, and the completion ──────────────────────────────────────────────────

/** Asks for a short reason (hold, cancel) and runs `onConfirm` with it. */
export function ReasonDialog({ open, onOpenChange, title, body, label, action, destructive, onConfirm }: {
  open: boolean; onOpenChange: (v: boolean) => void; title: string; body: string; label: string; action: string; destructive?: boolean;
  onConfirm: (reason: string) => Promise<unknown>;
}) {
  const { t } = useT();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{title}</DialogTitle><DialogDescription>{body}</DialogDescription></DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          if (!reason.trim()) { setError(t('service.errReason')); return; }
          setBusy(true);
          setError(null);
          try { await onConfirm(reason.trim()); onOpenChange(false); setReason(''); } catch (x) { setError(errorMessage(x)); } finally { setBusy(false); }
        }}>
          <ErrorLine error={error} />
          <div className="space-y-2"><Label htmlFor="reason">{label}</Label><Textarea id="reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} /></div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={busy} variant={destructive ? 'destructive' : 'default'}>{action}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** The technician finishes: what was done, and the name of the customer who signs it off. */
export function CompleteDialog({ open, onOpenChange, onConfirm }: {
  open: boolean; onOpenChange: (v: boolean) => void; onConfirm: (v: { notes: string | null; signedBy: string }) => Promise<unknown>;
}) {
  const { t } = useT();
  const [notes, setNotes] = useState('');
  const [signedBy, setSignedBy] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('service.completeTitle')}</DialogTitle><DialogDescription>{t('service.completeBody')}</DialogDescription></DialogHeader>
        <form className="space-y-4" noValidate onSubmit={async (e) => {
          e.preventDefault();
          if (!signedBy.trim()) { setError(t('service.errSignedBy')); return; }
          setBusy(true);
          setError(null);
          try { await onConfirm({ notes: opt(notes), signedBy: signedBy.trim() }); onOpenChange(false); } catch (x) { setError(errorMessage(x)); } finally { setBusy(false); }
        }}>
          <ErrorLine error={error} />
          <div className="space-y-2"><Label htmlFor="c-notes">{t('service.completionNotes')}</Label><Textarea id="c-notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="c-signed">{t('service.signedBy')}</Label><Input id="c-signed" value={signedBy} onChange={(e) => setSignedBy(e.target.value)} /></div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={busy}>{t('service.complete')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
