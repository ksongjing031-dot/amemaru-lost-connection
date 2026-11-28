package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.job.LockOnFacing;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * スキルを使う（Phase 0-B 5章 {@code UseSkill}：スキル枠の番号、向きと狙った相手）。
 *
 * <p>送るのは「どの枠を・何を狙って・押したか離したか」だけ。射程・視線・クールダウン・MPはサーバーで確かめる。
 * 番号（{@code sequence}）は、結果（{@link SkillResultS2C}）と手元のモーションを結びつけるために使う。
 * 溜めるスキルは、押したとき（{@link #PRESS}）に溜めを始め、離したとき（{@link #RELEASE}）に放つ。</p>
 * <p>ロックオン（11月24日）している間は、ロックした敵の番号（{@code faceEntity}）も送る。サーバーは使う前に、
 * その敵のほうへ向きを合わせる（扇・線・飛び道具も、ロックした敵に向かう）。</p>
 */
public final class UseSkillC2S {
    public static final int PRESS = 0;
    public static final int RELEASE = 1;
    /** 11月28日：ユニーク武器の固有スキル（スキル枠ではなく、手に持った武器の技。初期値のキーはY）。 */
    public static final int UNIQUE_SLOT = 15;

    private final int sequence;
    private final int action;
    private final int slot;
    private final int targetEntity;
    @Nullable
    private final Vec3 targetPoint;
    /** ロックした敵（向きを合わせる相手。ないときは -1）。 */
    private final int faceEntity;

    public UseSkillC2S(int sequence, int slot, int targetEntity, @Nullable Vec3 targetPoint) {
        this(sequence, PRESS, slot, targetEntity, targetPoint, -1);
    }

    public UseSkillC2S(int sequence, int action, int slot, int targetEntity, @Nullable Vec3 targetPoint) {
        this(sequence, action, slot, targetEntity, targetPoint, -1);
    }

    public UseSkillC2S(int sequence, int action, int slot, int targetEntity, @Nullable Vec3 targetPoint, int faceEntity) {
        this.action = action == RELEASE ? RELEASE : PRESS;
        this.sequence = sequence;
        this.slot = slot;
        this.targetEntity = targetEntity;
        this.targetPoint = targetPoint;
        this.faceEntity = faceEntity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sequence);
        buf.writeByte(action);
        buf.writeByte(slot);
        buf.writeVarInt(targetEntity + 1);
        buf.writeBoolean(targetPoint != null);
        if (targetPoint != null) {
            buf.writeDouble(targetPoint.x);
            buf.writeDouble(targetPoint.y);
            buf.writeDouble(targetPoint.z);
        }
        buf.writeVarInt(faceEntity + 1);
    }

    public static UseSkillC2S decode(FriendlyByteBuf buf) {
        int sequence = buf.readVarInt();
        int action = buf.readByte();
        int slot = buf.readByte();
        int entity = buf.readVarInt() - 1;
        Vec3 point = null;
        if (buf.readBoolean()) {
            point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        int face = buf.readVarInt() - 1;
        return new UseSkillC2S(sequence, action, slot, entity, point, face);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || slot < 0 || (slot >= PlayerData.LOADOUT_SIZE && slot != UNIQUE_SLOT)) {
            return;
        }
        if (targetPoint != null && (!Double.isFinite(targetPoint.x) || !Double.isFinite(targetPoint.y) || !Double.isFinite(targetPoint.z))) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            if (faceEntity >= 0) {
                LockOnFacing.face(player, faceEntity);
            } else {
                LockOnFacing.forget(player);
            }
            SkillService.Result result;
            if (slot == UNIQUE_SLOT) {
                // 固有スキルは溜めないので、押したときだけ使う
                result = action == RELEASE ? new SkillService.Result(SkillService.Failure.NOT_CHARGING)
                        : jp.amemaru.lostconnection.equipment.UniqueSkillService.tryUse(player, data, targetEntity, targetPoint);
            } else {
                result = action == RELEASE
                        ? SkillService.tryRelease(player, data, slot, targetEntity, targetPoint)
                        : SkillService.tryUse(player, data, slot, targetEntity, targetPoint);
            }
            ModNetwork.sendTo(player, new SkillResultS2C(sequence, result.ok(),
                    result.failure() == null ? -1 : result.failure().ordinal()));
        });
    }
}
