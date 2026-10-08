package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses and checks the query parameters of the {@code /api} endpoints (TODO-232).
 *
 * Problems are collected rather than thrown one at a time, so a request with a bad
 * {@code from} and a bad {@code limit} gets both messages back. Call
 * {@link #throwIfInvalid()} once every parameter has been read; the controller advice
 * turns the exception into a 400 with {@code {"errors": [...]}}.
 */
final class QueryParams {

    /** Longest allowed range, counted inclusively: to - from is at most 365 days. */
    static final int MAX_RANGE_DAYS = 366;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 500;

    // LocalDate.parse alone would also accept signed or 5+ digit years ("+12345-01-01").
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final List<String> errors = new ArrayList<>();

    /**
     * A missing or blank bound falls back to the default (the last 30 days ending today).
     * The order and span checks only run when both bounds parsed. Returns null when
     * there were errors.
     */
    DateRange dateRange(String from, String to, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from == null || from.isBlank() ? today.minusDays(DateRange.DEFAULT_DAYS) : date("from", from);
        LocalDate end = to == null || to.isBlank() ? today : date("to", to);
        if (start == null || end == null) {
            return null;
        }
        if (start.isAfter(end)) {
            errors.add("from must be on or before to");
        } else if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            errors.add("date range must span at most " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    /** A missing or blank limit falls back to {@code defaultLimit}. */
    int limit(String limit, int defaultLimit) {
        if (limit == null || limit.isBlank()) {
            return defaultLimit;
        }
        try {
            int value = Integer.parseInt(limit.trim());
            if (value >= MIN_LIMIT && value <= MAX_LIMIT) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below, same message as out of range
        }
        errors.add("limit must be an integer between " + MIN_LIMIT + " and " + MAX_LIMIT);
        return defaultLimit;
    }

    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
    }

    private LocalDate date(String name, String value) {
        if (ISO_DATE.matcher(value).matches()) {
            try {
                // ISO_LOCAL_DATE resolves strictly, so 2026-02-30 is rejected rather than clamped.
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                // fall through
            }
        }
        errors.add(name + " must be an ISO date (YYYY-MM-DD)");
        return null;
    }
}
