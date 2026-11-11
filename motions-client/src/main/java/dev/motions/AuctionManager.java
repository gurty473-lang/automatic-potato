package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Locale;

/**
 * Fully client-side auction tracker. Nothing is sent to the server and no item is needed:
 * it only shows a local card with a countdown, and can pick up bids from chat messages.
 */
public final class AuctionManager {
    public enum State { IDLE, ACTIVE, ENDED }

    public static final long WINNER_SHOW_MS = 8000; // how long the winner card stays up
    private static final long WINNER_MSG_EXTRA_MS = 5000; // the action-bar winner message stays up this much longer

    public static State state = State.IDLE;
    public static ItemStack stack = ItemStack.EMPTY;
    public static long minPrice, currentBid = -1;
    public static long worth;      // optional: what the item is really worth (0 = not set)
    public static int qty = 1;     // optional: how many of the item (1 = not set)
    public static int durationSec;
    public static long startMs, endMs, endedAtMs, bidPulseMs;
    public static String currentBidder; // who made the leading payment, if the message named them

    // Draft values edited in the UI
    public static Item draftItem = null;
    public static ItemStack draftStack = ItemStack.EMPTY;
    public static String draftPrice = "", draftWorth = "", draftQty = "";
    public static int draftDuration = 300;

    public static void setDraft(Item it) {
        draftItem = it;
        draftStack = it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    public static long parsePrice(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (s.isEmpty()) return -1;
        double mult = 1;
        char l = s.charAt(s.length() - 1);
        switch (l) {
            case 'k' -> mult = 1e3;
            case 'm' -> mult = 1e6;
            case 'b' -> mult = 1e9;
            case 't' -> mult = 1e12;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s) * mult;
            return v >= 0 && v < 9e18 ? (long) v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String fmt(long v) {
        if (v >= 1_000_000_000_000L) return trim(v / 1e12) + "T";
        if (v >= 1_000_000_000L) return trim(v / 1e9) + "B";
        if (v >= 1_000_000L) return trim(v / 1e6) + "M";
        if (v >= 1_000L) return trim(v / 1e3) + "K";
        return String.valueOf(v);
    }

    private static String trim(double d) {
        double r = Math.round(d * 10) / 10.0;
        long w = (long) r;
        return r == w ? Long.toString(w) : Double.toString(r);
    }

    private static String two(long v) { return v < 10 ? "0" + v : Long.toString(v); }

    public static String time(long sec) {
        long m = sec / 60, s = sec % 60;
        if (sec >= 3600) return (sec / 3600) + ":" + two((sec / 60) % 60) + ":" + two(s);
        return m + ":" + two(s);
    }

    /** Empty = not set (0). Otherwise a price like 1.5B; -1 if it can't be read. */
    public static long parseWorth(String s) { return s == null || s.isBlank() ? 0 : parsePrice(s); }

    /** Empty = 1. Otherwise a whole number 1..99999; -1 if it can't be read. */
    public static int parseQty(String s) {
        if (s == null || s.isBlank()) return 1;
        try {
            int v = Integer.parseInt(s.trim());
            return v >= 1 && v <= 99999 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String label(ItemStack s, int q) {
        return s.getName().getString() + (q > 1 ? " x" + q : "");
    }

    /** Starts a local auction. Worth and quantity are optional. Returns an error message, or null on success. */
    public static String start(Item item, long price, int dur, String worthText, String qtyText) {
        if (item == null) return "Select an item first.";
        if (price <= 0) return "Enter a valid price (e.g. 450M).";
        long w = parseWorth(worthText);
        if (w < 0) return "Worth must be a number like 1B, or leave it empty.";
        int q = parseQty(qtyText);
        if (q < 0) return "Quantity must be a whole number, or leave it empty.";
        if (state == State.ACTIVE) return "An auction is already running.";
        long now = System.currentTimeMillis();
        stack = new ItemStack(item);
        minPrice = price;
        worth = w;
        qty = q;
        durationSec = dur;
        currentBid = -1;
        currentBidder = null;
        startMs = now;
        endMs = now + dur * 1000L;
        bidPulseMs = now; // start glow
        state = State.ACTIVE;
        Sfx.start();
        Notice.show("Auction started: " + label(stack, q) + " (min " + fmt(minPrice) + ")");
        return null;
    }

    public static void stop() {
        if (state == State.ACTIVE) {
            state = State.IDLE;
            Notice.show("Auction stopped.");
        }
    }

    public static void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;
        long now = System.currentTimeMillis();
        if (state == State.ACTIVE && now >= endMs) {
            state = State.ENDED;
            endedAtMs = now;
            bidPulseMs = now;
            Sfx.end();
            if (currentBid < 0) {
                Notice.show("Auction ended. No winner.");
            } else {
                boolean copied = copyWinner(mc);
                Notice.showFor("Winner: " + (currentBidder != null ? currentBidder : "unknown") + " (" + fmt(currentBid) + ")"
                        + (copied ? " \u00a77- copied" : ""), WINNER_MSG_EXTRA_MS);
            }
        } else if (state == State.ENDED && now - endedAtMs > WINNER_SHOW_MS) {
            state = State.IDLE;
        }
    }

    /** Reads payments from server messages while an auction is active. Only the highest payment leads. */
    public static void onMessage(String msg) {
        if (state != State.ACTIVE || !PaymentParser.parse(msg)) return;
        String user = PaymentParser.name;
        long v = PaymentParser.amount;
        String who = user == null ? "Someone" : user;
        AuctionHud.pushPayment("+" + fmt(v) + (user != null ? "  " + user : "")); // little green amount
        Sfx.bid();
        if (v >= minPrice && v > currentBid) {
            currentBid = v;
            currentBidder = user;
            bidPulseMs = System.currentTimeMillis();
            Notice.show(who + " is leading with " + fmt(v));
        } else {
            Notice.show(who + " paid " + fmt(v) + (v < minPrice ? " (below min)" : ""));
        }
    }

    /** Name to type in commands: Bedrock (Floodgate) names keep their "." and use "_" instead of spaces. */
    public static String commandName(String name) {
        return name.startsWith(".") ? name.replace(' ', '_') : name;
    }

    /** Puts the winner's name on the clipboard so it can be pasted into /order, /tpa, etc. */
    private static boolean copyWinner(MinecraftClient mc) {
        if (!MotionsConfig.get().copyWinner || currentBidder == null || mc.keyboard == null) return false;
        mc.keyboard.setClipboard(commandName(currentBidder));
        return true;
    }
}
