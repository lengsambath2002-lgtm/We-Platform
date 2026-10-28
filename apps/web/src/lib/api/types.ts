// Response shapes of the platform API (apps/api payloads).

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST';
export type ProjectRole = 'LEAD' | 'MEMBER' | 'VIEWER';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type ApprovalStatus = 'PENDING' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface User {
  id: string;
  name: string;
  email: string;
  locale: string;
  avatarUrl: string | null;
}

export interface Session {
  accessToken: string;
  expiresIn: number;
  user: User;
}

export interface OrgSummary {
  id: string;
  slug: string;
  name: string;
  role: OrgRole;
}

export interface ModuleState {
  key: string;
  nameEn: string;
  nameKm: string;
  kind: 'CORE' | 'INDUSTRY';
  available: boolean;
  enabled: boolean;
}

export interface OrgContext {
  org: OrgSummary;
  permissions: string[];
  modules: ModuleState[];
}

export interface Member {
  membershipId: string;
  userId: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  role: OrgRole;
  status: 'ACTIVE' | 'SUSPENDED';
  title: string | null;
  joinedAt: string;
}

export interface Invitation {
  id: string;
  email: string;
  role: OrgRole;
  expiresAt: string;
  link: string | null;
}

export interface Team {
  id: string;
  name: string;
  membershipIds: string[];
}

export interface PersonRef {
  id: string;
  name: string;
  avatarUrl: string | null;
}

export interface Project {
  id: string;
  key: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'ON_HOLD' | 'DONE' | 'ARCHIVED';
  visibility: 'ORG' | 'PRIVATE';
  lead: PersonRef | null;
  startDate: string | null;
  dueDate: string | null;
  taskCounts: Partial<Record<TaskStatus, number>>;
  progress: number;
  myRole: ProjectRole | null;
  canEdit: boolean;
  canManage: boolean;
  updatedAt: string;
}

export interface ProjectMember {
  person: PersonRef;
  role: ProjectRole;
}

export interface Task {
  id: string;
  key: string;
  number: number;
  projectId: string;
  projectKey: string;
  projectName: string;
  parentId: string | null;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: Priority;
  assignee: PersonRef | null;
  reporter: PersonRef;
  startDate: string | null;
  dueDate: string | null;
  subtasks: number;
  subtasksDone: number;
  comments: number;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FileRef {
  id: string;
  name: string;
  contentType: string;
  size: number;
  createdAt: string;
}

export interface TaskDetail {
  task: Task;
  subtasks: Task[];
  attachments: FileRef[];
  canEdit: boolean;
  /** False while the project is archived. */
  canComment: boolean;
}

export interface Comment {
  id: string;
  author: PersonRef;
  body: string;
  createdAt: string;
  editedAt: string | null;
  canEdit: boolean;
  canDelete: boolean;
}

export interface ActivityItem {
  id: string;
  actor: PersonRef | null;
  verb: string;
  data: Record<string, unknown>;
  at: string;
}

export interface Workflow {
  id: string;
  name: string;
  moduleKey: string;
  active: boolean;
  steps: { order: number; type: 'USER' | 'ORG_ROLE' | 'PROJECT_LEAD' | 'MANAGER'; userId: string | null; userName: string | null; role: string | null }[];
}

export interface ApprovalSummary {
  id: string;
  title: string;
  moduleKey: string;
  recordType: string;
  recordId: string;
  recordLink: string;
  status: ApprovalStatus;
  currentStep: number;
  steps: number;
  requester: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalDetail {
  summary: ApprovalSummary;
  text: string | null;
  workflowName: string;
  steps: { order: number; approver: { id: string; name: string } | null; role: string | null; status: string; decidedBy: { id: string; name: string } | null; decidedAt: string | null }[];
  history: { actor: { id: string; name: string }; action: string; step: number | null; comment: string | null; at: string }[];
  canDecide: boolean;
  canResubmit: boolean;
  canWithdraw: boolean;
}

export interface CalendarEvent {
  id: string;
  title: string;
  description: string | null;
  location: string | null;
  startsAt: string;
  endsAt: string;
  allDay: boolean;
  projectId: string | null;
  ownerId: string;
  attendeeIds: string[];
  canEdit: boolean;
  canDelete: boolean;
}

export interface Deadline {
  module: string;
  id: string;
  key: string;
  title: string;
  date: string;
  status: TaskStatus;
  link: string;
  mine: boolean;
}

/** A module's whole-day span on the calendar (e.g. approved leave, `kind` 'leave'); `title` is the person's name for leave. */
export interface Period {
  module: string;
  kind: string;
  id: string;
  title: string;
  start: string;
  end: string;
  halfStart: boolean;
  halfEnd: boolean;
  link: string;
  mine: boolean;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  actorId: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface Overview {
  openTasks: number;
  overdueTasks: number;
  dueThisWeek: number;
  pendingApprovals: number;
  myTasks: Task[];
  deadlines: Task[];
  projects: { key: string; name: string; status: string; progress: number; open: number; total: number; dueDate: string | null }[];
  approvals: ApprovalSummary[];
  events: CalendarEvent[];
  activity: ActivityItem[];
  projectsEnabled: boolean;
}
