import type { Metadata } from 'next';
import { Kantumruy_Pro } from 'next/font/google';

import './globals.css';
import { Providers } from './providers';

// One typeface for Khmer and English (Latin), so mixed text looks even.
const kantumruy = Kantumruy_Pro({ subsets: ['khmer', 'latin', 'latin-ext'], variable: '--font-kantumruy', display: 'swap' });

export const metadata: Metadata = {
  title: { default: 'Business Platform', template: '%s · Business Platform' },
  description: 'Projects, people and operations for every kind of organisation, in one place.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      {/* Browser extensions add attributes to <body> before hydration; this ignores those on <body> only. */}
      <body className={`${kantumruy.variable} font-sans`} suppressHydrationWarning>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
