package com.autototeminventory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class InventoryTotemEquip implements ClientModInitializer {
    private static final int INV_START = 9;
    private static final int HOTBAR_START = 36;
    private static final int LAST_SLOT = 44;
    private static final int OFFHAND_BUTTON = 40;

    private static final Path CONFIG =
            FabricLoader.getInstance().getConfigDir().resolve("inventory-totem-equip.properties");

    private static int hotbarSlot = 1;

    private boolean inventoryWasOpen = false;

    @Override
    public void onInitializeClient() {
        loadConfig();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ping")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(
                                    "Slot de totem en hotbar: " + hotbarSlot + " (usa /ping 1-9)"));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer(1, 9))
                                .executes(ctx -> {
                                    hotbarSlot = IntegerArgumentType.getInteger(ctx, "slot");
                                    saveConfig();
                                    ctx.getSource().sendFeedback(Text.literal(
                                            "Slot de totem en hotbar: " + hotbarSlot));
                                    return 1;
                                }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean open = client.currentScreen instanceof InventoryScreen;
            if (open && !inventoryWasOpen) {
                handleInventoryOpen(client, (InventoryScreen) client.currentScreen);
            }
            inventoryWasOpen = open;
        });
    }

    private static void handleInventoryOpen(MinecraftClient client, InventoryScreen screen) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return;
        }
        PlayerScreenHandler handler = screen.getScreenHandler();

        if (!player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int from = findTotem(handler, -1);
            if (from != -1) {
                client.interactionManager.clickSlot(
                        handler.syncId, from, OFFHAND_BUTTON, SlotActionType.SWAP, player);
            }
        }

        int target = hotbarSlot - 1;
        int targetSlot = HOTBAR_START + target;
        if (!handler.getSlot(targetSlot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int from = findTotem(handler, targetSlot);
            if (from != -1) {
                client.interactionManager.clickSlot(
                        handler.syncId, from, target, SlotActionType.SWAP, player);
            }
        }
    }

    private static int findTotem(PlayerScreenHandler handler, int skip) {
        for (int slot = INV_START; slot <= LAST_SLOT; slot++) {
            if (slot != skip && handler.getSlot(slot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                return slot;
            }
        }
        return -1;
    }

    private static void loadConfig() {
        try (InputStream in = Files.newInputStream(CONFIG)) {
            Properties p = new Properties();
            p.load(in);
            int s = Integer.parseInt(p.getProperty("hotbarSlot", "1").trim());
            hotbarSlot = Math.max(1, Math.min(9, s));
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    private static void saveConfig() {
        try (OutputStream out = Files.newOutputStream(CONFIG)) {
            Properties p = new Properties();
            p.setProperty("hotbarSlot", Integer.toString(hotbarSlot));
            p.store(out, "Inventory Totem Equip");
        } catch (IOException ignored) {
        }
    }
}
