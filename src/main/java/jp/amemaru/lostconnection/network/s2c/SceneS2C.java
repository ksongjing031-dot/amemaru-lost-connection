package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ストーリーイベントの演出（Phase 0-B 5章 {@code StoryScene}：字幕と演出の手順）。画面の演出の手順をまとめて送り、
 * クライアントが自分の速さで流す。見終えたら {@code SceneStateC2S(DONE)} を返す。
 */
public record SceneS2C(int scene, ResourceLocation event, String title, String stepsJson, boolean replay) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(scene);
        buf.writeResourceLocation(event);
        buf.writeUtf(title, 128);
        buf.writeUtf(stepsJson, 32767);
        buf.writeBoolean(replay);
    }

    public static SceneS2C decode(FriendlyByteBuf buf) {
        return new SceneS2C(buf.readVarInt(), buf.readResourceLocation(), buf.readUtf(128), buf.readUtf(32767), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleScene(this));
    }
}
