'use client';

import Link from 'next/link';

import { Button } from '@/components/ui/button';
import { useT } from '@/i18n/provider';

export default function NotFound() {
  const { t } = useT();
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-lg font-semibold">{t('shell.pageNotFound')}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{t('shell.pageNotFoundBody')}</p>
      <Button asChild variant="outline" className="mt-6"><Link href="/">{t('shell.backToOverview')}</Link></Button>
    </div>
  );
}
