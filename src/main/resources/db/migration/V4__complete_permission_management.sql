ALTER TABLE permissions
    ADD COLUMN name VARCHAR(255);

UPDATE permissions
SET name = description
WHERE name IS NULL;

ALTER TABLE permissions
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE permissions
    ADD COLUMN status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

INSERT INTO permissions (id, name, code, description) VALUES
    ('10000000-0000-0000-0000-000000000010', 'View permissions', 'PERMISSION_VIEW', 'View permissions'),
    ('10000000-0000-0000-0000-000000000011', 'Create permissions', 'PERMISSION_CREATE', 'Create permissions'),
    ('10000000-0000-0000-0000-000000000012', 'Update permissions', 'PERMISSION_UPDATE', 'Update permissions'),
    ('10000000-0000-0000-0000-000000000013', 'Delete permissions', 'PERMISSION_DELETE', 'Delete permissions');

INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
CROSS JOIN permissions
WHERE roles.code = 'ADMIN'
  AND permissions.code IN ('PERMISSION_VIEW', 'PERMISSION_CREATE', 'PERMISSION_UPDATE', 'PERMISSION_DELETE');