package dev.motions;

/** Where the on-screen elements sit. Element 0 = auction card, 1 = LIVE pill. Positions are 0..1 across the free space. */
final class Layout {
    private Layout() {}

    static final int AUCTION = 0, LIVE = 1, MARGIN = 8;
    static final String[] NAMES = {"Auction card", "Live"};

    static int w(int e) { return e == AUCTION ? AuctionHud.W : LiveHud.W; }

    static int h(int e) { return e == AUCTION ? AuctionHud.H : LiveHud.H; }

    private static float freeX(int e, int sw) { return Math.max(0f, sw - w(e) * MotionsConfig.get().scale(e) - 2 * MARGIN); }

    private static float freeY(int e, int sh) { return Math.max(0f, sh - h(e) * MotionsConfig.get().scale(e) - 2 * MARGIN); }

    /** Top-left pixel of the element in scaled GUI coordinates. */
    static int posX(int e, int sw) { return Math.round(MARGIN + MotionsConfig.get().x(e) * freeX(e, sw)); }

    static int posY(int e, int sh) { return Math.round(MARGIN + MotionsConfig.get().y(e) * freeY(e, sh)); }

    /** Inverse of posX/posY: the 0..1 value for a pixel position. */
    static float fracX(int e, int sw, float px) { return Draw.clamp01((px - MARGIN) / Math.max(1f, freeX(e, sw))); }

    static float fracY(int e, int sh, float py) { return Draw.clamp01((py - MARGIN) / Math.max(1f, freeY(e, sh))); }
}
