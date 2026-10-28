import Link from 'next/link';

import { Button } from '@/components/ui/button';

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-lg font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-muted-foreground">This page doesn&apos;t exist in this organisation.</p>
      <Button asChild variant="outline" className="mt-6"><Link href="/">Back to Overview</Link></Button>
    </div>
  );
}
