package dev.motions;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** The Motions Client menu: Auction, Live, Layout and Settings tabs plus the item picker popup. */
public class MotionsScreen extends Screen {
    static final int TXT = 0xFFF1F3F6, MUTED = 0xFFA2ABB7, DANGER = 0xFFF87171, GREEN = 0xFF4ADE80, WHITE = Draw.WHITE, BLACK = 0xFF000000;
    static final int SB = 116;
    static final int[] DUR = {20, 30, 40, 60, 300, 1800, 3600, 86400};
    static final String[] DUR_L = {"20s", "30s", "40s", "1m", "5m", "30m", "1h", "24h"};
    static final String[] CHIPS = {"1M", "10M", "100M", "1B"};
    static final String[] TAB_NAMES = {"Auction", "Live", "Layout", "Settings"};
    static final String[] DOTS = {".", "..", "..."};
    static final String[] TAX_HOV = ids("tax", 3), TAX_HIT = ids("tax:", 3), TAX_L = {"Off", "I pay +2%", "I get -2%"},
            TAX_INFO = {"No tax is counted.", "Your payments cost 2% extra.", "Payments you get are 2% less."};
    static final String[] ANCHORS = {"TL", "TC", "TR", "BL", "BC", "BR"};
    static final String[] TG_ID = {"notif", "hud", "sounds", "anim", "blur", "copy", "welcome"};
    static final String[] TG_LABEL = {"Notifications", "Auction card", "UI sounds", "Animations", "Glass blur", "Copy winner", "Welcome intro"};
    static final Pattern MONEY = Pattern.compile("[0-9.,kKmMbBtT]*"), DIGITS = Pattern.compile("\\d*");

    // widget ids are built once so nothing is allocated per frame
    static String[] ids(String p, int n) {
        String[] a = new String[n];
        for (int i = 0; i < n; i++) a[i] = p + i;
        return a;
    }
    static final String[] TAB_HOV = ids("tab", 4), TAB_HIT = ids("tab:", 4), LSEL_HOV = ids("lsh", 2), LSEL_HIT = ids("lsel:", 2), MINI_HOV = ids("mini", 2),
            CHIP_HOV = ids("chip", 4), CHIP_HIT = ids("chip:", 4), DUR_HOV = ids("dur", 8), DUR_HIT = ids("dur:", 8),
            ANCH_HOV = ids("anch", 6), ANCH_HIT = ids("anch:", 6), ACC_HOV = ids("acc", 5), ACC_HIT = ids("acc:", 5),
            ACC_SEL = ids("accs", 5), GLS_HOV = ids("gls", 6), GLS_HIT = ids("gls:", 6), GLS_SEL = ids("glss", 6),
            TG_HOV = ids("tgh", 7), TG_ANIM = ids("tga", 7), TG_HIT = {"tg:notif", "tg:hud", "tg:sounds", "tg:anim", "tg:blur", "tg:copy", "tg:welcome"};

    // ---- item list (built once) ----
    static final class Entry { Item item; ItemStack stack; String lc, id; }
    static List<Entry> ALL;

    static List<Entry> all() {
        if (ALL == null) {
            List<Entry> l = new ArrayList<>();
            for (Item it : Registries.ITEM) {
                if (it == Items.AIR) continue;
                Entry e = new Entry();
                e.item = it;
                e.stack = new ItemStack(it);
                e.lc = it.getName().getString().toLowerCase(Locale.ROOT);
                e.id = Registries.ITEM.getId(it).getPath();
                l.add(e);
            }
            l.sort(Comparator.comparing((Entry e) -> e.lc).thenComparing(e -> e.id)); // A-Z
            ALL = l;
        }
        return ALL;
    }

    static ItemStack[] TAB_ST;
    static ItemStack SAMPLE, CHEST;

    static ItemStack[] tabStacks() {
        if (TAB_ST == null) TAB_ST = stacks(Items.GOLD_INGOT, Items.CLOCK, Items.COMPASS, Items.COMPARATOR);
        return TAB_ST;
    }

    static ItemStack chest() {
        if (CHEST == null) CHEST = new ItemStack(Items.CHEST);
        return CHEST;
    }

    static ItemStack[] stacks(Item... items) {
        ItemStack[] s = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) s[i] = new ItemStack(items[i]);
        return s;
    }

    static final class Hit { String id; int x, y, w, h; }

    // ---- state ----
    final Anim open = new Anim(0), tabHi = new Anim(0), page = new Anim(0), popup = new Anim(0), gridFade = new Anim(1),
            scroll = new Anim(0), durHi = new Anim(0), statusA = new Anim(0);
    final Map<String, Anim> anims = new HashMap<>();
    final Map<Entry, Anim> itemHover = new HashMap<>();
    final Set<String> hoverOn = new HashSet<>();
    final Hit[] hits = new Hit[96];
    int hitN;
    final AuctionHud.Card preview = new AuctionHud.Card();
    final LiveHud.PillText livePreview = new LiveHud.PillText();
    static final int MOTES = 16;
    final Fx fx = new Fx(64); // click ripples and spark bursts
    final float[] moteX = new float[MOTES], moteP = new float[MOTES], moteS = new float[MOTES]; // drifting light specks
    List<Entry> shown = new ArrayList<>();
    TextFieldWidget price, worth, qty, search;
    TextFieldWidget[] mainFields;

    boolean closing, popupOpen, snapX, snapY;
    int drag; // 0 none, 1 speed slider, 2 scale slider, 3 moving an element, 4 blur slider
    int tab, layoutSel, lastHoverIdx = -1; // layoutSel: which element the Layout tab is editing (0 card, 1 live)
    float scrollTarget, scrollCur, maxScroll, grabX, grabY, miniK = 0.3f;
    long popupCloseAt, statusAt;
    String status = "";
    int statusColor = DANGER;
    Entry hoverEntry;

    int wx, wy, ww, wh;
    int px, py, pw, ph;
    int gx, gy, gw, gh, cols = 1, cell = 26;
    int miniX, miniY, speedX, speedW = 100, scaleX, scaleW = 100, blurX, blurW = 60;
    Integer savedBlur; // the player's own blur setting, restored when the menu closes

    public MotionsScreen() {
        super(Text.literal("Motions Client"));
        Random rnd = new Random(7);
        for (int i = 0; i < MOTES; i++) { moteX[i] = rnd.nextFloat(); moteP[i] = rnd.nextFloat(); moteS[i] = 0.04f + rnd.nextFloat() * 0.06f; }
    }

    TextFieldWidget mk(int max, String init, Pattern ok, Consumer<String> on) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 100, 10, Text.empty());
        f.setDrawsBackground(false);
        f.setMaxLength(max);
        if (ok != null) f.setTextPredicate(s -> ok.matcher(s).matches());
        f.setText(init);
        f.setChangedListener(on);
        return f;
    }

    @Override
    protected void init() {
        price = mk(16, AuctionManager.draftPrice, MONEY, s -> AuctionManager.draftPrice = s);
        worth = mk(16, AuctionManager.draftWorth, MONEY, s -> AuctionManager.draftWorth = s);
        qty = mk(5, AuctionManager.draftQty, DIGITS, s -> AuctionManager.draftQty = s);
        search = mk(32, "", null, s -> refilter());
        mainFields = new TextFieldWidget[]{price, worth, qty};
        open.to(1);
        page.to(1);
        refilter();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (!closing) { closing = true; open.to(0); }
    }

    @Override
    public void removed() {
        restoreBlur();
        MotionsConfig.save();
    }

    /** The menu blur uses Minecraft's own setting; raise it while the menu is open and put it back afterwards. */
    void setBlur(int level) {
        var opt = client.options.getMenuBackgroundBlurriness();
        if (savedBlur == null) savedBlur = opt.getValue();
        if (opt.getValue() != level) opt.setValue(level);
    }

    void restoreBlur() {
        if (savedBlur == null) return;
        client.options.getMenuBackgroundBlurriness().setValue(savedBlur);
        savedBlur = null;
    }

    // ---- filtering ----
    void refilter() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (q.isEmpty() || e.lc.contains(q) || e.id.contains(q)) out.add(e);
        }
        shown = out;
        scrollTarget = 0;
        scroll.snap(0);
        gridFade.snap(0);
        gridFade.to(1);
    }

    // ---- shared helpers ----
    float hov(String id, boolean on) {
        Anim a = anims.computeIfAbsent(id, k -> new Anim(0));
        a.to(on ? 1f : 0f);
        if (on) { if (hoverOn.add(id)) Sfx.hover(); } else hoverOn.remove(id);
        return a.get();
    }

    void hit(String id, int x, int y, int w, int h) {
        if (hitN >= hits.length) return;
        Hit t = hits[hitN];
        if (t == null) hits[hitN] = t = new Hit();
        t.id = id; t.x = x; t.y = y; t.w = w; t.h = h;
        hitN++;
    }

    Hit hitAt(double mx, double my) {
        for (int i = hitN - 1; i >= 0; i--) {
            Hit t = hits[i];
            if (Draw.in(mx, my, t.x, t.y, t.w, t.h)) return t;
        }
        return null;
    }

    void setStatus(String s, int col) { status = s; statusColor = col; statusAt = System.currentTimeMillis(); statusA.snap(1); }

    static int tint() { return MotionsConfig.get().glassTint(); }

    /** Big glass window (main menu / popup). */
    void win(DrawContext c, int x, int y, int w, int h, int r, float a, float bodyA) {
        Draw.glass(c, x, y, w, h, r, Draw.a(WHITE, 0.22f * a), Draw.a(tint(), bodyA * a), Draw.a(WHITE, 0.32f * a));
    }

    /** Quiet inset panel (info boxes, rows, minimap). */
    void panel(DrawContext c, int x, int y, int w, int h, int r, float a) {
        Draw.border(c, x, y, w, h, r, Draw.a(Draw.mix(tint(), WHITE, 0.05f), 0.42f * a), Draw.a(WHITE, 0.10f * a));
    }

    /** Interactive control: lights up with the accent colour on hover. */
    void ctl(DrawContext c, int x, int y, int w, int h, int r, float a, float hv, int ac) {
        if (hv > 0.02f && MotionsConfig.get().animations) Draw.round(c, x - 2, y - 2, w + 4, h + 4, r + 2, Draw.a(ac, 0.13f * hv * a)); // soft halo
        int rim = Draw.a(Draw.mix(0x29FFFFFF, (ac & 0xFFFFFF) | 0x8C000000, hv), a);
        Draw.border(c, x, y, w, h, r, Draw.a(Draw.mix(tint(), WHITE, 0.07f + 0.05f * hv), 0.50f * a), rim);
    }

    /** Selected state: accent rim and accent-tinted glass. */
    void sel(DrawContext c, int x, int y, int w, int h, int r, float a, int ac) {
        Draw.border(c, x, y, w, h, r, Draw.a(Draw.mix(tint(), ac, 0.32f), 0.65f * a), Draw.a(ac, 0.9f * a));
    }

    /** Bold bubble-style heading: light fill with a thick darker accent outline. */
    void title(DrawContext c, String s, int x, int y, float a, int ac) {
        Draw.bubble(c, s, x, y, Draw.a(Draw.mix(WHITE, ac, 0.12f), a), Draw.a(Draw.mix(ac, BLACK, 0.55f), a * 0.9f));
        if (MotionsConfig.get().animations && a > 0.3f) { // a light shimmer sweeps across the lettering
            int w = Draw.width(s) + s.length() + 4, sx = x - 8 + Math.round((w + 16) * ((System.currentTimeMillis() % 3200) / 3200f));
            c.enableScissor(sx, y - 2, sx + 7, y + 11);
            Draw.bold(c, s, x, y, Draw.a(WHITE, 0.85f * a));
            c.disableScissor();
        }
    }

    void vline(DrawContext c, int x, int y1, int y2, float a) { c.fill(x, y1, x + 1, y2, Draw.a(WHITE, 0.10f * a)); }

    void field(DrawContext c, int mx, int my, float a, int ac, boolean live, String id, int x, int y, int w, int h,
               TextFieldWidget f, String hint) {
        boolean foc = f.isFocused();
        float hv = hov(id, foc || (live && Draw.in(mx, my, x, y, w, h)));
        if (live) hit(id, x, y, w, h);
        ctl(c, x, y, w, h, 6, a, hv, ac);
        int ty = y + (h - 8) / 2;
        f.setX(x + 8); f.setY(ty); f.setWidth(w - 16); f.setHeight(10);
        if (a > 0.3f) f.render(c, mx, my, 0);
        if (f.getText().isEmpty() && !foc) Draw.text(c, hint, x + 8, ty, Draw.a(MUTED, a * 0.7f));
    }

    void slider(DrawContext c, int mx, int my, float a, int ac, boolean live, String id, int x, int y, int w, float frac, boolean dragging, String bubble) {
        float hv = hov(id, dragging || (live && Draw.in(mx, my, x - 4, y - 6, w + 8, 16)));
        if (live) hit(id, x - 4, y - 6, w + 8, 16);
        Draw.round(c, x, y, w, 4, 2, Draw.a(BLACK, 0.35f * a));
        Draw.round(c, x, y, Math.max(2, Math.round(w * frac)), 4, 2, Draw.a(ac, a));
        int ke = (int) Math.ceil(hv);
        Draw.round(c, x + Math.round(w * frac) - 4 - ke, y - 2 - ke, 8 + 2 * ke, 8 + 2 * ke, 4, Draw.a(WHITE, a));
        if (dragging && bubble != null) { // the value pops up above the knob while you drag
            int bw = Draw.width(bubble) + 8, bx = x + Math.round(w * frac) - bw / 2;
            Draw.border(c, bx, y - 19, bw, 12, 5, Draw.a(BLACK, 0.55f * a), Draw.a(ac, 0.8f * a));
            Draw.text(c, bubble, bx + 4, y - 16, Draw.a(TXT, a));
        }
    }

    /** Vertical nav list with a sliding highlight (used by the sidebar and the popup). */
    void nav(DrawContext c, int mx, int my, float a, int ac, boolean live, int x, int y, int w, int h, int gap,
             String[] names, ItemStack[] icons, String[] hovIds, String[] hitIds, Anim hi, int sel) {
        hi.to(sel);
        int hy = y + Math.round(hi.get() * (h + gap));
        sel(c, x, hy, w, h, 6, a, ac);
        Draw.round(c, x - 8, hy + 2, 7, h - 4, 3, Draw.a(ac, 0.20f * a)); // glowing marker beside the selected tab
        Draw.round(c, x - 6, hy + 4, 3, h - 8, 1, Draw.a(ac, a));
        for (int i = 0; i < names.length; i++) {
            int yy = y + i * (h + gap);
            float hv = hov(hovIds[i], live && Draw.in(mx, my, x, yy, w, h));
            if (live) hit(hitIds[i], x, yy, w, h);
            if (a > 0.6f) Draw.item(c, icons[i], x + 6, yy + (h - 12) / 2f, 0.75f);
            Draw.text(c, names[i], x + 24, yy + (h - 8) / 2, Draw.a(Draw.mix(MUTED, TXT, Math.max(hv, i == sel ? 1f : 0f)), a));
        }
    }

    void profile(DrawContext c, int x, int fy, int w, float a) {
        c.fill(x, fy - 6, x + w, fy - 5, Draw.a(WHITE, 0.10f * a));
        Draw.round(c, x, fy + 2, 20, 20, 5, Draw.a(WHITE, 0.10f * a));
        Draw.text(c, textRenderer.trimToWidth(client.getSession().getUsername(), w - 26), x + 26, fy + 4, Draw.a(TXT, a));
        Draw.text(c, "Local profile", x + 26, fy + 14, Draw.a(MUTED, a));
    }

    /** Row of colour swatches; the selected one gets a white ring. */
    void swatches(DrawContext c, int mx, int my, float a, boolean live, int[] cols, boolean lighten, int sel,
                  String[] hovIds, String[] hitIds, String[] selIds, int right, int y) {
        for (int i = 0; i < cols.length; i++) {
            int x = right - (cols.length - i) * 20 + 4, sy = y + 5;
            Anim s = anims.computeIfAbsent(selIds[i], k -> new Anim(0));
            s.to(i == sel ? 1f : 0f);
            float hv = hov(hovIds[i], live && Draw.in(mx, my, x - 2, sy - 2, 18, 18));
            if (live) hit(hitIds[i], x - 2, sy - 2, 18, 18);
            Draw.round(c, x - 2, sy - 2, 18, 18, 6, Draw.a(WHITE, a * Math.max(s.get(), hv * 0.4f)));
            Draw.round(c, x, sy, 14, 14, 5, Draw.a(lighten ? Draw.mix(cols[i], WHITE, 0.30f) : cols[i], a));
        }
    }

    // ---- visual polish (all skipped when Animations is off) ----
    /** Two slow, soft colour glows drifting behind the content. */
    void aurora(DrawContext c, float o, int ac) {
        float t = System.currentTimeMillis() / 1000f;
        int bw = ww / 2, bh = wh / 3, rx = ww - bw - 20, ry = wh - bh - 20;
        Draw.glow(c, wx + 10 + Math.round(rx * (0.5f + 0.5f * (float) Math.sin(t * 0.35f))), wy + 10 + Math.round(ry * (0.5f + 0.5f * (float) Math.cos(t * 0.27f))),
                bw, bh, 16, ac, 0.10f * o);
        Draw.glow(c, wx + 10 + Math.round(rx * (0.5f + 0.5f * (float) Math.cos(t * 0.31f))), wy + 10 + Math.round(ry * (0.5f + 0.5f * (float) Math.sin(t * 0.22f))),
                bw, bh, 16, Draw.mix(ac, 0xFF7C5CFF, 0.6f), 0.08f * o);
    }

    /** Tiny light specks drifting upward through the window. */
    void motes(DrawContext c, float o, int ac) {
        float t = System.currentTimeMillis() / 1000f;
        int col = Draw.mix(ac, WHITE, 0.5f);
        for (int i = 0; i < MOTES; i++) {
            float life = (t * moteS[i] + moteP[i]) % 1f, al = (float) Math.sin(Math.PI * life) * 0.55f * o;
            if (al < 0.05f) continue;
            int sx = wx + 6 + Math.round(moteX[i] * (ww - 12) + (float) Math.sin(t * 0.8f + i) * 4f), sy = wy + wh - 6 - Math.round(life * (wh - 12));
            c.fill(sx, sy, sx + 2, sy + 2, Draw.a(col, al));
        }
    }

    /** A light streak that sweeps across a button now and then. */
    void shine(DrawContext c, int x, int y, int w, int h, float a) {
        if (!MotionsConfig.get().animations) return;
        float p = (System.currentTimeMillis() % 2600) / 1560f; // sweeps for 60% of the cycle, then rests
        if (p > 1f) return;
        int sx = x - 14 + Math.round((w + 28) * p);
        c.enableScissor(x, y, x + w, y + h);
        c.fill(sx, y, sx + 8, y + h, Draw.a(WHITE, 0.22f * a));
        c.fill(sx + 8, y, sx + 12, y + h, Draw.a(WHITE, 0.10f * a));
        c.disableScissor();
    }

    /** A ripple on every click, plus a spray of sparks on the satisfying ones. */
    void clickFx(String id, double mx, double my) {
        MotionsConfig cfg = MotionsConfig.get();
        if (!cfg.animations) return;
        fx.ripple((float) mx, (float) my, cfg.accent);
        if (id.equals("start") || id.equals("livebtn")) fx.burst((float) mx, (float) my, cfg.accent, 14);
        else if (id.equals("pick")) fx.burst((float) mx, (float) my, cfg.accent, 12);
        else if (id.startsWith("tg:") || id.startsWith("chip:") || id.startsWith("dur:")) fx.burst((float) mx, (float) my, cfg.accent, 7);
    }

    // ---- main render ----
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        hitN = 0;
        hoverEntry = null;
        float o = open.get();
        if (closing && o <= 0.02f) { client.setScreen(null); return; }
        MotionsConfig cfg = MotionsConfig.get();
        int ac = cfg.accent;

        // background: the real menu blur (allowed once per frame), or a plain dim if it's off
        boolean blurred = false;
        if (cfg.blur) {
            setBlur(cfg.blurLevel);
            try { renderBackground(c, mx, my, delta); blurred = true; } catch (IllegalStateException ignored) { }
        } else {
            restoreBlur();
        }
        if (!blurred) c.fill(0, 0, width, height, Draw.a(0x99000000, o));

        popup.to(popupOpen ? 1f : 0f);
        float pv = popup.get();
        if (popupOpen && popupCloseAt > 0 && System.currentTimeMillis() >= popupCloseAt) { popupCloseAt = 0; closePopup(); }
        boolean live = !popupOpen;

        ww = Math.min(540, width - 16);
        wh = Math.min(316, height - 16);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2 + Math.round((1f - Draw.easeOutBack(o)) * 18);
        win(c, wx, wy, ww, wh, 10, o, 0.60f);
        if (cfg.animations) { aurora(c, o, ac); motes(c, o, ac); }

        Draw.round(c, wx + 9, wy + 14, 9, 9, 3, Draw.a(ac, o));
        title(c, "Motions Client", wx + 22, wy + 14, o, ac);
        nav(c, mx, my, o, ac, live, wx + 8, wy + 42, SB - 16, 24, 4, TAB_NAMES, tabStacks(), TAB_HOV, TAB_HIT, tabHi, tab);
        profile(c, wx + 10, wy + wh - 38, SB - 20, o);
        vline(c, wx + SB, wy + 1, wy + wh - 1, o);

        page.to(1);
        float pg = page.get(), pa = o * pg;
        int ty = wy + 14 + Math.round((1f - pg) * 10);
        switch (tab) {
            case 0 -> auctionPage(c, mx, my, pa, ac, live, ty);
            case 1 -> livePage(c, mx, my, pa, ac, live, ty);
            case 2 -> layoutPage(c, mx, my, pa, ac, live, ty);
            default -> settingsPage(c, mx, my, pa, ac, live, ty);
        }

        if (pv > 0.01f) popupLayer(c, mx, my, delta, ac, pv);
        else search.setFocused(false);
        fx.draw(c, System.currentTimeMillis(), 1f);
        if (hoverEntry != null && pv > 0.95f) c.drawItemTooltip(textRenderer, hoverEntry.stack, mx, my);
    }

    // ---- auction page ----
    void auctionPage(DrawContext c, int mx, int my, float pa, int ac, boolean live, int ty) {
        int cx = wx + SB + 1, cw = ww - SB - 1;
        int lx = cx + 16, lw = Math.max(170, (cw - 48) / 2), rx = lx + lw + 16, rw = Math.min(AuctionHud.W, cx + cw - 16 - rx);

        title(c, "Create auction", lx, ty, pa, ac);
        statusA.to(System.currentTimeMillis() - statusAt < 3500 ? 1f : 0f);
        float sa = statusA.get();
        Draw.text(c, "Pick an item, set a price and start.", lx, ty + 12, Draw.a(MUTED, pa * (1f - sa)));
        Draw.text(c, status, lx, ty + 12, Draw.a(statusColor, pa * sa));

        // item button
        int iy = ty + 26;
        float hv = hov("itembtn", live && Draw.in(mx, my, lx, iy, lw, 38));
        if (live) hit("item", lx, iy, lw, 38);
        ctl(c, lx, iy, lw, 38, 8, pa, hv, ac);
        Draw.round(c, lx + 6, iy + 6, 26, 26, 6, Draw.a(BLACK, 0.30f * pa));
        Item di = AuctionManager.draftItem;
        if (di != null && pa > 0.5f) {
            float s = 1.2f + 0.12f * hv, size = 16 * s;
            Draw.item(c, AuctionManager.draftStack, lx + 6 + (26 - size) / 2f, iy + 6 + (26 - size) / 2f, s);
        }
        String it = di == null ? "Select item" : AuctionManager.draftStack.getName().getString();
        Draw.bold(c, textRenderer.trimToWidth(it, lw - 64), lx + 40, iy + 8, Draw.a(TXT, pa));
        Draw.text(c, "Click to browse all items", lx + 40, iy + 21, Draw.a(MUTED, pa));
        Draw.text(c, ">", lx + lw - 14, iy + 15, Draw.a(Draw.mix(MUTED, ac, hv), pa));

        // price + quick amounts
        int y = iy + 44;
        Draw.text(c, "Starting price", lx, y, Draw.a(MUTED, pa));
        field(c, mx, my, pa, ac, live, "f:price", lx, y + 10, lw, 20, price, "e.g. 450M");
        int chipY = y + 34, chipW = (lw - 12) / 4;
        for (int i = 0; i < 4; i++) {
            int x = lx + i * (chipW + 4);
            float ch = hov(CHIP_HOV[i], live && Draw.in(mx, my, x, chipY, chipW, 14));
            if (live) hit(CHIP_HIT[i], x, chipY, chipW, 14);
            ctl(c, x, chipY, chipW, 14, 5, pa, ch * 0.7f, ac);
            Draw.centered(c, CHIPS[i], x + chipW / 2, chipY + 3, Draw.a(Draw.mix(MUTED, TXT, ch), pa), false);
        }

        // optional: worth + quantity
        y = chipY + 20;
        int fw = (lw - 6) / 2, qx = lx + fw + 6, qw = lw - fw - 6;
        Draw.text(c, "Worth", lx, y, Draw.a(MUTED, pa));
        Draw.text(c, "Quantity", qx, y, Draw.a(MUTED, pa));
        field(c, mx, my, pa, ac, live, "f:worth", lx, y + 10, fw, 20, worth, "optional");
        field(c, mx, my, pa, ac, live, "f:qty", qx, y + 10, qw, 20, qty, "optional");

        // duration
        y += 36;
        Draw.text(c, "Duration", lx, y, Draw.a(MUTED, pa));
        int sy = y + 10, sw = lw / DUR.length, di2 = 0;
        for (int i = 0; i < DUR.length; i++) if (DUR[i] == AuctionManager.draftDuration) di2 = i;
        durHi.to(di2);
        panel(c, lx, sy, sw * DUR.length, 18, 6, pa);
        sel(c, lx + Math.round(durHi.get() * sw) + 1, sy + 1, sw - 2, 16, 5, pa, ac);
        for (int i = 0; i < DUR.length; i++) {
            int x = lx + i * sw;
            float dh = hov(DUR_HOV[i], live && Draw.in(mx, my, x, sy, sw, 18));
            if (live) hit(DUR_HIT[i], x, sy, sw, 18);
            Draw.centered(c, DUR_L[i], x + sw / 2, sy + 5, Draw.a(Draw.mix(MUTED, TXT, Math.max(dh, i == di2 ? 1f : 0f)), pa), false);
        }

        // start / stop
        int by = sy + 24;
        long pr = AuctionManager.parsePrice(price.getText()), wv = AuctionManager.parseWorth(worth.getText());
        int q = AuctionManager.parseQty(qty.getText());
        boolean running = AuctionManager.state == AuctionManager.State.ACTIVE;
        boolean valid = di != null && pr > 0 && wv >= 0 && q >= 1;
        float bh = hov("start", live && Draw.in(mx, my, lx, by, lw, 22));
        if (live) hit("start", lx, by, lw, 22);
        int bcol = running ? Draw.mix(0xFF4A2020, DANGER, 0.35f * bh)
                : valid ? Draw.mix(ac, WHITE, 0.18f * bh) : Draw.a(WHITE, 0.10f + 0.06f * bh);
        Draw.round(c, lx, by, lw, 22, 7, Draw.a(bcol, pa));
        if (valid && !running) shine(c, lx, by, lw, 22, pa);
        Draw.centered(c, running ? "Stop auction" : "Start auction", lx + lw / 2, by + 7,
                Draw.a(running ? 0xFFFFD0D0 : valid ? 0xFF0B0C0E : MUTED, pa), true);

        // live preview + summary
        if (rw < 150) return;
        Draw.text(c, "Live preview", rx, iy, Draw.a(MUTED, pa));
        AuctionHud.Card k = preview;
        k.stack = AuctionManager.draftStack;
        k.text(di == null ? "No item" : "No bids", "Min " + (pr > 0 ? AuctionManager.fmt(pr) : "-"),
                AuctionManager.time(AuctionManager.draftDuration),
                di == null ? "Choose an item" : AuctionManager.label(AuctionManager.draftStack, Math.max(q, 1))
                        + (wv > 0 ? " \u2022 worth " + AuctionManager.fmt(wv) : ""),
                q > 1 ? "x" + q : "");
        k.prog = 1f; k.glow = 0f; k.flash = 0f; k.time = -1f; k.titleCol = 0; k.minCol = 0;
        AuctionHud.drawCard(c, rx, iy + 12, rw, k, pa, ac);

        int iby = iy + 12 + AuctionHud.H + 10;
        panel(c, rx, iby, rw, 77, 8, pa);
        String[][] rows = {
                {"Item", di == null ? "-" : AuctionManager.draftStack.getName().getString()},
                {"Start price", pr > 0 ? AuctionManager.fmt(pr) : "-"},
                {"Worth", wv > 0 ? AuctionManager.fmt(wv) : "-"},
                {"Quantity", q > 1 ? "x" + q : "1"},
                {"Mode", "Client-side only"}};
        for (int i = 0; i < rows.length; i++) {
            Draw.text(c, rows[i][0], rx + 8, iby + 7 + i * 13, Draw.a(MUTED, pa));
            String v = textRenderer.trimToWidth(rows[i][1], rw - 80);
            Draw.text(c, v, rx + rw - 8 - Draw.width(v), iby + 7 + i * 13, Draw.a(TXT, pa));
        }
    }

    // ---- live tab ----
    void livePage(DrawContext c, int mx, int my, float pa, int ac, boolean live, int ty) {
        int cx = wx + SB + 1, cw = ww - SB - 1, lx = cx + 16, lw = Math.max(170, (cw - 48) / 2);
        int rx = lx + lw + 16, rw = cx + cw - 16 - rx;
        LiveSession.State s = LiveSession.state;
        long now = System.currentTimeMillis();
        title(c, "Live", lx, ty, pa, ac);
        Draw.text(c, "Track your money while you play.", lx, ty + 12, Draw.a(MUTED, pa));

        // banner: says exactly what to do next
        int by = ty + 28, bh = 62;
        if (s == LiveSession.State.WAITING) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / 220.0);
            Draw.round(c, lx - 2, by - 2, lw + 4, bh + 4, 10, Draw.a(ac, pa * (0.10f + 0.20f * p)));
            sel(c, lx, by, lw, bh, 8, pa, ac);
            Draw.bold(c, "Type /bal in chat now", lx + 10, by + 9, Draw.a(Draw.mix(TXT, ac, p * 0.5f), pa));
            Draw.text(c, "Waiting for your balance" + DOTS[(int) (now / 300 % 3)], lx + 10, by + 26, Draw.a(TXT, pa));
            Draw.text(c, "After that, payments count by themselves.", lx + 10, by + 40, Draw.a(MUTED, pa));
        } else {
            panel(c, lx, by, lw, bh, 8, pa);
            if (s == LiveSession.State.IDLE) {
                Draw.bold(c, "Not tracking yet", lx + 10, by + 7, Draw.a(TXT, pa));
                Draw.text(c, "1.  Press Start session", lx + 10, by + 20, Draw.a(MUTED, pa));
                Draw.text(c, "2.  Type ", lx + 10, by + 33, Draw.a(MUTED, pa));
                int chipX = lx + 10 + Draw.width("2.  Type "), chipW = Draw.width("/bal") + 8;
                Draw.round(c, chipX, by + 30, chipW, 14, 5, Draw.a(ac, 0.28f * pa));
                Draw.bold(c, "/bal", chipX + 4, by + 33, Draw.a(ac, pa));
                Draw.text(c, " in chat (once)", chipX + chipW + 2, by + 33, Draw.a(MUTED, pa));
                Draw.text(c, "3.  Payments are then tracked for you", lx + 10, by + 46, Draw.a(MUTED, pa));
            } else {
                Draw.bold(c, "Tracking", lx + 10, by + 8, Draw.a(TXT, pa));
                Draw.text(c, "Counting every payment in chat.", lx + 10, by + 26, Draw.a(MUTED, pa));
                Draw.text(c, "Type /bal any time to re-sync.", lx + 10, by + 40, Draw.a(MUTED, pa));
            }
        }

        // start / cancel / stop
        int btnY = by + bh + 8;
        boolean idle = s == LiveSession.State.IDLE;
        float bh2 = hov("livebtn", live && Draw.in(mx, my, lx, btnY, lw, 24));
        if (live) hit("livebtn", lx, btnY, lw, 24);
        Draw.round(c, lx, btnY, lw, 24, 7, Draw.a(idle ? Draw.mix(ac, WHITE, 0.18f * bh2) : Draw.mix(0xFF4A2020, DANGER, 0.35f * bh2), pa));
        if (idle) shine(c, lx, btnY, lw, 24, pa);
        Draw.centered(c, idle ? "Start session" : s == LiveSession.State.WAITING ? "Cancel" : "Stop session",
                lx + lw / 2, btnY + 8, Draw.a(idle ? 0xFF0B0C0E : 0xFFFFD0D0, pa), true);

        long profit = LiveSession.profit();
        if (s == LiveSession.State.TRACKING) {
            int sy = btnY + 32;
            panel(c, lx, sy, lw, 75, 8, pa);
            String[][] rows = {{"Started at", LiveSession.money(LiveSession.startBal)}, {"Balance now", LiveSession.money(LiveSession.curBal)},
                    {"Profit", LiveSession.signed(profit)}, {"Paid in / out", LiveSession.money(LiveSession.in) + " / " + LiveSession.money(LiveSession.out)},
                    {"Session", AuctionManager.time((now - LiveSession.startMs) / 1000)}};
            for (int i = 0; i < rows.length; i++) {
                int ry = sy + 7 + i * 13, col = i == 2 ? (profit < 0 ? DANGER : GREEN) : TXT;
                Draw.text(c, rows[i][0], lx + 10, ry, Draw.a(MUTED, pa));
                Draw.text(c, rows[i][1], lx + lw - 10 - Draw.width(rows[i][1]), ry, Draw.a(col, pa));
            }
            if (!LiveSession.lastLine.isEmpty() && sy + 94 < wy + wh - 6) // the exact chat line that was counted
                Draw.text(c, textRenderer.trimToWidth("Last: " + LiveSession.signed(LiveSession.lastDelta)
                        + (LiveSession.lastTax > 0 ? " (tax " + LiveSession.money(LiveSession.lastTax) + ")" : "") + "  " + LiveSession.lastLine, lw),
                        lx, sy + 80, Draw.a(MUTED, pa));
        }

        // preview of the pill
        if (rw < LiveHud.W) return;
        Draw.text(c, "Preview", rx, by, Draw.a(MUTED, pa));
        livePreview.set(s == LiveSession.State.TRACKING ? "LIVE: " + LiveSession.signed(profit)
                : s == LiveSession.State.WAITING ? "LIVE: type /bal" : "LIVE: +$87.9m");
        int pill = s == LiveSession.State.WAITING ? TXT : profit < 0 ? DANGER : GREEN;
        LiveHud.drawPill(c, rx, by + 12, livePreview, pill, pa, ac, 0f, pill);

        // Donut SMP's 2% payment tax: only counted while you're on donutsmp.net
        if (rw < 150) return;
        MotionsConfig cfg = MotionsConfig.get();
        int ty2 = by + 42;
        panel(c, rx, ty2, rw, 76, 8, pa);
        Draw.bold(c, "Donut SMP tax (2%)", rx + 8, ty2 + 7, Draw.a(TXT, pa));
        Draw.text(c, LiveSession.onDonut ? "Donut SMP detected" : "Not on Donut SMP, no tax", rx + 8, ty2 + 20, Draw.a(LiveSession.onDonut ? GREEN : MUTED, pa));
        int sw3 = (rw - 16 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = rx + 8 + i * (sw3 + 4), bY = ty2 + 34;
            float hv = hov(TAX_HOV[i], live && Draw.in(mx, my, bx, bY, sw3, 18));
            if (live) hit(TAX_HIT[i], bx, bY, sw3, 18);
            if (cfg.taxMode == i) sel(c, bx, bY, sw3, 18, 6, pa, ac); else ctl(c, bx, bY, sw3, 18, 6, pa, hv, ac);
            Draw.centered(c, TAX_L[i], bx + sw3 / 2, bY + 5, Draw.a(cfg.taxMode == i ? TXT : Draw.mix(MUTED, TXT, hv), pa), false);
        }
        Draw.text(c, textRenderer.trimToWidth(TAX_INFO[cfg.taxMode], rw - 16), rx + 8, ty2 + 57, Draw.a(MUTED, pa));
    }

    // ---- layout tab ----
    ItemStack sampleStack() {
        if (AuctionManager.draftItem != null) return AuctionManager.draftStack;
        if (SAMPLE == null) SAMPLE = new ItemStack(Items.DIAMOND);
        return SAMPLE;
    }

    static float frac(double mx, int x, int w) { return (float) Math.max(0, Math.min(1, (mx - x) / w)); }

    static String x2(float v) {
        int r = Math.round(v * 100);
        return (r / 100) + "." + (r % 100 < 10 ? "0" : "") + (r % 100) + "x";
    }

    /** Picks the element under the cursor (the LIVE pill sits on top of the card) and starts dragging it. */
    void beginMove(double mx, double my) {
        MotionsConfig cfg = MotionsConfig.get();
        float curX = (float) ((mx - miniX) / miniK), curY = (float) ((my - miniY) / miniK);
        drag = 3;
        for (int e = 1; e >= 0; e--) {
            float sc = cfg.scale(e), rx = Layout.posX(e, width), ry = Layout.posY(e, height);
            if (curX >= rx && curX < rx + Layout.w(e) * sc && curY >= ry && curY < ry + Layout.h(e) * sc) {
                layoutSel = e; grabX = curX - rx; grabY = curY - ry;
                dragTo(mx, my);
                return;
            }
        }
        grabX = Layout.w(layoutSel) * cfg.scale(layoutSel) / 2f; // empty space: carry the selected element by its centre
        grabY = Layout.h(layoutSel) * cfg.scale(layoutSel) / 2f;
        dragTo(mx, my);
    }

    void dragTo(double mx, double my) {
        MotionsConfig cfg = MotionsConfig.get();
        switch (drag) {
            case 1 -> cfg.animSpeed = Math.round((0.5f + 1.5f * frac(mx, speedX, speedW)) * 20f) / 20f;
            case 2 -> cfg.setScale(layoutSel, Math.round((0.5f + frac(mx, scaleX, scaleW)) * 20f) / 20f);
            case 4 -> cfg.blurLevel = 1 + Math.round(frac(mx, blurX, blurW) * 9f);
            case 3 -> {
                float fx = Layout.fracX(layoutSel, width, (float) ((mx - miniX) / miniK) - grabX);
                float fy = Layout.fracY(layoutSel, height, (float) ((my - miniY) / miniK) - grabY);
                if (fx < 0.02f) fx = 0f; else if (fx > 0.98f) fx = 1f;
                if (fy < 0.02f) fy = 0f; else if (fy > 0.98f) fy = 1f;
                if (snapX = Math.abs(fx - 0.5f) < 0.03f) fx = 0.5f;
                if (snapY = Math.abs(fy - 0.5f) < 0.03f) fy = 0.5f;
                cfg.setPos(layoutSel, fx, fy);
            }
            default -> { }
        }
    }

    void layoutPage(DrawContext c, int mx, int my, float pa, int ac, boolean live, int ty) {
        MotionsConfig cfg = MotionsConfig.get();
        int e = layoutSel, cx = wx + SB + 1, cw = ww - SB - 1, lx = cx + 16;
        title(c, "HUD layout", lx, ty, pa, ac);
        Draw.text(c, "Pick an element, then drag it in the preview.", lx, ty + 12, Draw.a(MUTED, pa));
        for (int i = 0; i < 2; i++) { // which element to edit
            int bx = lx + i * 90, sy = ty + 26;
            float hv = hov(LSEL_HOV[i], live && Draw.in(mx, my, bx, sy, 86, 16));
            if (live) hit(LSEL_HIT[i], bx, sy, 86, 16);
            if (i == e) sel(c, bx, sy, 86, 16, 6, pa, ac); else ctl(c, bx, sy, 86, 16, 6, pa, hv, ac);
            Draw.centered(c, Layout.NAMES[i], bx + 43, sy + 4, Draw.a(i == e ? TXT : Draw.mix(MUTED, TXT, hv), pa), false);
        }

        // mini version of your screen
        float aspect = (float) width / Math.max(1, height);
        int mh = Math.max(70, Math.min(110, wh - 150)), mw = Math.round(mh * aspect);
        int maxW = Math.max(110, cw - 32 - 150);
        if (mw > maxW) { mw = maxW; mh = Math.round(mw / aspect); }
        miniX = lx; miniY = ty + 50; miniK = (float) mw / Math.max(1, width);
        if (live) hit("mini", miniX, miniY, mw, mh);
        Draw.border(c, miniX, miniY, mw, mh, 6, Draw.a(BLACK, 0.35f * pa), Draw.a(WHITE, 0.12f * pa));
        boolean sx = drag == 3 && snapX, sy2 = drag == 3 && snapY;
        c.fill(miniX + mw / 2, miniY + 3, miniX + mw / 2 + 1, miniY + mh - 3, Draw.a(sx ? ac : WHITE, pa * (sx ? 0.7f : 0.07f)));
        c.fill(miniX + 3, miniY + mh / 2, miniX + mw - 3, miniY + mh / 2 + 1, Draw.a(sy2 ? ac : WHITE, pa * (sy2 ? 0.7f : 0.07f)));
        int hbw = Math.round(182 * miniK), hbh = Math.max(2, Math.round(22 * miniK));
        Draw.round(c, miniX + (mw - hbw) / 2, miniY + mh - hbh - 3, hbw, hbh, 2, Draw.a(WHITE, 0.08f * pa)); // hotbar hint

        AuctionHud.Card k = preview;
        k.stack = sampleStack();
        k.text("No bids", "Min 450M", "0:59", "Preview", "");
        k.prog = 1f; k.glow = 0f; k.flash = 0f; k.time = -1f; k.titleCol = 0; k.minCol = 0;
        livePreview.set("LIVE: +$87.9m");
        var m = c.getMatrices();
        for (int el = 0; el < 2; el++) { // both elements, the selected one highlighted
            float ks = miniK * cfg.scale(el);
            int ew = Math.max(4, Math.round(Layout.w(el) * ks)), eh = Math.max(4, Math.round(Layout.h(el) * ks));
            int ex = miniX + Math.round(Layout.posX(el, width) * miniK), ey = miniY + Math.round(Layout.posY(el, height) * miniK);
            float hv = hov(MINI_HOV[el], (drag == 3 && el == e) || (live && Draw.in(mx, my, ex, ey, ew, eh)));
            Draw.round(c, ex - 2, ey - 2, ew + 4, eh + 4, 5, Draw.a(ac, pa * ((el == e ? 0.20f : 0.05f) + 0.22f * hv)));
            m.pushMatrix();
            m.translate(ex, ey);
            m.scale(ks, ks);
            if (el == Layout.AUCTION) AuctionHud.drawCard(c, 0, 0, AuctionHud.W, k, pa, ac);
            else LiveHud.drawPill(c, 0, 0, livePreview, GREEN, pa, ac, 0f, GREEN);
            m.popMatrix();
        }

        float sc = cfg.scale(e);
        Draw.text(c, Layout.NAMES[e] + ": " + Math.round(cfg.x(e) * 100) + "% / " + Math.round(cfg.y(e) * 100) + "%    Scale " + x2(sc),
                lx, miniY + mh + 8, Draw.a(TXT, pa));
        Draw.text(c, "Saved automatically. Snaps to the centre.", lx, miniY + mh + 20, Draw.a(MUTED, pa));

        // controls on the right (apply to the selected element)
        int rx0 = lx + mw + 16, rw = cx + cw - 16 - rx0;
        if (rw < 90) return;
        int y = miniY;
        Draw.text(c, "Scale", rx0, y, Draw.a(MUTED, pa));
        String sv = x2(sc);
        Draw.text(c, sv, rx0 + rw - Draw.width(sv), y, Draw.a(TXT, pa));
        scaleX = rx0; scaleW = rw;
        slider(c, mx, my, pa, ac, live, "lscale", rx0, y + 14, rw, sc - 0.5f, drag == 2, x2(sc));
        y += 34;

        Draw.text(c, "Snap to", rx0, y, Draw.a(MUTED, pa));
        y += 12;
        int aw = (rw - 8) / 3, ah = 18;
        for (int i = 0; i < 6; i++) {
            int bx = rx0 + (i % 3) * (aw + 4), by = y + (i / 3) * (ah + 4);
            float av = hov(ANCH_HOV[i], live && Draw.in(mx, my, bx, by, aw, ah));
            if (live) hit(ANCH_HIT[i], bx, by, aw, ah);
            ctl(c, bx, by, aw, ah, 5, pa, av, ac);
            Draw.centered(c, ANCHORS[i], bx + aw / 2, by + 5, Draw.a(Draw.mix(MUTED, TXT, av), pa), false);
        }
        y += 2 * (ah + 4) + 4;

        float rv = hov("lreset", live && Draw.in(mx, my, rx0, y, rw, 20));
        if (live) hit("lreset", rx0, y, rw, 20);
        ctl(c, rx0, y, rw, 20, 6, pa, rv * 0.8f, DANGER);
        Draw.centered(c, "Reset " + Layout.NAMES[e].toLowerCase(Locale.ROOT), rx0 + rw / 2, y + 6, Draw.a(Draw.mix(MUTED, TXT, rv), pa), false);
    }

    // ---- settings page ----
    boolean tgOn(MotionsConfig cfg, int i) {
        return switch (i) {
            case 0 -> cfg.notifications;
            case 1 -> cfg.showAuctionHud;
            case 2 -> cfg.sounds;
            case 3 -> cfg.animations;
            case 4 -> cfg.blur;
            case 5 -> cfg.copyWinner;
            default -> cfg.welcome;
        };
    }

    void settingsPage(DrawContext c, int mx, int my, float pa, int ac, boolean live, int ty) {
        MotionsConfig cfg = MotionsConfig.get();
        int cx = wx + SB + 1, cw = ww - SB - 1, lx = cx + 16, rw = cw - 32, rEnd = lx + rw;
        title(c, "Settings", lx, ty, pa, ac);
        Draw.text(c, "Changes are saved automatically.", lx, ty + 12, Draw.a(MUTED, pa));

        // toggles, two per row
        int y = ty + 30, tw = (rw - 8) / 2;
        for (int i = 0; i < TG_ID.length; i++) {
            int slot = i < 5 ? i : i + 1; // slot 5 holds the blur-level slider
            int x = lx + (slot % 2) * (tw + 8), yy = y + (slot / 2) * 26;
            float hv = hov(TG_HOV[i], live && Draw.in(mx, my, x, yy, tw, 24));
            if (live) hit(TG_HIT[i], x, yy, tw, 24);
            ctl(c, x, yy, tw, 24, 7, pa, hv * 0.6f, ac);
            Draw.text(c, TG_LABEL[i], x + 10, yy + 8, Draw.a(TXT, pa));
            boolean on = tgOn(cfg, i);
            Anim a = anims.computeIfAbsent(TG_ANIM[i], k -> new Anim(on ? 1 : 0));
            a.to(on ? 1f : 0f);
            float t = a.get();
            int kx = x + tw - 38, kw = 10 + Math.round(8f * t * (1f - t)); // the knob stretches while it slides
            Draw.round(c, kx, yy + 5, 28, 14, 7, Draw.a(Draw.mix(0xFF2B3038, ac, t), pa));
            Draw.round(c, kx + 2 + Math.round(t * (24 - kw)), yy + 7, kw, 10, 5, Draw.a(Draw.mix(0xFF8B94A0, WHITE, t), pa));
        }
        // blur strength fills the sixth tile; the toggles after it continue below
        int bx = lx + tw + 8, by = y + 2 * 26;
        panel(c, bx, by, tw, 24, 7, pa);
        Draw.text(c, "Blur level", bx + 10, by + 8, Draw.a(TXT, pa));
        blurX = bx + 70; blurW = Math.max(30, tw - 70 - 28);
        slider(c, mx, my, pa, ac, live, "blurs", blurX, by + 10, blurW, (cfg.blurLevel - 1) / 9f, drag == 4, Integer.toString(cfg.blurLevel));
        Draw.text(c, Integer.toString(cfg.blurLevel), bx + tw - 18, by + 8, Draw.a(TXT, pa));
        y += 4 * 26;

        // animation speed
        panel(c, lx, y, rw, 24, 7, pa);
        Draw.text(c, "Animation speed", lx + 10, y + 8, Draw.a(TXT, pa));
        speedW = 90; speedX = rEnd - 10 - speedW - 34;
        slider(c, mx, my, pa, ac, live, "slider", speedX, y + 10, speedW, (cfg.animSpeed - 0.5f) / 1.5f, drag == 1, x2(cfg.animSpeed));
        Draw.text(c, x2(cfg.animSpeed), rEnd - 40, y + 8, Draw.a(TXT, pa));
        y += 26;

        panel(c, lx, y, rw, 24, 7, pa);
        Draw.text(c, "Accent colour", lx + 10, y + 8, Draw.a(TXT, pa));
        swatches(c, mx, my, pa, live, MotionsConfig.ACCENTS, false, indexOf(MotionsConfig.ACCENTS, cfg.accent), ACC_HOV, ACC_HIT, ACC_SEL, rEnd - 10, y);
        y += 26;

        panel(c, lx, y, rw, 24, 7, pa);
        Draw.text(c, "Glass colour", lx + 10, y + 8, Draw.a(TXT, pa));
        swatches(c, mx, my, pa, live, MotionsConfig.GLASS, true, cfg.glass, GLS_HOV, GLS_HIT, GLS_SEL, rEnd - 10, y);
    }

    static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    // ---- item picker popup ----
    void closePopup() { if (popupOpen) Sfx.close(); popupOpen = false; search.setFocused(false); }

    void popupLayer(DrawContext c, int mx, int my, float delta, int ac, float pv) {
        c.createNewRootLayer(); // draw the popup strictly above everything drawn so far
        c.fill(0, 0, width, height, Draw.a(0x99000000, pv));
        pw = Math.min(510, width - 20);
        ph = Math.min(310, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + Math.round((1f - pv) * 14);
        win(c, px, py, pw, ph, 10, pv, 0.70f);
        boolean act = popupOpen && pv > 0.5f;
        int sb = 104;

        sel(c, px + 8, py + 14, sb - 16, 22, 6, pv, ac); // the only tab
        if (pv > 0.6f) Draw.item(c, chest(), px + 14, py + 19, 0.75f);
        Draw.text(c, "Items", px + 32, py + 21, Draw.a(TXT, pv));
        profile(c, px + 10, py + ph - 34, sb - 20, pv);
        vline(c, px + sb, py + 1, py + ph - 1, pv);

        int bx = px + sb + 11, by = py + 10, bw = pw - sb - 11 - 10 - 24;
        field(c, mx, my, pv, ac, act, "f:search", bx, by, bw, 22, search, "Search items...");
        int cxb = px + pw - 28;
        float ch = hov("pclose", act && Draw.in(mx, my, cxb, by + 2, 18, 18));
        if (act) hit("pclose", cxb, by + 2, 18, 18);
        ctl(c, cxb, by + 2, 18, 18, 6, pv, ch, DANGER);
        Draw.centered(c, "x", cxb + 9, by + 7, Draw.a(Draw.mix(MUTED, DANGER, ch), pv), true);

        // item grid: only the visible rows are drawn
        gx = px + sb + 11;
        gy = by + 32;
        gw = pw - sb - 11 - 16;
        gh = py + ph - 10 - gy;
        cols = Math.max(1, gw / cell);
        gx += (gw - cols * cell) / 2;
        int rows = (shown.size() + cols - 1) / cols;
        float contentH = rows * cell;
        maxScroll = Math.max(0, contentH - gh);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll.to(scrollTarget);
        scrollCur = scroll.get();
        float gf = gridFade.get(), a = pv * gf;

        int hoverIdx = act ? entryAt(mx, my) : -1;
        if (hoverIdx != lastHoverIdx) { if (hoverIdx >= 0) Sfx.hover(); lastHoverIdx = hoverIdx; }

        if (shown.isEmpty()) {
            Draw.centered(c, "No items found", gx + gw / 2, gy + 30, Draw.a(MUTED, a), false);
        } else {
            c.enableScissor(gx - 2, gy, gx + cols * cell + 2, gy + gh);
            int first = Math.max(0, (int) Math.floor(scrollCur / cell));
            int last = Math.min(rows - 1, (int) Math.ceil((scrollCur + gh) / (float) cell));
            for (int idx = first * cols; idx < Math.min(shown.size(), (last + 1) * cols); idx++) {
                Entry e = shown.get(idx);
                float ig = Draw.clamp01(gf * 1.7f - Math.min(idx / cols - first, 9) * 0.09f), ia = pv * ig; // rows ripple in one after another
                int ix = gx + (idx % cols) * cell, iy = gy + Math.round((idx / cols) * cell - scrollCur + (1f - ig) * 10);
                boolean hv = idx == hoverIdx;
                if (hv) hoverEntry = e;
                Anim ha = itemHover.get(e);
                if (ha == null && hv) itemHover.put(e, ha = new Anim(0));
                float h = 0;
                if (ha != null) {
                    ha.to(hv ? 1f : 0f);
                    h = ha.get();
                    if (!hv && h == 0f) itemHover.remove(e);
                }
                if (e.item == AuctionManager.draftItem) sel(c, ix + 1, iy + 1, cell - 2, cell - 2, 6, ia, ac);
                else if (h > 0.01f) ctl(c, ix + 1, iy + 1, cell - 2, cell - 2, 6, ia * h, h, ac);
                if (ig > 0.4f) {
                    float s = 1f + 0.22f * h, size = 16 * s;
                    Draw.item(c, e.stack, ix + (cell - size) / 2f, iy + (cell - size) / 2f, s);
                }
            }
            c.disableScissor();
        }
        if (maxScroll > 0) {
            int th = Math.max(16, (int) (gh * (gh / contentH)));
            int sbx = px + pw - 14;
            Draw.round(c, sbx, gy, 4, gh, 2, Draw.a(BLACK, 0.30f * pv));
            Draw.round(c, sbx, gy + (int) ((scrollCur / maxScroll) * (gh - th)), 4, th, 2, Draw.a(Draw.mix(0x66FFFFFF, ac, 0.6f), pv));
        }
    }

    int entryAt(double mx, double my) {
        if (mx < gx || mx >= gx + cols * cell || my < gy || my >= gy + gh) return -1;
        int i = (int) Math.floor((my - gy + scrollCur) / cell) * cols + (int) ((mx - gx) / cell);
        return i >= 0 && i < shown.size() ? i : -1;
    }

    // ---- input ----
    void focus(TextFieldWidget f, Click ck) {
        for (TextFieldWidget t : mainFields) t.setFocused(t == f);
        search.setFocused(f == search);
        if (ck != null) f.mouseClicked(ck, false);
    }

    void unfocusAll() {
        for (TextFieldWidget t : mainFields) t.setFocused(false);
    }

    void flip(String name) {
        MotionsConfig cfg = MotionsConfig.get();
        boolean v;
        switch (name) {
            case "notif" -> v = cfg.notifications = !cfg.notifications;
            case "hud" -> v = cfg.showAuctionHud = !cfg.showAuctionHud;
            case "sounds" -> v = cfg.sounds = !cfg.sounds;
            case "anim" -> v = cfg.animations = !cfg.animations;
            case "blur" -> v = cfg.blur = !cfg.blur;
            case "copy" -> v = cfg.copyWinner = !cfg.copyWinner;
            case "welcome" -> v = cfg.welcome = !cfg.welcome;
            default -> { return; }
        }
        Sfx.toggle(v);
        MotionsConfig.save();
    }

    void startOrStop() {
        if (AuctionManager.state == AuctionManager.State.ACTIVE) {
            AuctionManager.stop();
            setStatus("Auction stopped.", MUTED);
            return;
        }
        String err = AuctionManager.start(AuctionManager.draftItem, AuctionManager.parsePrice(price.getText()),
                AuctionManager.draftDuration, worth.getText(), qty.getText());
        if (err != null) { setStatus(err, DANGER); Sfx.error(); }
        else close(); // the card pops onto your screen as the menu closes
    }

    void click(String id, Click ck) {
        MotionsConfig cfg = MotionsConfig.get();
        double mx = ck == null ? 0 : ck.x(), my = ck == null ? 0 : ck.y();
        int colon = id.indexOf(':');
        String kind = colon < 0 ? id : id.substring(0, colon), arg = colon < 0 ? "" : id.substring(colon + 1);
        switch (kind) {
            case "tab" -> {
                int t = Integer.parseInt(arg);
                if (t != tab) { tab = t; page.snap(0); unfocusAll(); }
            }
            case "item" -> { popupOpen = true; Sfx.open(); unfocusAll(); search.setFocused(true); }
            case "chip" -> price.setText(CHIPS[Integer.parseInt(arg)]);
            case "dur" -> AuctionManager.draftDuration = DUR[Integer.parseInt(arg)];
            case "f" -> focus(switch (arg) {
                case "price" -> price;
                case "worth" -> worth;
                case "qty" -> qty;
                default -> search;
            }, ck);
            case "start" -> startOrStop();
            case "tg" -> flip(arg);
            case "acc" -> { cfg.accent = MotionsConfig.ACCENTS[Integer.parseInt(arg)]; MotionsConfig.save(); }
            case "gls" -> { cfg.glass = Integer.parseInt(arg); MotionsConfig.save(); }
            case "slider" -> { drag = 1; dragTo(mx, my); }
            case "lscale" -> { drag = 2; dragTo(mx, my); }
            case "blurs" -> { drag = 4; dragTo(mx, my); }
            case "mini" -> beginMove(mx, my);
            case "anch" -> {
                int i = Integer.parseInt(arg);
                cfg.setPos(layoutSel, (i % 3) * 0.5f, i < 3 ? 0f : 1f);
                MotionsConfig.save();
            }
            case "lreset" -> { cfg.resetPlace(layoutSel); MotionsConfig.save(); }
            case "lsel" -> layoutSel = Integer.parseInt(arg);
            case "tax" -> { cfg.taxMode = Integer.parseInt(arg); MotionsConfig.save(); }
            case "livebtn" -> { if (LiveSession.state == LiveSession.State.IDLE) LiveSession.start(); else LiveSession.stop(); }
            case "pclose" -> closePopup();
            default -> { }
        }
    }

    @Override
    public boolean mouseClicked(Click ck, boolean doubled) {
        double mx = ck.x(), my = ck.y();
        if (ck.button() != 0 || closing) return true;
        if (popupOpen && !Draw.in(mx, my, px, py, pw, ph)) { closePopup(); return true; }
        Hit h = hitAt(mx, my);
        if (h != null) {
            if (!h.id.startsWith("tg:")) Sfx.click();
            clickFx(h.id, mx, my);
            click(h.id, ck);
            return true;
        }
        if (popupOpen) {
            int idx = entryAt(mx, my);
            if (idx >= 0) {
                AuctionManager.setDraft(shown.get(idx).item);
                Sfx.select();
                clickFx("pick", mx, my);
                popupCloseAt = System.currentTimeMillis() + 240; // let the selection highlight show briefly
            }
        } else {
            unfocusAll();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click ck, double dx, double dy) {
        if (drag == 0) return false;
        dragTo(ck.x(), ck.y());
        return true;
    }

    @Override
    public boolean mouseReleased(Click ck) {
        if (drag != 0) { drag = 0; MotionsConfig.save(); }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (!popupOpen) return false;
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) vAmt * 34f));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            if (popupOpen) closePopup(); else close();
            return true;
        }
        if (popupOpen) { search.keyPressed(input); return true; }
        for (TextFieldWidget f : mainFields) if (f.keyPressed(input)) return true;
        if (input.isEnter() && price.isFocused()) { startOrStop(); return true; }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (popupOpen) return search.charTyped(input);
        for (TextFieldWidget f : mainFields) if (f.charTyped(input)) return true;
        return false;
    }
}
