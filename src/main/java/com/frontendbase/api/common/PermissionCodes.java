package com.frontendbase.api.common;

import java.util.Set;

public final class PermissionCodes {
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String ROLE_VIEW = "ROLE_VIEW";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String PERMISSION_VIEW = "PERMISSION_VIEW";
    public static final String PERMISSION_CREATE = "PERMISSION_CREATE";
    public static final String PERMISSION_UPDATE = "PERMISSION_UPDATE";
    public static final String PERMISSION_DELETE = "PERMISSION_DELETE";

    public static final String WAREHOUSE_VIEW = "WAREHOUSE_VIEW";
    public static final String WAREHOUSE_CREATE = "WAREHOUSE_CREATE";
    public static final String WAREHOUSE_UPDATE = "WAREHOUSE_UPDATE";
    public static final String PRODUCT_GROUP_VIEW = "PRODUCT_GROUP_VIEW";
    public static final String PRODUCT_GROUP_CREATE = "PRODUCT_GROUP_CREATE";
    public static final String PRODUCT_GROUP_UPDATE = "PRODUCT_GROUP_UPDATE";
    public static final String PRODUCT_VIEW = "PRODUCT_VIEW";
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String USER_WAREHOUSE_VIEW = "USER_WAREHOUSE_VIEW";
    public static final String USER_WAREHOUSE_ASSIGN = "USER_WAREHOUSE_ASSIGN";

    public static final String STOCK_RECEIPT_VIEW = "STOCK_RECEIPT_VIEW";
    public static final String STOCK_RECEIPT_CREATE = "STOCK_RECEIPT_CREATE";
    public static final String STOCK_RECEIPT_UPDATE = "STOCK_RECEIPT_UPDATE";
    public static final String STOCK_RECEIPT_CONFIRM = "STOCK_RECEIPT_CONFIRM";
    public static final String STOCK_RECEIPT_CANCEL = "STOCK_RECEIPT_CANCEL";
    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_MOVEMENT_VIEW = "INVENTORY_MOVEMENT_VIEW";

    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_UPDATE = "CUSTOMER_UPDATE";
    public static final String SALES_ORDER_VIEW = "SALES_ORDER_VIEW";
    public static final String SALES_ORDER_CREATE = "SALES_ORDER_CREATE";
    public static final String SALES_ORDER_UPDATE = "SALES_ORDER_UPDATE";
    public static final String SALES_ORDER_CONFIRM = "SALES_ORDER_CONFIRM";
    public static final String SALES_ORDER_CANCEL = "SALES_ORDER_CANCEL";

    public static final Set<String> ALL = Set.of(
            DASHBOARD_VIEW,
            USER_VIEW,
            USER_CREATE,
            USER_UPDATE,
            USER_DELETE,
            ROLE_VIEW,
            ROLE_CREATE,
            ROLE_UPDATE,
            ROLE_DELETE,
            PERMISSION_VIEW,
            PERMISSION_CREATE,
            PERMISSION_UPDATE,
            PERMISSION_DELETE,
            WAREHOUSE_VIEW,
            WAREHOUSE_CREATE,
            WAREHOUSE_UPDATE,
            PRODUCT_GROUP_VIEW,
            PRODUCT_GROUP_CREATE,
            PRODUCT_GROUP_UPDATE,
            PRODUCT_VIEW,
            PRODUCT_CREATE,
            PRODUCT_UPDATE,
            USER_WAREHOUSE_VIEW,
            USER_WAREHOUSE_ASSIGN,
            STOCK_RECEIPT_VIEW,
            STOCK_RECEIPT_CREATE,
            STOCK_RECEIPT_UPDATE,
            STOCK_RECEIPT_CONFIRM,
            STOCK_RECEIPT_CANCEL,
            INVENTORY_VIEW,
            INVENTORY_MOVEMENT_VIEW,
            CUSTOMER_VIEW,
            CUSTOMER_CREATE,
            CUSTOMER_UPDATE,
            SALES_ORDER_VIEW,
            SALES_ORDER_CREATE,
            SALES_ORDER_UPDATE,
            SALES_ORDER_CONFIRM,
            SALES_ORDER_CANCEL);

    private PermissionCodes() {
    }
}
