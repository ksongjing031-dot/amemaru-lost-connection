package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.progression.ProgressionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 世界の様子（Phase 0-B 5章 {@code WorldState}）：進行日・章・開いた階・予告中のカウントダウン。全員に送る。
 * 残り時間はミリ秒で送り、クライアントが自分の時計で数える（時計のずれを持ち込まないため）。
 */
public record WorldStateS2C(int day, int chapter, long unlockedBits, int nextFloor, List<Countdown> countdowns) {
    /** 予告のカウントダウン（{@code text} は「1階がひらく」などの文）。 */
    public record Countdown(int id, String text, long remainingMillis) {
    }

    public static WorldStateS2C of(MinecraftServer server) {
        ProgressionData data = ProgressionData.get(server);
        long bits = 0L;
        for (int floor : data.unlockedFloors()) {
            if (floor >= 1 && floor <= 63) {
                bits |= 1L << floor;
            }
        }
        long now = System.currentTimeMillis();
        List<Countdown> list = new ArrayList<>();
        for (ProgressionData.Scheduled s : data.scheduled()) {
            list.add(new Countdown(s.id(), ProgressionService.describe(s).getString(), Math.max(0L, s.atMillis() - now)));
        }
        ProgressionData.Scheduled next = data.nextFloorUnlock();
        return new WorldStateS2C(data.day(), data.chapter(), bits, next == null ? 0 : next.value(), List.copyOf(list));
    }

    public boolean unlocked(int floor) {
        return floor >= 1 && floor <= 63 && (unlockedBits & (1L << floor)) != 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(day);
        buf.writeVarInt(chapter);
        buf.writeLong(unlockedBits);
        buf.writeVarInt(nextFloor);
        buf.writeVarInt(countdowns.size());
        for (Countdown c : countdowns) {
            buf.writeVarInt(c.id());
            buf.writeUtf(c.text(), 256);
            buf.writeVarLong(c.remainingMillis());
        }
    }

    public static WorldStateS2C decode(FriendlyByteBuf buf) {
        int day = buf.readVarInt();
        int chapter = buf.readVarInt();
        long bits = buf.readLong();
        int next = buf.readVarInt();
        int n = Math.min(64, buf.readVarInt());
        List<Countdown> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Countdown(buf.readVarInt(), buf.readUtf(256), buf.readVarLong()));
        }
        return new WorldStateS2C(day, chapter, bits, next, List.copyOf(list));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleWorldState(this));
    }
}
