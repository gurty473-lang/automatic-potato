package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** The on-screen auction card (glass style), the little green payment popups and the winner display. */
public final class AuctionHud {
    private AuctionHud() {}

    public static final int W = 232, H = 78;
    private static final int TXT = 0xFFF1F3F6, MUTED = 0xFFB4BCC8, GREEN = 0xFF4ADE80, BLACK = 0xFF000000;

    /** Everything the card needs to draw. Text widths and fonts are rebuilt only when the text changes. */
    public static final class Card {
        public ItemStack stack = ItemStack.EMPTY;
        public float prog = 1f, glow, flash, time = -1f; // time < 0 = static item
        public int titleCol, minCol;                     // 0 = default colour
        private String title = "", min = "", timer = "", info = "", count = "", infoShown = "";
        private Text titleT, timerT;
        private int minW, timerW;

        public void text(String title, String min, String timer, String info, String count) {
            if (titleT == null || !title.equals(this.title)) { this.title = title; titleT = Draw.boldText(Draw.fitBold(title, 104)); }
            if (timerT == null || !timer.equals(this.timer)) { this.timer = timer; timerT = Draw.boldText(timer); timerW = Draw.tr().getWidth(timerT); }
            if (!min.equals(this.min)) { this.min = min; minW = Draw.width(min); }
            if (!info.equals(this.info)) { this.info = info; infoShown = Draw.tr().trimToWidth(info, W - 30); }
            this.count = count;
        }
    }

    private static final Card CARD = new Card();
    private static final Anim in = new Anim(0), flashAnim = new Anim(0);
    private static boolean shown;
    private static int lastSec = -1;

    // the card text is rebuilt only when one of these changes
    private static AuctionManager.State cState;
    private static long cBid = Long.MIN_VALUE;
    private static int cSec = -1;
    private static String cBidder;
    private static ItemStack cStack;

    // little green "+500M" popups that fade out quickly, and a confetti burst for the winner
    private static final Pops POPS = new Pops(1700);
    private static final Fx CONFETTI = new Fx(48);
    private static long confettiFor;

    public static void pushPayment(String text) { POPS.push(text, GREEN); }

    public static void render(DrawContext c, RenderTickCounter tc) {
        MotionsConfig cfg = MotionsConfig.get();
        AuctionManager.State s = AuctionManager.state;
        // idle fast path: nothing to draw, so do no work at all
        if (!shown && (s == AuctionManager.State.IDLE || !cfg.showAuctionHud)) return;
        if (cfg.showAuctionHud && s != AuctionManager.State.IDLE) in.snap(1f); else in.to(0f);
        float vis = in.get();
        if (vis < 0.01f) { shown = false; return; }
        shown = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof MotionsScreen) return;

        long now = System.currentTimeMillis();
        boolean active = s == AuctionManager.State.ACTIVE;
        long total = AuctionManager.durationSec * 1000L;
        long rem = active ? Math.max(0, AuctionManager.endMs - now) : 0;
        int sec = (int) ((rem + 999) / 1000);
        if (sec != lastSec) { lastSec = sec; flashAnim.snap(1f); }
        flashAnim.to(0f);

        // pop-out: the card drops in from the nearest edge with a small bounce when an auction starts
        float pop = 1f;
        if (active && cfg.animations) pop = Draw.easeOutBack(Math.min(1f, (now - AuctionManager.startMs) / (650f / cfg.animSpeed)));
        float slide = pop * vis;
        float alpha = Math.max(0f, Math.min(1f, slide * 1.6f));
        if (alpha < 0.01f) return;

        updateText(s, sec);
        long since = now - AuctionManager.bidPulseMs;
        CARD.glow = since < 1200 ? 1f - since / 1200f : 0f;
        CARD.flash = flashAnim.get();
        CARD.time = (now % 1_000_000L) / 1000f;
        CARD.prog = active ? (total > 0 ? (float) rem / total : 0f)
                : Math.max(0f, 1f - (now - AuctionManager.endedAtMs) / (float) AuctionManager.WINNER_SHOW_MS);

        float sc = cfg.hudScale;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int x = Layout.posX(Layout.AUCTION, sw), y0 = Layout.posY(Layout.AUCTION, sh);
        boolean top = y0 + H * sc / 2f < sh / 2f;
        float off = (1f - slide) * (H * sc + 24f) * (top ? -1f : 1f);
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y0 + off);
        m.scale(sc, sc);
        drawCard(c, 0, 0, W, CARD, alpha, cfg.accent);
        m.popMatrix();

        if (s == AuctionManager.State.ENDED && cBid >= 0 && cfg.animations && AuctionManager.endedAtMs != confettiFor) {
            confettiFor = AuctionManager.endedAtMs; // the winner gets a confetti burst, once
            CONFETTI.confetti(x + W * sc / 2f, y0 + 14f * sc, W * sc, 36);
        }
        CONFETTI.draw(c, now, 1f);
        if (!POPS.isEmpty()) POPS.draw(c, now, x + W * sc / 2f, y0, H * sc, sc, top, alpha, cfg.animations);
    }

    /**
     * Rebuilds the card text, but only when something changed. Only the leading payer is ever shown:
     * their name in green as the title, the amount they paid in the pill, and WINNER once it ends.
     */
    private static void updateText(AuctionManager.State s, int sec) {
        if (s == cState && AuctionManager.currentBid == cBid && sec == cSec && AuctionManager.stack == cStack
                && AuctionManager.currentBidder == cBidder) return;
        cState = s; cBid = AuctionManager.currentBid; cSec = sec; cStack = AuctionManager.stack; cBidder = AuctionManager.currentBidder;
        Card k = CARD;
        k.stack = cStack;
        String item = AuctionManager.label(cStack, AuctionManager.qty);
        String count = AuctionManager.qty > 1 ? "x" + AuctionManager.qty : "";
        String minTxt = "Min " + AuctionManager.fmt(AuctionManager.minPrice);
        boolean lead = cBid >= 0;
        String leader = cBidder != null ? cBidder : "Unknown"; // Bedrock names keep their leading "."
        String paid = "Paid " + AuctionManager.fmt(cBid);
        k.titleCol = k.minCol = lead ? GREEN : 0;
        if (s == AuctionManager.State.ACTIVE) {
            String worth = AuctionManager.worth > 0 ? " \u2022 worth " + AuctionManager.fmt(AuctionManager.worth) : "";
            k.text(lead ? leader : "No bids", lead ? paid : minTxt, AuctionManager.time(sec),
                    lead ? "Leading \u2022 " + item : item + worth + " \u2022 live", count);
        } else if (lead) {
            k.text(leader, "WINNER", "ENDED", paid + " \u2022 " + item, count);
        } else {
            k.text("No winner", minTxt, "ENDED", "Auction ended \u2022 no payments", count);
        }
    }

    /** Draws the glass card at (x, y). Also used (static) for the preview in the menu. */
    public static void drawCard(DrawContext c, int x, int y, int w, Card k, float alpha, int ac) {
        if (alpha <= 0.01f || k.titleT == null) return;
        int tint = MotionsConfig.get().glassTint();
        if (k.glow > 0.01f) Draw.round(c, x - 3, y - 3, w + 6, H + 6, 11, Draw.a(ac, 0.30f * k.glow * alpha));
        Draw.glass(c, x, y, w, H, 9, Draw.a(ac, 0.85f * alpha), Draw.a(tint, 0.82f * alpha), Draw.a(Draw.WHITE, 0.28f * alpha));
        Draw.round(c, x + 8, y + 8, 38, 38, 8, Draw.a(BLACK, 0.30f * alpha)); // item well

        if (alpha > 0.5f && !k.stack.isEmpty()) drawItem(c, x, y, k, alpha, ac);
        if (!k.count.isEmpty()) { // stack-size badge in the corner of the item well
            var m = c.getMatrices();
            m.pushMatrix();
            m.translate(x + 44, y + 38);
            m.scale(0.8f, 0.8f);
            int cw = Draw.width(k.count);
            Draw.text(c, k.count, -cw + 1, 1, Draw.a(BLACK, 0.8f * alpha));
            Draw.text(c, k.count, -cw, 0, Draw.a(TXT, alpha));
            m.popMatrix();
        }

        Draw.bold(c, k.titleT, x + 54, y + 11, Draw.a(k.titleCol != 0 ? k.titleCol : TXT, alpha));
        Draw.border(c, x + 54, y + 25, k.minW + 14, 14, 7, Draw.a(BLACK, 0.30f * alpha), Draw.a(Draw.WHITE, 0.16f * alpha));
        Draw.text(c, k.min, x + 61, y + 28, Draw.a(k.minCol != 0 ? k.minCol : 0xFFD5DAE1, alpha));

        int tx = x + w - 64;
        Draw.border(c, tx, y + 13, 54, 22, 11, Draw.a(BLACK, 0.30f * alpha),
                Draw.a(Draw.mix(0x2EFFFFFF, ac, k.flash), alpha));
        Draw.bold(c, k.timerT, tx + 27 - k.timerW / 2, y + 20, Draw.a(TXT, alpha));

        Draw.round(c, x + 10, y + 53, 4, 4, 2, Draw.a(ac, alpha));
        Draw.text(c, k.infoShown, x + 19, y + 51, Draw.a(MUTED, alpha));

        Draw.round(c, x + 8, y + 63, w - 16, 4, 2, Draw.a(BLACK, 0.35f * alpha));
        int fw = (int) ((w - 16) * Math.max(0f, Math.min(1f, k.prog)));
        if (fw >= 2) Draw.round(c, x + 8, y + 63, fw, 4, 2, Draw.a(ac, alpha));
        Draw.centered(c, "Motions Client", x + w / 2, y + 69, Draw.a(0xFF7B8490, alpha), false);
    }

    /** Item icon: static in previews, otherwise floating, swaying and sparkling. */
    private static void drawItem(DrawContext c, int x, int y, Card k, float alpha, int ac) {
        if (k.time < 0 || !MotionsConfig.get().animations) {
            Draw.item(c, k.stack, x + 13, y + 13, 1.7f);
            return;
        }
        float t = k.time, glow = k.glow, cx = x + 27, cy = y + 27;
        Draw.round(c, x + 8, y + 8, 38, 38, 8, Draw.a(ac, (0.07f + 0.05f * (float) Math.sin(t * 3f) + 0.18f * glow) * alpha));
        float bob = (float) Math.sin(t * 2.4f) * 2.2f;
        float tilt = (float) Math.sin(t * 1.6f) * 0.14f;
        float spin = glow > 0f ? (1f - glow * glow) * 6.2832f : 0f; // one full spin on start / payment
        float scale = 1.6f + 0.06f * (float) Math.sin(t * 3.2f) + 0.25f * glow;
        Draw.itemCentered(c, k.stack, cx, cy + bob - glow * 4f, scale, tilt + spin);
        int sparkle = Draw.mix(ac, Draw.WHITE, 0.55f);
        for (int i = 0; i < 4; i++) {
            float ang = t * 0.9f + i * 1.5708f, r = 21f + 2f * (float) Math.sin(t * 2f + i);
            int sx = Math.round(cx + (float) Math.cos(ang) * r), sy = Math.round(cy + (float) Math.sin(ang) * r * 0.9f);
            int col = Draw.a(sparkle, Math.max(0f, 0.45f + 0.55f * (float) Math.sin(t * 3.4f + i * 1.7f)) * alpha);
            c.fill(sx - 1, sy, sx + 2, sy + 1, col);
            c.fill(sx, sy - 1, sx + 1, sy + 2, col);
        }
    }
}
