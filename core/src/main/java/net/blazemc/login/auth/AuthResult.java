package net.blazemc.login.auth;

import net.blazemc.login.storage.AccountRecord;

public record AuthResult(AuthOutcome outcome, AccountRecord account, String detail) {

    public static AuthResult of(AuthOutcome outcome) {
        return new AuthResult(outcome, null, null);
    }

    public static AuthResult of(AuthOutcome outcome, AccountRecord account) {
        return new AuthResult(outcome, account, null);
    }

    public boolean success() {
        return switch (outcome) {
            case REGISTERED, LOGGED_IN, PREMIUM_AUTO_LOGIN, SESSION_RESTORED,
                    LOGGED_OUT, PASSWORD_CHANGED, ALREADY_AUTHENTICATED, BYPASS -> true;
            default -> false;
        };
    }

    public boolean authenticates() {
        return switch (outcome) {
            case REGISTERED, LOGGED_IN, PREMIUM_AUTO_LOGIN, SESSION_RESTORED, BYPASS -> true;
            default -> false;
        };
    }
}
