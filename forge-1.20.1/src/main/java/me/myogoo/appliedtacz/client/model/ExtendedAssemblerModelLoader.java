package me.myogoo.appliedtacz.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

public final class ExtendedAssemblerModelLoader implements IGeometryLoader<ExtendedAssemblerModelLoader.Model> {
    @Override
    public Model read(JsonObject json, JsonDeserializationContext context) {
        JsonObject parent = new JsonObject();
        parent.addProperty("parent", ModList.get().isLoaded("expatternprovider")
                ? "expatternprovider:block/ex_molecular_assembler"
                : "appliedtacz:block/tacz_assembler");
        return new Model(context.deserialize(parent, BlockModel.class));
    }

    public record Model(BlockModel delegate) implements IUnbakedGeometry<Model> {
        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                IGeometryBakingContext context) {
            delegate.resolveParents(modelGetter);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides,
                ResourceLocation modelLocation) {
            return delegate.bake(baker, spriteGetter, modelState, modelLocation);
        }
    }
}
