package me.earth.headlessmc.mc.mixins;

import me.earth.headlessmc.mc.player.ClientChatHooks;
import me.earth.headlessmc.mc.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus$EnumState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityClientPlayerMP.class)
public abstract class MixinEntityClientPlayerMP implements Player {
    @Shadow
    public abstract void sendChatMessage(String message);

    @Override
    public void sendMessage(String message) {
        // The chat GUI offers the line to the client command handler before sending it, and this
        // path never opens that GUI - without this, a client-side command typed on the console
        // reaches the server instead of its own handler. This era has no client chat event.
        if (ClientChatHooks.consumedAsCommand(this, message)) {
            return;
        }

        this.sendChatMessage(message);
    }

    @Override
    public void openMenu() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiIngameMenu());
    }

    @Override
    public void openInventory() {
        if (Minecraft.getMinecraft().playerController.isRidingHorse()) {
            Minecraft.getMinecraft().thePlayer.sendHorseInteraction();
        } else {
            // Minecraft.getMinecraft().getNetHandler().addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus$EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            Minecraft.getMinecraft().displayGuiScreen(new GuiInventory(Minecraft.getMinecraft().thePlayer));
        }
    }

    @Override
    public void closeScreen() {
        if (Minecraft.getMinecraft().currentScreen != null) {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

}
