package dev.motions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MotionsConfig {
    public static final int[] ACCENTS = {0xFFA3E635, 0xFF22D3EE, 0xFFA78BFA, 0xFFFB923C, 0xFFF472B6};
    /** Glass tints: smoke, ocean, violet, rose, emerald, amber. */
    public static final int[] GLASS = {0xFF0B0D11, 0xFF0C2D55, 0xFF2B1657, 0xFF54152F, 0xFF0C3F2D, 0xFF4F320A};

    public boolean notifications = true;
    public boolean showAuctionHud = true;
    public boolean animations = true;
    public float animSpeed = 1.0f;
    public int accent = ACCENTS[0];
    public boolean sounds = true;
    public boolean blur = true; // real menu background blur (can be turned off for more FPS)
    public int blurLevel = 8;   // 1..10 (Minecraft's own default is 5)
    public int taxMode = 1;    // Donut SMP 2% tax in Live: 0 off, 1 you pay it on top of what you send, 2 it comes off what you receive
    public boolean copyWinner = true; // copy the winner's name to the clipboard when an auction ends
    public int glass = 0;       // index into GLASS
    public float hudX = 0.5f, hudY = 0.16f, hudScale = 1f;  // auction card: position (0..1 across the free space) and size
    public float liveX = 0.5f, liveY = 0f, liveScale = 1f;  // LIVE pill
    public boolean welcome = true; // 5-second intro when you join a server

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("motions-client.json");
    private static MotionsConfig inst = new MotionsConfig();

    public static MotionsConfig get() { return inst; }

    // element 0 = auction card, 1 = LIVE pill
    public float x(int e) { return e == 0 ? hudX : liveX; }

    public float y(int e) { return e == 0 ? hudY : liveY; }

    public float scale(int e) { return e == 0 ? hudScale : liveScale; }

    public void setPos(int e, float x, float y) {
        if (e == 0) { hudX = x; hudY = y; } else { liveX = x; liveY = y; }
    }

    public void setScale(int e, float s) {
        if (e == 0) hudScale = s; else liveScale = s;
    }

    public void resetPlace(int e) {
        setPos(e, 0.5f, e == 0 ? 0.16f : 0f);
        setScale(e, 1f);
    }

    public int glassTint() { return GLASS[Math.max(0, Math.min(GLASS.length - 1, glass))]; }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                MotionsConfig c = GSON.fromJson(Files.readString(FILE), MotionsConfig.class);
                if (c != null) inst = c;
            }
        } catch (Exception e) {
            inst = new MotionsConfig();
        }
        inst.animSpeed = Math.max(0.5f, Math.min(2f, inst.animSpeed));
        inst.hudScale = Math.max(0.5f, Math.min(1.5f, inst.hudScale <= 0 ? 1f : inst.hudScale));
        inst.taxMode = Math.max(0, Math.min(2, inst.taxMode));
        inst.hudX = Math.max(0f, Math.min(1f, inst.hudX));
        inst.hudY = Math.max(0f, Math.min(1f, inst.hudY));
        inst.liveX = Math.max(0f, Math.min(1f, inst.liveX));
        inst.liveY = Math.max(0f, Math.min(1f, inst.liveY));
        inst.liveScale = Math.max(0.5f, Math.min(1.5f, inst.liveScale <= 0 ? 1f : inst.liveScale));
        inst.blurLevel = Math.max(1, Math.min(10, inst.blurLevel <= 0 ? 8 : inst.blurLevel));
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(inst));
        } catch (IOException ignored) {
        }
    }
}
