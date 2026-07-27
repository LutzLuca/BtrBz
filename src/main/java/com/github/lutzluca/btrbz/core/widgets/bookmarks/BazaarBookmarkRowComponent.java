package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
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
import org.lwjgl.glfw.GLFW;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

final class BazaarBookmarkRowComponent extends BaseParentUIComponent {
    static final int HEIGHT = 17;
    private static final int ICON_SIZE = 16;
    private static final int DOT_SIZE = 5;

    private final BazaarBookmarkListComponent list;
    private final ItemComponent item;
    private BookmarksWidgetData.Bookmark bookmark;
    private Component productName;
    private boolean showItem;
    private boolean showIndicators;
    private boolean interactive;
    private boolean reorderable;
    private int index;
    private Consumer<BookmarksAction> actions;

    BazaarBookmarkRowComponent(
        BazaarBookmarkListComponent list,
        BookmarksWidgetData.Bookmark bookmark,
        BookmarksWidgetConfig options,
        boolean interactive,
        int index,
        Consumer<BookmarksAction> actions
    ) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.list = list;
        this.item = BazaarUi.item(bookmark.iconCopy(), ICON_SIZE);
        this.allowOverflow(true);
        this.update(bookmark, options, interactive, index, actions);
    }

    void update(
        BookmarksWidgetData.Bookmark bookmark,
        BookmarksWidgetConfig options,
        boolean interactive,
        int index,
        Consumer<BookmarksAction> actions
    ) {
        this.bookmark = bookmark;
        this.productName = bookmark.formattedProductName(options.abbreviateEnchanted());
        this.showItem = options.showItems();
        this.showIndicators = options.showIndicators();
        this.interactive = interactive;
        this.reorderable = interactive && options.sort() == BookmarksWidgetConfig.BookmarkSort.Manual;
        this.index = index;
        this.actions = actions;
        this.item.stack(bookmark.iconCopy());
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        if (!this.showItem) return;
        this.item.inflate(Size.of(ICON_SIZE, ICON_SIZE));
        this.item.mount(this, this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING, this.y);
    }

    @Override
    public List<UIComponent> children() {
        return this.showItem ? List.of(this.item) : List.of();
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Bookmark row owns its ItemStack");
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.interactive && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.interactive) return super.onMouseDown(click, doubled);
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.reorderable && this.list.beginDrag(this.bookmark.productId(), this.index)) return true;
            this.actions.accept(new BookmarksAction.Open(this.bookmark.productId()));
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.actions.accept(new BookmarksAction.Remove(this.bookmark.productId()));
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.reorderable || !this.list.dragging(this.bookmark.productId())) return false;
        this.list.dragPointer(this.y + (int) click.y());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.reorderable || !this.list.dragging(this.bookmark.productId())) return false;
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
        if (this.interactive && this.isInBoundingBox(mouseX, mouseY)) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);
        }
        if (this.list.dragging(this.bookmark.productId())) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_DRAG);
        }
        if (this.showItem) this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.item));

        var font = Minecraft.getInstance().font;
        int textX = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING + (this.showItem ? ICON_SIZE + 3 : 0);
        int indicatorCount = this.showIndicators
            ? (this.bookmark.buyOrder() ? 1 : 0) + (this.bookmark.sellOrder() ? 1 : 0)
            : 0;
        int indicatorWidth = indicatorCount == 0 ? 0 : indicatorCount * DOT_SIZE + (indicatorCount - 1) * 3;
        int available = Math.max(0, this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING
            - indicatorWidth - 4 - textX);
        graphics.text(font, ellipsize(this.productName, available), textX,
            this.y + (this.height - font.lineHeight) / 2, BazaarStyles.PRIMARY_TEXT, false);

        int dotX = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING - indicatorWidth;
        int dotY = this.y + (this.height - DOT_SIZE) / 2;
        if (this.showIndicators && this.bookmark.buyOrder()) {
            graphics.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, BazaarStyles.BUY_ACCENT);
            dotX += DOT_SIZE + 3;
        }
        if (this.showIndicators && this.bookmark.sellOrder()) {
            graphics.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, BazaarStyles.SELL_ACCENT);
        }
    }
}
