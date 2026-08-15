package com.github.lutzluca.btrbz.core.widgets.manager;

final class WidgetManagerSidebarScrollState {
    private boolean mountedDetail;
    private double overviewOffset;
    private double detailOffset;

    void openDetail() {
        this.detailOffset = 0;
    }

    void saveMountedOffset(double offset) {
        if (this.mountedDetail) {
            this.detailOffset = Math.max(0, offset);
        } else {
            this.overviewOffset = Math.max(0, offset);
        }
    }

    double mount(boolean detail) {
        this.mountedDetail = detail;
        return detail ? this.detailOffset : this.overviewOffset;
    }
}
