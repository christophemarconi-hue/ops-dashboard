package com.marlowefinch.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** The parameter checks of TODO-232 without Spring. Today is 2026-09-21, as in the demo profile. */
class QueryParamsTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 9, 21).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Test
    void missingBoundsDefaultToTheLast30DaysEndingToday() {
        DateRange range = DateRange.resolve(null, null, CLOCK);
        assertThat(range).isEqualTo(new DateRange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 9, 21)));
    }

    @Test
    void validBoundsAreParsed() {
        assertThat(DateRange.resolve("2026-07-01", "2026-07-31", CLOCK))
                .isEqualTo(new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void nonIsoAndOutOfFormatDatesAreRejected() {
        for (String bad : new String[] {"next-tuesday", "2026-9-1", "20260901", "+12345-01-01", "2026-02-30", " 2026-09-01"}) {
            assertThatThrownBy(() -> DateRange.resolve(bad, null, CLOCK))
                    .isInstanceOf(InvalidRequestException.class)
                    .extracting(e -> ((InvalidRequestException) e).errors())
                    .isEqualTo(java.util.List.of("from must be an ISO date (YYYY-MM-DD)"));
        }
    }

    @Test
    void the366DayLimitIsInclusive() {
        assertThat(DateRange.resolve("2025-09-20", "2026-09-20", CLOCK).from()).isEqualTo(LocalDate.of(2025, 9, 20));
        assertThatThrownBy(() -> DateRange.resolve("2025-09-19", "2026-09-20", CLOCK))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("date range must span at most 366 days");
    }

    @Test
    void fromAfterAMissingToIsComparedWithToday() {
        assertThatThrownBy(() -> DateRange.resolve("2026-09-22", null, CLOCK))
                .hasMessage("from must be on or before to");
    }

    @Test
    void limitDefaultsWhenMissingAndChecksTheBounds() {
        QueryParams params = new QueryParams();
        assertThat(params.limit(null, 20)).isEqualTo(20);
        assertThat(params.limit("", 20)).isEqualTo(20);
        assertThat(params.limit("1", 20)).isEqualTo(1);
        assertThat(params.limit("500", 20)).isEqualTo(500);
        params.throwIfInvalid();

        for (String bad : new String[] {"0", "501", "-1", "abc", "1e3", "99999999999"}) {
            QueryParams p = new QueryParams();
            p.limit(bad, 20);
            assertThatThrownBy(p::throwIfInvalid)
                    .hasMessage("limit must be an integer between 1 and 500");
        }
    }

    @Test
    void everyProblemIsCollected() {
        QueryParams params = new QueryParams();
        params.dateRange("bad", "worse", CLOCK);
        params.limit("0", 20);
        assertThatThrownBy(params::throwIfInvalid)
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).errors())
                .isEqualTo(java.util.List.of(
                        "from must be an ISO date (YYYY-MM-DD)",
                        "to must be an ISO date (YYYY-MM-DD)",
                        "limit must be an integer between 1 and 500"));
    }
}
