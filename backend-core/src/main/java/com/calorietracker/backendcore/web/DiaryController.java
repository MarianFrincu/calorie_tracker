package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.AddDiaryRequest;
import com.calorietracker.backendcore.dto.DiaryEntryResponse;
import com.calorietracker.backendcore.dto.MoveOrCopyDiaryRequest;
import com.calorietracker.backendcore.service.DiaryService;
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

@RestController
@RequestMapping("/api/diary")
public class DiaryController {

    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    /** All diary entries for {@code date} (defaults to today). */
    @GetMapping
    public List<DiaryEntryResponse> byDate(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return service.byDate(d).stream().map(DiaryEntryResponse::of).toList();
    }

    @PostMapping
    public ResponseEntity<DiaryEntryResponse> add(@Valid @RequestBody AddDiaryRequest req) {
        DiaryEntryResponse body = DiaryEntryResponse.of(service.add(req));
        return ResponseEntity.created(URI.create("/api/diary/" + body.id())).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-slot the entry (no new row). 200 with the updated entry. */
    @PostMapping("/{id}/move")
    public DiaryEntryResponse move(@PathVariable Long id,
                                   @Valid @RequestBody MoveOrCopyDiaryRequest req) {
        return DiaryEntryResponse.of(service.move(id, req.date(), req.meal()));
    }

    /** Duplicate the entry. 201 with the new row. */
    @PostMapping("/{id}/copy")
    public ResponseEntity<DiaryEntryResponse> copy(@PathVariable Long id,
                                                   @Valid @RequestBody MoveOrCopyDiaryRequest req) {
        DiaryEntryResponse body = DiaryEntryResponse.of(service.copy(id, req.date(), req.meal()));
        return ResponseEntity.created(URI.create("/api/diary/" + body.id())).body(body);
    }
}
