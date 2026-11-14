package me.myogoo.appliedtacz.client;

import appeng.core.definitions.AEItems;
import me.myogoo.appliedtacz.crafting.TaCZPatternMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TaCZPatternTooltips {
    private TaCZPatternTooltips() {
    }

    public static void append(ItemStack stack, List<Component> lines) {
        if (!stack.is(AEItems.PROCESSING_PATTERN.asItem())) {
            return;
        }
        String encoder = TaCZPatternMetadata.getEncodedBy(stack);
        if (encoder == null) {
            return;
        }
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents
                    && (contents.getKey().equals("extendedae_plus.pattern.hovertext.player")
                            || contents.getKey().equals("tooltip.appliedtacz.pattern.encoded_by"))) {
                // ExtendedAE Plus reads the compatible encodePlayer field written alongside our metadata.
                return;
            }
        }
        lines.add(Component.translatable("tooltip.appliedtacz.pattern.encoded_by", encoder)
                .withStyle(ChatFormatting.GRAY));
    }
}
