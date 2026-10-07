CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE CHECK (code = upper(trim(code)) AND length(code) > 0),
    name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    address VARCHAR(500),
    phone VARCHAR(20),
    note VARCHAR(2000),
    status SMALLINT NOT NULL CHECK (status IN (0,1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_warehouses_created ON warehouses(created_at DESC, id DESC);

CREATE INDEX ix_warehouses_status ON warehouses(status);

CREATE TABLE product_groups (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE CHECK (code = upper(trim(code)) AND length(code) > 0),
    name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(2000),
    status SMALLINT NOT NULL CHECK (status IN (0,1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_product_groups_created ON product_groups(created_at DESC, id DESC);

CREATE INDEX ix_product_groups_status ON product_groups(status);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE CHECK (code = upper(trim(code)) AND length(code) > 0),
    name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    group_id UUID NOT NULL REFERENCES product_groups(id),
    material VARCHAR(100),
    color VARCHAR(100),
    dimensions VARCHAR(255),
    length_meters NUMERIC(12,3) CHECK (length_meters > 0),
    unit VARCHAR(10) NOT NULL DEFAULT 'cay' CHECK (unit = 'cay'),
    reference_purchase_price NUMERIC(19,0) NOT NULL DEFAULT 0 CHECK (reference_purchase_price >= 0),
    default_sale_price NUMERIC(19,0) NOT NULL DEFAULT 0 CHECK (default_sale_price >= 0),
    low_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
    description VARCHAR(2000),
    status SMALLINT NOT NULL CHECK (status IN (0,1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_products_created ON products(created_at DESC, id DESC);

CREATE INDEX ix_products_status ON products(status);

CREATE INDEX ix_products_group ON products(group_id);

CREATE TABLE user_warehouses (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, warehouse_id)
);
CREATE INDEX ix_user_warehouses_warehouse ON user_warehouses(warehouse_id);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000020', 'WAREHOUSE_VIEW', 'WAREHOUSE_VIEW', 'WAREHOUSE_VIEW', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000021', 'WAREHOUSE_CREATE', 'WAREHOUSE_CREATE', 'WAREHOUSE_CREATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000022', 'WAREHOUSE_UPDATE', 'WAREHOUSE_UPDATE', 'WAREHOUSE_UPDATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000023', 'PRODUCT_GROUP_VIEW', 'PRODUCT_GROUP_VIEW', 'PRODUCT_GROUP_VIEW', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000024', 'PRODUCT_GROUP_CREATE', 'PRODUCT_GROUP_CREATE', 'PRODUCT_GROUP_CREATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000025', 'PRODUCT_GROUP_UPDATE', 'PRODUCT_GROUP_UPDATE', 'PRODUCT_GROUP_UPDATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000026', 'PRODUCT_VIEW', 'PRODUCT_VIEW', 'PRODUCT_VIEW', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000027', 'PRODUCT_CREATE', 'PRODUCT_CREATE', 'PRODUCT_CREATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000028', 'PRODUCT_UPDATE', 'PRODUCT_UPDATE', 'PRODUCT_UPDATE', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000029', 'USER_WAREHOUSE_VIEW', 'USER_WAREHOUSE_VIEW', 'USER_WAREHOUSE_VIEW', 1);

INSERT INTO permissions(id,name,code,description,status) VALUES ('20000000-0000-0000-0000-000000000030', 'USER_WAREHOUSE_ASSIGN', 'USER_WAREHOUSE_ASSIGN', 'USER_WAREHOUSE_ASSIGN', 1);

INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN' AND p.code IN ('WAREHOUSE_VIEW','WAREHOUSE_CREATE','WAREHOUSE_UPDATE','PRODUCT_GROUP_VIEW','PRODUCT_GROUP_CREATE','PRODUCT_GROUP_UPDATE','PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE','USER_WAREHOUSE_VIEW','USER_WAREHOUSE_ASSIGN');
