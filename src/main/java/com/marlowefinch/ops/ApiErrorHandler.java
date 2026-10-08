package com.marlowefinch.ops;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns invalid query parameters into {@code 400 {"errors": [...]}} (TODO-232, AC-4). */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, List<String>> invalidRequest(InvalidRequestException e) {
        return Map.of("errors", e.errors());
    }
}
