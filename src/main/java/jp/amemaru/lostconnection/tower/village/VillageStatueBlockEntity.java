package jp.amemaru.lostconnection.tower.village;

import jp.amemaru.lostconnection.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 凍った村人の氷像（6階「凍結した村」。11月25日）。どの姿か（{@code skin}：村人の見た目の名前）・どんな格好か（{@code pose}）・
 * 向き・大きさ・記憶の氷像なら、その記憶の名前（{@code memory}。ふつうの氷像は ""）。地図（tools/mapgen/build_floor6.py）が
 * 構造物の中に書き、{@link VillageStatueRenderer} が人の形に描く。顔は白く抜けている（ロストの侵食。Phase 0-A 9章の6階）。
 */
public class VillageStatueBlockEntity extends BlockEntity {
    private String skin = "man";
    private String pose = "stand";
    private float yaw;
    private float scale = 1.0F;
    private String memory = "";

    public VillageStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_STATUE.get(), pos, state);
    }

    public String skin() {
        return skin;
    }

    public String pose() {
        return pose;
    }

    public float yaw() {
        return yaw;
    }

    public float scale() {
        return scale;
    }

    public String memory() {
        return memory;
    }

    /** 記憶の氷像か（熱の結晶で溶かせる）。 */
    public boolean isMemory() {
        return !memory.isEmpty();
    }

    /** 管理者・自動テスト：中身を決める。 */
    public void set(String skin, String pose, float yaw, float scale, String memory) {
        this.skin = skin;
        this.pose = pose;
        this.yaw = yaw;
        this.scale = scale;
        this.memory = memory;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        skin = tag.contains("skin") ? tag.getString("skin") : "man";
        pose = tag.contains("pose") ? tag.getString("pose") : "stand";
        yaw = tag.getFloat("yaw");
        scale = tag.contains("scale") ? Math.max(0.3F, Math.min(2.5F, tag.getFloat("scale"))) : 1.0F;
        memory = tag.getString("memory");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("skin", skin);
        tag.putString("pose", pose);
        tag.putFloat("yaw", yaw);
        tag.putFloat("scale", scale);
        tag.putString("memory", memory);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 描く範囲（人の背丈ぶん上まで。描く前に、見えない所を省くため）。 */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 1.0, p.getY(), p.getZ() - 1.0, p.getX() + 2.0, p.getY() + 3.0, p.getZ() + 2.0);
    }
}
