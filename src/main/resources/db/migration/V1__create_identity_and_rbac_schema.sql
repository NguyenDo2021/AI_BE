CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    username_normalized VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL UNIQUE,
    phone VARCHAR(20),
    status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_username_not_blank CHECK (length(trim(username)) > 0),
    CONSTRAINT ck_users_full_name_not_blank CHECK (length(trim(full_name)) > 0)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_users_created_at ON users (created_at DESC);

INSERT INTO roles (id, name, code, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Administrator', 'ADMIN', 'Full access to the frontend modules'),
    ('00000000-0000-0000-0000-000000000002', 'User manager', 'USER_MANAGER', 'Manage users and view dashboard');

INSERT INTO permissions (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000001', 'DASHBOARD_VIEW', 'View the dashboard'),
    ('10000000-0000-0000-0000-000000000002', 'USER_VIEW', 'View users'),
    ('10000000-0000-0000-0000-000000000003', 'USER_CREATE', 'Create users'),
    ('10000000-0000-0000-0000-000000000004', 'USER_UPDATE', 'Update users'),
    ('10000000-0000-0000-0000-000000000005', 'USER_DELETE', 'Delete users'),
    ('10000000-0000-0000-0000-000000000006', 'ROLE_VIEW', 'View roles');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions;

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM permissions
WHERE code IN ('DASHBOARD_VIEW', 'USER_VIEW', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE');
