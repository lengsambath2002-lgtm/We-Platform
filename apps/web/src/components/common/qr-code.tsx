'use client';

import qrcode from 'qrcode-generator';
import { useMemo } from 'react';

/** Any text as a QR code, drawn as SVG on white so phones read it in dark mode too. */
export function QrCode({ value, label, className }: { value: string; label: string; className?: string }) {
  const svg = useMemo(() => {
    const qr = qrcode(0, 'M');
    qr.addData(value);
    qr.make();
    return qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true });
  }, [value]);
  // The SVG is generated locally from `value`.
  return <div role="img" aria-label={label} className={className ?? 'size-44 rounded-md bg-white p-1'} dangerouslySetInnerHTML={{ __html: svg }} />;
}
