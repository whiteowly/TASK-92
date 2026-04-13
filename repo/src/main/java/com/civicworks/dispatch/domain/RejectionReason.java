package com.civicworks.dispatch.domain;

/**
 * Mandatory rejection reason for forced-assignment driver responses
 * (api-spec §3.5). Required when a driver declines a forced assignment.
 */
public enum RejectionReason {
    DRIVER_UNAVAILABLE,
    DISTANCE_TOO_FAR,
    VEHICLE_ISSUE,
    SAFETY_CONCERN,
    SHIFT_ENDED,
    OTHER
}
