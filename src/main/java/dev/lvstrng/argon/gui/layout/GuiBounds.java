package dev.lvstrng.argon.gui.layout;

/**
 * Mutable panel bounds with one coordinate system for drawing and pointer input.
 * Drag offsets are captured on press so panels never jump to the cursor.
 */
public final class GuiBounds {
    private final int minimumWidth;
    private final int minimumHeight;
    private int x;
    private int y;
    private int width;
    private int height;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private boolean dragging;
    private boolean resizing;
    private boolean changedDuringInteraction;

    public GuiBounds(int minimumWidth, int minimumHeight) {
        this.minimumWidth = minimumWidth;
        this.minimumHeight = minimumHeight;
    }

    public void initialize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean beginDrag(double mouseX, double mouseY) {
        if (resizing) {
            return false;
        }
        dragging = true;
        changedDuringInteraction = false;
        dragOffsetX = (int) Math.round(mouseX) - x;
        dragOffsetY = (int) Math.round(mouseY) - y;
        return true;
    }

    public boolean beginResize(double mouseX, double mouseY) {
        if (dragging) {
            return false;
        }
        resizing = true;
        changedDuringInteraction = false;
        resizeStartX = (int) Math.round(mouseX);
        resizeStartY = (int) Math.round(mouseY);
        resizeStartWidth = width;
        resizeStartHeight = height;
        return true;
    }

    public void update(double mouseX, double mouseY, int screenWidth, int screenHeight, int margin) {
        int oldX = x;
        int oldY = y;
        int oldWidth = width;
        int oldHeight = height;
        if (dragging) {
            x = (int) Math.round(mouseX) - dragOffsetX;
            y = (int) Math.round(mouseY) - dragOffsetY;
        } else if (resizing) {
            width = resizeStartWidth + (int) Math.round(mouseX) - resizeStartX;
            height = resizeStartHeight + (int) Math.round(mouseY) - resizeStartY;
        }
        clampToScreen(screenWidth, screenHeight, margin);
        changedDuringInteraction |= oldX != x || oldY != y || oldWidth != width || oldHeight != height;
    }

    /** Returns whether the completed interaction changed these bounds. */
    public boolean endInteraction() {
        boolean changed = changedDuringInteraction;
        dragging = false;
        resizing = false;
        changedDuringInteraction = false;
        return changed;
    }

    /**
     * Keeps the full panel usable after resolution or GUI-scale changes. The
     * effective minimum shrinks only when the physical screen cannot contain it.
     */
    public boolean clampToScreen(int screenWidth, int screenHeight, int margin) {
        int oldX = x;
        int oldY = y;
        int oldWidth = width;
        int oldHeight = height;
        int usableWidth = Math.max(1, screenWidth - margin * 2);
        int usableHeight = Math.max(1, screenHeight - margin * 2);
        int effectiveMinimumWidth = Math.min(minimumWidth, usableWidth);
        int effectiveMinimumHeight = Math.min(minimumHeight, usableHeight);
        width = clamp(width, effectiveMinimumWidth, usableWidth);
        height = clamp(height, effectiveMinimumHeight, usableHeight);
        x = clamp(x, margin, Math.max(margin, screenWidth - margin - width));
        y = clamp(y, margin, Math.max(margin, screenHeight - margin - height));
        return oldX != x || oldY != y || oldWidth != width || oldHeight != height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= bottom();
    }

    public boolean isOverHeader(double mouseX, double mouseY, int headerHeight) {
        return mouseX >= x && mouseX <= right() && mouseY >= y && mouseY <= y + headerHeight;
    }

    public boolean isOverResizeHandle(double mouseX, double mouseY, int handleSize) {
        return mouseX >= right() - handleSize && mouseX <= right()
                && mouseY >= bottom() - handleSize && mouseY <= bottom();
    }

    public boolean isInteracting() {
        return dragging || resizing;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isResizing() {
        return resizing;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
