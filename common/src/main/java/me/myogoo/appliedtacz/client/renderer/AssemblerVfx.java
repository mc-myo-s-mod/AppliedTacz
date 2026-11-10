package me.myogoo.appliedtacz.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

final class AssemblerVfx {
    private static final int SEGMENTS = 32;
    private static final float RADIUS = 5.55f / 16;
    private static final double[] IDLE_PERIODS = {240, 300, 400};
    private static final double[] CRAFTING_PERIODS = {100, 125, 160};
    private static final double TRANSITION_TICKS = 8;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    static float visibility(double distanceSquared) {
        return (float) Math.max(0, Math.min(1, (24 * 24 - distanceSquared) / (24 * 24 - 16 * 16)));
    }

    static void emit(PoseStack poses, VertexConsumer vertices, double time, Activity activity, float visibility) {
        if (visibility <= 0) {
            return;
        }
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * Math.PI * 2 / 40));
        float amount = activity.amount;
        float brightness = visibility * (0.22f + amount * (0.38f + pulse * 0.25f));
        float red = 0.72f + amount * 0.28f;
        float green = 0.9f - amount * 0.42f;
        float blue = 1 - amount * 0.84f;
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        for (int ring = 0; ring < 3; ring++) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(ring * 55));
            poses.mulPose(Axis.XP.rotationDegrees(25 + ring * 60));
            poses.mulPose(Axis.ZP.rotationDegrees((float) activity.phases[ring]));
            var matrix = poses.last().pose();
            for (int segment = 0; segment < SEGMENTS; segment++) {
                if (segment % 8 >= 6) {
                    continue;
                }
                arc(vertices, matrix, segment, 0.022f, brightness * 0.14f, red, green, blue);
                arc(vertices, matrix, segment, 0.006f, brightness * 0.65f, red, green, blue);
            }
            for (int packet = 0; packet < 2; packet++) {
                double angle = time % 80 * Math.PI * 2 / 80 + ring + packet * Math.PI;
                float x = RADIUS * (float) Math.cos(angle);
                float y = RADIUS * (float) Math.sin(angle);
                float packetAlpha = packet == 0 ? 1 : amount;
                spark(vertices, matrix, x, y, 0.020f, brightness * 0.13f * packetAlpha, red, green, blue);
                spark(vertices, matrix, x, y, 0.008f, brightness * packetAlpha, red, green, blue);
            }
            poses.popPose();
        }
        poses.popPose();
    }

    private static void arc(VertexConsumer vertices, Matrix4f matrix, int segment, float width, float alpha,
            float red, float green, float blue) {
        float inner = RADIUS - width / 2;
        float outer = RADIUS + width / 2;
        point(vertices, matrix, COS[segment] * inner, SIN[segment] * inner, 0, alpha, red, green, blue);
        point(vertices, matrix, COS[segment] * outer, SIN[segment] * outer, 0, alpha, red, green, blue);
        point(vertices, matrix, COS[segment + 1] * outer, SIN[segment + 1] * outer, 0, alpha, red, green, blue);
        point(vertices, matrix, COS[segment + 1] * inner, SIN[segment + 1] * inner, 0, alpha, red, green, blue);
    }

    private static void spark(VertexConsumer vertices, Matrix4f matrix, float x, float y, float size, float alpha,
            float red, float green, float blue) {
        point(vertices, matrix, x - size, y, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y + size, 0, alpha, red, green, blue);
        point(vertices, matrix, x + size, y, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y - size, 0, alpha, red, green, blue);
        point(vertices, matrix, x - size, y, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y, size, alpha, red, green, blue);
        point(vertices, matrix, x + size, y, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y, -size, alpha, red, green, blue);
        point(vertices, matrix, x, y - size, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y, size, alpha, red, green, blue);
        point(vertices, matrix, x, y + size, 0, alpha, red, green, blue);
        point(vertices, matrix, x, y, -size, alpha, red, green, blue);
    }

    private static void point(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z, float alpha,
            float red, float green, float blue) {
        AssemblerVfxVertices.emit(vertices, matrix, x, y, z, red, green, blue, alpha);
    }

    static final class Activity {
        private double lastTime;
        private double transitionStart;
        private float amount;
        private float transitionFrom;
        private float target;
        final double[] phases = new double[3];
        private boolean initialized;

        float update(double time, boolean crafting) {
            float nextTarget = crafting ? 1 : 0;
            if (!initialized) {
                initialized = true;
                amount = transitionFrom = target = nextTarget;
                lastTime = transitionStart = time;
                for (int ring = 0; ring < phases.length; ring++) {
                    phases[ring] = time * 360 / (crafting ? CRAFTING_PERIODS[ring] : IDLE_PERIODS[ring]) % 360;
                }
                return amount;
            }
            if (nextTarget != target) {
                transitionFrom = amount;
                target = nextTarget;
                transitionStart = time;
            }
            float nextAmount = transitionAmount(time);
            double elapsed = Math.max(0, time - lastTime);
            float averageAmount = (amount + nextAmount) * 0.5f;
            for (int ring = 0; ring < phases.length; ring++) {
                double period = IDLE_PERIODS[ring]
                        + averageAmount * (CRAFTING_PERIODS[ring] - IDLE_PERIODS[ring]);
                phases[ring] = (phases[ring] + elapsed * 360 / period) % 360;
            }
            lastTime = time;
            amount = nextAmount;
            return amount;
        }

        private float transitionAmount(double time) {
            float progress = (float) Math.max(0, Math.min(1, (time - transitionStart) / TRANSITION_TICKS));
            progress = progress * progress * (3 - 2 * progress);
            return transitionFrom + (target - transitionFrom) * progress;
        }
    }
}
