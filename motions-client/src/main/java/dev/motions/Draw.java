package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

/** Small drawing helpers shared by the menu and the HUD card. */
public final class Draw {
    private Draw() {}

    public static final int WHITE = 0xFFFFFFFF;

    public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    /** Multiplies the alpha of an ARGB colour. */
    public static int a(int c, float al) {
        al = Math.max(0f, Math.min(1f, al));
        return (Math.round(((c >>> 24) & 255) * al) << 24) | (c & 0xFFFFFF);
    }

    public static int mix(int x, int y, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 0; s <= 24; s += 8) {
            int cx = (x >>> s) & 255, cy = (y >>> s) & 255;
            out |= (Math.round(cx + (cy - cx) * t) & 255) << s;
        }
        return out;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- rounded rectangles ----
    private static final int[][] RUNS = new int[24][];

    /** Corner shape for radius r as {inset, firstRow, rowCount} triples, built once. */
    private static int[] runs(int r) {
        int[] a = r < RUNS.length ? RUNS[r] : null;
        if (a == null) {
            int[] ins = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                ins[i] = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            }
            int[] tmp = new int[r * 3];
            int n = 0;
            for (int i = 0; i < r; ) {
                int j = i;
                while (j < r && ins[j] == ins[i]) j++;
                tmp[n++] = ins[i]; tmp[n++] = i; tmp[n++] = j - i;
                i = j;
            }
            a = Arrays.copyOf(tmp, n);
            if (r < RUNS.length) RUNS[r] = a;
        }
        return a;
    }

    /** Filled rounded rectangle. Fills never overlap, so translucent colours stay even. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0 || (col >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        int[] rn = runs(r);
        for (int k = 0; k < rn.length; k += 3) {
            int ins = rn[k], row = rn[k + 1], cnt = rn[k + 2];
            c.fill(x + ins, y + row, x + w - ins, y + row + cnt, col);
            c.fill(x + ins, y + h - row - cnt, x + w - ins, y + h - row, col);
        }
        c.fill(x, y + r, x + w, y + h - r, col);
    }

    /** A rounded rect with a 1px rim: the rim colour shows at the edge, the fill sits on top. */
    public static void border(DrawContext c, int x, int y, int w, int h, int r, int fill, int rim) {
        round(c, x, y, w, h, r, rim);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Frosted-glass panel: translucent body, bright rim and a highlight line along the top edge. */
    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int rim, int body, int highlight) {
        border(c, x, y, w, h, r, body, rim);
        if ((highlight >>> 24) >= 8) c.fill(x + r, y + 1, x + w - r, y + 2, highlight);
    }

    // ---- text ----
    public static int width(String s) { return tr().getWidth(s); }

    public static void text(DrawContext c, String s, int x, int y, int col) {
        if ((col >>> 24) >= 8) c.drawText(tr(), s, x, y, col, false);
    }

    public static Text boldText(String s) { return Text.literal(s).formatted(Formatting.BOLD); }

    public static void bold(DrawContext c, Text t, int x, int y, int col) {
        if ((col >>> 24) >= 8) c.drawText(tr(), t, x, y, col, false);
    }

    public static void bold(DrawContext c, String s, int x, int y, int col) {
        if ((col >>> 24) >= 8) c.drawText(tr(), boldText(s), x, y, col, false);
    }

    public static void centered(DrawContext c, String s, int cx, int y, int col, boolean bold) {
        if (bold) bold(c, s, cx - tr().getWidth(boldText(s)) / 2, y, col);
        else text(c, s, cx - width(s) / 2, y, col);
    }

    /** Bold "bubble" lettering: bold text with a thick soft outline, used for titles. */
    public static void bubble(DrawContext c, String s, int x, int y, int fill, int outline) {
        if ((fill >>> 24) < 8) return;
        Text t = boldText(s);
        TextRenderer r = tr();
        if ((outline >>> 24) >= 8) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0) c.drawText(r, t, x + dx, y + dy, outline, false);
        }
        c.drawText(r, t, x, y, fill, false);
    }

    /** Shortens bold text with an ellipsis so it fits in maxW pixels. */
    public static String fitBold(String s, int maxW) {
        TextRenderer r = tr();
        if (r.getWidth(boldText(s)) <= maxW) return s;
        String b = s;
        while (b.length() > 1 && r.getWidth(boldText(b + "\u2026")) > maxW) b = b.substring(0, b.length() - 1);
        return b + "\u2026";
    }

    // ---- items ----
    public static void item(DrawContext c, ItemStack s, float x, float y, float scale) {
        if (s == null || s.isEmpty()) return;
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawItem(s, 0, 0);
        m.popMatrix();
    }

    /** Draws an item centred on (cx, cy), rotated by rot radians and scaled. */
    public static void itemCentered(DrawContext c, ItemStack s, float cx, float cy, float scale, float rot) {
        if (s == null || s.isEmpty()) return;
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate(rot);
        m.scale(scale, scale);
        m.translate(-8f, -8f);
        c.drawItem(s, 0, 0);
        m.popMatrix();
    }

    // ---- animation maths ----
    public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    /** Fast start, soft landing. */
    public static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Overshoots a little before settling (used for pop-in effects). */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Soft glow: four nested rounded rects stacking up toward the middle (cheap stand-in for a blur). */
    public static void glow(DrawContext c, int x, int y, int w, int h, int r, int col, float alpha) {
        int s = Math.max(1, Math.min(w, h) / 10);
        for (int k = 0; k < 4; k++) {
            int in = k * s;
            round(c, x + in, y + in, w - 2 * in, h - 2 * in, Math.max(1, r - in), a(col, alpha * 0.25f));
        }
    }
}
