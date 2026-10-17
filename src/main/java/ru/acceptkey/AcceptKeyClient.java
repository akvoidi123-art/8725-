package ru.acceptkey;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * Remembers the last chat message that has a clickable [принять] / [accept] button
 * and runs its command when the bound key is pressed.
 */
public final class AcceptKeyClient implements ClientModInitializer {

    /** Words that mark an "accept" button (lower case). */
    private static final List<String> ACCEPT_WORDS = List.of("принять", "accept");
    /** How long a request stays acceptable. */
    private static final long EXPIRE_MS = 60_000L;

    private static volatile String pendingCommand;
    private static volatile long pendingAt;

    private static KeyMapping acceptKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("acceptkey", "main"));

        acceptKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.acceptkey.accept",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RETURN,      // Enter by default, change it in Controls
                category));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) scan(message);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (acceptKey.consumeClick()) accept(mc);
        });
    }

    /** Finds a run_command click event on a component whose text contains an accept word. */
    private static void scan(Component message) {
        for (Component part : message.toFlatList(Style.EMPTY)) {
            String text = part.getString().toLowerCase(Locale.ROOT);
            if (ACCEPT_WORDS.stream().noneMatch(text::contains)) continue;
            if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) {
                pendingCommand = run.command();
                pendingAt = System.currentTimeMillis();
                return;
            }
        }
    }

    private static void accept(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) return;
        String cmd = pendingCommand;
        if (cmd == null || System.currentTimeMillis() - pendingAt > EXPIRE_MS) {
            pendingCommand = null;
            mc.player.displayClientMessage(Component.translatable("acceptkey.no_request"), true);
            return;
        }
        pendingCommand = null;
        mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        mc.player.displayClientMessage(Component.translatable("acceptkey.accepted"), true);
    }
}
