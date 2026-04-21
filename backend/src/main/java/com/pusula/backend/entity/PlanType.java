package com.pusula.backend.entity;

/**
 * Subscription plan tiers for the SaaS platform.
 * Each tier unlocks different feature flags and quota limits.
 */
public enum PlanType {
    CIRAK,   // Free tier — basic service ticket management
    USTA,    // Professional — finance, proposals, multi-technician
    PATRON   // Enterprise — unlimited everything, custom branding
}
