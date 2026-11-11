package dev.motions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads server messages such as "Steve paid you $500M" or "You have been paid $500M by .Bedrock Player".
 * Supports Java names and Bedrock (Floodgate) names that start with a "." and may contain spaces.
 */
final class PaymentParser {
    private PaymentParser() {}

    /** Result of the last successful parse() (static so reading chat allocates almost nothing). */
    static String name;  // null if the message didn't name anybody
    static long amount;

    // Bedrock: "." + gamertag (up to 2 extra words, never "has/just/paid/payed/you"). Java: 3-16 chars, not a bare amount.
    private static final String NAME = "(\\.[A-Za-z0-9_]{1,16}(?:\\s(?!(?:has|just|paid|payed|you)\\b)[A-Za-z0-9_]{1,16}){0,2}"
            + "|(?!\\d+[kmbt]?\\b)[A-Za-z0-9_]{3,16})";
    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern PAID_YOU = Pattern.compile(NAME + "\\s+(?:(?:has|just)\\s+)?(?:paid|payed)\\s+you", CI);
    private static final Pattern BEEN_PAID = Pattern.compile("\\byou\\s+(?:(?:have|'ve)\\s+)?(?:been|were)\\s+(?:paid|payed)\\b", CI);
    private static final Pattern YOU_PAID = Pattern.compile("\\byou\\s+(?:(?:have|'ve|just)\\s+)?(?:paid|payed)\\b", CI);
    private static final Pattern BY_USER = Pattern.compile("\\b(?:by|from)\\s+" + NAME, CI);
    // A "$" amount; digits are possessive so they can never be cut short ("$100USD" is 100, not 10); a k/m/b/t suffix
    // only counts when no letter follows it ("100 more" is 100); percentages like "10%" are skipped.
    private static final Pattern AMOUNT = Pattern.compile("(?<![A-Za-z0-9_.])(\\$)?(\\d[\\d,]*+(?:\\.\\d+)?+)(?:\\s?([kmbt])(?![A-Za-z]))?(?!%)", CI);
    // normal player chat ("Name: text", "<Name> text", "Name » text") is ignored so it can't be faked
    private static final Pattern CHAT_LIKE = Pattern.compile("^\\s*(?:<[^>]{1,40}>|(?:\\[[^\\]]{1,20}\\]\\s*)*\\.?[A-Za-z0-9_]{3,16}\\s*(?::|\u00bb|>>))");

    /** Index where a chat-style prefix ("Name:", "<Name>", "Name \u00bb") ends, or 0 if the message doesn't start like chat. */
    static int chatPrefixEnd(String msg) {
        Matcher m = CHAT_LIKE.matcher(msg);
        return m.find() ? m.end() : 0;
    }

    /** Normal player chat - ignored for payments so it can't be faked. */
    static boolean isPlayerChat(String msg) { return chatPrefixEnd(msg) > 0; }

    /** The amount at or after index {@code from} ("87,992,910", "$1.5B", "50k"), preferring one with a "$"; -1 if none. */
    static long amountIn(String s, int from) {
        Matcher a = AMOUNT.matcher(s);
        String num = null, suffix = null;
        for (int pos = from; pos < s.length() && a.find(pos); pos = a.end()) {
            if (num == null) { num = a.group(2); suffix = a.group(3); } // the first number is the fallback...
            if (a.group(1) != null) { num = a.group(2); suffix = a.group(3); break; } // ...but a "$" amount wins
        }
        return num == null ? -1 : AuctionManager.parsePrice(num + (suffix == null ? "" : suffix));
    }

    /** Cheap scan so ordinary chat never reaches the regexes. */
    private static boolean mentionsPay(String s) {
        for (int i = 0, n = s.length() - 3; i < n; i++) {
            if (s.regionMatches(true, i, "paid", 0, 4) || s.regionMatches(true, i, "paye", 0, 4)) return true;
        }
        return false;
    }

    private static boolean candidate(String msg) {
        return msg != null && msg.length() <= 300 && mentionsPay(msg) && !isPlayerChat(msg);
    }

    /** True for "X paid you $5M" / "You have been paid $5M by X"; the result is in {@link #name} and {@link #amount}. */
    static boolean parse(String msg) { return candidate(msg) && received(msg); }

    /** +amount when someone paid you, -amount when you paid someone, 0 for anything else. */
    static long signed(String msg) {
        if (!candidate(msg)) return 0;
        if (received(msg)) return amount;
        return sent(msg) ? -amount : 0;
    }

    private static boolean received(String msg) {
        String user = null;
        int from = 0;
        Matcher m = PAID_YOU.matcher(msg);
        if (m.find()) {
            user = m.group(1);
            from = m.end();
        } else if (BEEN_PAID.matcher(msg).find()) {
            Matcher b = BY_USER.matcher(msg);
            if (b.find()) user = b.group(1);
        } else {
            return false;
        }
        long v = amountIn(msg, from);
        if (v <= 0) return false;
        name = user;
        amount = v;
        return true;
    }

    /** "You paid Steve $30M", "You have paid $30M to Steve". */
    private static boolean sent(String msg) {
        Matcher m = YOU_PAID.matcher(msg);
        if (!m.find()) return false;
        long v = amountIn(msg, m.end());
        if (v <= 0) return false;
        name = null;
        amount = v;
        return true;
    }
}
