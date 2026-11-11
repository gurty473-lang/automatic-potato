package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** The "LIVE: +$87.9m" pill at the top of the screen while a session runs. Red with a "-" when you're losing. */
public final class LiveHud {
    private LiveHud() {}

    public static final int W = 116, H = 22;
    private static final int GREEN = 0xFF4ADE80, RED = 0xFFF87171, TXT = 0xFFF1F3F6;

    /** Bold text plus its width, rebuilt only when the string changes. */
    public static final class PillText {
        private String s = "";
        private Text t;
        private int w;

        public void set(String n) {
            if (t != null && n.equals(s)) return;
            s = n;
            t = Draw.boldText(n);
            w = Draw.tr().getWidth(t);
        }
    }

    private static final PillText TEXT = new PillText();
    private static final Pops POPS = new Pops(1500);
    private static final Anim in = new Anim(0);
    private static boolean shown;
    private static LiveSession.State cState;
    private static long cVal = Long.MIN_VALUE, lastNs;
    private static float disp; // the number on screen: it rolls toward the real profit

    /** Floating "+$50m" / "-$30m" next to the pill when a payment is counted. */
    public static void pushDelta(long d) {
        POPS.push((d > 0 ? "+$" : "-$") + LiveSession.fmt(Math.abs(d)), d > 0 ? GREEN : RED);
    }

    public static void render(DrawContext c, RenderTickCounter tc) {
        LiveSession.State s = LiveSession.state;
        if (!shown && s == LiveSession.State.IDLE) return; // idle fast path: no work at all
        if (s == LiveSession.State.IDLE) in.to(0f); else in.snap(1f);
        float vis = in.get();
        if (vis < 0.01f) { shown = false; return; }
        shown = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof MotionsScreen) return;

        MotionsConfig cfg = MotionsConfig.get();
        long now = System.currentTimeMillis(), ns = System.nanoTime();
        boolean waiting = s == LiveSession.State.WAITING;
        long p = waiting ? 0 : LiveSession.profit();

        // the number rolls toward the real profit instead of jumping
        float dt = lastNs == 0 ? 0f : Math.min((ns - lastNs) / 1e9f, 0.1f);
        lastNs = ns;
        if (s != cState) { cState = s; disp = p; cVal = Long.MIN_VALUE; }
        if (!cfg.animations || Math.abs(p - disp) < 1f) disp = p;
        else disp += (p - disp) * (1f - (float) Math.exp(-dt * 9f));
        long val = Math.round(disp);
        if (val != cVal) { // rebuild the text only when the shown number changes
            cVal = val;
            TEXT.set(waiting ? "LIVE: type /bal" : "LIVE: " + LiveSession.signed(val));
        }

        int col = waiting ? TXT : p < 0 ? RED : GREEN;
        long since = now - LiveSession.changedMs;
        float glow = waiting ? 0.3f + 0.3f * (float) Math.sin(now / 220.0) : since < 900 ? 1f - since / 900f : 0f;
        int glowCol = waiting ? cfg.accent : LiveSession.lastDelta < 0 ? RED : GREEN;

        float sc = cfg.liveScale;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int x = Layout.posX(Layout.LIVE, sw), y0 = Layout.posY(Layout.LIVE, sh);
        boolean top = y0 + H * sc / 2f < sh / 2f;
        float off = (1f - vis) * (H * sc + 24f) * (top ? -1f : 1f);
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y0 + off);
        m.scale(sc, sc);
        drawPill(c, 0, 0, TEXT, col, vis, cfg.accent, glow, glowCol);
        m.popMatrix();
        if (!POPS.isEmpty()) POPS.draw(c, now, x + W * sc / 2f, y0, H * sc, sc, top, vis, cfg.animations);
    }

    /** Glass pill with the text centred. */
    public static void drawPill(DrawContext c, int x, int y, PillText t, int col, float a, int ac, float glow, int glowCol) {
        if (a <= 0.01f || t.t == null) return;
        if (glow > 0.01f) Draw.round(c, x - 2, y - 2, W + 4, H + 4, H / 2 + 2, Draw.a(glowCol, 0.30f * glow * a));
        Draw.glass(c, x, y, W, H, H / 2, Draw.a(ac, 0.75f * a), Draw.a(MotionsConfig.get().glassTint(), 0.82f * a),
                Draw.a(Draw.WHITE, 0.25f * a));
        Draw.bold(c, t.t, x + (W - t.w) / 2, y + (H - 8) / 2, Draw.a(col, a));
    }
}
