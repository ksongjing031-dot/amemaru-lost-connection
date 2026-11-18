package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 光る部分（{@code <絵>_glowmask.png}）を、暗い所でも明るく描く層（GeckoLibのモデル用）。
 *
 * <p>同じ形をもう一度、光る部分の絵で描き重ねる（バニラのクモの目と同じやり方）。光る部分の絵に描いた色が、そのまま
 * 明るく出る（透明な画素は描かない）。光る部分の絵がなければ何もしない。</p>
 *
 * <p>GeckoLibの {@code AutoGlowingGeoLayer} は、元の絵から光る画素を抜き取って別に描くため、環境によっては抜けた所が
 * 黒く見えることがあり（GitHub Actions の写真で確認）、光る部分の絵がないとモデル全体が黒くなる。そのため、元の絵に手を
 * 加えないこの方法にした。</p>
 */
public class OptionalGlowLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final Map<ResourceLocation, Optional<ResourceLocation>> GLOWMASKS = new ConcurrentHashMap<>();

    public OptionalGlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    /** 資源（リソースパック）を読み直したとき、覚えた結果を捨てる。 */
    public static void clearCache() {
        GLOWMASKS.clear();
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Optional<ResourceLocation> glowmask = glowmask(getTextureResource(animatable));
        if (glowmask.isEmpty()) {
            return;
        }
        RenderType glowType = RenderType.entityTranslucentEmissive(glowmask.get());
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType, bufferSource.getBuffer(glowType),
                partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** その絵の、光る部分の絵（なければ空）。 */
    private static Optional<ResourceLocation> glowmask(ResourceLocation texture) {
        return GLOWMASKS.computeIfAbsent(texture, base -> {
            String path = base.getPath();
            String glow = path.endsWith(".png") ? path.substring(0, path.length() - 4) + "_glowmask.png" : path + "_glowmask";
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), glow);
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent()
                    ? Optional.of(location) : Optional.empty();
        });
    }
}
