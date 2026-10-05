package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

/** Mounted product search controls. The screen owns the draft and selection transitions. */
public final class ProductSearchControls extends FlowLayout {
    private final Function<String, List<IndexedProduct>> lookup;
    private final Supplier<String> unavailableHint;

    private final BazaarData data;
    private final int productWidth;
    private final TextBoxComponent searchBox;
    private final FlowLayout resultRows;
    private final RestorableVerticalScrollContainer<FlowLayout> scroller;
    private final Consumer<IndexedProduct> select;
    private Results results = new Results(List.of(), "");

    public ProductSearchControls(
        BazaarData data,
        Function<String, List<IndexedProduct>> lookup,
        Supplier<String> unavailableHint,
        int productWidth,
        int resultHeight,
        String query,
        Consumer<String> queryChanged,
        Consumer<IndexedProduct> select
    ) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.data = data;
        this.lookup = lookup;
        this.unavailableHint = unavailableHint;
        this.productWidth = productWidth;
        this.select = select;
        this.gap(7);
        this.searchBox = UIComponents.textBox(Sizing.fill(100));
        this.searchBox.setMaxLength(120);
        this.searchBox.text(query);
        this.searchBox.onChanged().subscribe(value -> {
            queryChanged.accept(value);
            this.refresh(true);
        });
        this.child(this.searchBox);
        this.resultRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.resultRows.gap(3);
        this.scroller = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.fixed(resultHeight),
            this.resultRows);
        this.scroller.scrollbarThiccness(3);
        this.child(this.scroller);
        this.refresh(true);
    }

    public void refresh(boolean force) {
        var next = Results.lookup(this.lookup, this.unavailableHint, this.searchBox.getValue());
        if (!force && next.equals(this.results)) {
            return;
        }
        double offset = force ? 0 : this.scroller.savedScrollOffset();
        this.scroller.restoreScrollOffset(offset);
        this.results = next;
        this.resultRows.clearChildren();
        if (next.matches().isEmpty()) {
            String hint = next.emptyHint(this.searchBox.getValue());
            if (!hint.isEmpty()) {
                this.resultRows.child(BazaarUi.text(hint, UiStyles.palette().muted()).maxWidth(this.productWidth - 24));
            }
            return;
        }
        for (var product : next.matches()) {
            boolean duplicate = next.matches().stream().filter(other -> other.strippedName()
                .equalsIgnoreCase(product.strippedName())).count() > 1;
            this.resultRows.child(new ProductSearchRow(this.data, product, this.productWidth - 20, duplicate,
                () -> this.select.accept(product)));
        }
    }

    @Nullable
    public IndexedProduct selectionFor(KeyEvent event) {
        if (this.searchFocused()
            && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
            && !this.results.matches().isEmpty()) {
            return this.results.matches().getFirst();
        }
        return null;
    }

    public void focusSearch() {
        var handler = this.focusHandler();
        if (handler != null) {
            handler.focus(this.searchBox, UIComponent.FocusSource.MOUSE_CLICK);
        }
    }

    public ViewState saveViewState() {
        return new ViewState(this.scroller.savedScrollOffset(), this.searchFocused());
    }

    public void restoreViewState(ViewState state) {
        this.scroller.restoreScrollOffset(state.scrollOffset());
        if (state.searchFocused()) {
            this.focusSearch();
        }
    }

    private boolean searchFocused() {
        var handler = this.focusHandler();
        return handler != null && handler.focused() == this.searchBox;
    }

    public record ViewState(double scrollOffset, boolean searchFocused) {}

    public record Results(List<IndexedProduct> matches, String unavailableHint) {
        public static Results lookup(
            Function<String, List<IndexedProduct>> lookup,
            Supplier<String> unavailableHint,
            String query
        ) {
            return new Results(lookup.apply(query), unavailableHint.get());
        }

        public String emptyHint(String query) {
            return !this.unavailableHint.isEmpty()
                ? this.unavailableHint
                : query.isBlank() ? "" : "No matches. Try a shorter name.";
        }
    }
}
