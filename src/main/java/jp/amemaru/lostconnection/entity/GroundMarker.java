package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 地面の印（11月22日）：ボスの技の当たる所を、敵の体から離れた所（跳んで降りる所・人の足もと）に赤い円で出す。
 * 見えない小さなもので、決まった時間がたつと消える。保存しない（区画が閉じたら消える）。当たり判定はサーバーの技が持つ（印は見せるだけ）。
 */
public class GroundMarker extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(GroundMarker.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TICKS = SynchedEntityData.defineId(GroundMarker.class, EntityDataSerializers.INT);
    /** 印の種類（色）。11月24日 */
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(GroundMarker.class, EntityDataSerializers.INT);
    /** 赤：よける所。 */
    public static final int KIND_DANGER = 0;
    /** 青：みんなで集まって受ける所（ボスの「みんなで受ける一撃」）。 */
    public static final int KIND_SHARE = 1;
    /** 緑：芽吹く所（2階のボスの「芽吹きの種」。11月25日）。 */
    public static final int KIND_SEED = 2;
    /** 白：ほかの人から離れる所（5階のボスの「切断判定」。11月25日）。 */
    public static final int KIND_SEVER = 3;
    /** 金：逃げこむ所（安全な所。11月25日：4階のボスの「残響の色」の床のまわり）。 */
    public static final int KIND_SAFE = 4;
    /** 音の色（白・青・金・紅）の、逃げこむ所（4階のボスの「残響の色」。KIND_NOTE + 音の番号）。 */
    public static final int KIND_NOTE = 10;
    /** 打ったあと、明るく見せておく長さ（tick） */
    private static final int AFTER_TICKS = 4;

    private int life;

    public GroundMarker(EntityType<? extends GroundMarker> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    /** 印を置く（{@code ticks} のあと技が当たる。その少しあとで消える）。 */
    @Nullable
    public static GroundMarker place(ServerLevel level, Vec3 at, float radius, int ticks) {
        return place(level, at, radius, ticks, KIND_DANGER);
    }

    /** 種類（{@link #KIND_DANGER}・{@link #KIND_SHARE}）を決めて印を置く。 */
    @Nullable
    public static GroundMarker place(ServerLevel level, Vec3 at, float radius, int ticks, int kind) {
        GroundMarker marker = ModEntities.GROUND_MARKER.get().create(level);
        if (marker == null) {
            return null;
        }
        marker.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        marker.entityData.set(DATA_RADIUS, radius);
        marker.entityData.set(DATA_TICKS, Math.max(1, ticks));
        marker.entityData.set(DATA_KIND, kind);
        marker.life = Math.max(1, ticks) + AFTER_TICKS;
        level.addFreshEntity(marker);
        return marker;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 1.0F);
        entityData.define(DATA_TICKS, 20);
        entityData.define(DATA_KIND, KIND_DANGER);
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public int ticks() {
        return entityData.get(DATA_TICKS);
    }

    /** クライアント：置いてから技が当たるまでの割合（0〜1）。 */
    public float progress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / Math.max(1, ticks()), 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && --life <= 0) {
            discard();
        }
    }

    /** 円は体より大きいので、画面の外かどうかは円の大きさで決める。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double r = radius() + 0.5;
        return getBoundingBox().inflate(r, 0.5, r);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
