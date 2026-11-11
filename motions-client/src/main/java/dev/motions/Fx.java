package dev.motions;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Cheap particle effects (click ripples, spark bursts, confetti) in fixed-size pools.
 * Nothing is allocated while playing, and drawing is skipped entirely while nothing is alive.
 */
final class Fx {
    private static final byte RIPPLE = 1, SPARK = 2, CONFETTI = 3;
    private static final int[] PALETTE = {0xFFFFD166, 0xFFEF476F, 0xFF4CC9F0, 0xFF4ADE80, 0xFFFFFFFF};

    private final int cap;
    private final byte[] kind;
    private final float[] x, y, vx, vy;
    private final long[] born;
    private final int[] life, col;
    private final Random rnd = new Random();
    private int next;
    private long lastEnd; // when the last particle dies: nothing to draw after this

    Fx(int cap) {
        this.cap = cap;
        kind = new byte[cap];
        x = new float[cap]; y = new float[cap]; vx = new float[cap]; vy = new float[cap];
        born = new long[cap];
        life = new int[cap]; col = new int[cap];
    }

    private int slot(long now, int lifeMs, byte k, float px, float py, int color) {
        int i = next;
        next = (next + 1) % cap; // ring buffer: the oldest particle is reused
        kind[i] = k; x[i] = px; y[i] = py; col[i] = color; born[i] = now; life[i] = lifeMs;
        lastEnd = Math.max(lastEnd, now + lifeMs);
        return i;
    }

    /** A soft ring-flash that expands from a click. */
    void ripple(float px, float py, int color) {
        slot(System.currentTimeMillis(), 420, RIPPLE, px, py, color);
    }

    /** A small spray of sparks. */
    void burst(float px, float py, int color, int n) {
        long now = System.currentTimeMillis();
        for (int k = 0; k < n; k++) {
            int i = slot(now, 450 + rnd.nextInt(300), SPARK, px, py, k % 3 == 0 ? 0xFFFFFFFF : color);
            double ang = rnd.nextFloat() * 6.2832;
            float sp = 25 + rnd.nextFloat() * 55;
            vx[i] = (float) Math.cos(ang) * sp;
            vy[i] = (float) Math.sin(ang) * sp - 15;
        }
    }

    /** Colourful pieces shot upward that then fall. */
    void confetti(float px, float py, float spread, int n) {
        long now = System.currentTimeMillis();
        for (int k = 0; k < n; k++) {
            int i = slot(now, 1700 + rnd.nextInt(700), CONFETTI, px + (rnd.nextFloat() - 0.5f) * spread * 0.8f, py, PALETTE[rnd.nextInt(PALETTE.length)]);
            vx[i] = (rnd.nextFloat() - 0.5f) * 110;
            vy[i] = -(60 + rnd.nextFloat() * 100);
        }
    }

    void draw(DrawContext c, long now, float alpha) {
        if (now >= lastEnd) return;
        for (int i = 0; i < cap; i++) {
            if (kind[i] == 0) continue;
            long age = now - born[i];
            if (age >= life[i]) { kind[i] = 0; continue; }
            float f = age / (float) life[i], s = age / 1000f;
            switch (kind[i]) {
                case RIPPLE -> {
                    int r = Math.max(1, Math.round(Draw.ease(f) * 23f));
                    Draw.round(c, Math.round(x[i]) - r, Math.round(y[i]) - r, 2 * r, 2 * r, r, Draw.a(col[i], (1f - f) * 0.30f * alpha));
                }
                case SPARK -> {
                    int px = Math.round(x[i] + vx[i] * s), py = Math.round(y[i] + vy[i] * s + 70f * s * s);
                    int cc = Draw.a(col[i], (1f - f) * alpha);
                    c.fill(px - 1, py, px + 2, py + 1, cc);
                    c.fill(px, py - 1, px + 1, py + 2, cc);
                }
                default -> {
                    int px = Math.round(x[i] + vx[i] * s), py = Math.round(y[i] + vy[i] * s + 150f * s * s);
                    c.fill(px, py, px + 3, py + 2, Draw.a(col[i], Math.min(1f, (1f - f) * 2.5f) * alpha));
                }
            }
        }
    }
}
