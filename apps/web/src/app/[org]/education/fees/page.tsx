'use client';

import { Pencil, Plus, Receipt, SearchX, Send, Wallet } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { toast } from 'sonner';

import { FeeItemDialog, InvoiceDialog } from '@/components/education/fee-dialogs';
import { FEE_LABEL, FeeBadge, Money, Pager, studentName } from '@/components/education/ui';
import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { eduApi, FEE_STATUSES, type FeeItem, useFeeInvoices, useFeeItems, useYears } from '@/lib/api/education';
import { useOrgMutation } from '@/lib/api/queries';
import { day } from '@/lib/format';
import { usd } from '@/lib/money';
import { useOrg } from '@/lib/org-context';

const SIZE = 25;
const ALL = 'all';

function Invoices({ org }: { org: string }) {
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState(params.get('status') ?? ALL);
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const { data, error, isPending, refetch } = useFeeInvoices(org, { q: q.trim() || undefined, status: status === ALL ? undefined : status, page, size: SIZE });
  const href = (id: string) => `/${org}/education/fees/invoices/${id}`;
  return (
    <>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div className="grid flex-1 grid-cols-2 gap-3 sm:flex sm:items-end">
          <div className="col-span-2 space-y-1 sm:w-72">
            <Label htmlFor="f-q" className="text-xs text-muted-foreground">{t('common.search')}</Label>
            <Input id="f-q" type="search" value={q} placeholder={t('education.searchInvoices')} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="f-s" className="text-xs text-muted-foreground">{t('education.status')}</Label>
            <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
              <SelectTrigger id="f-s" className="w-full sm:w-44"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('education.allStatuses')}</SelectItem>
                <SelectItem value="OPEN">{t('education.fee_OPEN')}</SelectItem>
                <SelectItem value="OVERDUE">{FEE_LABEL.OVERDUE}</SelectItem>
                {FEE_STATUSES.map((s) => <SelectItem key={s} value={s}>{FEE_LABEL[s]}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
        </div>
        <Button size="sm" variant="outline" onClick={() => setAdding(true)}><Plus className="size-4" />{t('education.newInvoice')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={6} />
        : data.items.length === 0 ? (q || status !== ALL ? <EmptyState icon={SearchX} title={t('education.noMatchTitle')} body={t('education.noMatchBody')} />
          : <EmptyState icon={Receipt} title={t('education.noInvoicesTitle')} body={t('education.noInvoicesBody')} />)
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader><TableRow>
                    <TableHead>{t('education.number')}</TableHead><TableHead>{t('education.student')}</TableHead><TableHead>{t('education.description')}</TableHead>
                    <TableHead>{t('education.dueOn')}</TableHead><TableHead>{t('education.status')}</TableHead>
                    <TableHead className="text-right">{t('education.amount')}</TableHead><TableHead className="text-right">{t('education.balance')}</TableHead>
                  </TableRow></TableHeader>
                  <TableBody>
                    {data.items.map((i) => (
                      <TableRow key={i.id}>
                        <TableCell className="font-medium"><Link href={href(i.id)} className="text-primary hover:underline">{i.number}</Link></TableCell>
                        <TableCell className="max-w-48 truncate">{studentName(i.student)}</TableCell>
                        <TableCell className="max-w-56 truncate text-muted-foreground">{i.description}</TableCell>
                        <TableCell className="whitespace-nowrap">{day(i.dueOn)}</TableCell>
                        <TableCell><FeeBadge status={i.status} overdue={i.overdue} /></TableCell>
                        <TableCell className="text-right tabular">{usd(i.amount)}</TableCell>
                        <TableCell className="text-right">{i.status === 'VOID' ? <span className="text-muted-foreground">—</span> : <Money value={i.balance} className="items-end" />}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.items.map((i) => (
                  <li key={i.id}>
                    <Link href={href(i.id)} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-muted/50">
                      <span className="min-w-0"><span className="block text-sm font-medium">{i.number} · {studentName(i.student)}</span>
                        <span className="block truncate text-xs text-muted-foreground">{i.description} · {t('education.dueOnDate', { date: day(i.dueOn) })}</span></span>
                      <span className="flex shrink-0 flex-col items-end gap-1"><span className="text-sm font-medium tabular">{usd(i.amount)}</span><FeeBadge status={i.status} overdue={i.overdue} /></span>
                    </Link>
                  </li>
                ))}
              </ul>
              <Pager page={page} size={SIZE} total={data.total} onPage={setPage} />
            </>
          )}
      {adding && <InvoiceDialog org={org} onClose={() => setAdding(false)} onSaved={(id) => router.push(`/${org}/education/fees/invoices/${id}`)} />}
    </>
  );
}

function Items({ org }: { org: string }) {
  const { t } = useT();
  const years = useYears(org);
  const current = years.data?.find((y) => y.current);
  const { data, error, isPending, refetch } = useFeeItems(org);
  const [editing, setEditing] = useState<FeeItem | 'new' | null>(null);
  const [billing, setBilling] = useState<FeeItem | null>(null);
  const bill = useOrgMutation(org, (id: string) => eduApi.bill(org, id));
  return (
    <>
      <div className="mb-4 flex justify-end">
        {current && <Button size="sm" variant="outline" onClick={() => setEditing('new')}><Plus className="size-4" />{t('education.newFee')}</Button>}
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={Wallet} title={t('education.noFeesTitle')} body={current ? t('education.noFeesBody') : t('education.noYearBody')}
          action={current && <Button size="sm" variant="outline" onClick={() => setEditing('new')}>{t('education.newFee')}</Button>} />
          : (
            <ul className="divide-y rounded-lg border bg-card">
              {data.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <p className="font-medium">{f.nameEn} {!f.active && <span className="text-xs font-normal text-muted-foreground">· {t('education.switchedOff')}</span>}</p>
                    <p className="text-sm text-muted-foreground">{[f.gradeName ?? t('education.everyGrade'), t('education.dueOnDate', { date: day(f.dueOn) }), t('education.billedCount', { count: f.billed })].join(' · ')}</p>
                    {f.billed > 0 && <p className="text-xs text-muted-foreground tabular">{t('education.collectedOf', { collected: usd(f.collected), outstanding: usd(f.outstanding) })}</p>}
                  </div>
                  <div className="flex items-center gap-2">
                    <Money value={f.amount} className="items-end" />
                    <Button variant="ghost" size="icon-sm" aria-label={t('education.editName', { name: f.nameEn })} onClick={() => setEditing(f)}><Pencil className="size-4" /></Button>
                    {f.active && <Button size="sm" variant="outline" onClick={() => setBilling(f)}><Send className="size-4" />{t('education.bill')}</Button>}
                  </div>
                </li>
              ))}
            </ul>
          )}
      {editing && <FeeItemDialog org={org} item={editing === 'new' ? undefined : editing} yearId={current?.id} onClose={() => setEditing(null)} />}
      <AlertDialog open={billing !== null} onOpenChange={(v) => !v && setBilling(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('education.billTitle', { name: billing?.nameEn ?? '' })}</AlertDialogTitle>
            <AlertDialogDescription>{t('education.billBody', { grade: billing?.gradeName ?? t('education.everyGrade'), amount: usd(billing?.amount ?? 0) })}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={async () => {
              if (!billing) return;
              try {
                const r = await bill.mutateAsync(billing.id);
                toast.success(r.created ? t('education.billedToast', { count: r.created }) : t('education.billedNone'));
              } catch (e) { toast.error(errorMessage(e)); }
              setBilling(null);
            }}>{t('education.bill')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

/** Fees (admins): student invoices with their balances, and the fee items that are billed to each grade. */
function FeesPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const params = useSearchParams();
  const router = useRouter();
  const tab = params.get('tab') === 'items' ? 'items' : 'invoices';
  if (!can('education.manage')) return <PermissionDenied />;
  return (
    <>
      <PageHeader title={t('education.feesTitle')} subtitle={t('education.feesSubtitle')} />
      <Tabs value={tab} onValueChange={(v) => router.replace(`/${slug}/education/fees${v === 'items' ? '?tab=items' : ''}`)} className="mb-4">
        <TabsList>
          <TabsTrigger value="invoices">{t('education.invoices')}</TabsTrigger>
          <TabsTrigger value="items">{t('education.feeItems')}</TabsTrigger>
        </TabsList>
      </Tabs>
      {tab === 'items' ? <Items org={slug} /> : <Invoices org={slug} />}
    </>
  );
}

export default function Fees() {
  return <Suspense fallback={<ListSkeleton />}><FeesPage /></Suspense>;
}
