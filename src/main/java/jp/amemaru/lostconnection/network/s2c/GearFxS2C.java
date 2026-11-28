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
 * 装備の特殊効果が働いた演出（11月28日のご指示「ユニーク武器や装備には特殊効果や特殊スキルをつけましょう」【補完】：
 * 今まで目に見えなかった特殊効果が、働いた瞬間に分かるようにする）。クライアントは {@code client.UniqueSkillFx#gear}。
 *
 * <ul>
 *   <li>{@code EXTRA}：追加ダメージ（相手に、武器の色の衝撃の輪と火花）</li>
 *   <li>{@code DOUBLE}：二重の当たり（鏡の写しの刃が、もう一度走る）</li>
 *   <li>{@code DRAIN}：吸収（相手から自分へ、緑の光の粒が流れこむ）</li>
 *   <li>{@code EXECUTE}：処刑（弱った相手の上に、翠の環が光る）</li>
 *   <li>{@code WARD}：「第七の門鍵」の認証が、受けたダメージを減らした（その人に、翠の六角の盾が一瞬光る）</li>
 *   <li>{@code WARD_ON}・{@code WARD_OFF}：認証が働きはじめた・やんだ（付けている人の足もとに翠の環。本人の画面に「認証」）</li>
 * </ul>
 */
public final class GearFxS2C {
    private static final double RANGE = 48.0;

    public enum Kind {
        EXTRA,
        DOUBLE,
        DRAIN,
        EXECUTE,
        WARD,
        WARD_ON,
        WARD_OFF
    }

    private final Kind kind;
    private final int sourceId;
    private final int targetId;
    private final int color;

    public GearFxS2C(Kind kind, int sourceId, int targetId, int color) {
        this.kind = kind;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.color = color;
    }

    /** まわり（48ブロック）の人に送る。 */
    public void sendNear(ServerLevel level, Vec3 at) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, level.dimension())), this);
    }

    public static GearFxS2C decode(FriendlyByteBuf buf) {
        return new GearFxS2C(buf.readEnum(Kind.class), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeVarInt(sourceId + 1);
        buf.writeVarInt(targetId + 1);
        buf.writeInt(color);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.amemaru.lostconnection.client.UniqueSkillFx.gear(this));
    }

    public Kind kind() {
        return kind;
    }

    /** 効果を持っている人（なければ -1）。 */
    public int sourceId() {
        return sourceId;
    }

    /** 効いた相手（なければ -1）。 */
    public int targetId() {
        return targetId;
    }

    public int color() {
        return color;
    }
}
