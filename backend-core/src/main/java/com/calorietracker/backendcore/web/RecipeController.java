package com.calorietracker.backendcore.web;

import com.calorietracker.backendcore.dto.CreateRecipeRequest;
import com.calorietracker.backendcore.dto.ParsedRecipe;
import com.calorietracker.backendcore.dto.RecipeResponse;
import com.calorietracker.backendcore.service.RecipeService;
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
@RequestMapping("/api/recipes")
public class RecipeController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LEN = 200;

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    /**
     * Paginated search. {@code scope=mine} (default) returns only the caller's
     * own recipes; {@code scope=all} also includes the public library.
     */
    @GetMapping
    public List<RecipeResponse> search(
            @RequestParam(name = "q",     required = false, defaultValue = "") String q,
            @RequestParam(name = "page",  required = false, defaultValue = "0")  int page,
            @RequestParam(name = "size",  required = false, defaultValue = "20") int size,
            @RequestParam(name = "scope", required = false, defaultValue = "mine") String scope) {
        String safeQ = q.length() > MAX_QUERY_LEN ? q.substring(0, MAX_QUERY_LEN) : q;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        List<?> rows = "all".equalsIgnoreCase(scope)
                ? service.searchAll(safeQ, pageable)
                : service.searchMine(safeQ, pageable);
        return rows.stream()
                .map(o -> RecipeResponse.of((com.calorietracker.backendcore.model.Recipe) o))
                .toList();
    }

    @GetMapping("/{id}")
    public RecipeResponse get(@PathVariable Long id) {
        return RecipeResponse.of(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody CreateRecipeRequest req) {
        RecipeResponse body = RecipeResponse.of(service.create(req));
        return ResponseEntity.created(URI.create("/api/recipes/" + body.id())).body(body);
    }

    /** Update an owned recipe (name, ingredients, cooked-weight). Public seed recipes are read-only. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public RecipeResponse update(@PathVariable Long id,
                                 @Valid @RequestBody CreateRecipeRequest req) {
        return RecipeResponse.of(service.update(id, req));
    }

    /**
     * Persists a recipe blueprint produced by the AI service: creates each
     * ingredient + the recipe in a single transaction.
     * <p>
     * Lives under /api/recipes (not /api/ai) so the gateway can route the
     * stateless /api/ai/** prefix to a dedicated, DB-free ai-service while
     * keeping all writes here in backend-core.
     */
    @PostMapping("/from-ai")
    public ResponseEntity<RecipeResponse> createFromAi(@Valid @RequestBody ParsedRecipe blueprint) {
        RecipeResponse body = RecipeResponse.of(service.createFromAi(blueprint));
        return ResponseEntity.created(URI.create("/api/recipes/" + body.id())).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
