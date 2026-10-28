'use client';

import { ErrorState } from '@/components/common/states';

export default function OrgError({ error, reset }: { error: Error; reset: () => void }) {
  return <ErrorState error={error} retry={reset} className="mt-10" />;
}
