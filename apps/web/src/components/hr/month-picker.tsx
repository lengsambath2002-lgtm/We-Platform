'use client';

import { addMonths, format, parse } from 'date-fns';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';
import { fmt } from '@/lib/format';

/** "‹ November 2026 ›" for a yyyy-MM month. */
export function MonthPicker({ month, onChange }: { month: string; onChange: (m: string) => void }) {
  const { t } = useT();
  const d = parse(month, 'yyyy-MM', new Date());
  const go = (n: number) => onChange(format(addMonths(d, n), 'yyyy-MM'));
  return (
    <div className="flex items-center gap-1">
      <Button variant="outline" size="icon" className="size-8" aria-label={t('hr.prevMonth')} onClick={() => go(-1)}><ChevronLeft className="size-4" /></Button>
      <span className="min-w-32 text-center text-sm font-medium tabular">{fmt(d, 'MMMM yyyy')}</span>
      <Button variant="outline" size="icon" className="size-8" aria-label={t('hr.nextMonth')} onClick={() => go(1)}><ChevronRight className="size-4" /></Button>
    </div>
  );
}
