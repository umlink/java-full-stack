package com.example.bootserver.identity.internal.domain;

/** Spring Security、RBAC 种子数据与测试共同使用的稳定权限机器码。 */
public final class PermissionCodes {

    public static final String USER_MANAGEMENT = "user:manage";

    public static final String PRODUCT_MANAGEMENT = "product:manage";

    public static final String ORDER_MANAGEMENT = "order:manage";

    private PermissionCodes() {
    }
}
