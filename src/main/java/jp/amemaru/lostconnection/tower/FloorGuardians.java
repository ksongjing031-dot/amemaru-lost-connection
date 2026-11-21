package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 石柱の守り手（11月22日【補完】。1階の西の丘の「群れの長」）。決まった所に1体だけ、人が近づいたら出す。倒されたら決まった秒数の
 * あとにまた出す（ほかの人も課題をこなせるように）。倒した人とパーティの石柱の課題は {@link TowerService#creditGuardian} が記録する。
 */
public final class FloorGuardians {
    private final FloorDef def;
    private final Map<String, UUID> alive = new HashMap<>();
    private final Map<String, Long> respawnAt = new HashMap<>();

    public FloorGuardians(FloorDef def) {
        this.def = def;
    }

    public void tick(ServerLevel level, List<ServerPlayer> onFloor, long now) {
        for (FloorDef.Guardian guardian : def.extras().guardians()) {
            UUID id = alive.get(guardian.id());
            if (id != null) {
                Entity entity = level.getEntity(id);
                if (entity != null && entity.isAlive()) {
                    continue;
                }
                alive.remove(guardian.id());
                // 倒されていなくても（区画が閉じて消えた）、少し待ってから出し直す
                respawnAt.putIfAbsent(guardian.id(), now + (entity == null ? 20L * 5 : 20L * guardian.respawnSeconds()));
            }
            if (now < respawnAt.getOrDefault(guardian.id(), 0L)) {
                continue;
            }
            Vec3 at = def.world(guardian.pos());
            double range = guardian.activation();
            boolean near = false;
            for (ServerPlayer player : onFloor) {
                if (!player.isSpectator() && player.position().distanceToSqr(at) <= range * range) {
                    near = true;
                    break;
                }
            }
            if (!near || !level.hasChunkAt(BlockPos.containing(at))) {
                continue;
            }
            AmemaruMob mob = spawn(level, guardian, at);
            if (mob != null) {
                alive.put(guardian.id(), mob.getUUID());
                respawnAt.remove(guardian.id());
            }
        }
    }

    private AmemaruMob spawn(ServerLevel level, FloorDef.Guardian guardian, Vec3 at) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(guardian.enemy());
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof AmemaruMob mob)) {
            return null;
        }
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        mob.setHome(BlockPos.containing(at));
        mob.setTransient(true);
        if (guardian.variant() != null) {
            mob.setVariant(guardian.variant());
        }
        mob.setGuardian(def.number(), guardian.id());
        level.addFreshEntity(mob);
        AmemaruMod.LOGGER.info("[amemaru] Guardian {} of floor {} appeared", guardian.id(), def.number());
        return mob;
    }

    /** 守り手が倒れた：決まった秒数のあとにまた出す。 */
    public void onDefeated(String guardianId, long now) {
        FloorDef.Guardian guardian = def.extras().guardian(guardianId);
        alive.remove(guardianId);
        if (guardian != null) {
            respawnAt.put(guardianId, now + 20L * guardian.respawnSeconds());
        }
    }

    /** いま出ている守り手（管理者の確かめ用）。 */
    public boolean isAlive(ServerLevel level, String guardianId) {
        UUID id = alive.get(guardianId);
        Entity entity = id == null ? null : level.getEntity(id);
        return entity != null && entity.isAlive();
    }

    /** すぐ出せるようにする（管理者・自動の確かめ用）。 */
    public void allowNow(String guardianId) {
        respawnAt.remove(guardianId);
    }

    /** 出ている守り手を消す（階を建て直したとき）。 */
    public void clear(ServerLevel level) {
        for (UUID id : alive.values()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        alive.clear();
        respawnAt.clear();
    }
}
