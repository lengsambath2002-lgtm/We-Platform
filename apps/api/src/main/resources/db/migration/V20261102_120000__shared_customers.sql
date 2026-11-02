-- One customer list for the whole organisation, as in real-world suites: General Business (deals, quotes, invoices)
-- and Service & Operations (tickets, sites, assets, work orders) now share the same customer records.
--
-- biz_customer becomes `customer`; each service customer moves into it, keeping its id, unless the organisation
-- already has a customer of the same name, in which case the service records point at that one and its empty
-- contact details are filled from the service customer. svc_customer is then dropped.

ALTER TABLE biz_customer RENAME TO customer;
ALTER INDEX biz_customer_pkey RENAME TO customer_pkey;
ALTER INDEX ix_biz_customer_name RENAME TO ix_customer_name;
ALTER TABLE customer RENAME CONSTRAINT biz_customer_kind_check TO customer_kind_check;
ALTER TABLE customer RENAME CONSTRAINT biz_customer_org_id_fkey TO customer_org_id_fkey;
ALTER TABLE customer RENAME CONSTRAINT biz_customer_created_by_fkey TO customer_created_by_fkey;
-- Service creates customers without a kind: a company (or household) unless someone says otherwise.
ALTER TABLE customer ALTER COLUMN kind SET DEFAULT 'COMPANY';

CREATE TEMP TABLE svc_customer_map ON COMMIT DROP AS
SELECT s.id AS old_id,
       COALESCE((SELECT c.id FROM customer c
                 WHERE c.org_id = s.org_id AND lower(c.name) = lower(s.name)
                 ORDER BY c.archived, c.created_at LIMIT 1), s.id) AS new_id
FROM svc_customer s;

INSERT INTO customer (id, org_id, kind, name, contact_name, email, phone, address, notes, archived, created_by, created_at, updated_at)
SELECT s.id, s.org_id, 'COMPANY', s.name, s.contact_name, s.email, s.phone, s.address, s.notes, s.archived, s.created_by, s.created_at, s.updated_at
FROM svc_customer s JOIN svc_customer_map m ON m.old_id = s.id
WHERE m.new_id = s.id;

UPDATE customer c
SET contact_name = COALESCE(c.contact_name, s.contact_name),
    email        = COALESCE(c.email, s.email),
    phone        = COALESCE(c.phone, s.phone),
    address      = COALESCE(c.address, s.address),
    notes        = COALESCE(c.notes, s.notes),
    updated_at   = now()
FROM svc_customer s JOIN svc_customer_map m ON m.old_id = s.id
WHERE m.new_id <> s.id AND c.id = m.new_id;

ALTER TABLE svc_site       DROP CONSTRAINT svc_site_customer_id_fkey;
ALTER TABLE svc_asset      DROP CONSTRAINT svc_asset_customer_id_fkey;
ALTER TABLE svc_ticket     DROP CONSTRAINT svc_ticket_customer_id_fkey;
ALTER TABLE svc_work_order DROP CONSTRAINT svc_work_order_customer_id_fkey;

UPDATE svc_site t       SET customer_id = m.new_id FROM svc_customer_map m WHERE t.customer_id = m.old_id AND m.new_id <> m.old_id;
UPDATE svc_asset t      SET customer_id = m.new_id FROM svc_customer_map m WHERE t.customer_id = m.old_id AND m.new_id <> m.old_id;
UPDATE svc_ticket t     SET customer_id = m.new_id FROM svc_customer_map m WHERE t.customer_id = m.old_id AND m.new_id <> m.old_id;
UPDATE svc_work_order t SET customer_id = m.new_id FROM svc_customer_map m WHERE t.customer_id = m.old_id AND m.new_id <> m.old_id;

ALTER TABLE svc_site       ADD CONSTRAINT svc_site_customer_id_fkey       FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE;
ALTER TABLE svc_asset      ADD CONSTRAINT svc_asset_customer_id_fkey      FOREIGN KEY (customer_id) REFERENCES customer (id);
ALTER TABLE svc_ticket     ADD CONSTRAINT svc_ticket_customer_id_fkey     FOREIGN KEY (customer_id) REFERENCES customer (id);
ALTER TABLE svc_work_order ADD CONSTRAINT svc_work_order_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customer (id);

DROP TABLE svc_customer;
