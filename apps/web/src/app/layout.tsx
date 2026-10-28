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
      {/* Browser extensions add attributes to <body> before hydration; this ignores those on <body> only. */}
      <body className={`${inter.variable} ${khmer.variable} font-sans`} suppressHydrationWarning>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
