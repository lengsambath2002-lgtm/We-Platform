import { differenceInCalendarDays, format, formatDistanceToNowStrict, isToday, isTomorrow, isYesterday, parseISO } from 'date-fns';

/** "Today", "Tomorrow", "Mon 3 Nov" … with whether it is overdue. */
export function dueLabel(date: string | null, done = false) {
  if (!date) return null;
  const d = parseISO(date);
  const days = differenceInCalendarDays(d, new Date());
  const text = isToday(d) ? 'Today' : isTomorrow(d) ? 'Tomorrow' : isYesterday(d) ? 'Yesterday' : format(d, days > -180 && days < 180 ? 'EEE d MMM' : 'd MMM yyyy');
  return { text, overdue: !done && days < 0, soon: !done && days >= 0 && days <= 2 };
}

export function ago(iso: string) {
  return formatDistanceToNowStrict(parseISO(iso), { addSuffix: true });
}

export function day(iso: string) {
  return format(parseISO(iso), 'd MMM yyyy');
}

export function bytes(n: number) {
  return n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(0)} KB` : `${(n / 1048576).toFixed(1)} MB`;
}
