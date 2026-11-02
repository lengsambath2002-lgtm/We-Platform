import type { Metadata } from 'next';

import { LegalPage } from '@/components/legal/legal-page';

export const metadata: Metadata = { title: 'Privacy Policy' };

export default function Page() {
  return <LegalPage doc="privacy" />;
}
