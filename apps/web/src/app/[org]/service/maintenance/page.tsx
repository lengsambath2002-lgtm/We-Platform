'use client';

import { CalendarPlus, Hammer, Pencil, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/page-header';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { PlanDialog } from '@/components/service/dialogs';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { useOrgMutation } from '@/lib/api/queries';
import { type Plan, svcApi, usePlans } from '@/lib/api/service';
import { day } from '@/lib/format';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/**
 * Preventive maintenance plans. The next work order of each is generated automatically (every hour) a few days
 * before it is due; "Generate now" does the same at once, and never makes a work order twice.
 */
export default function MaintenancePage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = usePlans(slug);
  const [editing, setEditing] = useState<Plan | 'new' | null>(null);
  const generate = useOrgMutation(slug, () => svcApi.generate(slug));
  if (!can('service.manage')) return <PermissionDenied />;
  const add = <Button size="sm" onClick={() => setEditing('new')}><CalendarPlus className="size-4" />{t('service.newPlan')}</Button>;
  const every = (p: Plan) => t(p.intervalUnit === 'DAYS' ? 'service.everyDays' : 'service.everyMonths', { n: p.intervalCount });
  return (
    <>
      <PageHeader title={t('service.maintenanceTitle')} subtitle={t('service.maintenanceSubtitle')}
        actions={<>
          <Button size="sm" variant="outline" disabled={generate.isPending} onClick={async () => {
            try {
              const r = await generate.mutateAsync(undefined);
              toast.success(r.created ? t('service.generated', { count: r.created }) : t('service.nothingDue'));
            } catch (e) { toast.error(errorMessage(e)); }
          }}><RefreshCw className="size-4" />{t('service.generateNow')}</Button>
          {add}
        </>} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={Hammer} title={t('service.noPlansTitle')} body={t('service.noPlansBody')} action={add} />
          : (
            <>
              <div className="hidden rounded-lg border bg-card md:block">
                <Table>
                  <TableHeader><TableRow>
                    <TableHead>{t('service.planTitle')}</TableHead><TableHead>{t('service.asset')}</TableHead><TableHead>{t('service.every')}</TableHead>
                    <TableHead>{t('service.nextDue')}</TableHead><TableHead>{t('service.generatedOn')}</TableHead><TableHead>{t('service.technician')}</TableHead><TableHead />
                  </TableRow></TableHeader>
                  <TableBody>
                    {data.map((p) => (
                      <TableRow key={p.id} className={cn(!p.active && 'opacity-60')}>
                        <TableCell className="font-medium">{p.title}{!p.active && <span className="ml-2 text-xs font-normal text-muted-foreground">{t('service.paused')}</span>}</TableCell>
                        <TableCell className="max-w-56 truncate">{p.asset ? <Link className="text-primary hover:underline" href={`/${slug}/service/assets/${p.asset.id}`}>{p.asset.name}</Link> : '—'}
                          {p.customer && <span className="block text-xs text-muted-foreground">{p.customer.name}</span>}</TableCell>
                        <TableCell>{every(p)}</TableCell>
                        <TableCell className="whitespace-nowrap">{day(p.nextDue)}</TableCell>
                        <TableCell className="whitespace-nowrap text-muted-foreground">{day(p.generateOn)}</TableCell>
                        <TableCell>{p.technician?.name ?? <span className="text-muted-foreground">{t('service.noTechnician')}</span>}</TableCell>
                        <TableCell className="text-right"><Button size="icon-sm" variant="ghost" aria-label={t('service.editPlanOf', { title: p.title })} onClick={() => setEditing(p)}><Pencil className="size-4" /></Button></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y rounded-lg border bg-card md:hidden">
                {data.map((p) => (
                  <li key={p.id} className="flex items-start justify-between gap-3 px-4 py-3">
                    <span className="min-w-0"><span className="block truncate text-sm font-medium">{p.title}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[p.asset?.name, every(p)].filter(Boolean).join(' · ')}</span>
                      <span className="block text-xs text-muted-foreground">{t('service.dueOn', { date: day(p.nextDue) })}</span></span>
                    <Button size="icon-sm" variant="ghost" aria-label={t('service.editPlanOf', { title: p.title })} onClick={() => setEditing(p)}><Pencil className="size-4" /></Button>
                  </li>
                ))}
              </ul>
            </>
          )}
      {editing && <PlanDialog org={slug} plan={editing === 'new' ? undefined : editing} open onOpenChange={(v) => !v && setEditing(null)} />}
    </>
  );
}
