package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 頭の上に出す数字（Phase 0-B 5章 {@code CombatText}：ダメージの数字、連携の成立）。
 *
 * <p>相手が見えているプレイヤーにだけ送る（{@link PacketDistributor#TRACKING_ENTITY_AND_SELF}）。
 * 連携の成立のときは、関わったプレイヤーのエンティティ番号も送り、その人たちの画面には大きな文字を出す。</p>
 */
public final class CombatTextS2C {
    public enum Kind {
        /** 与えたダメージ。 */
        DAMAGE,
        /** 会心のダメージ。 */
        CRIT,
        /** プレイヤーが受けたダメージ。 */
        TAKEN,
        /** 回復。 */
        HEAL,
        /** MPの回復・受け渡し。 */
        MP,
        /** 連携！ */
        LINK,
        /** 三連携！ */
        LINK3,
        /** 連携支援。 */
        SUPPORT,
        /** 肩代わり（盾結び）。 */
        SHARED,
        /** 障壁が吸ったダメージ。 */
        ABSORBED,
        /** 受け流し！ */
        PARRY,
        /** 砕けた！（凍結を砕いた） */
        SHATTER,
        /** 凍結！ */
        FROZEN;

        private static final Kind[] VALUES = values();

        static Kind byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : DAMAGE;
        }
    }

    private static final int MAX_INVOLVED = 16;

    private final int entityId;
    private final float amount;
    private final Kind kind;
    private final int[] involved;

    public CombatTextS2C(int entityId, float amount, Kind kind, int[] involved) {
        this.entityId = entityId;
        this.amount = amount;
        this.kind = kind;
        this.involved = involved;
    }

    /** 相手が見えているプレイヤーに送る。 */
    public static void send(Entity target, double amount, Kind kind) {
        send(target, amount, kind, new int[0]);
    }

    /** 相手が見えているプレイヤーに送る（連携に関わったプレイヤーつき）。 */
    public static void send(Entity target, double amount, Kind kind, int[] involved) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new CombatTextS2C(target.getId(), (float) amount, kind, involved));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(amount);
        buf.writeByte(kind.ordinal());
        int count = Math.min(involved.length, MAX_INVOLVED);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(involved[i]);
        }
    }

    public static CombatTextS2C decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        float amount = buf.readFloat();
        Kind kind = Kind.byIndex(buf.readByte());
        int count = Math.min(buf.readByte(), MAX_INVOLVED);
        int[] involved = new int[Math.max(0, count)];
        for (int i = 0; i < involved.length; i++) {
            involved[i] = buf.readVarInt();
        }
        return new CombatTextS2C(entityId, amount, kind, involved);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleCombatText(this));
    }

    public int entityId() {
        return entityId;
    }

    public float amount() {
        return amount;
    }

    public Kind kind() {
        return kind;
    }

    public int[] involved() {
        return involved;
    }
}
