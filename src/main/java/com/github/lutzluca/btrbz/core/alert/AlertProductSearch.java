package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Mounted product search controls; the editor keeps the selected product and draft. */
final class AlertProductSearch {
    private static final int SEARCH_LIMIT = 12;

    private final BazaarData data;
    private final AlertEditorState editor;
    private final int width;
    private final FlowLayout resultRows;
    private final TextBoxComponent box;
    private final Consumer<IndexedProduct> select;
    private List<IndexedProduct> matches = List.of();

    AlertProductSearch(
        FlowLayout pane,
        BazaarData data,
        AlertEditorState editor,
        int width,
        int resultHeight,
        @Nullable ButtonComponent cancel,
        Consumer<IndexedProduct> select
    ) {
        this.data = data;
        this.editor = editor;
        this.width = width;
        this.select = select;

        var heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        heading.gap(6);
        heading.verticalAlignment(VerticalAlignment.CENTER);
        heading.child(BazaarUi.text("Item", BazaarStyles.PRIMARY_TEXT));
        heading.child(BazaarUi.spacer());
        if (cancel != null) {
            heading.child(cancel);
        }
        pane.child(heading);

        this.box = UIComponents.textBox(Sizing.fill(100));
        this.box.setMaxLength(120);
        this.box.text(editor.query());
        this.box.onChanged().subscribe(query -> {
            this.editor.query(query);
            this.refresh(true);
        });
        pane.child(this.box);

        this.resultRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.resultRows.gap(3);
        var results = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fixed(resultHeight), this.resultRows);
        results.scrollbarThiccness(3);
        pane.child(results);
        this.refresh(true);
    }

    TextBoxComponent box() {
        return this.box;
    }

    Optional<IndexedProduct> firstMatch() {
        return this.matches.stream().findFirst();
    }

    void refresh(boolean force) {
        var next = this.data.searchProducts(this.editor.query(), SEARCH_LIMIT);
        if (!force && next.equals(this.matches)) {
            return;
        }
        this.matches = next;
        this.resultRows.clearChildren();
        if (next.isEmpty()) {
            String hint = !this.data.hasMarketData()
                ? "Waiting for market data…"
                : this.editor.query().isBlank() ? "" : "No matches. Try a shorter name.";
            if (!hint.isEmpty()) {
                this.resultRows.child(BazaarUi.text(hint, BazaarStyles.MUTED_TEXT).maxWidth(this.width - 24));
            }
            return;
        }
        for (var product : next) {
            boolean duplicate = next.stream().filter(other -> other.strippedName()
                .equalsIgnoreCase(product.strippedName())).count() > 1;
            this.resultRows.child(new AlertProductRow(this.data, product, this.width - 20, duplicate,
                () -> this.select.accept(product)));
        }
    }
}
