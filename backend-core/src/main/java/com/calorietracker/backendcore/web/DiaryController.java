package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.AddDiaryRequest;
import com.calorietracker.backendcore.dto.DiaryEntryResponse;
import com.calorietracker.backendcore.dto.MoveOrCopyDiaryRequest;
import com.calorietracker.backendcore.service.DiaryService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diary")
public class DiaryController {

    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
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
