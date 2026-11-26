package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 覚醒の演出（11月27日）。{@code AWAKEN}：覚醒したプレイヤーのまわりに、職業の色の魔法陣と光の柱。{@code ECHO}：覚醒の「もう一度」が
 * 当たった相手に、職業の色の衝撃波と光。クライアントは {@code AwakenFx}。
 */
public final class AwakenFxS2C {
    private static final double RANGE = 48.0;

    public enum Kind {
        AWAKEN,
        ECHO
    }

    private final Kind kind;
    private final int entityId;
    private final int color;

    public AwakenFxS2C(Kind kind, int entityId, int color) {
        this.kind = kind;
        this.entityId = entityId;
        this.color = color;
    }

    public void sendNear(ServerLevel level, Vec3 at) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, level.dimension())), this);
    }

    public static AwakenFxS2C decode(FriendlyByteBuf buf) {
        return new AwakenFxS2C(buf.readEnum(Kind.class), buf.readVarInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(entityId);
        buf.writeInt(color);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.amemaru.lostconnection.client.AwakenFx.play(this));
    }

    public Kind kind() {
        return kind;
    }

    public int entityId() {
        return entityId;
    }

    public int color() {
        return color;
    }
}
