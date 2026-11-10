package me.myogoo.appliedtacz.client.renderer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline export through TaCZ's actual textured cube renderer; never packaged with the mod. */
public class ExportWorkbenchPreview implements VertexConsumer {
    private final List<float[]> vertices = new ArrayList<>();
    private final boolean colorOnly;
    private float[] vertex;

    private ExportWorkbenchPreview(boolean colorOnly) {
        this.colorOnly = colorOnly;
    }

    public static void main(String[] args) throws Exception {
        if (!ExportWorkbenchPreview.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Enable assertions with -ea");
        }
        Gson gson = new GsonBuilder().registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer()).create();
        Path pack = Path.of(args[0]);
        Map<String, Object> models = new LinkedHashMap<>();
        for (String name : new String[] {"ammo_workbench", "attachment_workbench", "gun_smith_table"}) {
            var display = gson.fromJson(Files.readString(pack.resolve("assets/tacz/display/blocks/" + name + ".json")),
                    com.google.gson.JsonObject.class);
            String modelId = display.get("model").getAsString();
            String textureId = display.get("texture").getAsString();
            Path source = resource(pack, modelId, "geo_models", ".json");
            Path texture = resource(pack, textureId, "textures", ".png");
            assert Files.isRegularFile(texture) : texture;
            var model = new BedrockModel(gson.fromJson(Files.readString(source), BedrockModelPOJO.class), BedrockVersion.NEW);
            var bounds = AEMolecularAssemblerRenderer.measureWorkbench(model);
            assert bounds != null && bounds.getXsize() > 0 && bounds.getYsize() > 0 && bounds.getZsize() > 0;
            var poses = new PoseStack();
            // NORTH.toYRot() is 180; capture the rotating display at time zero.
            AEMolecularAssemblerRenderer.transformWorkbench(poses, bounds, 180, 0, true);
            var consumer = new ExportWorkbenchPreview(false);
            for (var part : model.getShouldRender()) {
                part.render(poses, ItemDisplayContext.NONE, consumer, 0xf000f0, 0);
            }
            assert !consumer.vertices.isEmpty() && consumer.vertices.size() % 4 == 0 : "quad stream";
            // TaCZ emits collapsed origin quads for missing per-face UVs; they draw no pixels.
            for (int i = consumer.vertices.size() - 4; i >= 0; i -= 4) {
                float[] a = consumer.vertices.get(i);
                float[] b = consumer.vertices.get(i + 1);
                float[] c = consumer.vertices.get(i + 2);
                var ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
                var ac = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
                if (ab.cross(ac).lengthSquared() < 1e-12) consumer.vertices.subList(i, i + 4).clear();
            }
            for (float[] v : consumer.vertices) {
                for (float component : v) assert Float.isFinite(component) : "non-finite vertex";
                // A sphere is rotation-invariant, including the actual TaCZ cube inflation.
                assert new Vector3f(v[0] - 8, v[1] - 8, v[2] - 8).length() <= 6 : name + " rotation clearance";
            }
            models.put(name, Map.of("source", source.toString(), "texture", texture.toString(),
                    "facing", "north", "vertices", consumer.vertices));
            System.out.println(name + ": " + consumer.vertices.size() / 4 + " real TaCZ textured quads");
        }
        var vfx = new ExportWorkbenchPreview(true);
        AssemblerVfx.emit(new PoseStack(), vfx, 0, true, 1);
        assert vfx.vertices.size() == 720 : "crafting VFX vertex budget";
        models.put("vfx", vfx.vertices);
        Files.writeString(Path.of(args[1]), gson.toJson(models));
        System.out.println("PASS: actual emitted vertices, finite UVs/normals, all-angle rotation clearance");
    }

    private static Path resource(Path pack, String id, String directory, String suffix) {
        String[] parts = id.split(":", 2);
        return pack.resolve("assets/" + parts[0] + "/" + directory + "/" + parts[1] + suffix);
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        vertex = colorOnly ? new float[] {x * 16, y * 16, z * 16, 0, 0, 0, 0}
                : new float[] {x * 16, y * 16, z * 16, 0, 0, 0, 0, 0};
        vertices.add(vertex);
        return this;
    }
    @Override public VertexConsumer setUv(float u, float v) { vertex[3] = u; vertex[4] = v; return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) {
        vertex[5] = x; vertex[6] = y; vertex[7] = z; return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        if (colorOnly) {
            vertex[3] = r / 255f;
            vertex[4] = g / 255f;
            vertex[5] = b / 255f;
            vertex[6] = a / 255f;
        }
        return this;
    }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
}
