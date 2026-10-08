package com.marlowefinch.ops;

import java.util.List;

/** Thrown by {@link QueryParams} with every problem found in one request. Mapped to a 400 by {@link ApiErrorHandler}. */
public class InvalidRequestException extends RuntimeException {

    private final List<String> errors;

    public InvalidRequestException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
