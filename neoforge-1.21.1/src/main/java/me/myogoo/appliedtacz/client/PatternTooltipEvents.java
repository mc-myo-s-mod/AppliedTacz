package me.myogoo.appliedtacz.client;

import me.myogoo.appliedtacz.AppliedTaCZ;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = AppliedTaCZ.MODID, value = Dist.CLIENT)
public final class PatternTooltipEvents {
    private PatternTooltipEvents() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        TaCZPatternTooltips.append(event.getItemStack(), event.getToolTip());
    }
}
