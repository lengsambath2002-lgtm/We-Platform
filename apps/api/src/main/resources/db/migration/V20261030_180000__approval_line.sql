-- The approval line: each employee's 2nd approver (empty = their manager's manager), workflow steps that ask for
-- them, and requests approved automatically when nobody stands above the requester (the top of the organisation).

ALTER TABLE employee_profile ADD COLUMN second_manager_id UUID REFERENCES app_user (id) ON DELETE SET NULL;
ALTER TABLE employee_profile ADD CONSTRAINT employee_profile_second_manager_check
    CHECK (second_manager_id IS NULL OR second_manager_id <> user_id);

ALTER TABLE workflow_step_def DROP CONSTRAINT workflow_step_def_approver_type_check;
ALTER TABLE workflow_step_def ADD CONSTRAINT workflow_step_def_approver_type_check
    CHECK (approver_type IN ('USER', 'ORG_ROLE', 'PROJECT_LEAD', 'MANAGER', 'SECOND_MANAGER'));

ALTER TABLE approval_action DROP CONSTRAINT approval_action_action_check;
ALTER TABLE approval_action ADD CONSTRAINT approval_action_action_check
    CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'RESUBMIT', 'WITHDRAW', 'AUTO_APPROVE'));
