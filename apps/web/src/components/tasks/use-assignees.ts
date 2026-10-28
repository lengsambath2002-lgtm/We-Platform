'use client';

import { useMemo } from 'react';

import { useMembers, useProjectMembers } from '@/lib/api/queries';
import type { PersonRef, Project } from '@/lib/api/types';

/** Who a task in this project can be assigned to (the server checks the same rule). */
export function useAssignees(org: string, project: Project | undefined): PersonRef[] {
  const members = useMembers(org);
  const projectMembers = useProjectMembers(org, project?.key ?? '');
  return useMemo(() => {
    if (!project) return [];
    const pm = projectMembers.data ?? [];
    const viewers = new Set(pm.filter((m) => m.role === 'VIEWER').map((m) => m.person.id));
    const inProject = new Set(pm.map((m) => m.person.id));
    return (members.data ?? [])
      .filter((m) => m.status === 'ACTIVE' && !viewers.has(m.userId))
      .filter((m) => inProject.has(m.userId) || m.role === 'OWNER' || m.role === 'ADMIN'
        || (project.visibility === 'ORG' && m.role !== 'GUEST'))
      .map((m) => ({ id: m.userId, name: m.name, avatarUrl: m.avatarUrl }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [members.data, projectMembers.data, project]);
}
