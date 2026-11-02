-- Sign-in with Google through Firebase Authentication: the Firebase user an account is linked to.
ALTER TABLE app_user ADD COLUMN firebase_uid VARCHAR(128) UNIQUE;
