package com.king.esb.admin.security;

/** Platform roles, ordered from least to most privileged. */
public enum Role {
    VIEWER,
    OPERATOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
