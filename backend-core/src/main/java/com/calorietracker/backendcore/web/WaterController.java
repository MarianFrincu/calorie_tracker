package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.AddWaterRequest;
import com.calorietracker.backendcore.model.WaterEntry;
import com.calorietracker.backendcore.service.WaterService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

@RestController
@RequestMapping("/api/water")
public class WaterController {

    private final WaterService service;

    public WaterController(WaterService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> byDate(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return service.byDate(d).stream()
                .map(w -> Map.<String, Object>of("id", w.getId(), "ml", w.getMl()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@Valid @RequestBody AddWaterRequest req) {
        WaterEntry w = service.add(req);
        Map<String, Object> body = Map.of("id", w.getId(), "ml", w.getMl());
        return ResponseEntity.created(URI.create("/api/water/" + w.getId())).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
