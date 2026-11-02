import type { SVGProps } from 'react';

/**
 * VirtualBiz's app icon (its brand colours, as in VirtualBiz's own favicon): Retail & Commerce is VirtualBiz, so the
 * module and its shops show VirtualBiz's mark rather than a generic icon. Sized like the icons it stands in for.
 */
export function VirtualBizMark(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" {...props}>
      <rect width="64" height="64" rx="15" fill="#F5B800" />
      <path fill="#141A2B" d="M13 16a3 3 0 0 1 3-3h32a3 3 0 0 1 3 3v8a6.33 6.33 0 0 1-12.67 0a6.33 6.33 0 0 1-12.66 0A6.33 6.33 0 0 1 13 24z" />
      <path d="M20.5 36.5 32 51l11.5-14.5" fill="none" stroke="#141A2B" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
