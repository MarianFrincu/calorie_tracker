package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.CreateRecipeRequest;
import com.calorietracker.backendcore.dto.ParsedRecipe;
import com.calorietracker.backendcore.dto.ParsedRecipeIngredient;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.Ingredient;
import com.calorietracker.backendcore.model.Recipe;
import com.calorietracker.backendcore.model.RecipeIngredient;
import com.calorietracker.backendcore.repository.IngredientRepository;
import com.calorietracker.backendcore.repository.RecipeRepository;
import com.calorietracker.backendcore.web.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final CurrentUserService currentUser;

    public RecipeService(RecipeRepository recipes, IngredientRepository ingredients, CurrentUserService currentUser) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.currentUser = currentUser;
    }

    /** Owner-only search ("My recipes" tab) with typo-tolerant fallback. */
    @Transactional(readOnly = true)
    public List<Recipe> searchMine(String query, Pageable pageable) {
        AppUser me = currentUser.current();
        String q = query == null ? "" : query.trim();
        List<Recipe> hits = recipes.searchMine(me.getId(), q, pageable).getContent();
        if (!hits.isEmpty() || q.length() < 2) return hits;
        return FuzzySearch.rank(recipes.findByOwnerUserId(me.getId()), q,
                Recipe::getName,
                FuzzySearch.defaultTolerance(q), pageable.getPageSize());
    }

    /** Owner + public search (used by add-to-diary) with typo-tolerant fallback. */
    @Transactional(readOnly = true)
    public List<Recipe> searchAll(String query, Pageable pageable) {
        AppUser me = currentUser.current();
        String q = query == null ? "" : query.trim();
        List<Recipe> hits = recipes.searchAll(me.getId(), q, pageable).getContent();
        if (!hits.isEmpty() || q.length() < 2) return hits;
        return FuzzySearch.rank(recipes.findAllVisible(me.getId()), q,
                Recipe::getName,
                FuzzySearch.defaultTolerance(q), pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public Recipe findById(Long id) {
        Recipe r = recipes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe " + id + " not found"));
        ensureVisible(r);
        // Touch lazy collection while inside the transaction so the controller can map it.
        r.getIngredients().size();
        return r;
    }

    @Transactional
    public Recipe create(CreateRecipeRequest req) {
        AppUser me = currentUser.current();

        Recipe r = new Recipe();
        r.setName(req.name());
        r.setServings(req.servings() == null ? 1 : req.servings());
        r.setOwnerUserId(me.getId());

        double kcal = 0, protein = 0, carbs = 0, fat = 0, fiber = 0, rawGrams = 0;
        for (CreateRecipeRequest.Line line : req.ingredients()) {
            Ingredient ing = ingredients.findById(line.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + line.ingredientId() + " not found"));
            // Public ingredients and the caller's own are both fair game.
            if (ing.getOwnerUserId() != null && !ing.getOwnerUserId().equals(me.getId())) {
                throw new ResourceNotFoundException("Ingredient " + line.ingredientId() + " not found");
            }
            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipe(r);
            ri.setIngredient(ing);
            ri.setAmountGrams(line.amountGrams());
            r.getIngredients().add(ri);

            double factor = line.amountGrams() / 100.0;
            kcal    += ing.getKcalPer100g()    * factor;
            protein += ing.getProteinPer100g() * factor;
            carbs   += ing.getCarbsPer100g()   * factor;
            fat     += ing.getFatPer100g()     * factor;
            fiber   += ing.getFiberPer100g()   * factor;
            rawGrams += line.amountGrams();
        }

        r.setTotalKcal((int) Math.round(kcal));
        r.setTotalProtein(round1(protein));
        r.setTotalCarbs(round1(carbs));
        r.setTotalFat(round1(fat));
        r.setTotalFiber(round1(fiber));
        // If the client didn't send a cooked-weight, fall back to raw sum so
        // per-100g math stays usable.
        double cookedGrams = req.totalCookedGrams() == null || req.totalCookedGrams() <= 0
                ? rawGrams : req.totalCookedGrams();
        r.setTotalCookedGrams(round1(cookedGrams));
        return recipes.save(r);
    }

    /**
     * Persists a recipe blueprint produced by the AI: creates each ingredient as
     * a new user-private row, then a Recipe referencing them. One transaction.
     */
    @Transactional
    public Recipe createFromAi(ParsedRecipe blueprint) {
        AppUser me = currentUser.current();

        Recipe r = new Recipe();
        r.setName(blueprint.name() == null || blueprint.name().isBlank() ? "My recipe" : blueprint.name());
        r.setServings(blueprint.servings() == null ? 1 : Math.max(1, blueprint.servings()));
        r.setOwnerUserId(me.getId());

        double kcal = 0, protein = 0, carbs = 0, fat = 0, fiber = 0, rawGrams = 0;
        if (blueprint.ingredients() != null) {
            for (ParsedRecipeIngredient ai : blueprint.ingredients()) {
                Ingredient ing = new Ingredient();
                ing.setName(ai.name());
                ing.setKcalPer100g(ai.kcalPer100g());
                ing.setProteinPer100g(ai.proteinPer100g());
                ing.setCarbsPer100g(ai.carbsPer100g());
                ing.setFatPer100g(ai.fatPer100g());
                ing.setFiberPer100g(ai.fiberPer100g());
                ing.setOwnerUserId(me.getId());
                ing = ingredients.save(ing);

                RecipeIngredient ri = new RecipeIngredient();
                ri.setRecipe(r);
                ri.setIngredient(ing);
                ri.setAmountGrams(ai.amountGrams());
                r.getIngredients().add(ri);

                double factor = ai.amountGrams() / 100.0;
                kcal    += ai.kcalPer100g()    * factor;
                protein += ai.proteinPer100g() * factor;
                carbs   += ai.carbsPer100g()   * factor;
                fat     += ai.fatPer100g()     * factor;
                fiber   += ai.fiberPer100g()   * factor;
                rawGrams += ai.amountGrams();
            }
        }
        r.setTotalKcal((int) Math.round(kcal));
        r.setTotalProtein(round1(protein));
        r.setTotalCarbs(round1(carbs));
        r.setTotalFat(round1(fat));
        r.setTotalFiber(round1(fiber));
        // Use the user-supplied cooked weight if present; otherwise fall back to raw sum.
        double cookedGrams = blueprint.totalCookedGrams() == null || blueprint.totalCookedGrams() <= 0
                ? rawGrams : blueprint.totalCookedGrams();
        r.setTotalCookedGrams(round1(cookedGrams));
        return recipes.save(r);
    }

    /** Replace name + ingredient lines + cooked-weight of an owned recipe. Same math as create. */
    @Transactional
    public Recipe update(Long id, CreateRecipeRequest req) {
        Recipe r = recipes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe " + id + " not found"));
        AppUser me = currentUser.current();
        if (r.getOwnerUserId() == null) {
            throw new IllegalStateException("Cannot edit a public recipe.");
        }
        if (!r.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Recipe " + id + " not found");
        }

        r.setName(req.name());
        r.setServings(req.servings() == null ? r.getServings() : req.servings());
        // Replace the entire line set. orphanRemoval=true on the relationship
        // means clearing + adding is enough — Hibernate deletes the old rows.
        r.getIngredients().clear();

        double kcal = 0, protein = 0, carbs = 0, fat = 0, fiber = 0, rawGrams = 0;
        for (CreateRecipeRequest.Line line : req.ingredients()) {
            Ingredient ing = ingredients.findById(line.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + line.ingredientId() + " not found"));
            if (ing.getOwnerUserId() != null && !ing.getOwnerUserId().equals(me.getId())) {
                throw new ResourceNotFoundException("Ingredient " + line.ingredientId() + " not found");
            }
            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipe(r);
            ri.setIngredient(ing);
            ri.setAmountGrams(line.amountGrams());
            r.getIngredients().add(ri);

            double factor = line.amountGrams() / 100.0;
            kcal    += ing.getKcalPer100g()    * factor;
            protein += ing.getProteinPer100g() * factor;
            carbs   += ing.getCarbsPer100g()   * factor;
            fat     += ing.getFatPer100g()     * factor;
            fiber   += ing.getFiberPer100g()   * factor;
            rawGrams += line.amountGrams();
        }

        r.setTotalKcal((int) Math.round(kcal));
        r.setTotalProtein(round1(protein));
        r.setTotalCarbs(round1(carbs));
        r.setTotalFat(round1(fat));
        r.setTotalFiber(round1(fiber));
        double cookedGrams = req.totalCookedGrams() == null || req.totalCookedGrams() <= 0
                ? rawGrams : req.totalCookedGrams();
        r.setTotalCookedGrams(round1(cookedGrams));
        return recipes.save(r);
    }

    @Transactional
    public void delete(Long id) {
        Recipe r = recipes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe " + id + " not found"));
        AppUser me = currentUser.current();
        if (r.getOwnerUserId() == null) {
            throw new IllegalStateException("Cannot delete a public recipe.");
        }
        if (!r.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Recipe " + id + " not found");
        }
        recipes.delete(r);
    }

    /** Public recipes are visible to everyone; private ones only to the owner. */
    private void ensureVisible(Recipe r) {
        Long owner = r.getOwnerUserId();
        if (owner != null && !owner.equals(currentUser.current().getId())) {
            throw new ResourceNotFoundException("Recipe " + r.getId() + " not found");
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
