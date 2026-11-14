package me.myogoo.appliedtacz.client.renderer;

import appeng.client.render.crafting.MolecularAssemblerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.config.AppliedTaczClientConfig;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

public class AEMolecularAssemblerRenderer implements BlockEntityRenderer<AEMolecularAssemblerBlockEntity> {
    private final Map<BedrockModel, AABB> workbenchBounds = new WeakHashMap<>();
    private final Map<AEMolecularAssemblerBlockEntity, AssemblerVfx.Activity> vfxActivity = new WeakHashMap<>();

    public AEMolecularAssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AEMolecularAssemblerBlockEntity assembler, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        var state = assembler.getBlockState();
        if (assembler.getLevel() == null || !(state.getBlock() instanceof MolecularAssemblerBlock block)) {
            return;
        }
        WorkbenchUpgradeKind kind = WorkbenchUpgradeKind.fromAppliedWorkbench(state.getBlock());
        if (block.isExtended() || BuiltInRegistries.BLOCK.getKey(block).getPath().startsWith("tacz_assembler_")) {
            renderWorkbench(assembler, kind, partialTick, poses, buffers, light, overlay);
        }
    }

    private void renderWorkbench(AEMolecularAssemblerBlockEntity assembler, WorkbenchUpgradeKind kind,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (!isWorkbenchVisible(assembler.isNetworkPowered(), assembler.isNetworkOnline(), assembler.hasBootedGrid())) {
            return;
        }
        if (AppliedTaczClientConfig.renderWorkbench()) {
            renderWorkbenchContents(assembler, kind, partialTick, poses, buffers, light, overlay);
        }
        // AE2's translucent prism writes depth, so draw it after the contents behind the glass.
        var minecraft = Minecraft.getInstance();
        var prism = minecraft.getModelManager().getModel(MolecularAssemblerRenderer.LIGHTS_MODEL);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), buffers.getBuffer(RenderType.tripwire()),
                null, prism, 1, 1, 1, light, overlay);
    }

    private void renderWorkbenchContents(AEMolecularAssemblerBlockEntity assembler, WorkbenchUpgradeKind kind,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var id = assembler.getId();
        if (id == null || DefaultAssets.EMPTY_BLOCK_ID.equals(id)) {
            id = kind == null ? DefaultAssets.DEFAULT_BLOCK_ID : kind.defaultBlockId();
        }
        TimelessAPI.getClientBlockIndex(id).ifPresent(index -> {
            var model = index.getModel();
            if (model == null) {
                return;
            }
            var bounds = workbenchBounds.computeIfAbsent(model, AEMolecularAssemblerRenderer::measureWorkbench);
            if (bounds == null || bounds.getXsize() <= 0 || bounds.getYsize() <= 0 || bounds.getZsize() <= 0) {
                return;
            }
            poses.pushPose();
            // All three rotations repeat after 2400 ticks; wrap before converting the world clock to floating point.
            double time = assembler.getLevel().getGameTime() % 2400 + partialTick;
            transformWorkbench(poses, bounds,
                    assembler.getBlockState().getValue(MolecularAssemblerBlock.FACING).toYRot(), time,
                    AppliedTaczClientConfig.rotateWorkbench());
            model.render(poses, ItemDisplayContext.NONE, RenderType.entityTranslucent(index.getTexture()),
                    light, overlay);
            poses.popPose();
            var position = assembler.getBlockPos();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            float visibility = AssemblerVfx.visibility(camera.distanceToSqr(
                    position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5));
            var activity = vfxActivity.computeIfAbsent(assembler, ignored -> new AssemblerVfx.Activity());
            activity.update((double) assembler.getLevel().getGameTime() + partialTick, assembler.isCrafting());
            if (visibility > 0) {
                AssemblerVfx.emit(poses, buffers.getBuffer(AssemblerVfxRenderType.GLOW), time, activity, visibility);
            }
        });
    }

    static boolean isWorkbenchVisible(boolean powered, boolean online, boolean booted) {
        return powered && online && booted;
    }

    static void transformWorkbench(PoseStack poses, AABB bounds, float yaw, double time, boolean rotate) {
        // Fit the bounding sphere so every rotation stays inside the open frame.
        double diameter = Math.sqrt(bounds.getXsize() * bounds.getXsize()
                + bounds.getYsize() * bounds.getYsize() + bounds.getZsize() * bounds.getZsize());
        float scale = (float) (11.5 / 16 / diameter);
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YN.rotationDegrees(yaw + 180));
        if (rotate) {
            poses.mulPose(Axis.XP.rotationDegrees((float) (time % 400 * 360 / 400)));
            poses.mulPose(Axis.YP.rotationDegrees((float) (time % 600 * 360 / 600)));
            poses.mulPose(Axis.ZP.rotationDegrees((float) (time % 800 * 360 / 800)));
        }
        poses.scale(scale, scale, scale);
        var center = bounds.getCenter();
        poses.translate(-center.x, -center.y, -center.z);
        poses.mulPose(Axis.ZN.rotationDegrees(180));
    }

    static AABB measureWorkbench(BedrockModel model) {
        var poses = new PoseStack();
        poses.mulPose(Axis.ZN.rotationDegrees(180));
        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (var part : model.getShouldRender()) {
            measurePart(part, poses, min, max);
        }
        return min.isFinite() && max.isFinite() ? new AABB(min.x, min.y, min.z, max.x, max.y, max.z) : null;
    }

    private static void measurePart(BedrockPart part, PoseStack poses, Vector3f min, Vector3f max) {
        if (!part.visible) {
            return;
        }
        poses.pushPose();
        part.translateAndRotateAndScale(poses);
        for (var cube : part.cubes) {
            AABB bounds;
            if (cube instanceof BedrockCubeBox box) {
                bounds = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            } else if (cube instanceof BedrockCubePerFace box) {
                bounds = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            } else {
                continue;
            }
            // shortcut: bounds omit decorative inflation; measure vertices if a gunpack exceeds the fit clearance.
            for (int corner = 0; corner < 8; corner++) {
                var vertex = new Vector3f((float) ((corner & 1) == 0 ? bounds.minX : bounds.maxX) / 16,
                        (float) ((corner & 2) == 0 ? bounds.minY : bounds.maxY) / 16,
                        (float) ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) / 16);
                poses.last().pose().transformPosition(vertex);
                min.min(vertex);
                max.max(vertex);
            }
        }
        for (var child : part.children) {
            measurePart(child, poses, min, max);
        }
        poses.popPose();
    }

}
