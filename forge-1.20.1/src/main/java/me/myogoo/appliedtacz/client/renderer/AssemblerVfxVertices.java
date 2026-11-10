package me.myogoo.appliedtacz.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

final class AssemblerVfxVertices {
    static void emit(VertexConsumer vertices, Matrix4f pose, float x, float y, float z,
            float red, float green, float blue, float alpha) {
        vertices.vertex(pose, x, y, z).color(red, green, blue, alpha).endVertex();
    }
}
