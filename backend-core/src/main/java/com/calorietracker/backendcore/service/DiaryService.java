package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.AddDiaryRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.DiaryEntry;
import com.calorietracker.backendcore.model.Ingredient;
import com.calorietracker.backendcore.model.Meal;
import com.calorietracker.backendcore.model.Recipe;
import com.calorietracker.backendcore.repository.DiaryEntryRepository;
import com.calorietracker.backendcore.repository.IngredientRepository;
import com.calorietracker.backendcore.repository.RecipeRepository;
import com.calorietracker.backendcore.web.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {

    private final DiaryEntryRepository diary;
    private final IngredientRepository ingredients;
    private final RecipeRepository recipes;
    private final CurrentUserService currentUser;

    public DiaryService(DiaryEntryRepository diary,
                        IngredientRepository ingredients,
                        RecipeRepository recipes,
                        CurrentUserService currentUser) {
        this.diary = diary;
        this.ingredients = ingredients;
        this.recipes = recipes;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<DiaryEntry> byDate(LocalDate date) {
        AppUser me = currentUser.current();
        return diary.findByUserIdAndEntryDateOrderByMealAscIdAsc(me.getId(), date);
    }

    @Transactional
    public DiaryEntry add(AddDiaryRequest req) {
        AppUser me = currentUser.current();
        DiaryEntry e = new DiaryEntry();
        e.setUserId(me.getId());
        e.setEntryDate(req.date());
        e.setMeal(req.meal());

        // Three input shapes: ingredient, recipe, or freeform (from the AI flow).
        if (req.ingredientId() != null) {
            if (req.amountGrams() == null) {
                throw new IllegalArgumentException("amountGrams is required when ingredientId is provided");
            }
            Ingredient ing = ingredients.findById(req.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient " + req.ingredientId() + " not found"));
            ensureIngredientVisible(ing, me);
            double factor = req.amountGrams() / 100.0;
            e.setIngredientId(ing.getId());
            e.setDisplayName(ing.getName());
            e.setAmountText(formatGrams(req.amountGrams()));
            e.setKcal((int) Math.round(ing.getKcalPer100g() * factor));
            e.setProtein(round1(ing.getProteinPer100g() * factor));
            e.setCarbs(round1(ing.getCarbsPer100g() * factor));
            e.setFat(round1(ing.getFatPer100g() * factor));
            e.setFiber(round1(ing.getFiberPer100g() * factor));

        } else if (req.recipeId() != null) {
            Recipe r = recipes.findById(req.recipeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe " + req.recipeId() + " not found"));
            ensureRecipeVisible(r, me);
            e.setRecipeId(r.getId());
            e.setDisplayName(r.getName());
            // Preferred path: per-100g-cooked × grams of cooked food eaten.
            // Fall back to the legacy servings flow if the caller didn't send
            // amountGrams (older clients).
            if (req.amountGrams() != null && req.amountGrams() > 0) {
                double grams = req.amountGrams();
                double cooked = r.getTotalCookedGrams() == null || r.getTotalCookedGrams() <= 0
                        ? r.getIngredients().stream().mapToDouble(ri -> ri.getAmountGrams()).sum()
                        : r.getTotalCookedGrams();
                double factor = cooked > 0 ? grams / cooked : 0;
                e.setAmountText(round1(grams) + " g");
                e.setKcal((int) Math.round(r.getTotalKcal() * factor));
                e.setProtein(round1(r.getTotalProtein() * factor));
                e.setCarbs(round1(r.getTotalCarbs() * factor));
                e.setFat(round1(r.getTotalFat() * factor));
                e.setFiber(round1(r.getTotalFiber() * factor));
            } else {
                double servings = req.servings() == null ? 1.0 : req.servings();
                e.setAmountText(servings + (servings == 1.0 ? " serving" : " servings"));
                e.setKcal((int) Math.round(r.getTotalKcal() * servings));
                e.setProtein(round1(r.getTotalProtein() * servings));
                e.setCarbs(round1(r.getTotalCarbs() * servings));
                e.setFat(round1(r.getTotalFat() * servings));
                e.setFiber(round1(r.getTotalFiber() * servings));
            }

        } else if (req.customName() != null && !req.customName().isBlank()) {
            e.setDisplayName(req.customName());
            e.setAmountText(null);
            e.setKcal(nz(req.customKcal()));
            e.setProtein(nz(req.customProtein()));
            e.setCarbs(nz(req.customCarbs()));
            e.setFat(nz(req.customFat()));
            e.setFiber(nz(req.customFiber()));
        } else {
            throw new IllegalArgumentException("Provide ingredientId, recipeId, or customName");
        }

        return diary.save(e);
    }

    @Transactional
    public void delete(Long id) {
        AppUser me = currentUser.current();
        DiaryEntry e = ownedOr404(id, me);
        diary.delete(e);
    }

    /** Move the entry to a different (date, meal). Same row, just re-slotted. */
    @Transactional
    public DiaryEntry move(Long id, LocalDate newDate, Meal newMeal) {
        AppUser me = currentUser.current();
        DiaryEntry e = ownedOr404(id, me);
        e.setEntryDate(newDate);
        e.setMeal(newMeal);
        return diary.save(e);
    }

    /** Duplicate the entry into a (possibly different) (date, meal). Returns the new row. */
    @Transactional
    public DiaryEntry copy(Long id, LocalDate newDate, Meal newMeal) {
        AppUser me = currentUser.current();
        DiaryEntry src = ownedOr404(id, me);
        DiaryEntry dst = new DiaryEntry();
        dst.setUserId(me.getId());
        dst.setEntryDate(newDate);
        dst.setMeal(newMeal);
        dst.setIngredientId(src.getIngredientId());
        dst.setRecipeId(src.getRecipeId());
        dst.setDisplayName(src.getDisplayName());
        dst.setAmountText(src.getAmountText());
        dst.setKcal(src.getKcal());
        dst.setProtein(src.getProtein());
        dst.setCarbs(src.getCarbs());
        dst.setFat(src.getFat());
        dst.setFiber(src.getFiber());
        return diary.save(dst);
    }

    private DiaryEntry ownedOr404(Long id, AppUser me) {
        DiaryEntry e = diary.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry " + id + " not found"));
        if (!e.getUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Entry " + id + " not found");
        }
        return e;
    }

    private static String formatGrams(double g) {
        return (g == Math.floor(g) ? String.valueOf((long) g) : String.valueOf(g)) + " g";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static double nz(Double v) { return v == null ? 0.0 : v; }

    /** Public + user-owned are visible to the caller. */
    private void ensureIngredientVisible(Ingredient i, AppUser me) {
        if (i.getOwnerUserId() != null && !i.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Ingredient " + i.getId() + " not found");
        }
    }

    private void ensureRecipeVisible(Recipe r, AppUser me) {
        if (r.getOwnerUserId() != null && !r.getOwnerUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Recipe " + r.getId() + " not found");
        }
    }
}
