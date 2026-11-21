package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 本人の画面に出す短い文（Phase 5）：
 * <ul>
 *   <li>{@code TITLE}：画面の真ん中の章題・階の名前（{@code speaker} が上の小さな行、{@code text} が大きな行）</li>
 *   <li>{@code ANCIENT}：石柱の古代文字の一行（はじめは古代の文字で出て、読める字に変わる。大結晶が言葉の仲立ちをしている。Phase 0-A 4章）</li>
 *   <li>{@code SUBTITLE}：字幕（話し手と台詞）</li>
 *   <li>{@code TIP}：戦い方の手がかり（ボスのチュートリアル）</li>
 * </ul>
 */
public record StoryTextS2C(Kind kind, String speaker, String text, int ticks) {
    public enum Kind { TITLE, ANCIENT, SUBTITLE, TIP }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(speaker, 128);
        buf.writeUtf(text, 512);
        buf.writeVarInt(ticks);
    }

    public static StoryTextS2C decode(FriendlyByteBuf buf) {
        return new StoryTextS2C(buf.readEnum(Kind.class), buf.readUtf(128), buf.readUtf(512), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStoryText(this));
    }
}
