'use client';

import qrcode from 'qrcode-generator';
import { useMemo } from 'react';

/** A KHQR string as a QR code (scanned by any Cambodian banking app), drawn as SVG. */
export function KhqrCode({ value, label }: { value: string; label: string }) {
  const svg = useMemo(() => {
    const qr = qrcode(0, 'M');
    qr.addData(value);
    qr.make();
    return qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true });
  }, [value]);
  return (
    <figure className="inline-flex flex-col items-center gap-2 rounded-xl border bg-white p-3">
      <div className="flex h-10 w-full items-center justify-center rounded-t-md bg-[#e1232e] text-sm font-bold tracking-widest text-white">KHQR</div>
      {/* The SVG is generated locally from the invoice's own KHQR string. */}
      <div role="img" aria-label={label} className="size-48" dangerouslySetInnerHTML={{ __html: svg }} />
    </figure>
  );
}
