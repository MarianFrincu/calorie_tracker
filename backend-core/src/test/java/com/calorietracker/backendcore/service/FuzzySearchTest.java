package com.calorietracker.backendcore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the fuzzy-search fallback. Pure function over an in-memory
 * list, so no Spring context, no DB — keeps the test fast and isolated.
 */
class FuzzySearchTest {

    private record Food(String name) {}

    @Test
    void single_edit_typo_finds_target() {
        // "zucini" -> "zucchini" is 2 edits (insert c, insert h). Tolerance for 6-char query is 2.
        List<Food> pool = List.of(new Food("Apple"), new Food("Zucchini"), new Food("Broccoli"));
        List<Food> hits = FuzzySearch.rank(pool, "zucini", Food::name,
                FuzzySearch.defaultTolerance("zucini"), 10);
        assertThat(hits).extracting(Food::name).containsExactly("Zucchini");
    }

    @Test
    void word_level_match_inside_multi_word_name() {
        List<Food> pool = List.of(new Food("Zucchini, raw"), new Food("Pumpkin, raw"));
        List<Food> hits = FuzzySearch.rank(pool, "zucini", Food::name,
                FuzzySearch.defaultTolerance("zucini"), 10);
        assertThat(hits).extracting(Food::name).containsExactly("Zucchini, raw");
    }

    @Test
    void unrelated_query_returns_empty() {
        List<Food> pool = List.of(new Food("Apple"), new Food("Banana"), new Food("Carrot"));
        List<Food> hits = FuzzySearch.rank(pool, "xyzqq", Food::name,
                FuzzySearch.defaultTolerance("xyzqq"), 10);
        assertThat(hits).isEmpty();
    }

    @Test
    void tolerance_scales_with_query_length() {
        // 3 chars -> 1, 6 chars -> 2, longer -> 3.
        assertThat(FuzzySearch.defaultTolerance("abc")).isEqualTo(1);
        assertThat(FuzzySearch.defaultTolerance("abcdef")).isEqualTo(2);
        assertThat(FuzzySearch.defaultTolerance("abcdefghij")).isEqualTo(3);
    }

    @Test
    void results_are_capped_at_limit() {
        List<Food> pool = List.of(new Food("Aa"), new Food("Ab"), new Food("Ac"),
                                  new Food("Ad"), new Food("Ae"));
        List<Food> hits = FuzzySearch.rank(pool, "Ax", Food::name,
                FuzzySearch.defaultTolerance("Ax"), 2);
        assertThat(hits).hasSize(2);
    }

    @Test
    void empty_query_returns_empty() {
        List<Food> pool = List.of(new Food("Apple"));
        assertThat(FuzzySearch.rank(pool, "", Food::name, 2, 10)).isEmpty();
        assertThat(FuzzySearch.rank(pool, null, Food::name, 2, 10)).isEmpty();
    }
}
