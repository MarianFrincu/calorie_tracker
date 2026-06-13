package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.AddWeightRequest;
import com.calorietracker.backendcore.dto.WeightEntryResponse;
import com.calorietracker.backendcore.service.WeightService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Per-day weight log (one entry per user per day). */
@RestController
@RequestMapping("/api/weight")
public class WeightController {

    private final WeightService service;

    public WeightController(WeightService service) {
        this.service = service;
    }

    /** Weight entries (optionally restricted to a date range). */
    @GetMapping
    public List<WeightEntryResponse> list(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to).stream().map(WeightEntryResponse::of).toList();
    }

    /** Upserts the weight for the given date. */
    @PostMapping
    public ResponseEntity<WeightEntryResponse> upsert(@Valid @RequestBody AddWeightRequest req) {
        WeightEntryResponse body = WeightEntryResponse.of(service.upsert(req));
        return ResponseEntity.created(URI.create("/api/weight/" + body.id())).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
