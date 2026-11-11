package dev.motions;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;

/** Small floating texts ("+$50m") that drift away from a HUD element and fade out quickly. */
final class Pops {
    private record Pop(String text, int color, long born, int slot) {}

    private final ArrayList<Pop> list = new ArrayList<>();
    private final long lifeMs;

    Pops(long lifeMs) { this.lifeMs = lifeMs; }

    void push(String text, int color) {
        long now = System.currentTimeMillis();
        int alive = 0;
        for (Pop p : list) if (now - p.born() < lifeMs) alive++;
        if (list.size() >= 8) list.remove(0);
        list.add(new Pop(text, color, now, alive % 3)); // slots keep simultaneous popups from overlapping
    }

    boolean isEmpty() { return list.isEmpty(); }

    /** Draws beside an element: cx = its horizontal centre, y0 = its top, h = its height (both already scaled). */
    void draw(DrawContext c, long now, float cx, float y0, float h, float sc, boolean top, float alpha, boolean drift) {
        var m = c.getMatrices();
        float ps = sc * 0.85f;
        for (int i = list.size() - 1; i >= 0; i--) {
            Pop p = list.get(i);
            long age = now - p.born();
            if (age >= lifeMs) { list.remove(i); continue; }
            float t = age / (float) lifeMs;
            float a = Math.min(1f, age / 100f) * (1f - t * t) * alpha;
            float d = drift ? t * 12f : 0f;
            float py = top ? y0 + h + 3f + p.slot() * 9f * ps + d
                           : y0 - 11f * ps - 3f - p.slot() * 9f * ps - d;
            m.pushMatrix();
            m.translate(cx, py);
            m.scale(ps, ps);
            Draw.centered(c, p.text(), 0, 0, Draw.a(p.color(), a), true);
            m.popMatrix();
        }
    }
}
