package dev.motions;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks your money from the moment you start a session. The first /bal reply sets your starting balance; after that
 * every "X paid you" and "You paid X" in chat is added or taken off automatically. Typing /bal again re-syncs the
 * balance if you ever want to. Nothing is ever sent to the server, and nothing is read unless a session is running.
 */
public final class LiveSession {
    private LiveSession() {}

    public enum State { IDLE, WAITING, TRACKING }

    public static State state = State.IDLE;
    public static long startBal, curBal, startMs, changedMs, in, out, lastDelta, lastTax;
    public static boolean onDonut; // joined donutsmp.net (any subdomain): the 2% payment tax applies
    private static String lastAddr = "";
    private static long leftAt;
    public static String lastLine = ""; // the chat line behind the last change, shown in the Live tab so you can see what was read

    // "Balance: $87,992,910", "Your balance is 87.99M", "Money: $5,000", "You have $1.5B"
    private static final Pattern KEY = Pattern.compile("\\b(?:bal(?:ance)?|money|wallet|purse)\\b|you have \\$", Pattern.CASE_INSENSITIVE);

    /** Begins a session: the next /bal reply becomes the starting balance. */
    public static void start() {
        state = State.WAITING;
        Notice.show("Type /bal in chat to start tracking.");
    }

    public static void stop() { state = State.IDLE; }

    public static long profit() { return curBal - startBal; }

    /** Called for game messages, but does nothing unless a session is running. */
    public static void onMessage(String msg, boolean actionBar) {
        if (state == State.IDLE) return;
        long now = System.currentTimeMillis();
        if (state == State.TRACKING && !actionBar) {
            long d = PaymentParser.signed(msg); // + paid you, - you paid
            if (d != 0) {
                long tax = tax(d), net = d - tax; // the 2% Donut SMP tax makes your payments cost more / your income less
                curBal += net;
                if (net > 0) in += net; else out -= net;
                changedMs = now;
                lastDelta = net;
                lastTax = tax;
                lastLine = msg.length() > 90 ? msg.substring(0, 90) : msg;
                LiveHud.pushDelta(net);
                if (net > 0) Sfx.bid(); else Sfx.click();
                return;
            }
        }
        long b = parseBalance(msg);
        if (b < 0) return;
        if (state == State.WAITING) {
            startBal = curBal = b;
            in = out = 0;
            lastTax = 0;
            lastLine = "";
            startMs = changedMs = now;
            state = State.TRACKING;
            Sfx.start();
            Notice.show("Tracking started at " + money(b) + ". Payments are counted automatically.");
        } else if (b != curBal) { // a later /bal just corrects the balance
            lastDelta = b - curBal;
            lastTax = 0;
            lastLine = "balance re-synced";
            curBal = b;
            changedMs = now;
        }
    }

    /** The 2% Donut SMP tax on a payment d (+ received, - sent) as a positive amount; 0 when it doesn't apply. */
    static long tax(long d) {
        int mode = MotionsConfig.get().taxMode;
        if (!onDonut || mode == 0 || (mode == 1 && d >= 0) || (mode == 2 && d <= 0)) return 0;
        return Math.round(Math.abs(d) * 0.02);
    }

    /** Called on every join: notes whether this is Donut SMP, and ends the session if it's another server (or you were away a while). */
    public static void onJoin(ClientPlayNetworkHandler handler) {
        ServerInfo info = handler.getServerInfo();
        String addr = info == null || info.address == null ? "" : info.address;
        onDonut = isDonut(addr) || (info != null && info.name != null && info.name.toLowerCase(Locale.ROOT).contains("donut"));
        if (!addr.equals(lastAddr) || System.currentTimeMillis() - leftAt > 120_000) state = State.IDLE;
        lastAddr = addr;
    }

    public static void onLeave() { leftAt = System.currentTimeMillis(); }

    /** "donutsmp.net", "play.donutsmp.net:25565" ... */
    static boolean isDonut(String address) {
        String host = address.toLowerCase(Locale.ROOT).trim();
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("donutsmp.net") || host.endsWith(".donutsmp.net");
    }

    static long parseBalance(String msg) {
        if (msg == null || msg.length() > 200) return -1;
        Matcher k = KEY.matcher(msg);
        if (!k.find()) return -1;
        // "Balance: $5" is a server label, but "Steve: my balance is 5m" is a player talking - ignore that
        int chat = PaymentParser.chatPrefixEnd(msg);
        if (chat > 0 && k.start() >= chat) return -1;
        long v = PaymentParser.amountIn(msg, k.end());
        return v >= 0 ? v : PaymentParser.amountIn(msg, 0); // amount before the word, e.g. "$5M is your balance"
    }

    /** 87,992,910 becomes "87.9m": cut (not rounded) to one decimal. Negative values keep a "-". */
    public static String fmt(long v) {
        long a = Math.abs(v);
        String s;
        if (a >= 1_000_000_000_000L) s = tenths(a, 1_000_000_000_000L) + "t";
        else if (a >= 1_000_000_000L) s = tenths(a, 1_000_000_000L) + "b";
        else if (a >= 1_000_000L) s = tenths(a, 1_000_000L) + "m";
        else if (a >= 1_000L) s = tenths(a, 1_000L) + "k";
        else s = Long.toString(a);
        return v < 0 ? "-" + s : s;
    }

    private static String tenths(long a, long unit) {
        long t = a / (unit / 10); // whole tenths of the unit, integer maths so nothing rounds up
        return t % 10 == 0 ? Long.toString(t / 10) : (t / 10) + "." + (t % 10);
    }

    /** Money text: "$87.9m" or "-$1.2m". */
    public static String money(long v) { return (v < 0 ? "-$" : "$") + fmt(Math.abs(v)); }

    /** Profit text: "+$87.9m", "$0" or "-$1.2m". */
    public static String signed(long p) { return p > 0 ? "+" + money(p) : money(p); }
}
