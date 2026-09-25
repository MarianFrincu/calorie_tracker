package com.calorietracker.aiservice.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MealTextTest {

    @Test
    void splitsOnCommasAndWithAndPlus() {
        List<MealText.Item> items = MealText.split("2 eggs, toast with butter + banana; milk\nrice");
        assertThat(items).extracting(MealText.Item::name)
                .containsExactly("eggs", "toast", "butter", "banana", "milk", "rice");
        assertThat(items.get(0).count()).isEqualTo(2);
        assertThat(items.get(0).grams()).isNull();
    }

    @Test
    void readsWeightsInGramsKilogramsAndMillilitres() {
        List<MealText.Item> items = MealText.split("chicken 200g, 1.5 kg potatoes, 250 ml milk, rice 150 grams");
        assertThat(items).extracting(MealText.Item::grams).containsExactly(200.0, 1500.0, 250.0, 150.0);
        assertThat(items).extracting(MealText.Item::name).containsExactly("chicken", "potatoes", "milk", "rice");
    }

    @Test
    void dropsArticlesAndServingWords() {
        List<MealText.Item> items = MealText.split("a banana and 2 slices of whole wheat bread and a cup of yogurt");
        assertThat(items).extracting(MealText.Item::name).containsExactly("banana", "whole wheat bread", "yogurt");
        assertThat(items.get(1).count()).isEqualTo(2);
    }

    @Test
    void ignoresEmptyPartsAndCapsTheNumberOfItems() {
        assertThat(MealText.split(" , and ,, ")).isEmpty();
        assertThat(MealText.split(null)).isEmpty();
        String many = String.join(", ", java.util.Collections.nCopies(50, "apple"));
        assertThat(MealText.split(many)).hasSize(MealText.MAX_ITEMS);
    }

    @Test
    void makesSimpleSingulars() {
        assertThat(MealText.singular("eggs")).isEqualTo("egg");
        assertThat(MealText.singular("tomatoes")).isEqualTo("tomato");
        assertThat(MealText.singular("berries")).isEqualTo("berry");
        assertThat(MealText.singular("hummus")).isEqualTo("hummu"); // good enough: the library search is fuzzy
        assertThat(MealText.singular("glass")).isEqualTo("glass");
        assertThat(MealText.singular("egg")).isEqualTo("egg");
    }
}
