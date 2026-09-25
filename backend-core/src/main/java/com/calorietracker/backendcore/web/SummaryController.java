package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.DaySummaryResponse;
import com.calorietracker.backendcore.service.ClientClock;
import com.calorietracker.backendcore.service.SummaryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-call payload for the desktop day view: target, totals per meal, water,
 * and the diary entries themselves.
 */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService service;
    private final ClientClock clock;

    public SummaryController(SummaryService service, ClientClock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public DaySummaryResponse day(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.forDate(date == null ? clock.today() : date);
    }
}
