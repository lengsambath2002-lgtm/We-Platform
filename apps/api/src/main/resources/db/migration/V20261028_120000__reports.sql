-- Reports: range scans by creation / completion time within a project, and approvals by submission time.
CREATE INDEX ix_task_project_created ON task (project_id, created_at) WHERE parent_id IS NULL;
CREATE INDEX ix_task_project_completed ON task (project_id, completed_at) WHERE completed_at IS NOT NULL AND parent_id IS NULL;
CREATE INDEX ix_approval_org_created ON approval_request (org_id, created_at);
