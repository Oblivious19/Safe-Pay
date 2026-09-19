package com.ofss.security;

public record RefreshRotationResult(
        RefreshRotationStatus status,
        RefreshSessionResult session) {

    public static RefreshRotationResult rotated(
            RefreshSessionResult session) {
        return new RefreshRotationResult(
                RefreshRotationStatus.ROTATED,
                session);
    }

    public static RefreshRotationResult invalid() {
        return new RefreshRotationResult(
                RefreshRotationStatus.INVALID,
                null);
    }

    public static RefreshRotationResult replayDetected() {
        return new RefreshRotationResult(
                RefreshRotationStatus.REPLAY_DETECTED,
                null);
    }
}
