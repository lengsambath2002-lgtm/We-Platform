import { differenceInCalendarDays, format, formatDistanceToNowStrict, isToday, isTomorrow, isYesterday, parseISO } from 'date-fns';

import { dateLocale, tr } from '@/i18n/core';

/** "Today", "Tomorrow", "Mon 3 Nov" … in the current language, with whether it is overdue. */
export function dueLabel(date: string | null, done = false) {
  if (!date) return null;
  const d = parseISO(date);
  const days = differenceInCalendarDays(d, new Date());
  const text = isToday(d) ? tr('common.today') : isTomorrow(d) ? tr('common.tomorrow') : isYesterday(d) ? tr('common.yesterday')
    : format(d, days > -180 && days < 180 ? 'EEE d MMM' : 'd MMM yyyy', { locale: dateLocale() });
  return { text, overdue: !done && days < 0, soon: !done && days >= 0 && days <= 2 };
}

export function ago(iso: string) {
  return formatDistanceToNowStrict(parseISO(iso), { addSuffix: true, locale: dateLocale() });
}

export function day(iso: string) {
  return format(parseISO(iso), 'd MMM yyyy', { locale: dateLocale() });
}

/** date-fns `format` in the current language (for calendars and headings). */
export function fmt(d: Date, pattern: string) {
  return format(d, pattern, { locale: dateLocale() });
}

export function bytes(n: number) {
  return n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(0)} KB` : `${(n / 1048576).toFixed(1)} MB`;
}
