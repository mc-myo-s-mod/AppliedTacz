package me.myogoo.appliedtacz.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TaCZPatternMetadata {
    private static final String METADATA = "appliedtacz:pattern";
    private static final String WORKBENCH = "workbench";
    private static final String ENCODED_BY = "encoded_by";

    private TaCZPatternMetadata() {
    }

    public static void set(ItemStack pattern, ResourceLocation workbenchId, Player player) {
        CompoundTag metadata = new CompoundTag();
        metadata.putString(WORKBENCH, workbenchId.toString());
        metadata.putString(ENCODED_BY, player.getGameProfile().getName());
        pattern.getOrCreateTag().put(METADATA, metadata);
        pattern.getOrCreateTag().putString("encodePlayer", player.getGameProfile().getName());
    }

    public static @Nullable ResourceLocation getWorkbenchId(ItemStack pattern) {
        String id = metadata(pattern).getString(WORKBENCH);
        return id.isEmpty() || id.length() > 256 ? null : ResourceLocation.tryParse(id);
    }

    public static @Nullable String getEncodedBy(ItemStack pattern) {
        String name = metadata(pattern).getString(ENCODED_BY);
        return name.isEmpty() ? null : name;
    }

    public static boolean hasWorkbenchId(ItemStack pattern) {
        return metadata(pattern).contains(WORKBENCH);
    }

    private static CompoundTag metadata(ItemStack pattern) {
        CompoundTag tag = pattern.getTag();
        return tag == null ? new CompoundTag() : tag.getCompound(METADATA);
    }
}
