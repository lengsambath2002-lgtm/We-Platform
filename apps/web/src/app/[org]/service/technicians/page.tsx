'use client';

import { HardHat, Pencil, UserPlus } from 'lucide-react';
import { useState } from 'react';

import { PageHeader } from '@/components/common/page-header';
import { PersonAvatar } from '@/components/common/person';
import { EmptyState, ErrorState, ListSkeleton, PermissionDenied } from '@/components/common/states';
import { StaffDialog } from '@/components/service/dialogs';
import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { type Staff, useStaff } from '@/lib/api/service';
import { useOrg } from '@/lib/org-context';
import { cn } from '@/lib/utils';

/** The service staff: technicians (skills, phone, open work orders) and dispatchers. Admins add and change them. */
export default function TechniciansPage() {
  const { slug, can } = useOrg();
  const { t } = useT();
  const { data, error, isPending, refetch } = useStaff(slug, can('service.manage'));
  const [editing, setEditing] = useState<Staff | 'new' | null>(null);
  if (!can('service.manage')) return <PermissionDenied />;
  const admin = can('service.admin');
  const add = admin && <Button size="sm" onClick={() => setEditing('new')}><UserPlus className="size-4" />{t('service.addStaff')}</Button>;
  const section = (title: string, items: Staff[]) => (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">{title} <span className="font-normal text-muted-foreground">{items.length}</span></h2>
      <ul className="divide-y rounded-lg border bg-card">
        {items.map((s) => (
          <li key={s.id} className={cn('flex flex-wrap items-center gap-3 px-4 py-3', !s.active && 'opacity-60')}>
            <PersonAvatar person={s.person} className="size-9" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{s.person.name}{!s.active && <span className="ml-2 text-xs font-normal text-muted-foreground">{t('service.inactive')}</span>}</p>
              <p className="truncate text-xs text-muted-foreground">{[s.phone, s.email].filter(Boolean).join(' · ')}</p>
              {s.skills.length > 0 && <p className="mt-1 flex flex-wrap gap-1">{s.skills.map((k) => <span key={k} className="rounded-full bg-muted px-2 py-0.5 text-xs">{k}</span>)}</p>}
            </div>
            {s.role === 'TECHNICIAN' && <span className="text-sm text-muted-foreground tabular">{t('service.openWorkOrdersN', { count: s.openWorkOrders })}</span>}
            {admin && <Button size="icon-sm" variant="ghost" aria-label={t('service.editStaff', { name: s.person.name })} onClick={() => setEditing(s)}><Pencil className="size-4" /></Button>}
          </li>
        ))}
      </ul>
    </section>
  );
  return (
    <>
      <PageHeader title={t('service.techniciansTitle')} subtitle={t('service.techniciansSubtitle')} actions={add} />
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={4} />
        : data.length === 0 ? <EmptyState icon={HardHat} title={t('service.noStaffTitle')} body={admin ? t('service.noStaffBody') : t('service.noStaffBodyDispatcher')} action={add} />
          : (
            <div className="space-y-8">
              {section(t('service.technicians'), data.filter((s) => s.role === 'TECHNICIAN'))}
              {data.some((s) => s.role === 'DISPATCHER') && section(t('service.dispatchers'), data.filter((s) => s.role === 'DISPATCHER'))}
            </div>
          )}
      {editing && <StaffDialog org={slug} staff={editing === 'new' ? undefined : editing} taken={(data ?? []).map((s) => s.person.id)} open onOpenChange={(v) => !v && setEditing(null)} />}
    </>
  );
}
