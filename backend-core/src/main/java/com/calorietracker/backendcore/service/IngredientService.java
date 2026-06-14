package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.CreateIngredientRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Ingredient;
import com.calorietracker.backendcore.repository.IngredientRepository;
import com.calorietracker.backendcore.repository.RecipeRepository;
import com.calorietracker.backendcore.web.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientService {

    private final IngredientRepository repository;
    private final RecipeRepository recipes;
    private final CurrentUserService currentUser;

    public IngredientService(IngredientRepository repository, RecipeRepository recipes, CurrentUserService currentUser) {
        this.repository = repository;
        this.recipes = recipes;
        this.currentUser = currentUser;
    }

    /** Owner-only search ("My ingredients" tab). Falls back to fuzzy matching when the
     *  substring query returns nothing - so "zucini" can still find "Zucchini". */
    @Transactional(readOnly = true)
    public List<Ingredient> searchMine(String query, Pageable pageable) {
        AppUser me = currentUser.current();
        String q = query == null ? "" : query.trim();
        List<Ingredient> hits = repository.searchMine(me.getId(), q, pageable).getContent();
        if (!hits.isEmpty() || q.length() < 2) return hits;
        return FuzzySearch.rank(repository.findByOwnerUserId(me.getId()), q,
                Ingredient::getName,
                FuzzySearch.defaultTolerance(q), pageable.getPageSize());
    }

    /** Owner + public search (used by add-to-diary and recipe builder). Fuzzy fallback
     *  kicks in when the substring search comes back empty. */
    @Transactional(readOnly = true)
    public List<Ingredient> searchAll(String query, Pageable pageable) {
        AppUser me = currentUser.current();
        String q = query == null ? "" : query.trim();
        List<Ingredient> hits = repository.searchAll(me.getId(), q, pageable).getContent();
        if (!hits.isEmpty() || q.length() < 2) return hits;
        return FuzzySearch.rank(repository.findAllVisible(me.getId()), q,
                Ingredient::getName,
                FuzzySearch.defaultTolerance(q), pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public Ingredient findById(Long id) {
        Ingredient i = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + id + " not found"));
        ensureVisible(i);
        return i;
    }

    @Transactional
    public Ingredient create(CreateIngredientRequest req) {
        AppUser me = currentUser.current();
        Ingredient i = new Ingredient();
        i.setName(req.name());
        i.setBrand(req.brand());
        i.setKcalPer100g(req.kcalPer100g());
        i.setProteinPer100g(req.proteinPer100g());
        i.setCarbsPer100g(req.carbsPer100g());
        i.setFatPer100g(req.fatPer100g());
        i.setFiberPer100g(req.fiberPer100g());
        i.setOwnerUserId(me.getId()); // user-private
        return repository.save(i);
    }

    /** Update an owned ingredient. Refuses to touch public seed rows or someone else's row. */
    @Transactional
    public Ingredient update(Long id, CreateIngredientRequest req) {
        Ingredient i = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + id + " not found"));
        AppUser me = currentUser.current();
        if (i.getOwnerUserId() == null) {
            throw new IllegalStateException("Cannot edit a public ingredient.");
        }
        if (!i.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Ingredient " + id + " not found");
        }
        i.setName(req.name());
        i.setBrand(req.brand());
        i.setKcalPer100g(req.kcalPer100g());
        i.setProteinPer100g(req.proteinPer100g());
        i.setCarbsPer100g(req.carbsPer100g());
        i.setFatPer100g(req.fatPer100g());
        i.setFiberPer100g(req.fiberPer100g());
        return repository.save(i);
    }

    /** Refuses to delete if the ingredient is still referenced by any recipe. */
    @Transactional
    public void delete(Long id) {
        Ingredient i = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + id + " not found"));
        AppUser me = currentUser.current();
        if (i.getOwnerUserId() == null) {
            throw new IllegalStateException("Cannot delete a public ingredient.");
        }
        if (!i.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Ingredient " + id + " not found");
        }
        long uses = recipes.countUsesOfIngredient(i.getId());
        if (uses > 0) {
            throw new IllegalStateException(
                    "Cannot delete \"" + i.getName() + "\" - it is used in " + uses + " recipe line item(s).");
        }
        repository.delete(i);
    }

    /** Public ingredients are visible to everyone; private ones only to the owner. */
    private void ensureVisible(Ingredient i) {
        Long owner = i.getOwnerUserId();
        if (owner != null && !owner.equals(currentUser.current().getId())) {
            throw new ResourceNotFoundException("Ingredient " + i.getId() + " not found");
        }
    }
}
