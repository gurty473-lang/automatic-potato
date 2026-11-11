package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Random;

/** A 5-second animated "Welcome <name>" intro, shown once each time you join a server. */
public final class Welcome {
    private Welcome() {}

    private static final long DURATION = 5000, FADE_IN = 650, FADE_OUT = 900;
    private static final String HELLO = "WELCOME";
    private static final float S = 2f; // size of the name
    private static final int SPARKS = 18, WHITE = Draw.WHITE, SOFT = 0xFFC8CFD8;

    private static boolean pending; // joined; waiting for the loading screen to go away
    private static long startMs;    // 0 = not showing
    private static Text[] nameT, helloT; // one bold Text per letter, built once per join
    private static int[] nameX, nameCw, helloX;
    private static int nameW, helloW;
    private static String hint = "";
    private static final float[] sparkX = new float[SPARKS], sparkPhase = new float[SPARKS], sparkSpeed = new float[SPARKS];

    /** Called once when you join a server or a world. */
    public static void onJoin() { pending = MotionsConfig.get().welcome; }

    /** Starts the intro as soon as the game is actually visible (not behind the loading screen). */
    public static void tick(MinecraftClient mc) {
        if (!pending || mc.player == null || mc.currentScreen != null) return;
        pending = false;
        String user = mc.getSession().getUsername();
        nameT = new Text[user.length()];
        nameX = new int[nameT.length];
        nameCw = new int[nameT.length];
        nameW = layout(user, nameT, nameX, nameCw, 0);
        helloT = new Text[HELLO.length()];
        helloX = new int[helloT.length];
        helloW = layout(HELLO, helloT, helloX, new int[helloT.length], 3);
        hint = "Press " + MotionsClient.openKey.getBoundKeyLocalizedText().getString() + " to open Motions Client";
        Random r = new Random(user.hashCode());
        for (int i = 0; i < SPARKS; i++) {
            sparkX[i] = r.nextFloat() * 2f - 1f;
            sparkPhase[i] = r.nextFloat();
            sparkSpeed[i] = 0.35f + r.nextFloat() * 0.5f;
        }
        startMs = System.currentTimeMillis();
        Sfx.welcome();
    }

    /** Measures each letter once so drawing never has to. Returns the total width. */
    private static int layout(String s, Text[] out, int[] xs, int[] widths, int gap) {
        int x = 0;
        for (int i = 0; i < s.length(); i++) {
            out[i] = Draw.boldText(String.valueOf(s.charAt(i)));
            widths[i] = Draw.tr().getWidth(out[i]);
            xs[i] = x;
            x += widths[i] + gap;
        }
        return Math.max(0, x - gap);
    }

    public static void render(DrawContext c, RenderTickCounter tc) {
        if (startMs == 0) return;
        long age = System.currentTimeMillis() - startMs;
        if (age >= DURATION) { startMs = 0; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        MotionsConfig cfg = MotionsConfig.get();
        int ac = cfg.accent;
        float t = age / 1000f;
        float a = Math.min(1f, age / (float) FADE_IN) * Math.min(1f, (DURATION - age) / (float) FADE_OUT);
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int cx = sw / 2, cy = Math.max(62, sh / 4);

        // glass band that grows out from the centre, with a quick flash of light
        int full = Math.min(sw - 16, Math.max(Math.round(nameW * S), helloW) + 100);
        float grow = Draw.ease(Math.min(1f, age / 550f));
        int bw = Math.round(full * grow), bx = cx - bw / 2, by = cy - 34;
        Draw.glass(c, bx, by, bw, 72, 12, Draw.a(ac, 0.8f * a), Draw.a(cfg.glassTint(), 0.62f * a), Draw.a(WHITE, 0.30f * a));
        float flash = Math.max(0f, 1f - age / 380f);
        if (flash > 0f) Draw.round(c, bx, by, bw, 72, 12, Draw.a(WHITE, 0.30f * flash * a));
        if (grow < 0.3f) return;

        // "WELCOME" types in letter by letter
        for (int i = 0; i < helloT.length; i++) {
            float ap = Draw.clamp01((t - 0.20f - i * 0.05f) / 0.25f);
            if (ap > 0f) Draw.bold(c, helloT[i], cx - helloW / 2 + helloX[i], Math.round(cy - 24 + (1f - ap) * 5f), Draw.a(SOFT, a * ap));
        }

        // the name: every letter pops in, then ripples while a shimmer sweeps across it
        var m = c.getMatrices();
        int base = Draw.mix(WHITE, ac, 0.55f), shadow = Draw.mix(ac, 0xFF000000, 0.6f);
        float wave = Draw.clamp01((t - 1.0f) / 0.5f), sweep = (t * 0.8f) % 1.8f - 0.3f, x0 = cx - nameW * S / 2f;
        for (int i = 0; i < nameT.length; i++) {
            float ap = Draw.clamp01((t - 0.40f - i * 0.055f) / 0.35f);
            if (ap <= 0f) continue;
            float pop = Math.max(0.01f, Draw.easeOutBack(ap));
            float xn = (nameX[i] + nameCw[i] / 2f) / Math.max(1, nameW);
            int col = Draw.mix(base, WHITE, Draw.clamp01(1f - Math.abs(xn - sweep) * 5f));
            float la = a * Math.min(1f, ap * 1.6f);
            m.pushMatrix();
            m.translate(x0 + (nameX[i] + nameCw[i] / 2f) * S, cy - 2 + (float) Math.sin(t * 3.2f + i * 0.6f) * 1.8f * wave);
            m.scale(S * pop, S * pop);
            m.translate(-nameCw[i] / 2f, -4f);
            Draw.bold(c, nameT[i], 1, 1, Draw.a(shadow, la * 0.9f));
            Draw.bold(c, nameT[i], 0, 0, Draw.a(col, la));
            m.popMatrix();
        }

        // glowing underline draws out from the middle, then the hint fades in
        float ul = Draw.ease(Draw.clamp01((t - 0.95f) / 0.5f));
        if (ul > 0f) {
            int w = Math.round(nameW * S * ul), y = cy + 14;
            c.fill(cx - w / 2, y, cx + w / 2, y + 2, Draw.a(ac, 0.95f * a));
            c.fill(cx - w / 2, y - 2, cx + w / 2, y + 5, Draw.a(ac, 0.14f * a));
        }
        float ha = Draw.clamp01((t - 1.3f) / 0.45f);
        if (ha > 0f) Draw.centered(c, hint, cx, cy + 22, Draw.a(SOFT, 0.9f * a * ha), false);

        // sparkles drifting up through the band
        int spark = Draw.mix(ac, WHITE, 0.6f);
        for (int i = 0; i < SPARKS; i++) {
            float life = (t * sparkSpeed[i] + sparkPhase[i]) % 1f;
            float al = (float) Math.sin(Math.PI * life) * a * grow;
            if (al < 0.05f) continue;
            int sx = cx + Math.round(sparkX[i] * bw / 2f * 0.95f), sy = Math.round(cy + 30 - life * 64f);
            int col = Draw.a(i % 2 == 0 ? spark : WHITE, al);
            c.fill(sx - 1, sy, sx + 2, sy + 1, col);
            c.fill(sx, sy - 1, sx + 1, sy + 2, col);
        }
    }
}
