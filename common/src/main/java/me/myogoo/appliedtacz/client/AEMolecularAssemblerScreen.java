package me.myogoo.appliedtacz.client;

import java.util.ArrayList;
import java.util.List;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AEMolecularAssemblerScreen extends AEBaseScreen<AEMolecularAssemblerMenu> {
    public AEMolecularAssemblerScreen(AEMolecularAssemblerMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));
        setTextContent("dialog_title", getTitle());
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.appliedtacz.molecular_assembler");
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        Component workbench = AssemblerNames.workbenchName(menu.workbenchId);
        String name = workbench.getString();
        if (name.startsWith("TaCZ ")) {
            workbench = Component.literal(name.substring(5)).withStyle(workbench.getStyle());
        }
        float scale = getStyle().getText().get("workbench_title").getScale();
        int maxWidth = (int) ((imageWidth - 16) / scale);
        // Keep addon names on the second line without shrinking the shared title font.
        if (font.width(workbench) > maxWidth) {
            workbench = Component.literal(font.plainSubstrByWidth(workbench.getString(),
                    maxWidth - font.width("…")) + "…").withStyle(workbench.getStyle());
        }
        setTextContent("workbench_title", workbench);
    }

    // AE2 1.21 adds a background even to an empty toolbar. The hook does not exist on 1.20.
    protected boolean shouldAddToolbar() {
        return false;
    }

    private List<Component> getCompatibleUpgrades() {
        var lines = new ArrayList<Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        lines.addAll(Upgrades.getTooltipLinesForMachine(menu.getUpgrades().getUpgradableItem()));
        return lines;
    }
}
