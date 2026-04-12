package me.myogoo.appliedtacz.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class AEGunSmithTableRenderer implements BlockEntityRenderer<AEGunSmithTableBlockEntity> {
    public AEGunSmithTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    private Optional<ClientBlockIndex> getIndex(AEGunSmithTableBlockEntity blockEntity) {
        ResourceLocation id = AETaCZWorkbenchIds.getMenuBlockId(blockEntity);
        if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    @Override
    public void render(AEGunSmithTableBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        getIndex(blockEntity).ifPresent(index -> {
            BedrockModel model = index.getModel();
            ResourceLocation texture = index.getTexture();
            if (model == null) {
                return;
            }

            BlockState blockState = blockEntity.getBlockState();
            if (!(blockState.getBlock() instanceof AbstractGunSmithTableBlock block)) {
                return;
            }
            if (!block.isRoot(blockState)) {
                return;
            }

            Direction facing = blockState.getValue(AbstractGunSmithTableBlock.FACING);
            poseStack.pushPose();
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.mulPose(Axis.YN.rotationDegrees(block.parseRotation(facing)));
            RenderType renderType = RenderType.entityTranslucent(texture);
            model.render(poseStack, ItemDisplayContext.NONE, renderType, combinedLightIn, combinedOverlayIn);
            poseStack.popPose();
        });
    }

    @Override
    public boolean shouldRenderOffScreen(AEGunSmithTableBlockEntity blockEntity) {
        return true;
    }
}
