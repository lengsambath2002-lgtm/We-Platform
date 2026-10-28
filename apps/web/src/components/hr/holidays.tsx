'use client';

import { Landmark, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState, ErrorState, ListSkeleton } from '@/components/common/states';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useT } from '@/i18n/provider';
import { errorMessage } from '@/lib/api/client';
import { hrApi, useHolidays } from '@/lib/api/hr';
import { useOrgMutation } from '@/lib/api/queries';
import { fmt } from '@/lib/format';

function AddHoliday({ org, year, onClose }: { org: string; year: number; onClose: () => void }) {
  const { t } = useT();
  const [date, setDate] = useState(`${year}-01-01`);
  const [nameEn, setNameEn] = useState('');
  const [nameKm, setNameKm] = useState('');
  const add = useOrgMutation(org, () => hrApi.addHoliday(org, { date, nameEn: nameEn.trim(), nameKm: nameKm.trim() || null }));
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>{t('hr.addHoliday')}</DialogTitle><DialogDescription>{t('hr.addHolidayBody')}</DialogDescription></DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2"><Label htmlFor="h-date">{t('hr.holidayDate')}</Label><Input id="h-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="h-en">{t('hr.holidayNameEn')}</Label><Input id="h-en" value={nameEn} onChange={(e) => setNameEn(e.target.value)} maxLength={120} autoFocus /></div>
          <div className="space-y-2"><Label htmlFor="h-km">{t('hr.holidayNameKm')} <span className="font-normal text-muted-foreground">{t('common.optional')}</span></Label><Input id="h-km" lang="km" value={nameKm} onChange={(e) => setNameKm(e.target.value)} maxLength={120} /></div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!date || !nameEn.trim() || add.isPending} onClick={async () => {
            try { await add.mutateAsync(undefined); toast.success(t('hr.holidayAdded')); onClose(); } catch (e) { toast.error(errorMessage(e)); }
          }}>{add.isPending ? t('common.saving') : t('common.add')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Public holidays: leave doesn't count them and the calendar shows them. Cambodia's official list can be copied in per year. */
export function Holidays({ org }: { org: string }) {
  const { t, locale } = useT();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [adding, setAdding] = useState(false);
  const { data, error, isPending, refetch } = useHolidays(org, year);
  const importCambodia = useOrgMutation(org, () => hrApi.importCambodia(org, year));
  const remove = useOrgMutation(org, (id: string) => hrApi.removeHoliday(org, id));

  return (
    <section aria-labelledby="hol-h">
      <h2 id="hol-h" className="text-base font-semibold">{t('hr.holidays')}</h2>
      <p className="mb-3 text-sm text-muted-foreground">{t('hr.holidaysBody')}</p>
      <div className="mb-4 flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="h-year" className="text-xs text-muted-foreground">{t('hr.year')}</Label>
          <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
            <SelectTrigger id="h-year" className="w-28"><SelectValue /></SelectTrigger>
            <SelectContent>{[thisYear - 1, thisYear, thisYear + 1].map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <Button size="sm" variant="outline" disabled={importCambodia.isPending} onClick={async () => {
          try {
            const r = await importCambodia.mutateAsync(undefined);
            toast.success(r.added ? t('hr.holidaysImported', { count: r.added }) : t('hr.holidaysAlreadyThere'));
          } catch (e) { toast.error(errorMessage(e)); }
        }}><Landmark className="size-4" />{t('hr.importCambodia', { year })}</Button>
        <Button size="sm" onClick={() => setAdding(true)}><Plus className="size-4" />{t('hr.addHoliday')}</Button>
      </div>
      {error ? <ErrorState error={error} retry={() => refetch()} /> : isPending ? <ListSkeleton rows={3} /> : data.length === 0 ? (
        <EmptyState icon={Landmark} title={t('hr.noHolidaysTitle', { year })} body={t('hr.noHolidaysBody')} />
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {data.map((h) => (
            <li key={h.id} className="flex items-center gap-3 px-4 py-2.5">
              <span className="w-28 shrink-0 text-sm tabular text-muted-foreground">{fmt(new Date(`${h.date}T00:00:00`), 'EEE d MMM')}</span>
              <span className="min-w-0 flex-1 text-sm" lang={locale === 'km' ? 'km' : 'en'}>{locale === 'km' ? h.nameKm : h.nameEn}</span>
              <Button variant="ghost" size="icon" className="size-8 shrink-0" aria-label={t('hr.removeHoliday', { name: locale === 'km' ? h.nameKm : h.nameEn })}
                onClick={() => remove.mutateAsync(h.id).then(() => toast.success(t('hr.holidayRemoved'))).catch((e) => toast.error(errorMessage(e)))}>
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
      {adding && <AddHoliday org={org} year={year} onClose={() => setAdding(false)} />}
    </section>
  );
}
