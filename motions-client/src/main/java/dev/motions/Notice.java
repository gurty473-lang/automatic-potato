package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Action-bar notifications. The vanilla bar fades after ~3 seconds, so longer messages are re-shown once a second. */
public final class Notice {
    private Notice() {}

    private static String keepMsg;
    private static long keepUntil;
    private static int keepTicks;

    public static void show(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (MotionsConfig.get().notifications && mc.inGameHud != null)
            mc.inGameHud.setOverlayMessage(Text.literal("\u00a7a[Motions] \u00a7f" + msg), false);
    }

    /** Shows the message and keeps it up for roughly extraMs longer than normal. */
    public static void showFor(String msg, long extraMs) {
        if (!MotionsConfig.get().notifications) return;
        show(msg);
        keepMsg = msg;
        keepUntil = System.currentTimeMillis() + extraMs;
        keepTicks = 0;
    }

    public static void tick() {
        if (keepMsg == null) return;
        if (System.currentTimeMillis() >= keepUntil) { keepMsg = null; return; }
        if (++keepTicks % 20 == 0) show(keepMsg); // refresh once a second, before it can start fading
    }
}
