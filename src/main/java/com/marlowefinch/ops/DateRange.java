package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today". {@link #resolve} checks its
 * input (strict ISO dates, {@code from} on or before {@code to}, at most 366 days
 * counted inclusively) and throws {@link InvalidRequestException} listing every
 * problem; see {@link QueryParams} and TODO-232. The constructor itself does not check.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;

    public static DateRange resolve(String from, String to, Clock clock) {
        QueryParams params = new QueryParams();
        DateRange range = params.dateRange(from, to, clock);
        params.throwIfInvalid();
        return range;
    }
}
