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
            PERMISSION_DELETE);

    private PermissionCodes() {
    }
}
