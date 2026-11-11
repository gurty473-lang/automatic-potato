package dev.motions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MotionsClient implements ClientModInitializer {
    public static KeyBinding openKey;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("motions", "main"));

    @Override
    public void onInitializeClient() {
        MotionsConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.motions.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new MotionsScreen());
            }
            Notice.tick();
            Welcome.tick(mc);
            AuctionManager.tick(mc);
        });

        // each element does nothing at all while it has nothing to show
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("motions", "auction_hud"), AuctionHud::render);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("motions", "live_hud"), LiveHud::render);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("motions", "welcome"), Welcome::render);

        // chat is only turned into text while the auction or a live session needs it
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            boolean auction = !overlay && AuctionManager.state == AuctionManager.State.ACTIVE; // payments: chat only, so a
            if (!auction && LiveSession.state == LiveSession.State.IDLE) return;                  // repeating action bar can't double count
            String s = msg.getString();
            if (auction) AuctionManager.onMessage(s);
            LiveSession.onMessage(s, overlay);
        });

        // joining a server (or world) starts the welcome intro once
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LiveSession.onJoin(handler); // detects Donut SMP, ends the session if it's a different server
            Welcome.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LiveSession.onLeave());
    }
}
