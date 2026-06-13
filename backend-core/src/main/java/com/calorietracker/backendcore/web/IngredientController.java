package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.CreateIngredientRequest;
import com.calorietracker.backendcore.dto.IngredientResponse;
import com.calorietracker.backendcore.model.Ingredient;
import com.calorietracker.backendcore.service.IngredientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/ingredients")
public class IngredientController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LEN = 200;

    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    /**
     * Paginated search. {@code scope=mine} (default) returns only the caller's
     * own ingredients; {@code scope=all} also includes the public library.
     */
    @GetMapping
    public List<IngredientResponse> search(
            @RequestParam(name = "q",     required = false, defaultValue = "") String q,
            @RequestParam(name = "page",  required = false, defaultValue = "0")  int page,
            @RequestParam(name = "size",  required = false, defaultValue = "20") int size,
            @RequestParam(name = "scope", required = false, defaultValue = "mine") String scope) {
        String safeQ = q.length() > MAX_QUERY_LEN ? q.substring(0, MAX_QUERY_LEN) : q;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        List<Ingredient> rows = "all".equalsIgnoreCase(scope)
                ? service.searchAll(safeQ, pageable)
                : service.searchMine(safeQ, pageable);
        return rows.stream().map(IngredientResponse::of).toList();
    }

    @GetMapping("/{id}")
    public IngredientResponse get(@PathVariable Long id) {
        return IngredientResponse.of(service.findById(id));
    }

    /** Create a private (user-owned) ingredient. */
    @PostMapping
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody CreateIngredientRequest req) {
        IngredientResponse body = IngredientResponse.of(service.create(req));
        return ResponseEntity.created(URI.create("/api/ingredients/" + body.id())).body(body);
    }

    /** Delete an owned ingredient. Refuses if it's still referenced by recipes. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
