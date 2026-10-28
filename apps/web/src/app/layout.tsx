import type { Metadata } from 'next';
import { Inter, Noto_Sans_Khmer } from 'next/font/google';

import './globals.css';
import { Providers } from './providers';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter', display: 'swap' });
const khmer = Noto_Sans_Khmer({ subsets: ['khmer'], variable: '--font-khmer', display: 'swap' });

export const metadata: Metadata = {
  title: { default: 'Business Platform', template: '%s · Business Platform' },
  description: 'Projects, people and operations for every kind of organisation, in one place.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${inter.variable} ${khmer.variable} font-sans`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
