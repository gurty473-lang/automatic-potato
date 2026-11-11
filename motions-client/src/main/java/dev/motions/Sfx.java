package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Small UI sound helper. All sounds respect the "UI sounds" setting. */
public final class Sfx {
    private Sfx() {}
    private static long lastHover;

    private static void play(SoundEvent s, float pitch, float vol) {
        if (!MotionsConfig.get().sounds) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(s, pitch, vol));
    }

    private static void play(RegistryEntry<SoundEvent> s, float pitch, float vol) {
        play(s.value(), pitch, vol);
    }

    public static void hover() {
        long n = System.currentTimeMillis();
        if (n - lastHover < 45) return;
        lastHover = n;
        play(SoundEvents.UI_BUTTON_CLICK, 2.0f, 0.12f);
    }
    public static void click()  { play(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.7f); }
    public static void toggle(boolean on) { play(SoundEvents.UI_BUTTON_CLICK, on ? 1.4f : 0.8f, 0.7f); }
    public static void select() { play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6f, 0.35f); }
    public static void open()   { play(SoundEvents.UI_BUTTON_CLICK, 1.25f, 0.6f); }
    public static void close()  { play(SoundEvents.UI_BUTTON_CLICK, 0.85f, 0.6f); }
    public static void error()  { play(SoundEvents.ENTITY_VILLAGER_NO, 1.3f, 0.4f); }
    public static void start()  { play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 0.45f); }
    public static void bid()    { play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.5f, 0.7f); }
    public static void welcome() { play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.45f); }
    public static void end()    { play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f); }
}
