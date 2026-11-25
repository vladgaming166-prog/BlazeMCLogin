package net.blazemc.login.movement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovementGuardTest {

    @Test
    void lookOnlyIsNeverCancelled() {
        assertThat(MovementGuard.isLookOnly(1, 64, 1, 1, 64, 1)).isTrue();
        assertThat(MovementGuard.shouldCancelMove(1, 64, 1, 1, 64, 1, 64, 0.8)).isFalse();
    }

    @Test
    void walkingIsCancelledWithoutNeedingATeleport() {
        assertThat(MovementGuard.hasHorizontalMove(1.0, 1.0, 1.2, 1.0)).isTrue();
        assertThat(MovementGuard.shouldCancelMove(1.0, 64, 1.0, 1.2, 64, 1.0, 64, 0.8)).isTrue();
    }

    @Test
    void smallVerticalIdleIsAllowed() {
        assertThat(MovementGuard.shouldCancelMove(1, 64.0, 1, 1, 64.03, 1, 64.0, 0.8)).isFalse();
        assertThat(MovementGuard.shouldCancelMove(1, 64.0, 1, 1, 65.5, 1, 64.0, 0.8)).isTrue();
    }

    @Test
    void emergencyCorrectionOnlyWhenFar() {
        assertThat(MovementGuard.needsEmergencyCorrection(0, 64, 0, 0.4, 64, 0.1, 2.5)).isFalse();
        assertThat(MovementGuard.needsEmergencyCorrection(0, 64, 0, 8, 64, 0, 2.5)).isTrue();
    }
}
