import { AlertTriangle, Lock, type LucideIcon, RefreshCw, SearchX, WifiOff } from 'lucide-react';
import type { ReactNode } from 'react';

import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { ApiError, errorMessage } from '@/lib/api/client';
import { cn } from '@/lib/utils';

/** What will appear here and, when there is one, the next step. */
export function EmptyState({ icon: Icon, title, body, action, className }: {
  icon: LucideIcon; title: string; body?: string; action?: ReactNode; className?: string;
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed px-6 py-10 text-center', className)}>
      <div className="grid size-10 place-items-center rounded-full bg-muted text-muted-foreground">
        <Icon className="size-5" aria-hidden />
      </div>
      <p className="text-sm font-medium">{title}</p>
      {body && <p className="max-w-sm text-sm text-muted-foreground">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/** A failed load: says what kind of failure (offline, no access, not found, other) and offers a retry. */
export function ErrorState({ error, retry, className }: { error: unknown; retry?: () => void; className?: string }) {
  const e = error instanceof ApiError ? error : null;
  if (e?.status === 403 && e.code !== 'MODULE_DISABLED') return <PermissionDenied className={className} />;
  const Icon = e?.code === 'NETWORK_ERROR' ? WifiOff : e?.status === 404 ? SearchX : AlertTriangle;
  const title = e?.code === 'NETWORK_ERROR' ? "You're offline" : e?.status === 404 ? 'Not found' : e?.code === 'MODULE_DISABLED' ? 'Module switched off' : "Couldn't load this";
  return (
    <div role="alert" className={cn('flex flex-col items-center gap-2 rounded-lg border px-6 py-10 text-center', className)}>
      <Icon className="size-6 text-muted-foreground" aria-hidden />
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-sm text-sm text-muted-foreground">{errorMessage(error)}</p>
      {retry && e?.status !== 404 && (
        <Button variant="outline" size="sm" className="mt-2" onClick={retry}>
          <RefreshCw className="size-4" /> Try again
        </Button>
      )}
    </div>
  );
}

export function PermissionDenied({ className }: { className?: string }) {
  return (
    <div role="alert" className={cn('flex flex-col items-center gap-2 rounded-lg border px-6 py-10 text-center', className)}>
      <Lock className="size-6 text-muted-foreground" aria-hidden />
      <p className="text-sm font-medium">You don&apos;t have access</p>
      <p className="max-w-sm text-sm text-muted-foreground">Ask an owner or admin of this organisation if you need it.</p>
    </div>
  );
}

export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-2" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-11 w-full" />
      ))}
    </div>
  );
}
