package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

final class BazaarBookmarkRowComponent extends BaseParentUIComponent {
    static final int HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int DOT_SIZE = 5;

    private final BazaarBookmarkListComponent list;
    private final ItemComponent item;
    private BookmarksWidgetData.Bookmark bookmark;
    private Component productName;
    private boolean interactive;
    private boolean reorderable;
    private boolean reserveScrollbarSpace;
    private int index;
    private Consumer<BookmarksAction> actions;

    BazaarBookmarkRowComponent(
        BazaarBookmarkListComponent list,
        BookmarksWidgetData.Bookmark bookmark,
        BookmarksWidgetConfig options,
        boolean interactive,
        boolean reserveScrollbarSpace,
        int index,
        Consumer<BookmarksAction> actions
    ) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.list = list;
        this.item = BazaarUi.item(bookmark.itemStack(), ICON_SIZE);
        this.allowOverflow(true);
        this.update(bookmark, options, interactive, reserveScrollbarSpace, index, actions);
    }

    void update(
        BookmarksWidgetData.Bookmark bookmark,
        BookmarksWidgetConfig options,
        boolean interactive,
        boolean reserveScrollbarSpace,
        int index,
        Consumer<BookmarksAction> actions
    ) {
        this.bookmark = bookmark;
        this.productName = bookmark.formattedProductName(false);
        this.interactive = interactive;
        this.reorderable = interactive && options.sort == BookmarksWidgetConfig.BookmarkSort.Manual;
        this.reserveScrollbarSpace = reserveScrollbarSpace;
        this.index = index;
        this.actions = actions;
        this.item.stack(bookmark.itemStack());
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        this.item.inflate(Size.of(ICON_SIZE, ICON_SIZE));
        this.item.mount(
            this,
            this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING,
            this.y + (this.height - ICON_SIZE) / 2
        );
    }

    @Override
    public List<UIComponent> children() {
        return List.of(this.item);
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Bookmark row owns its item icon");
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.interactive && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.interactive) return super.onMouseDown(click, doubled);
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.reorderable && this.list.beginDrag(this.bookmark.productId(), this.index)) return true;
            this.actions.accept(new BookmarksAction.Open(this.bookmark.productId()));
            return true;
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.actions.accept(new BookmarksAction.Remove(this.bookmark.productId()));
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.reorderable || !this.list.trackingDrag(this.bookmark.productId())) return false;
        this.list.dragPointer(this.y + (int) click.y());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.reorderable || !this.list.trackingDrag(this.bookmark.productId())) return false;
        var result = this.list.finishDrag().orElse(null);
        if (result == null) return false;
        if (!result.moved()) {
            this.actions.accept(new BookmarksAction.Open(result.key()));
        } else {
            this.actions.accept(new BookmarksAction.Reorder(result.key(), result.dropIndex()));
        }
        return true;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        if (this.interactive && !this.list.hoverSuppressed() && this.isInBoundingBox(mouseX, mouseY)) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);
        }
        if (this.list.dragging(this.bookmark.productId())) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_DRAG);
        }
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.item));

        var font = Minecraft.getInstance().font;
        int textX = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING + ICON_SIZE + 3;
        int indicatorCount = (this.bookmark.buyOrder() ? 1 : 0) + (this.bookmark.sellOrder() ? 1 : 0);
        int indicatorWidth = indicatorCount == 0 ? 0 : indicatorCount * DOT_SIZE + (indicatorCount - 1) * 3;
        int trailingInset = WidgetLayoutTokens.rowTrailingInset(this.reserveScrollbarSpace);
        int available = Math.max(0, this.x + this.width - trailingInset
            - indicatorWidth - 4 - textX);
        graphics.text(font, ellipsize(this.productName, available), textX,
            this.y + (this.height - font.lineHeight) / 2, BazaarStyles.PRIMARY_TEXT, false);

        int dotX = this.x + this.width - trailingInset - indicatorWidth;
        int dotY = this.y + (this.height - DOT_SIZE) / 2;
        if (this.bookmark.buyOrder()) {
            graphics.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, BazaarStyles.BUY_ACCENT);
            dotX += DOT_SIZE + 3;
        }
        if (this.bookmark.sellOrder()) {
            graphics.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, BazaarStyles.SELL_ACCENT);
        }
    }
}
