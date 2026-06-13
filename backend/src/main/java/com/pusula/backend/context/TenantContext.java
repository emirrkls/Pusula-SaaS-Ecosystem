package com.pusula.backend.context;

/**
 * Thread-local holder for the current tenant (company) context.
 * Set by TenantInterceptor after JWT authentication, cleared after each request.
 * 
 * Usage: TenantContext.getTenantId() from any service layer to get the
 * authenticated user's company ID without passing it through method params.
 */
public class TenantContext {
    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> readOnlyImpersonation = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        currentTenantId.set(tenantId);
    }

    public static Long getTenantId() {
        return currentTenantId.get();
    }

    public static void setReadOnlyImpersonation(boolean readOnly) {
        readOnlyImpersonation.set(readOnly);
    }

    public static boolean isReadOnlyImpersonation() {
        return Boolean.TRUE.equals(readOnlyImpersonation.get());
    }

    public static void clear() {
        currentTenantId.remove();
        readOnlyImpersonation.remove();
    }
}
