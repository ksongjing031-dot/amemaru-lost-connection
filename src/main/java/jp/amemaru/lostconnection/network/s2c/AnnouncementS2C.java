package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 全員の画面の真ん中に出す知らせ（Phase 0-B 5章 {@code Announcement}）：階がひらいた・日が進んだ・予告・物語の告知など。
 */
public record AnnouncementS2C(Kind kind, String text) {
    /** 知らせの種類（帯の色と、チャットの頭の言葉が変わる）。 */
    public enum Kind {
        DAY("announce.amemaru.kind.day", 0xFFD27A),
        FLOOR("announce.amemaru.kind.floor", 0x8FE6FF),
        SCHEDULE("announce.amemaru.kind.schedule", 0xFFE9A8),
        EVENT("announce.amemaru.kind.event", 0xFFD9F0),
        STORY("announce.amemaru.kind.story", 0xCFF5FF),
        BOSS("announce.amemaru.kind.boss", 0xFFB08A);

        private final String labelKey;
        private final int color;

        Kind(String labelKey, int color) {
            this.labelKey = labelKey;
            this.color = color;
        }

        public String labelKey() {
            return labelKey;
        }

        public int color() {
            return color;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(text, 512);
    }

    public static AnnouncementS2C decode(FriendlyByteBuf buf) {
        return new AnnouncementS2C(buf.readEnum(Kind.class), buf.readUtf(512));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAnnouncement(this));
    }
}
