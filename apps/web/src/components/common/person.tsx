import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import type { PersonRef } from '@/lib/api/types';
import { cn } from '@/lib/utils';

export function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]?.toUpperCase()).join('');
}

export function PersonAvatar({ person, className }: { person: Pick<PersonRef, 'name' | 'avatarUrl'> | null; className?: string }) {
  if (!person) {
    return <span className={cn('grid size-6 place-items-center rounded-full border border-dashed text-[10px] text-muted-foreground', className)} title="Unassigned">?</span>;
  }
  return (
    <Avatar className={cn('size-6', className)} title={person.name}>
      {person.avatarUrl && <AvatarImage src={person.avatarUrl} alt="" />}
      <AvatarFallback className="bg-primary/10 text-[10px] font-semibold text-primary">{initials(person.name)}</AvatarFallback>
    </Avatar>
  );
}
