package com.calorietracker.aiservice.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a free-text meal description into food items with an amount.
 *
 * <p>"2 eggs, 150g rice and toast with butter" becomes
 * [egg ×2, rice 150 g, toast ×1, butter ×1]. Separators are commas,
 * semicolons, "+", new lines, "and" and "with". An amount is either a
 * weight ("200g", "0.5 kg", "250 ml" - ml counted as grams) or a count
 * ("2 eggs"; no number means 1). Articles and serving words ("a", "slice of",
 * "cup of"...) are dropped from the name.
 */
public final class MealText {

    /** One food mention. {@code grams} is null when the text gave a count instead. */
    public record Item(String name, int count, Double grams) {}

    /** Upper bound per request: keeps work (and library lookups) bounded. */
    public static final int MAX_ITEMS = 20;

    private static final Pattern SEPARATORS = Pattern.compile(",|;|\\+|\\n|\\band\\b|\\bwith\\b");
    private static final Pattern WEIGHT =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|kilograms?|g|gr|grams?|ml|millilit(?:er|re)s?)\\b");
    private static final Pattern COUNT = Pattern.compile("(\\d+)");
    private static final Pattern FILLER = Pattern.compile(
            "\\b(a|an|some|of|the|slices?|pieces?|cups?|bowls?|glass(?:es)?|tbsp|tsp|tablespoons?|teaspoons?|handfuls?|portions?|servings?)\\b");

    private MealText() {}

    public static List<Item> split(String text) {
        List<Item> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        for (String raw : SEPARATORS.split(text.toLowerCase(Locale.ROOT))) {
            if (out.size() == MAX_ITEMS) break;
            String part = raw.trim();
            if (part.isEmpty()) continue;

            int count = 1;
            Double grams = null;
            Matcher w = WEIGHT.matcher(part);
            if (w.find()) {
                double amount = Double.parseDouble(w.group(1).replace(',', '.'));
                grams = w.group(2).startsWith("k") ? amount * 1000 : amount;
                part = part.substring(0, w.start()) + " " + part.substring(w.end());
            } else {
                Matcher c = COUNT.matcher(part);
                if (c.find()) count = Math.max(1, Math.min(100, Integer.parseInt(c.group(1))));
            }

            String name = FILLER.matcher(part.replaceAll("\\d+([.,]\\d+)?", " ")).replaceAll(" ")
                    .replaceAll("[^\\p{L}\\s-]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (name.isEmpty()) continue;
            out.add(new Item(name, count, grams));
        }
        return out;
    }

    /** "eggs" -> "egg", "tomatoes" -> "tomato", "berries" -> "berry"; unchanged when no plural form. */
    public static String singular(String name) {
        if (name.endsWith("ies") && name.length() > 4) return name.substring(0, name.length() - 3) + "y";
        if (name.endsWith("oes") && name.length() > 4) return name.substring(0, name.length() - 2);
        if (name.endsWith("s") && !name.endsWith("ss") && name.length() > 3) return name.substring(0, name.length() - 1);
        return name;
    }

    public static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
