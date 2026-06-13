package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.DailyNutritionPoint;
import com.calorietracker.backendcore.dto.DailyWaterPoint;
import com.calorietracker.backendcore.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Aggregated daily series for the chart views. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    /** Cap the range to roughly a year. Both endpoints fan out one DB call per day,
     *  so an unbounded range would let a single request issue thousands of queries. */
    private static final int MAX_RANGE_DAYS = 366;

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** Daily kcal + macros + fiber + per-day targets. Default range = last 7 days. */
    @GetMapping("/nutrition")
    public List<DailyNutritionPoint> nutrition(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] range = sanitizeRange(from, to);
        return service.nutrition(range[0], range[1]);
    }

    /** Daily water + per-day target. Default range = last 7 days. */
    @GetMapping("/water")
    public List<DailyWaterPoint> water(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] range = sanitizeRange(from, to);
        return service.water(range[0], range[1]);
    }

    /** Default-fills + validates the requested range. Rejects from &gt; to and ranges &gt; MAX_RANGE_DAYS. */
    private static LocalDate[] sanitizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "range is " + days + " days, max " + MAX_RANGE_DAYS + " - narrow the from/to window");
        }
        return new LocalDate[]{start, end};
    }
}
