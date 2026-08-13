package me.earth.headlessmc.mc.mixins;

import me.earth.headlessmc.mc.player.ClientChatHooks;
import me.earth.headlessmc.mc.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer implements Player {
    @Shadow
    public abstract void chat(String component);

    @Override
    public void sendMessage(String message) {
        // What the chat screen does before sending, and the only place this path runs it: this era
        // has no client command API, so a mod intercepting the chat event is how a client-side
        // command is claimed before it reaches the server.
        String line = ClientChatHooks.onSend(message);
        if (line == null) {
            return;
        }

        this.chat(line);
    }

    @Override
    public void openMenu() {
        Minecraft.getInstance().setScreen(null);
        Minecraft.getInstance().pauseGame(false);
    }

    @Override
    public void openInventory() {
        Minecraft.getInstance().setScreen(null);
        if (Minecraft.getInstance().gameMode != null
                && Minecraft.getInstance().gameMode.isServerControlledInventory()) {
            LocalPlayer.class.cast(this).sendOpenInventory();
        } else {
            Minecraft.getInstance().setScreen(new InventoryScreen(LocalPlayer.class.cast(this)));
        }
    }

    @Override
    public void closeScreen() {
        if (Minecraft.getInstance().screen != null) {
            Minecraft.getInstance().setScreen(null);
        }
    }

}
