ALTER TABLE roles
    ADD COLUMN status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

INSERT INTO permissions (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000007', 'ROLE_CREATE', 'Create roles'),
    ('10000000-0000-0000-0000-000000000008', 'ROLE_UPDATE', 'Update roles'),
    ('10000000-0000-0000-0000-000000000009', 'ROLE_DELETE', 'Delete roles');

INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
CROSS JOIN permissions
WHERE roles.code = 'ADMIN'
  AND permissions.code IN ('ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE');