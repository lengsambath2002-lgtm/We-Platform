-- Documents: the organisation's shared library of folders and files, with versions and a trash.
-- Files themselves are stored_file rows (same store, limits and types as task attachments).

CREATE TABLE doc_folder (
    id          UUID          PRIMARY KEY,
    org_id      UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    parent_id   UUID          REFERENCES doc_folder (id) ON DELETE CASCADE,
    name        VARCHAR(120)  NOT NULL,
    created_by  UUID          NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- In the trash when set; everything inside goes with it.
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID          REFERENCES app_user (id),
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_doc_folder_org ON doc_folder (org_id, parent_id);

CREATE TABLE document (
    id           UUID           PRIMARY KEY,
    org_id       UUID           NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    folder_id    UUID           REFERENCES doc_folder (id) ON DELETE CASCADE,
    name         VARCHAR(255)   NOT NULL,
    description  VARCHAR(2000),
    -- The current version (its number and file); older ones are in document_version.
    version      INT            NOT NULL CHECK (version > 0),
    file_id      UUID           NOT NULL REFERENCES stored_file (id),
    uploaded_by  UUID           NOT NULL REFERENCES app_user (id),
    updated_by   UUID           NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID           REFERENCES app_user (id)
);
CREATE INDEX ix_document_folder ON document (org_id, folder_id);
CREATE INDEX ix_document_name ON document (org_id, lower(name));

CREATE TABLE document_version (
    id           UUID          PRIMARY KEY,
    org_id       UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    document_id  UUID          NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    version      INT           NOT NULL CHECK (version > 0),
    file_id      UUID          NOT NULL REFERENCES stored_file (id),
    note         VARCHAR(500),
    uploaded_by  UUID          NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (document_id, version)
);
