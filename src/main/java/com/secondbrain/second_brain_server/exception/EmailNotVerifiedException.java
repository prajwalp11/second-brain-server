package com.secondbrain.second_brain_server.exception;

/**
 * Thrown on login when the account's email is not yet verified.
 * Mapped to HTTP 403 with a distinct code so the app can route to the OTP screen.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
