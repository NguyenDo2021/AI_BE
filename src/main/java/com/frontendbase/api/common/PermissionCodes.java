package com.frontendbase.api.common;

import java.util.Set;

public final class PermissionCodes {
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String ROLE_VIEW = "ROLE_VIEW";

    public static final Set<String> ALL = Set.of(
            DASHBOARD_VIEW,
            USER_VIEW,
            USER_CREATE,
            USER_UPDATE,
            USER_DELETE,
            ROLE_VIEW
    );

    private PermissionCodes() {
    }
}
