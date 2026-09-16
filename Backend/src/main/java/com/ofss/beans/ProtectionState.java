package com.ofss.beans;

/** Target domain states; deliberately not wired to the legacy JPA enum yet. */
public enum ProtectionState {
    CREATED, AUTHORIZED, PROTECTED, HARD_HOLD, RELEASED, CANCELLED, REJECTED, SETTLED
}
