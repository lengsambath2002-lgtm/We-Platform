-- "Delete my account": the sign-in is erased; organisations keep the records they must keep.
ALTER TABLE app_user ADD COLUMN deleted_at TIMESTAMPTZ;
