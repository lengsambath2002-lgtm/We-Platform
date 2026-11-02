'use client';

import {
  ArrowRight, BarChart3, Bell, CalendarDays, Check, CheckCircle2, ChevronDown, DatabaseBackup, Download, FileText, FolderKanban,
  Languages, LayoutDashboard, type LucideIcon, Menu, ScrollText, ShieldCheck, Smartphone, Stamp, UsersRound,
} from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';

import { LanguageSwitch } from '@/components/shell/language-switch';
import { MODULE_ICON } from '@/components/shell/nav';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import type { MessageKey } from '@/i18n/messages';
import { useT } from '@/i18n/provider';
import { type Plan, usePlans } from '@/lib/api/billing';
import { useSession } from '@/lib/auth/session';
import { usd } from '@/lib/money';
import { cn } from '@/lib/utils';

const MODULES = ['projects', 'business', 'education', 'retail', 'hr', 'service'] as const;
const NAV = [
  { href: '#features', label: 'landing.navFeatures' },
  { href: '#modules', label: 'landing.navModules' },
  { href: '#pricing', label: 'landing.navPricing' },
  { href: '#faq', label: 'landing.navFaq' },
] as const satisfies readonly { href: string; label: MessageKey }[];

function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2">
      <span className="grid size-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground shadow-sm">BP</span>
      <span className="text-base font-semibold tracking-tight">Business Platform</span>
    </Link>
  );
}

function Eyebrow({ children }: { children: React.ReactNode }) {
  return <p className="text-sm font-semibold text-primary">{children}</p>;
}

function SectionHead({ eyebrow, title, lead, center = true }: { eyebrow: string; title: string; lead?: string; center?: boolean }) {
  return (
    <div className={cn('max-w-2xl', center && 'mx-auto text-center')}>
      <Eyebrow>{eyebrow}</Eyebrow>
      <h2 className="mt-2 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">{title}</h2>
      {lead && <p className="mt-4 text-base text-pretty text-muted-foreground sm:text-lg">{lead}</p>}
    </div>
  );
}

/** Sign in and start a trial; for someone already signed in, the way into the app. */
function AccountActions({ stacked }: { stacked?: boolean }) {
  const { t } = useT();
  const { user } = useSession();
  if (user) return <Button asChild className={cn(stacked && 'w-full')}><Link href="/">{t('landing.openApp')}<ArrowRight className="size-4" /></Link></Button>;
  return (
    <>
      <Button asChild variant={stacked ? 'outline' : 'ghost'}><Link href="/sign-in">{t('landing.signIn')}</Link></Button>
      <Button asChild><Link href="/sign-up">{t('landing.startTrial')}</Link></Button>
    </>
  );
}

function Header() {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  return (
    <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
        <Logo />
        <nav className="hidden items-center gap-1 md:flex" aria-label={t('shell.mainNav')}>
          {NAV.map((n) => (
            <a key={n.href} href={n.href} className="rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground">{t(n.label)}</a>
          ))}
        </nav>
        <div className="ml-auto hidden items-center gap-2 md:flex">
          <LanguageSwitch />
          <AccountActions />
        </div>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="ml-auto md:hidden" aria-label={t('landing.openMenu')}><Menu className="size-5" /></Button>
          </SheetTrigger>
          <SheetContent side="right" className="w-72 p-6">
            <SheetTitle className="sr-only">Business Platform</SheetTitle>
            <nav className="mt-6 flex flex-col gap-1">
              {NAV.map((n) => (
                <a key={n.href} href={n.href} onClick={() => setOpen(false)} className="rounded-md px-3 py-2.5 text-base font-medium hover:bg-muted">{t(n.label)}</a>
              ))}
            </nav>
            <div className="mt-6 flex flex-col gap-2 border-t pt-6">
              <AccountActions stacked />
              <LanguageSwitch className="mt-2 self-start" />
            </div>
          </SheetContent>
        </Sheet>
      </div>
    </header>
  );
}

/** A drawing of the app (not a screenshot): sidebar, four numbers, my tasks and a week of activity. */
function ProductPreview() {
  const { t } = useT();
  const stats = [
    { label: t('landing.previewOpenTasks'), value: '24' },
    { label: t('landing.previewApprovals'), value: '3' },
    { label: t('landing.previewOnLeave'), value: '2' },
    { label: t('landing.previewInvoices'), value: '$1,280' },
  ];
  const tasks = [
    { title: t('landing.previewTask1'), when: t('landing.previewToday'), done: false },
    { title: t('landing.previewTask2'), when: t('landing.previewToday'), done: false },
    { title: t('landing.previewTask3'), when: t('landing.previewTomorrow'), done: false },
    { title: t('landing.previewTask4'), when: t('landing.previewTomorrow'), done: true },
  ];
  const bars = [40, 65, 52, 80, 70, 35, 58];
  return (
    <div aria-hidden className="relative mx-auto max-w-5xl">
      <div className="overflow-hidden rounded-2xl border bg-card shadow-2xl shadow-primary/10 ring-1 ring-black/5 dark:ring-white/5">
        <div className="flex h-10 items-center gap-1.5 border-b bg-muted/40 px-4">
          <span className="size-2.5 rounded-full bg-muted-foreground/25" /><span className="size-2.5 rounded-full bg-muted-foreground/25" /><span className="size-2.5 rounded-full bg-muted-foreground/25" />
          <span className="mx-auto hidden h-5 w-64 rounded-md bg-background text-center text-[11px] leading-5 text-muted-foreground sm:block">app.businessplatform/angkor-coffee</span>
        </div>
        <div className="flex">
          <div className="hidden w-48 shrink-0 border-r bg-sidebar p-3 sm:block">
            <div className="mb-4 flex items-center gap-2 px-1"><span className="grid size-6 place-items-center rounded bg-muted text-[10px] font-semibold">AC</span><span className="truncate text-xs font-semibold">Angkor Coffee</span></div>
            {[LayoutDashboard, FolderKanban, Stamp, CalendarDays, UsersRound, BarChart3].map((Icon, i) => (
              <div key={i} className={cn('mb-1 flex h-7 items-center gap-2 rounded-md px-2', i === 0 ? 'bg-accent text-primary' : 'text-muted-foreground')}>
                <Icon className="size-3.5" /><span className={cn('h-1.5 rounded-full', i === 0 ? 'w-16 bg-primary/40' : 'w-20 bg-muted-foreground/20')} />
              </div>
            ))}
          </div>
          <div className="min-w-0 flex-1 p-4 sm:p-6">
            <p className="text-sm font-semibold">{t('landing.previewOverview')}</p>
            <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
              {stats.map((s) => (
                <div key={s.label} className="rounded-lg border bg-background p-3">
                  <p className="truncate text-[11px] text-muted-foreground">{s.label}</p>
                  <p className="mt-1 text-lg font-semibold tabular">{s.value}</p>
                </div>
              ))}
            </div>
            <div className="mt-3 grid gap-3 lg:grid-cols-5">
              <div className="rounded-lg border bg-background p-3 lg:col-span-3">
                <p className="mb-2 text-xs font-semibold">{t('landing.previewMyTasks')}</p>
                <ul className="divide-y">
                  {tasks.map((x) => (
                    <li key={x.title} className="flex items-center gap-2 py-2 text-xs">
                      {x.done ? <CheckCircle2 className="size-3.5 text-primary" /> : <span className="size-3.5 rounded-full border-2 border-muted-foreground/40" />}
                      <span className={cn('flex-1 truncate', x.done && 'text-muted-foreground line-through')}>{x.title}</span>
                      <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{x.when}</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="hidden rounded-lg border bg-background p-3 sm:block lg:col-span-2">
                <p className="mb-2 text-xs font-semibold">{t('landing.previewActivity')}</p>
                <div className="flex h-28 items-end gap-2">
                  {bars.map((h, i) => <div key={i} className={cn('flex-1 rounded-t', i === 3 ? 'bg-primary' : 'bg-primary/25')} style={{ height: `${h}%` }} />)}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Hero() {
  const { t } = useT();
  return (
    <section className="relative overflow-hidden border-b">
      <div className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[520px] bg-[linear-gradient(to_right,var(--border)_1px,transparent_1px),linear-gradient(to_bottom,var(--border)_1px,transparent_1px)] bg-[size:48px_48px] opacity-50 [mask-image:linear-gradient(to_bottom,black,transparent)]" aria-hidden />
      <div className="mx-auto max-w-6xl px-4 pt-16 pb-20 sm:px-6 sm:pt-24">
        <div className="mx-auto max-w-3xl text-center">
          <a href="#pricing" className="inline-flex items-center gap-2 rounded-full border bg-card px-3 py-1 text-xs font-medium text-muted-foreground shadow-xs transition-colors hover:text-foreground">
            <span className="size-1.5 rounded-full bg-primary" aria-hidden />{t('landing.eyebrow')}<ArrowRight className="size-3" aria-hidden />
          </a>
          <h1 className="mt-6 text-4xl font-semibold tracking-tight text-balance sm:text-6xl">{t('landing.heroTitle')}</h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-pretty text-muted-foreground">{t('landing.heroLead')}</p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Button asChild size="lg" className="h-11 px-6 text-base"><Link href="/sign-up">{t('landing.startTrial')}<ArrowRight className="size-4" /></Link></Button>
            <Button asChild size="lg" variant="outline" className="h-11 bg-card px-6 text-base"><a href="#pricing">{t('landing.seePricing')}</a></Button>
          </div>
          <ul className="mt-6 flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-muted-foreground">
            {(['landing.heroNote1', 'landing.heroNote2', 'landing.heroNote3'] as const).map((k) => (
              <li key={k} className="flex items-center gap-1.5"><Check className="size-4 text-primary" aria-hidden />{t(k)}</li>
            ))}
          </ul>
        </div>
        <div className="mt-16"><ProductPreview /></div>
      </div>
    </section>
  );
}

function Modules() {
  const { t } = useT();
  return (
    <section id="modules" className="scroll-mt-16 py-20 sm:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <SectionHead eyebrow={t('landing.modulesEyebrow')} title={t('landing.modulesTitle')} lead={t('landing.modulesLead')} />
        <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {MODULES.map((k) => {
            const Icon = MODULE_ICON[k];
            return (
              <div key={k} className="group rounded-xl border bg-card p-6 shadow-xs transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md">
                <span className="grid size-11 place-items-center rounded-lg bg-primary/10 text-primary"><Icon className="size-5" aria-hidden /></span>
                <h3 className="mt-5 text-base font-semibold">{t(`landing.mod_${k}`)}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{t(`modules.about_${k}`)}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

const FEATURES: { icon: LucideIcon; title: MessageKey; body: MessageKey }[] = [
  { icon: Stamp, title: 'landing.f_approvals', body: 'landing.f_approvalsBody' },
  { icon: ShieldCheck, title: 'landing.f_roles', body: 'landing.f_rolesBody' },
  { icon: CalendarDays, title: 'landing.f_calendar', body: 'landing.f_calendarBody' },
  { icon: FileText, title: 'landing.f_documents', body: 'landing.f_documentsBody' },
  { icon: Bell, title: 'landing.f_notifications', body: 'landing.f_notificationsBody' },
  { icon: BarChart3, title: 'landing.f_reports', body: 'landing.f_reportsBody' },
  { icon: Languages, title: 'landing.f_language', body: 'landing.f_languageBody' },
  { icon: Smartphone, title: 'landing.f_phone', body: 'landing.f_phoneBody' },
];

function Features() {
  const { t } = useT();
  return (
    <section id="features" className="scroll-mt-16 border-y bg-card py-20 sm:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <SectionHead eyebrow={t('landing.featuresEyebrow')} title={t('landing.featuresTitle')} lead={t('landing.featuresLead')} />
        <dl className="mt-14 grid gap-x-8 gap-y-10 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f) => (
            <div key={f.title}>
              <dt className="flex items-center gap-3 text-base font-semibold">
                <span className="grid size-9 place-items-center rounded-lg border bg-background text-primary"><f.icon className="size-4" aria-hidden /></span>
                {t(f.title)}
              </dt>
              <dd className="mt-3 text-sm text-muted-foreground">{t(f.body)}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}

function Steps() {
  const { t } = useT();
  const steps = [['landing.step1', 'landing.step1Body'], ['landing.step2', 'landing.step2Body'], ['landing.step3', 'landing.step3Body']] as const;
  return (
    <section className="py-20 sm:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <SectionHead eyebrow={t('landing.stepsEyebrow')} title={t('landing.stepsTitle')} />
        <ol className="mt-14 grid gap-6 md:grid-cols-3">
          {steps.map(([title, body], i) => (
            <li key={title} className="relative rounded-xl border bg-card p-6 shadow-xs">
              <span className="grid size-9 place-items-center rounded-full bg-primary text-sm font-semibold text-primary-foreground tabular">{i + 1}</span>
              <h3 className="mt-5 text-base font-semibold">{t(title)}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{t(body)}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function Security() {
  const { t } = useT();
  const items: { icon: LucideIcon; title: MessageKey; body: MessageKey }[] = [
    { icon: ShieldCheck, title: 'landing.sec_twoStep', body: 'landing.sec_twoStepBody' },
    { icon: DatabaseBackup, title: 'landing.sec_backups', body: 'landing.sec_backupsBody' },
    { icon: Download, title: 'landing.sec_export', body: 'landing.sec_exportBody' },
    { icon: ScrollText, title: 'landing.sec_audit', body: 'landing.sec_auditBody' },
  ];
  return (
    <section className="border-y bg-card py-20 sm:py-28">
      <div className="mx-auto grid max-w-6xl gap-12 px-4 sm:px-6 lg:grid-cols-5 lg:items-center">
        <div className="lg:col-span-2">
          <SectionHead center={false} eyebrow={t('landing.securityEyebrow')} title={t('landing.securityTitle')} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:col-span-3">
          {items.map((x) => (
            <div key={x.title} className="rounded-xl border bg-background p-5">
              <x.icon className="size-5 text-primary" aria-hidden />
              <h3 className="mt-3 text-sm font-semibold">{t(x.title)}</h3>
              <p className="mt-1.5 text-sm text-muted-foreground">{t(x.body)}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function PlanCard({ plan, yearly, featured, total }: { plan: Plan; yearly: boolean; featured: boolean; total: number }) {
  const { t, locale } = useT();
  const monthly = Number(plan.priceMonthly);
  const perMonth = yearly ? Number(plan.priceYearly) / 12 : monthly;
  const free = monthly === 0;
  return (
    <div className={cn('relative flex flex-col rounded-2xl border bg-card p-7 shadow-xs', featured && 'border-primary shadow-xl shadow-primary/10 ring-1 ring-primary lg:-my-4 lg:py-11')}>
      {featured && <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-primary px-3 py-1 text-xs font-semibold text-primary-foreground">{t('landing.popular')}</span>}
      <h3 className="text-lg font-semibold">{locale === 'km' && plan.nameKm ? plan.nameKm : plan.nameEn}</h3>
      <p className="mt-6 flex items-baseline gap-1.5">
        <span className="text-5xl font-semibold tracking-tight tabular">{free ? '$0' : usd(perMonth)}</span>
        <span className="text-sm text-muted-foreground">{t('landing.perMember')}</span>
      </p>
      <p className="mt-2 h-5 text-sm text-muted-foreground tabular">
        {free ? t('landing.free') : yearly ? t('landing.billedYearly', { amount: usd(plan.priceYearly) }) : t('landing.billedMonthly')}
      </p>
      <Button asChild className="mt-6 w-full" variant={featured ? 'default' : 'outline'} size="lg">
        <Link href="/sign-up">{free ? t('landing.startFree') : plan.trialDays > 0 ? t('landing.startTrial') : t('landing.choose')}</Link>
      </Button>
      <ul className="mt-8 space-y-3 border-t pt-6 text-sm">
        <li className="flex gap-3"><Check className="size-4 shrink-0 text-primary" aria-hidden />{plan.maxMembers == null ? t('landing.unlimited') : t('landing.upTo', { count: plan.maxMembers })}</li>
        <li className="flex gap-3"><Check className="size-4 shrink-0 text-primary" aria-hidden />{t('landing.storage', { count: plan.storageGb })}</li>
        <li className="flex gap-3"><Check className="size-4 shrink-0 text-primary" aria-hidden />{plan.modules.length >= total ? t('landing.allModules') : t('landing.modulesCount', { count: plan.modules.length })}</li>
        {plan.trialDays > 0 && <li className="flex gap-3"><Check className="size-4 shrink-0 text-primary" aria-hidden />{t('landing.trialDays', { count: plan.trialDays })}</li>}
        {plan.modules.filter((k) => (MODULES as readonly string[]).includes(k)).map((k) => (
          <li key={k} className="flex gap-3 text-muted-foreground"><Check className="size-4 shrink-0 text-muted-foreground/60" aria-hidden />{t(`landing.mod_${k as (typeof MODULES)[number]}`)}</li>
        ))}
      </ul>
    </div>
  );
}

function Pricing() {
  const { t } = useT();
  const plans = usePlans();
  const [yearly, setYearly] = useState(false);
  const list = (plans.data ?? []).filter((p) => p.publicPlan).sort((a, b) => a.sortOrder - b.sortOrder);
  // "Most popular": the cheapest paid plan with a trial (the one new organisations start on).
  const featured = list.filter((p) => Number(p.priceMonthly) > 0).sort((a, b) => Number(a.priceMonthly) - Number(b.priceMonthly))[0]?.code;
  const ref = list.find((p) => Number(p.priceMonthly) > 0);
  const monthsFree = ref ? Math.round(12 - Number(ref.priceYearly) / Number(ref.priceMonthly)) : 0;
  return (
    <section id="pricing" className="scroll-mt-16 py-20 sm:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <SectionHead eyebrow={t('landing.pricingEyebrow')} title={t('landing.pricingTitle')} lead={t('landing.pricingLead')} />
        <div className="mt-10 flex justify-center">
          <div role="radiogroup" aria-label={t('landing.pricingTitle')} className="inline-flex items-center gap-1 rounded-full border bg-card p-1 shadow-xs">
            {[false, true].map((y) => (
              <button key={String(y)} type="button" role="radio" aria-checked={yearly === y} onClick={() => setYearly(y)}
                className={cn('inline-flex h-9 items-center gap-2 rounded-full px-4 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground',
                  yearly === y && 'bg-primary text-primary-foreground hover:text-primary-foreground')}>
                {t(y ? 'landing.yearly' : 'landing.monthly')}
                {y && monthsFree > 0 && <span className={cn('rounded-full px-2 py-0.5 text-[11px]', yearly ? 'bg-primary-foreground/20' : 'bg-accent text-accent-foreground')}>{t('landing.yearlySave', { count: monthsFree })}</span>}
              </button>
            ))}
          </div>
        </div>
        {plans.error ? (
          <p className="mt-12 text-center text-sm text-muted-foreground">{t('landing.pricingError')}</p>
        ) : !plans.data ? (
          <div className="mt-14 grid gap-6 lg:grid-cols-3">{[0, 1, 2].map((i) => <div key={i} className="h-[520px] animate-pulse rounded-2xl border bg-muted/40" />)}</div>
        ) : (
          <div className={cn('mt-14 grid items-start gap-6', list.length >= 3 ? 'lg:grid-cols-3' : 'mx-auto max-w-3xl md:grid-cols-2')}>
            {list.map((p) => <PlanCard key={p.code} plan={p} yearly={yearly} featured={p.code === featured} total={MODULES.length} />)}
          </div>
        )}
        <p className="mt-10 text-center text-sm text-muted-foreground">{t('landing.payNote')}</p>
      </div>
    </section>
  );
}

function Faq() {
  const { t } = useT();
  const qs = [1, 2, 3, 4, 5, 6] as const;
  return (
    <section id="faq" className="scroll-mt-16 border-t bg-card py-20 sm:py-28">
      <div className="mx-auto grid max-w-6xl gap-12 px-4 sm:px-6 lg:grid-cols-3">
        <SectionHead center={false} eyebrow={t('landing.faqEyebrow')} title={t('landing.faqTitle')} />
        <div className="divide-y rounded-xl border bg-background lg:col-span-2">
          {qs.map((n) => (
            <details key={n} className="group px-5 [&_summary::-webkit-details-marker]:hidden">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 py-5 text-base font-medium">
                {t(`landing.q${n}`)}
                <ChevronDown className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-180" aria-hidden />
              </summary>
              <p className="-mt-1 pb-5 text-sm text-muted-foreground">{t(`landing.a${n}`)}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

function FinalCta() {
  const { t } = useT();
  return (
    <section className="px-4 py-20 sm:px-6 sm:py-28">
      <div className="mx-auto max-w-5xl overflow-hidden rounded-3xl bg-primary px-6 py-16 text-center text-primary-foreground shadow-xl shadow-primary/20 sm:px-16">
        <h2 className="text-3xl font-semibold tracking-tight text-balance sm:text-4xl">{t('landing.ctaTitle')}</h2>
        <p className="mx-auto mt-4 max-w-xl text-base text-primary-foreground/80 sm:text-lg">{t('landing.ctaLead')}</p>
        <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <Button asChild size="lg" variant="secondary" className="h-11 bg-white px-6 text-base text-primary hover:bg-white/90">
            <Link href="/sign-up">{t('landing.startTrial')}<ArrowRight className="size-4" /></Link>
          </Button>
          <Button asChild size="lg" variant="ghost" className="h-11 px-6 text-base text-primary-foreground hover:bg-white/10 hover:text-primary-foreground">
            <Link href="/sign-in">{t('landing.signIn')}</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}

function Footer() {
  const { t } = useT();
  const col = (title: string, links: { href: string; label: string }[]) => (
    <div>
      <p className="text-sm font-semibold">{title}</p>
      <ul className="mt-4 space-y-3 text-sm">
        {links.map((l) => <li key={l.href}><Link href={l.href} className="text-muted-foreground hover:text-foreground">{l.label}</Link></li>)}
      </ul>
    </div>
  );
  return (
    <footer className="border-t">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-14 sm:px-6 md:grid-cols-5">
        <div className="md:col-span-2">
          <Logo />
          <p className="mt-4 max-w-xs text-sm text-muted-foreground">{t('landing.footerTagline')}</p>
          <LanguageSwitch className="mt-6" />
        </div>
        {col(t('landing.footerProduct'), NAV.map((n) => ({ href: `/${n.href}`, label: t(n.label) })))}
        {col(t('landing.footerAccount'), [{ href: '/sign-in', label: t('landing.signIn') }, { href: '/sign-up', label: t('landing.createAccount') }])}
        {col(t('landing.footerLegal'), [{ href: '/terms', label: t('auth.terms') }, { href: '/privacy', label: t('auth.privacy') }])}
      </div>
      <div className="border-t">
        <p className="mx-auto max-w-6xl px-4 py-6 text-xs text-muted-foreground sm:px-6">{t('landing.rights', { year: new Date().getFullYear() })}</p>
      </div>
    </footer>
  );
}

/**
 * The public landing page: what the platform is, its modules, pricing and FAQ, with sign-up everywhere. With `section`
 * (for /pricing and /features), it opens scrolled to that section.
 */
export function Landing({ section }: { section?: 'pricing' | 'features' }) {
  useEffect(() => {
    if (section) document.getElementById(section)?.scrollIntoView();
  }, [section]);
  return (
    <div className="min-h-dvh bg-background">
      <Header />
      <main>
        <Hero />
        <Modules />
        <Features />
        <Steps />
        <Security />
        <Pricing />
        <Faq />
        <FinalCta />
      </main>
      <Footer />
    </div>
  );
}
