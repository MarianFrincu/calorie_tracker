package com.calorietracker.backendcore.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Typo-tolerant search fallback. When the substring/LIKE query returns nothing,
 * we run this over the candidate pool and rank by edit distance to the query,
 * so e.g. "zucini" can find "zucchini" and "brocoli" can find "broccoli".
 * <p>
 * Distances are computed both against the full name and each individual word,
 * since users typically misspell a single token. Results within
 * {@code maxDistance} are returned ordered best-first.
 */
public final class FuzzySearch {

    private FuzzySearch() {}

    /** Top-{@code limit} matches whose edit distance to {@code query} is &le; {@code maxDistance}. */
    public static <T> List<T> rank(List<T> candidates, String query,
                                   Function<T, String> nameOf,
                                   int maxDistance, int limit) {
        if (candidates == null || candidates.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.toLowerCase().trim();
        record Scored<U>(U item, int dist) {}
        List<Scored<T>> ranked = new ArrayList<>();
        for (T c : candidates) {
            String name = nameOf.apply(c);
            if (name == null) continue;
            String lower = name.toLowerCase();
            int best = levenshtein(q, lower);
            // Also compare against each token, so "zucini" matches "zucchini" inside
            // a multi-word name like "zucchini, raw".
            for (String w : lower.split("[\\s,\\-]+")) {
                if (w.isBlank()) continue;
                int d = levenshtein(q, w);
                if (d < best) best = d;
            }
            if (best <= maxDistance) ranked.add(new Scored<>(c, best));
        }
        ranked.sort(Comparator.<Scored<T>>comparingInt(Scored::dist)
                .thenComparing(s -> nameOf.apply(s.item()).toLowerCase()));
        List<T> out = new ArrayList<>(Math.min(ranked.size(), limit));
        for (int i = 0; i < ranked.size() && i < limit; i++) out.add(ranked.get(i).item());
        return out;
    }

    /** Reasonable default tolerance: ~1/3 of the query length, min 1, capped at 3. */
    public static int defaultTolerance(String query) {
        if (query == null) return 0;
        int n = query.length();
        if (n <= 3) return 1;
        if (n <= 6) return 2;
        return 3;
    }

    /** Classic two-row dynamic-programming Levenshtein distance. */
    public static int levenshtein(String a, String b) {
        if (a.equals(b)) return 0;
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ai = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ai == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
}
