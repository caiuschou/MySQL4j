package io.mysql4j;

public class MySQL4jException extends RuntimeException {
    public MySQL4jException(String message) {
        super(message);
    }

    public MySQL4jException(String message, Throwable cause) {
        super(message, cause);
    }
}
