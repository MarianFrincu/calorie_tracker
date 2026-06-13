package com.calorietracker.desktop.ui;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

/**
 * Drives a TableView with paginated REST loading + a "Load more" button.
 * Reset to page 0 with {@link #resetAndLoad(String)} on every new search;
 * append the next page with the bundled button.
 *
 * The page size is implicit in the loader (typically 20). The pager knows the
 * page is the "last one" when the loader returned fewer items than the previous
 * page - we simply hide the button when the loader returns an empty page.
 */
public class Pager<T> {

    @FunctionalInterface
    public interface PageLoader<T> {
        List<T> load(String query, int page) throws Exception;
    }

    private final PageLoader<T> loader;
    private final TableView<T> table;
    private final Button loadMoreBtn = new Button("Load more");
    private final Label statusLabel = new Label("");
    private final HBox bar;

    private String currentQuery = "";
    private int currentPage = 0;
    private int lastBatchSize = -1;
    private boolean exhausted = false;

    public Pager(PageLoader<T> loader, TableView<T> table) {
        this.loader = loader;
        this.table = table;
        loadMoreBtn.setOnAction(e -> loadNextPage());
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
        statusLabel.getStyleClass().add("muted");
        bar = new HBox(8, loadMoreBtn, statusLabel);
        bar.setAlignment(Pos.CENTER);
    }

    /** Returns the row to put under the table: contains "Load more" + count. */
    public HBox loadMoreBar() {
        return bar;
    }

    /** Reset state and fetch page 0 for a fresh query. */
    public void resetAndLoad(String query) {
        this.currentQuery = query == null ? "" : query;
        this.currentPage = 0;
        this.lastBatchSize = -1;
        this.exhausted = false;
        table.setItems(FXCollections.observableArrayList());
        statusLabel.setText("loading...");
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
        fetch(true);
    }

    private void loadNextPage() {
        if (exhausted) return;
        currentPage++;
        loadMoreBtn.setDisable(true);
        statusLabel.setText("loading...");
        fetch(false);
    }

    private void fetch(boolean replace) {
        final String q = currentQuery;
        final int page = currentPage;
        Async.run(() -> loader.load(q, page), list -> {
            // If the user moved on (changed query) while we were loading, ignore.
            if (!q.equals(currentQuery)) return;
            if (replace) {
                table.setItems(FXCollections.observableArrayList(list));
            } else {
                table.getItems().addAll(list);
            }
            lastBatchSize = list.size();
            exhausted = list.isEmpty();
            loadMoreBtn.setDisable(false);
            // Show the button when the page came back full (likely more behind it).
            // Stays hidden when results fit in a single page.
            boolean showButton = !exhausted && lastBatchSize > 0;
            loadMoreBtn.setVisible(showButton);
            loadMoreBtn.setManaged(showButton);
            int total = table.getItems().size();
            statusLabel.setText(exhausted && total == 0 ? "" : total + " loaded" + (showButton ? " - more available" : ""));
        });
    }
}
