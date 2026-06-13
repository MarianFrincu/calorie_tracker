package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/**
 * Sidebar entry "Explore" — a small shell that hosts two related tools:
 * <ul>
 *   <li><b>Compare</b> — pick 2-4 foods and see them side-by-side.</li>
 *   <li><b>Advanced search</b> — build rule-based filters (e.g. "protein &gt; 20
 *       AND fiber &gt; 5" or "fat &lt; 0.5 × daily fat target").</li>
 * </ul>
 * Both share the same data shape (Ingredient records), so it makes sense for
 * them to live under one sidebar entry instead of two.
 */
public class FoodExplorerView extends BorderPane {

    public FoodExplorerView(ApiClient api) {
        Tab compareTab = new Tab("Compare", new CompareView(api));
        Tab advancedTab = new Tab("Advanced search", new AdvancedSearchView(api));
        TabPane tabs = new TabPane(compareTab, advancedTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);
    }
}
