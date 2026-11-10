package me.myogoo.appliedtacz.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

/** Captures the actual VFX vertex stream without starting a graphics context. */
public class AssemblerVfxCheck implements VertexConsumer {
    private final List<float[]> vertices = new ArrayList<>();
    private float[] current;

    public static void main(String[] args) {
        if (!AssemblerVfxCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Enable assertions with -ea");
        }
        assert AssemblerVfx.visibility(0) == 1;
        assert AssemblerVfx.visibility(16 * 16) == 1;
        assert AssemblerVfx.visibility(20 * 20) > 0 && AssemblerVfx.visibility(20 * 20) < 1;
        assert AssemblerVfx.visibility(24 * 24) == 0;
        assert AssemblerVfx.visibility(64 * 64) == 0;
        for (boolean crafting : new boolean[] {false, true}) {
            for (double time = 0.5; time < 2400; time += 37.5) {
                var stream = capture(time, crafting, 1);
                assert stream.vertices.size() == 720 : "bounded vertex count";
                for (var v : stream.vertices) {
                    for (float component : v) assert Float.isFinite(component) : "finite vertex";
                    double radius = Math.sqrt(Math.pow(v[0] - 0.5, 2)
                            + Math.pow(v[1] - 0.5, 2) + Math.pow(v[2] - 0.5, 2));
                    assert radius < 6.0 / 16 : "effect outside frame";
                    assert v[6] >= 0 && v[6] <= 1 : "alpha range";
                }
            }
            assert capture(0, crafting, 0).vertices.isEmpty() : "distance culling";
            double period = crafting ? 4000 : 2400;
            assert difference(capture(0.5, crafting, 1), capture(period + 0.5, crafting, 1)) < 0.00001 : "loop seam";
            assert difference(capture(0, crafting, 1), capture(0.5, crafting, 1)) > 0.001 : "partial-tick motion";
            for (double boundary : new double[] {80, 240, 300, 400, 2400}) {
                assert difference(capture(boundary - 0.001, crafting, 1),
                        capture(boundary + 0.001, crafting, 1)) < 0.01 : "wrap discontinuity";
            }
        }
        assert capture(10, true, 1).maxAlpha() > capture(10, false, 1).maxAlpha() : "crafting intensity";
        assert capture(10, true, 1).maxAlpha() > capture(30, true, 1).maxAlpha() : "crafting pulse";
        assert capture(10, true, 0.5f).maxAlpha() < capture(10, true, 1).maxAlpha() : "distance fade";
        assert capture(10, false, 1).vertices.get(0)[3] < capture(10, true, 1).vertices.get(0)[3]
                : "activity color change";

        var transition = new AssemblerVfx.Activity();
        assert transition.update(0, false) == 0 : "idle start";
        assert transition.update(1, true) == 0 : "transition starts without a jump";
        float middle = transition.update(5, true);
        assert middle > 0 && middle < 1 : "smooth transition midpoint";
        assert transition.update(9, true) == 1 : "crafting transition complete";
        double phase = transition.phases[0];
        assert transition.update(9, false) == 1 && transition.phases[0] == phase : "stopping preserves phase and color";
        assert transition.update(17, false) == 0 : "idle transition complete";
        var idle = new AssemblerVfx.Activity();
        var crafting = new AssemblerVfx.Activity();
        idle.update(0, false);
        crafting.update(0, true);
        assert crafting.update(0.5, true) == 1 : "already crafting stays fully active on subsequent frames";
        idle.update(10, false);
        crafting.update(10, true);
        assert crafting.phases[0] > idle.phases[0] : "faster crafting spin";
        System.out.println("PASS: 3 rings, bounded packets, frame clearance, pose restoration, smooth activity transition, faster craft spin, distance fade/cull");
    }

    private static AssemblerVfxCheck capture(double time, boolean crafting, float visibility) {
        var stream = new AssemblerVfxCheck();
        var poses = new PoseStack();
        var before = new org.joml.Matrix4f(poses.last().pose());
        var activity = new AssemblerVfx.Activity();
        activity.update(time, crafting);
        AssemblerVfx.emit(poses, stream, time, activity, visibility);
        assert poses.last().pose().equals(before) : "pose leak";
        return stream;
    }

    private static double difference(AssemblerVfxCheck a, AssemblerVfxCheck b) {
        assert a.vertices.size() == b.vertices.size();
        double max = 0;
        for (int i = 0; i < a.vertices.size(); i++) {
            for (int component = 0; component < 7; component++) {
                max = Math.max(max, Math.abs(a.vertices.get(i)[component] - b.vertices.get(i)[component]));
            }
        }
        return max;
    }

    private float maxAlpha() {
        float max = 0;
        for (var v : vertices) max = Math.max(max, v[6]);
        return max;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        current = new float[] {x, y, z, 0, 0, 0, 0};
        vertices.add(current);
        return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        assert r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255;
        current[3] = r / 255f;
        current[4] = g / 255f;
        current[5] = b / 255f;
        current[6] = a / 255f;
        return this;
    }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
}
