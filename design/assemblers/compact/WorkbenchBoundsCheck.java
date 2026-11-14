package me.myogoo.appliedtacz.client.renderer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Run with -ea and the compiled mod plus its Minecraft/TaCZ runtime classpath. */
public class WorkbenchBoundsCheck {
    private static final double EPSILON = 0.00001;

    public static void main(String[] args) throws Exception {
        if (!WorkbenchBoundsCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Enable assertions with -ea");
        }
        checkBadgeTransform();
        for (int state = 0; state < 8; state++) {
            assert AEMolecularAssemblerRenderer.isWorkbenchVisible((state & 1) != 0, (state & 2) != 0,
                    (state & 4) != 0) == (state == 7) : "workbench must be hidden while offline or booting";
        }
        Gson gson = new GsonBuilder().registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer()).create();
        for (String directory : args) {
            for (String name : new String[] { "ammo_workbench", "attachment_workbench", "gun_smith_table" }) {
                var pojo = gson.fromJson(Files.readString(Path.of(directory, name + ".json")), BedrockModelPOJO.class);
                var model = new BedrockModel(pojo, BedrockVersion.NEW);
                var bounds = checkUnchanged(model);
                assert bounds != null && bounds.getXsize() > 0 && bounds.getYsize() > 0 && bounds.getZsize() > 0 : name;
                checkFit(bounds);
                System.out.println(name + " " + bounds);
            }
        }

        var model = new BedrockModel() {};
        assert AEMolecularAssemblerRenderer.measureWorkbench(model) == null : "empty model";
        var parent = new BedrockPart("parent");
        parent.setPos(16, 0, 0);
        parent.zRot = (float) (Math.PI / 2);
        parent.xScale = 2;
        parent.yScale = 3;
        var child = new BedrockPart("child");
        child.setPos(16, 0, 0);
        child.zRot = (float) (Math.PI / 2);
        child.xScale = 0.5f;
        child.cubes.add(new BedrockCubeBox(0, 0, 0, 0, 0, 16, 32, 48, 0, false, 64, 64));
        parent.addChild(child);
        model.getShouldRender().add(parent);
        // Local [0..1,0..2,0..3] passes through child then parent, then the renderer's Z flip.
        assertBounds(checkUnchanged(model), -1, -2, 0, 0.5, 2, 3);
        checkFit(checkUnchanged(model));

        child.visible = false;
        assert checkUnchanged(model) == null : "hidden child";
        child.visible = true;
        parent.visible = false;
        assert checkUnchanged(model) == null : "hidden parent excludes descendants";

        var negative = new BedrockModel() {};
        var negativePart = new BedrockPart("negative-size");
        negativePart.cubes.add(new BedrockCubeBox(0, 0, 16, 32, 48, -16, -32, -48, 0, false, 64, 64));
        negative.getShouldRender().add(negativePart);
        assertBounds(checkUnchanged(negative), -1, -2, 0, 0, 0, 3);
        checkFit(checkUnchanged(negative));
        System.out.println("PASS: online-only visibility, defaults, nested rotation/scale, negative sizes, hidden models, XYZ rotation fits, rotation disabled, fixed center, smooth loop, no mutation");
    }

    private static void checkBadgeTransform() {
        for (int[] origin : new int[][] {{0, 0}, {13, 27}, {-9, 5}, {-32, -48}}) {
            var poses = new PoseStack();
            AssemblerWorkbenchBadge.transformBadge(poses, origin[0], origin[1]);
            var matrix = poses.last().pose();
            var topLeft = matrix.transformPosition(new Vector3f(0, 0, 0));
            var bottomRight = matrix.transformPosition(new Vector3f(16, 16, 0));
            assert Math.abs(topLeft.x - (origin[0] + 8)) < EPSILON
                    && Math.abs(topLeft.y - (origin[1] + 8)) < EPSILON : topLeft;
            assert Math.abs(bottomRight.x - (origin[0] + 16)) < EPSILON
                    && Math.abs(bottomRight.y - (origin[1] + 16)) < EPSILON : bottomRight;
            assert Math.abs(bottomRight.z - topLeft.z) < EPSILON : "badge depth scale";
            // Both supported GuiGraphics implementations add 150 when rendering an item.
            float itemDepth = topLeft.z + 150;
            assert Math.abs(itemDepth - (ItemRenderer.ITEM_COUNT_BLIT_OFFSET + 10)) < EPSILON
                    && itemDepth > ItemRenderer.ITEM_COUNT_BLIT_OFFSET : "badge must render above count plane";
            assert AbstractContainerScreen.SLOT_ITEM_BLIT_OFFSET + itemDepth < 400
                    : "nested slot badge must remain below tooltip plane";
            var inverse = new org.joml.Matrix4f(matrix).invert();
            inverse.transformPosition(bottomRight);
            assert Math.abs(bottomRight.x - 16) < EPSILON && Math.abs(bottomRight.y - 16) < EPSILON
                    : "badge transform must be invertible: " + bottomRight;
        }
    }

    private static AABB checkUnchanged(BedrockModel model) {
        String before = snapshot(model);
        AABB bounds = AEMolecularAssemblerRenderer.measureWorkbench(model);
        assert before.equals(snapshot(model)) : "measurement mutated shared model";
        AABB again = AEMolecularAssemblerRenderer.measureWorkbench(model);
        assert bounds == null ? again == null : bounds.equals(again) : "measurement is not repeatable";
        return bounds;
    }

    private static String snapshot(BedrockModel model) {
        var state = new StringBuilder();
        for (var root : model.getShouldRender()) {
            snapshot(root, state);
        }
        return state.toString();
    }

    private static void snapshot(BedrockPart part, StringBuilder state) {
        state.append(System.identityHashCode(part)).append(part.visible).append(part.cubes)
                .append(Arrays.toString(new float[] {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                        part.offsetX, part.offsetY, part.offsetZ, part.xScale, part.yScale, part.zScale,
                        part.additionalQuaternion.x, part.additionalQuaternion.y,
                        part.additionalQuaternion.z, part.additionalQuaternion.w}));
        for (var child : part.children) {
            snapshot(child, state);
        }
    }

    private static void assertBounds(AABB actual, double x0, double y0, double z0, double x1, double y1, double z1) {
        assert actual != null;
        assert Math.abs(actual.minX - x0) < EPSILON && Math.abs(actual.minY - y0) < EPSILON
                && Math.abs(actual.minZ - z0) < EPSILON && Math.abs(actual.maxX - x1) < EPSILON
                && Math.abs(actual.maxY - y1) < EPSILON && Math.abs(actual.maxZ - z1) < EPSILON : actual;
    }

    private static void checkFit(AABB bounds) {
        for (int yaw : new int[] {0, 90, 180, 270}) {
            var stationary = new PoseStack();
            AEMolecularAssemblerRenderer.transformWorkbench(stationary, bounds, yaw, 0, true);
            for (double time = 0; time <= 2400; time += 5) {
                var stopped = new PoseStack();
                AEMolecularAssemblerRenderer.transformWorkbench(stopped, bounds, yaw, time + 0.5, false);
                assert stationary.last().pose().equals(stopped.last().pose(), (float) EPSILON)
                        : "disabled rotation must retain the centered, block-facing pose";
                var poses = new PoseStack();
                AEMolecularAssemblerRenderer.transformWorkbench(poses, bounds, yaw, time + 0.5, true);
                var center = bounds.getCenter();
                // Bounds are measured after the TaCZ Z flip; undo it before testing the complete render transform.
                var pivot = new Vector3f((float) -center.x, (float) -center.y, (float) center.z);
                poses.last().pose().transformPosition(pivot);
                assert pivot.distance(0.5f, 0.5f, 0.5f) < EPSILON : "center drift: " + pivot;
                for (int corner = 0; corner < 8; corner++) {
                    var point = new Vector3f((float) -((corner & 1) == 0 ? bounds.minX : bounds.maxX),
                            (float) -((corner & 2) == 0 ? bounds.minY : bounds.maxY),
                            (float) ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ));
                    poses.last().pose().transformPosition(point);
                    assert point.x >= 2.0 / 16 - EPSILON && point.x <= 14.0 / 16 + EPSILON : point;
                    assert point.y >= 2.0 / 16 - EPSILON && point.y <= 14.0 / 16 + EPSILON : point;
                    assert point.z >= 2.0 / 16 - EPSILON && point.z <= 14.0 / 16 + EPSILON : point;
                }
            }
            var start = new PoseStack();
            var end = new PoseStack();
            var next = new PoseStack();
            AEMolecularAssemblerRenderer.transformWorkbench(start, bounds, yaw, 0.5, true);
            AEMolecularAssemblerRenderer.transformWorkbench(end, bounds, yaw, 2400.5, true);
            AEMolecularAssemblerRenderer.transformWorkbench(next, bounds, yaw, 1, true);
            assert start.last().pose().equals(end.last().pose(), (float) EPSILON) : "loop jump";
            assert !start.last().pose().equals(next.last().pose(), (float) EPSILON) : "partial tick not animated";
            for (double boundary : new double[] {400, 600, 800, 2400}) {
                var before = new PoseStack();
                var after = new PoseStack();
                AEMolecularAssemblerRenderer.transformWorkbench(before, bounds, yaw, boundary - 0.001, true);
                AEMolecularAssemblerRenderer.transformWorkbench(after, bounds, yaw, boundary + 0.001, true);
                assert before.last().pose().equals(after.last().pose(), 0.001f) : "rotation wrap jump";
            }
        }
    }
}
