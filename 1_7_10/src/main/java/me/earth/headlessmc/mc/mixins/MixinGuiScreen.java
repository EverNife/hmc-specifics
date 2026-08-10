package me.earth.headlessmc.mc.mixins;

import me.earth.headlessmc.mc.ComponentHelper;
import me.earth.headlessmc.mc.GuiScreenMixinWorkaround;
import me.earth.headlessmc.mc.IdManager;
import me.earth.headlessmc.mc.ReflectionHelper;
import me.earth.headlessmc.mc.gui.GuiButton;
import me.earth.headlessmc.mc.gui.GuiElement;
import me.earth.headlessmc.mc.gui.GuiScreen;
import me.earth.headlessmc.mc.gui.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(net.minecraft.client.gui.GuiScreen.class)
public abstract class MixinGuiScreen implements GuiScreen {
    @Shadow
    protected List<net.minecraft.client.gui.GuiButton> buttonList;

    @Invoker("mouseClicked")
    public abstract void hmc$mouseClicked(int x, int y, int button);

    @Invoker("mouseReleased")
    public abstract void hmc$mouseReleased(int x, int y, int button);

    /**
     * A real click is a press AND a release, and on a container the two do different work: the
     * press takes a stack onto the cursor, but putting one down is finalized by the release,
     * because this version routes it through the drag-split state machine. Pressing alone can
     * therefore pick items up and never put them back.
     */
    @Override
    public void click(int x, int y, int button) {
        hmc$mouseClicked(x, y, button);
        hmc$mouseReleased(x, y, button);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initHook(CallbackInfo ci) {
        IdManager.INSTANCE.setContext(this);
    }

    @Override
    public List<GuiButton> getButtons() {
        Set<GuiButton> buttons = new HashSet<>();
        buttonList.stream()
                  .filter(GuiButton.class::isInstance)
                  .map(GuiButton.class::cast)
                  .forEach(buttons::add);
        buttons.addAll(ReflectionHelper.findAll(this, GuiButton.class));
        return new ArrayList<>(buttons);
    }

    @Override
    public List<TextField> getTextFields() {
        return new ArrayList<>(ReflectionHelper.findAll(this, TextField.class));
    }

    @Override
    public List<GuiElement> getAllElements() {
        Set<GuiElement> result = new LinkedHashSet<>();
        buttonList.stream()
                .filter(GuiElement.class::isInstance)
                .map(GuiElement.class::cast)
                .forEach(result::add);
        result.addAll(ReflectionHelper.findAll(this, GuiElement.class));
        //noinspection ConstantValue
        if (GuiContainer.class.isInstance(this)) {
            GuiContainer guiContainer = GuiContainer.class.cast(this);
            for (Object slotObj : guiContainer.inventorySlots.inventorySlots) {
                if (!(slotObj instanceof Slot)) {
                    continue;
                }

                Slot slot = (Slot) slotObj;
                // An empty slot holds null here, not an empty stack - ItemStack.EMPTY only exists
                // from 1.11 on. Any container has empty slots, so an unguarded read throws before
                // a single element is collected, taking the whole screen dump down with it.
                ItemStack stack = slot.getStack();
                String text = "";
                List<String> tooltip = Collections.emptyList();
                if (stack != null && stack.getItem() != null) {
                    IChatComponent comp = (new ChatComponentText("[")).appendText(stack.getDisplayName()).appendText("]");
                    text = ComponentHelper.toAnsiString(comp);
                    tooltip = hmc$getItemToolTip(stack);
                }

                int[] xyOffset = GuiScreenMixinWorkaround.getXYOffset(guiContainer);
                int xOffset = xyOffset[0];
                int yOffset = xyOffset[1];

                result.add(new me.earth.headlessmc.mc.gui.Slot(
                        text,
                        slot.xDisplayPosition + xOffset,
                        slot.yDisplayPosition + yOffset,
                        18,
                        18,
                        slot.slotNumber,
                        tooltip
                ));
            }

            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                // AbstractContainerScreen draggingItem is not accessible :(
                // Carrying nothing is null on this version, same as an empty slot.
                ItemStack carried = player.inventory.getItemStack();
                if (carried != null && carried.getItem() != null) {
                    IChatComponent comp = (new ChatComponentText("[")).appendText(carried.getDisplayName()).appendText("]");
                    String text = ComponentHelper.toAnsiString(comp);

                    List<String> tooltip = hmc$getItemToolTip(carried);
                    result.add(new me.earth.headlessmc.mc.gui.Carried(
                            text,
                            0,
                            0,
                            18,
                            18,
                            result.size(),
                            tooltip
                    ));
                }
            }
        }

        return new ArrayList<>(result);
    }

    @SuppressWarnings("unchecked")
    public List<String> hmc$getItemToolTip(ItemStack itemStack) {
        Minecraft mc = Minecraft.getMinecraft();
        List<String> list = itemStack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for(int k = 0; k < list.size(); ++k) {
            if (k == 0) {
                list.set(k, itemStack.getRarity().rarityColor + (String)list.get(k));
            } else {
                list.set(k, EnumChatFormatting.GRAY + (String)list.get(k));
            }
        }

        List<String> result = new ArrayList<>(list.size());
        for (String string : list) {
            result.add(ComponentHelper.toAnsiString(string, string));
        }

        return result;
    }


}
