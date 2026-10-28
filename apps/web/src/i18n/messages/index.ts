import * as account from './account';
import * as admin from './admin';
import * as approvals from './approvals';
import * as auth from './auth';
import * as calendar from './calendar';
import * as common from './common';
import * as documents from './documents';
import * as modules from './modules';
import * as org from './org';
import * as overview from './overview';
import * as projects from './projects';
import * as reports from './reports';
import * as shell from './shell';
import * as status from './status';
import * as tasks from './tasks';

/**
 * One file per area (namespace), each exporting `en` and `km` with the same keys
 * (`km` is typed from `en`, so a missing Khmer key fails the typecheck). Keys are used as `t('<namespace>.<key>')`.
 */
export const namespaces = { account, admin, approvals, auth, calendar, common, documents, modules, org, overview, projects, reports, shell, status, tasks };

type NS = typeof namespaces;
export type MessageKey = { [N in keyof NS]: `${N & string}.${keyof NS[N]['en'] & string}` }[keyof NS];
