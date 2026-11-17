package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
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
 * スキルの演出（Phase 0-B 5章 {@code SkillEffect}：誰がどのスキルを使ったか、アニメーションと演出の番号）。
 *
 * <p>パーティクルや大きな演出はクライアントで作る（Phase 0-B 11章）。サーバーは「何が・どこで」だけを、近くの人に送る。</p>
 */
public final class SkillFxS2C {
    public enum Phase {
        /** 詠唱を始めた（{@code ticks} は詠唱の長さ）。 */
        CAST_START,
        /** 詠唱をやめた・失敗した。 */
        CAST_CANCEL,
        /** スキルを放った（{@code ticks} は陣が残る時間や、着弾までの遅れ）。 */
        RELEASE,
        /** 遅れて落ちる攻撃が着弾した・跳躍で着地した・弾が当たった。 */
        IMPACT,
        /** 溜めを始めた（{@code ticks} は溜めきるまでの時間）。 */
        CHARGE_START,
        /** 構えていたスキルが発動した（受け流しの反撃。{@code to} は攻撃してきた相手）。 */
        TRIGGER;

        private static final Phase[] VALUES = values();

        static Phase byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : RELEASE;
        }
    }

    /** 演出を送る範囲（ブロック）。 */
    public static final double RANGE = 64.0;
    private static final int MAX_TARGETS = 32;

    private final Phase phase;
    private final int casterId;
    private final int skillIndex;
    private final Vec3 from;
    private final Vec3 to;
    private final float radius;
    private final int ticks;
    private final int[] targets;
    /** 強さの目安：溜めるスキルは溜めの割合（0〜1）、ほかは強化の倍率（1 ＝ ふつう）。演出の大きさに使う。 */
    private final float power;

    public SkillFxS2C(Phase phase, int casterId, int skillIndex, Vec3 from, Vec3 to, float radius, int ticks, int[] targets) {
        this(phase, casterId, skillIndex, from, to, radius, ticks, targets, 1.0F);
    }

    public SkillFxS2C(Phase phase, int casterId, int skillIndex, Vec3 from, Vec3 to, float radius, int ticks, int[] targets,
                      float power) {
        this.power = power;
        this.phase = phase;
        this.casterId = casterId;
        this.skillIndex = skillIndex;
        this.from = from;
        this.to = to;
        this.radius = radius;
        this.ticks = ticks;
        this.targets = targets;
    }

    /** {@code at} の近くにいるプレイヤーに送る。 */
    public void sendNear(ResourceKey<Level> dimension, Vec3 at) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, dimension)), this);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase.ordinal());
        buf.writeVarInt(casterId);
        buf.writeVarInt(skillIndex);
        writeVec(buf, from);
        writeVec(buf, to);
        buf.writeFloat(radius);
        buf.writeVarInt(ticks);
        int count = Math.min(targets.length, MAX_TARGETS);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(targets[i]);
        }
        buf.writeFloat(power);
    }

    public static SkillFxS2C decode(FriendlyByteBuf buf) {
        Phase phase = Phase.byIndex(buf.readByte());
        int casterId = buf.readVarInt();
        int skillIndex = buf.readVarInt();
        Vec3 from = readVec(buf);
        Vec3 to = readVec(buf);
        float radius = buf.readFloat();
        int ticks = buf.readVarInt();
        int count = Math.max(0, Math.min(buf.readByte(), MAX_TARGETS));
        int[] targets = new int[count];
        for (int i = 0; i < count; i++) {
            targets[i] = buf.readVarInt();
        }
        float power = buf.readFloat();
        return new SkillFxS2C(phase, casterId, skillIndex, from, to, radius, ticks, targets, power);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSkillFx(this));
    }

    public Phase phase() {
        return phase;
    }

    public int casterId() {
        return casterId;
    }

    public int skillIndex() {
        return skillIndex;
    }

    public Vec3 from() {
        return from;
    }

    public Vec3 to() {
        return to;
    }

    public float radius() {
        return radius;
    }

    public int ticks() {
        return ticks;
    }

    public int[] targets() {
        return targets;
    }

    public float power() {
        return power;
    }
}
