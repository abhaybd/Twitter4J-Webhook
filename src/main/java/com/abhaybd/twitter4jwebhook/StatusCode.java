package com.abhaybd.twitter4jwebhook;

public class StatusCode {
    public static final StatusCode OK = createSuccess(-1, null);

    public static StatusCode createSuccess(int httpCode, String message) {
        return new StatusCode(httpCode, message, false);
    }

    public static StatusCode createSuccess(String message) {
        return new StatusCode(-1, message, false);
    }

    public static StatusCode createError(int httpCode, String message) {
        return new StatusCode(httpCode, message, true);
    }

    public static StatusCode createError(String message) {
        return new StatusCode(-1, message, true);
    }

    /**
     * The http status code associated with this error. If this error is not an HTTP error, will be -1.
     */
    public final int httpCode;
    /**
     * The message associated with this error. May be null if there is no message.
     */
    public final String message;

    public final boolean isError;

    private StatusCode(int httpCode, String message, boolean isError) {
        this.httpCode = httpCode;
        this.message = message;
        this.isError = isError;
    }
}
