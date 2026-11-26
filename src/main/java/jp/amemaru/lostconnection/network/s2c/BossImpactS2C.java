package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * ボスの技が当たった瞬間の演出（11月27日のご指示「ボスの攻撃エフェクトがほぼ存在しない、攻撃範囲が見えるだけなので、ボス特有の
 * 攻撃時にプレイヤーがスキルを使った時のようなエフェクトを追加する」【補完】）。
 *
 * <p>形（{@code shape}）は予兆と同じ（{@code AmemaruMob.TELEGRAPH_*}：扇・線・円・形なし）。クライアントの {@code BossImpactFx} が、
 * ボスごとの色と材質（草原の土・森の葉・水路の水…）で、斬撃・地割れ・衝撃波・光の柱などを出す。</p>
 */
public final class BossImpactS2C {
    /** 届ける範囲（ブロック）。 */
    private static final double RANGE = 80.0;

    private final int entityId;
    private final String boss;
    private final String skill;
    private final int shape;
    private final Vec3 at;
    private final float yaw;
    private final float size;
    private final float arg;

    public BossImpactS2C(int entityId, String boss, String skill, int shape, Vec3 at, float yaw, float size, float arg) {
        this.entityId = entityId;
        this.boss = boss;
        this.skill = skill;
        this.shape = shape;
        this.at = at;
        this.yaw = yaw;
        this.size = size;
        this.arg = arg;
    }

    public static BossImpactS2C decode(FriendlyByteBuf buf) {
        return new BossImpactS2C(buf.readVarInt(), buf.readUtf(64), buf.readUtf(64), buf.readByte(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(boss, 64);
        buf.writeUtf(skill, 64);
        buf.writeByte(shape);
        buf.writeDouble(at.x);
        buf.writeDouble(at.y);
        buf.writeDouble(at.z);
        buf.writeFloat(yaw);
        buf.writeFloat(size);
        buf.writeFloat(arg);
    }

    /** まわりの人に送る。 */
    public void sendNear(ResourceKey<Level> dimension) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, dimension)), this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.amemaru.lostconnection.client.BossImpactFx.play(this));
    }

    public int entityId() {
        return entityId;
    }

    public String boss() {
        return boss;
    }

    public String skill() {
        return skill;
    }

    public int shape() {
        return shape;
    }

    public Vec3 at() {
        return at;
    }

    public float yaw() {
        return yaw;
    }

    public float size() {
        return size;
    }

    public float arg() {
        return arg;
    }
}
