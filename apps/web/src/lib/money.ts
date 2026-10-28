import { currentLocale } from '@/i18n/core';

/**
 * Money on screen: amounts are USD (as the API stores them); riel is shown beside them at a fixed reference
 * rate, rounded to the nearest 100 ៛. This is the only place that knows the rate or formats money.
 */
export const KHR_PER_USD = 4100;

/** USD → riel, rounded to the nearest 100. */
export function toKhr(usd: number): number {
  return Math.round((usd * KHR_PER_USD) / 100) * 100;
}

const locale = () => (currentLocale() === 'km' ? 'km-KH' : 'en-US');

/** "$1,234.50" */
export function usd(n: number | string | null | undefined): string {
  const v = Number(n ?? 0);
  return new Intl.NumberFormat(locale(), { style: 'currency', currency: 'USD', currencyDisplay: 'narrowSymbol', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);
}

/** "5,061,500 ៛" (the riel equivalent of a USD amount). */
export function khr(usdAmount: number | string | null | undefined): string {
  return `${new Intl.NumberFormat(locale(), { maximumFractionDigits: 0 }).format(toKhr(Number(usdAmount ?? 0)))} ៛`;
}

/** Rounds half-up to cents, as the API does (for live totals shown while editing; the API's are the real ones). */
export function cents(n: number): number {
  return Math.sign(n) * Math.round((Math.abs(n) + Number.EPSILON) * 100) / 100;
}

export interface LineLike { quantity: number; unitPrice: number; discountPct: number; vat: boolean }

/** The same arithmetic as the API's `Totals`, for display while a document is edited. */
export function documentTotals(lines: LineLike[], vatEnabled: boolean, vatRate = 10) {
  let subtotal = 0;
  let discount = 0;
  let taxable = 0;
  const nets = lines.map((l) => {
    const amount = cents((Number(l.quantity) || 0) * (Number(l.unitPrice) || 0));
    const d = cents((amount * (Number(l.discountPct) || 0)) / 100);
    const net = cents(amount - d);
    subtotal = cents(subtotal + net);
    discount = cents(discount + d);
    if (l.vat) taxable = cents(taxable + net);
    return net;
  });
  const vat = vatEnabled ? cents((taxable * vatRate) / 100) : 0;
  return { nets, subtotal, discount, vat, total: cents(subtotal + vat) };
}
