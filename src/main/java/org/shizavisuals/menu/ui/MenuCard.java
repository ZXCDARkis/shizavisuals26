package org.shizavisuals.menu.ui;

public abstract class MenuCard extends Component {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 57;
    protected static final int RADIUS = 8;
    protected static final int PADDING_X = 16;
    protected static final int AVATAR_SIZE = 32;
    protected static final int ACTION_BOX = 24;
    protected static final int ACTION_ICON = 16;
    protected static final int ACTION_GAP = 4;
    protected static final float AVATAR_Y = (HEIGHT - AVATAR_SIZE) / 2.0F;

    protected int designX;
    protected int designY;
    protected int mouseX;
    protected int mouseY;
    protected float alpha = 1.0F;

    protected final void placeBounds(Component owner, int x, int y, int mouseX, int mouseY, float alpha) {
        this.designX = x;
        this.designY = y;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.alpha = alpha;
        attach(owner, owner.sx(x), owner.sy(y), owner.px(WIDTH), owner.px(HEIGHT));
    }

    protected final int actionSlotX(int indexFromRight) {
        return WIDTH - PADDING_X - ACTION_BOX - indexFromRight * (ACTION_BOX + ACTION_GAP);
    }

    protected final float actionRowY() {
        return this.designY + (HEIGHT - ACTION_BOX) / 2.0F;
    }

    protected final boolean cardHovered() {
        return contains(this.mouseX, this.mouseY);
    }
}
