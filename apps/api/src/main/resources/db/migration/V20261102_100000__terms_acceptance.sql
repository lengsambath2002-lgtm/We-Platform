-- Which Terms of Service / Privacy Policy version an account accepted at sign-up, and when.
ALTER TABLE app_user
    ADD COLUMN terms_version     VARCHAR(20),
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;
