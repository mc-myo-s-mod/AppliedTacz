package me.myogoo.appliedtacz.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AEMolecularAssemblerScreen extends AEBaseScreen<AEMolecularAssemblerMenu> {
    public AEMolecularAssemblerScreen(AEMolecularAssemblerMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent("dialog_title", getTitle());
    }

    @Override
    public Component getTitle() {
        return AssemblerNames.title(menu.workbenchId, menu.isExtended());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("dialog_title", getTitle());
        String networkState = !menu.networkPowered ? "offline"
                : !menu.gridBooted ? "booting" : menu.networkOnline ? "connected" : "no_channel";
        boolean active = menu.networkPowered && menu.networkOnline && menu.gridBooted;
        Component network = Component.translatable("gui.appliedtacz.ae_network." + networkState)
                .withStyle(active ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);
        setTextContent("network", Component.translatable("gui.appliedtacz.ae_network", network));

        String status = !active ? "offline" : menu.crafting ? "crafting"
                : menu.getPendingOutput().isEmpty() ? "ready" : "returning";
        setTextContent("status", Component.translatable("gui.appliedtacz.assembler.status",
                Component.translatable("gui.appliedtacz.assembler." + status)));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        int divider = style.getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();
        graphics.hLine(offsetX + 12, offsetX + 243, offsetY + 26, divider);
        graphics.hLine(offsetX + 12, offsetX + 243, offsetY + 139, divider);
        Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + 15, offsetY + 73).blit(graphics);
        for (int i = 0; i < AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS; i++) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + 119 + i * 18, offsetY + 73).blit(graphics);
        }
        for (int i = 0; i < AEMolecularAssemblerBlockEntity.PATTERN_SLOTS; i++) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + 47 + i * 18, offsetY + 116).blit(graphics);
        }
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int y = row < 3 ? 157 + row * 18 : 215;
                Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + 47 + column * 18, offsetY + y).blit(graphics);
            }
        }
    }

}
