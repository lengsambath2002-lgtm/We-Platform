import { enUS, km as kmDate } from 'date-fns/locale';

import { type MessageKey, namespaces } from './messages';

export type Locale = 'en' | 'km';
export const LOCALES: Locale[] = ['en', 'km'];
export const toLocale = (v: string | null | undefined): Locale => (v?.toLowerCase().startsWith('km') || v?.toLowerCase().startsWith('kh') ? 'km' : 'en');

let current: Locale = 'en';
/** The language used by code outside React (date helpers, error messages, the API client). */
export const currentLocale = () => current;
export const setCurrentLocale = (l: Locale) => { current = l; };
export const dateLocale = () => (current === 'km' ? kmDate : enUS);

export type Vars = Record<string, string | number>;

/**
 * Looks up `ns.key` in the given language (English if missing) and fills `{name}` placeholders.
 * With a numeric `count`, `<key>_one` is used when count is 1 and that key exists (Khmer has no plural forms).
 */
export function translate(locale: Locale, key: MessageKey, vars?: Vars): string {
  const [ns, k] = key.split('.', 2) as [keyof typeof namespaces, string];
  const table = namespaces[ns] as unknown as Record<Locale, Record<string, string>>;
  const pick = (name: string) => table[locale]?.[name] ?? table.en[name];
  const text = (vars?.count === 1 && pick(`${k}_one`)) || pick(k) || key;
  return vars ? text.replace(/\{(\w+)\}/g, (m, v: string) => (v in vars ? String(vars[v]) : m)) : text;
}

/** For non-React code: translate in the current language. */
export const tr = (key: MessageKey, vars?: Vars) => translate(current, key, vars);

/**
 * A lookup table whose values are read in the current language each time, e.g.
 * `STATUS_LABEL[s]`. Safe as a module constant because pages remount when the language changes.
 */
export function labels<K extends string>(keys: readonly K[], key: (k: K) => MessageKey): Record<K, string> {
  const o = {} as Record<K, string>;
  for (const k of keys) Object.defineProperty(o, k, { enumerable: true, get: () => tr(key(k)) });
  return o;
}
