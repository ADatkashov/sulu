package com.sulu.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business error with a stable machine-readable code (clients map it to localized text). */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) { this(status, code, message, null); }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status; this.code = code; this.details = details;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }

    public static ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found"); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
    public static ApiException bad(String code, String msg) { return new ApiException(HttpStatus.BAD_REQUEST, code, msg); }
    public static ApiException conflict(String code, String msg) { return new ApiException(HttpStatus.CONFLICT, code, msg); }
    public static ApiException unprocessable(String code, String msg) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, msg); }
}
