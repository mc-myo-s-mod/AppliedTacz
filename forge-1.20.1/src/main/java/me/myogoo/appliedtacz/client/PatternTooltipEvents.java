package me.myogoo.appliedtacz.client;

import me.myogoo.appliedtacz.AppliedTaCZ;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AppliedTaCZ.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PatternTooltipEvents {
    private PatternTooltipEvents() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        TaCZPatternTooltips.append(event.getItemStack(), event.getToolTip());
    }
}
