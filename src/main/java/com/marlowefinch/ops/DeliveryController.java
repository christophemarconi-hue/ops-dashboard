package com.marlowefinch.ops;

import java.time.Clock;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {

    static final int DEFAULT_LIMIT = 20;

    private final DashboardRepository repository;
    private final Clock clock;

    public DeliveryController(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/api/deliveries/on-time")
    public List<CarrierOnTime> onTime(@RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return repository.onTimeByCarrier(DateRange.resolve(from, to, clock));
    }

    @GetMapping("/api/deliveries/late")
    public List<LateDelivery> late(@RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to,
                                   @RequestParam(required = false) String limit) {
        // A String so that "abc" gets the same 400 body as every other bad parameter.
        QueryParams params = new QueryParams();
        DateRange range = params.dateRange(from, to, clock);
        int rows = params.limit(limit, DEFAULT_LIMIT);
        params.throwIfInvalid();
        return repository.lateDeliveries(range, rows);
    }
}
