package com.civicworks.platform.error;

public final class ErrorCode {
    private ErrorCode() {}

    // Auth
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String SESSION_INVALID = "SESSION_INVALID";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    // Idempotency
    public static final String MISSING_IDEMPOTENCY_KEY = "MISSING_IDEMPOTENCY_KEY";
    public static final String IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD = "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD";

    // Content
    public static final String CONTENT_NOT_PUBLIC = "CONTENT_NOT_PUBLIC";
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    // Billing
    public static final String BILL_BELOW_ZERO = "BILL_BELOW_ZERO";
    public static final String LATE_FEE_CAP_REACHED = "LATE_FEE_CAP_REACHED";

    // Dispatch
    public static final String ORDER_NOT_GRABBABLE = "ORDER_NOT_GRABBABLE";
    public static final String DRIVER_CONSTRAINT_FAILED = "DRIVER_CONSTRAINT_FAILED";
    public static final String REJECTION_REASON_REQUIRED = "REJECTION_REASON_REQUIRED";
    public static final String FORCED_REASSIGN_COOLDOWN = "FORCED_REASSIGN_COOLDOWN";
    public static final String ZONE_CAPACITY_EXCEEDED = "ZONE_CAPACITY_EXCEEDED";

    // Settlement
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    public static final String BILL_ALREADY_SETTLED = "BILL_ALREADY_SETTLED";
    public static final String REVERSAL_ALREADY_EXISTS = "REVERSAL_ALREADY_EXISTS";

    // Generic
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "OPTIMISTIC_LOCK_CONFLICT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
}
