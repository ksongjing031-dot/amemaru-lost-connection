package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 演出の形（陣・光の柱・斬撃の弧・障壁など）を描くときの描き方（クライアント専用）。
 *
 * <ul>
 *   <li>{@link #glow}：自分で光り、光を足し合わせる（暗い所でも明るい。重なるほど白く光る）。</li>
 *   <li>{@link #translucent}：自分で光る、ふつうの半透明（氷の塊など、向こうが少し暗く見えるもの）。</li>
 * </ul>
 * <p>どちらも裏表の両方を描き、奥行きの情報は書かない（演出どうしが隠し合わない）。</p>
 */
public final class FxRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(texture -> create("amemaru_fx_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(texture -> create("amemaru_fx_translucent",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                          boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType glow(ResourceLocation texture) {
        return GLOW.apply(texture);
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(texture);
    }
}
