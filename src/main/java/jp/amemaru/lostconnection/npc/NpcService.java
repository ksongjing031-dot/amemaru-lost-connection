package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * NPCを立つ所に置く（街を建てたとき・管理者のコマンド）。立つ所は、NPCの定義の {@code home}（中央広場の真ん中からの位置）。
 * 同じNPCがすでにいれば、消してから置き直す（重ならない）。
 */
public final class NpcService {
    /** 同じNPCを探す範囲（街の範囲を包む）。 */
    private static final double SEARCH = 256.0;

    private NpcService() {
    }

    /** 街のNPCを、すべて立つ所に置く。置いた数を返す。 */
    public static int spawnAll(ServerLevel level, BlockPos cityOrigin) {
        int count = 0;
        for (NpcDef def : NpcDefs.all().values()) {
            if (def.home() != null && "conelia".equals(def.home().place())) {
                // 決まった日・物語フラグから現れるNPC（ノア・エルネスト。Phase 6 その3）は、まだなら置かない
                if (def.appears() != null && !def.appears().testServer(level.getServer())) {
                    remove(level, cityOrigin, def.id());
                    continue;
                }
                if (spawn(level, cityOrigin, def) != null) {
                    count++;
                }
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Placed {} NPC(s) in the city", count);
        return count;
    }

    /** 1人を置く（すでにいれば置き直す）。 */
    public static NpcEntity spawn(ServerLevel level, BlockPos cityOrigin, NpcDef def) {
        NpcDef.Home home = def.home();
        if (home == null) {
            return null;
        }
        Vec3 pos = new Vec3(cityOrigin.getX() + home.pos().x, cityOrigin.getY() + home.pos().y, cityOrigin.getZ() + home.pos().z);
        level.getChunkAt(BlockPos.containing(pos));
        remove(level, cityOrigin, def.id());
        NpcEntity npc = ModEntities.NPC.get().create(level);
        if (npc == null) {
            return null;
        }
        npc.setNpcId(def.id());
        npc.moveTo(pos.x, pos.y, pos.z, home.yaw(), 0.0F);
        npc.setYHeadRot(home.yaw());
        npc.setYBodyRot(home.yaw());
        npc.setHome(pos, home.yaw());
        level.addFreshEntity(npc);
        return npc;
    }

    /** そのNPCを消す（読み込まれている所だけ）。消した数を返す。 */
    public static int remove(ServerLevel level, BlockPos around, ResourceLocation id) {
        AABB box = new AABB(around).inflate(SEARCH, 128.0, SEARCH);
        List<NpcEntity> found = new ArrayList<>(level.getEntitiesOfClass(NpcEntity.class, box, npc -> id.equals(npc.npcId())));
        for (Entity entity : found) {
            entity.discard();
        }
        return found.size();
    }

    /** その所に、職業の登録の窓口のNPC（ギルドの受付）がいるか（職業の登録の画面を、NPCとの会話から開いたとき）。 */
    public static boolean isRegistrarAt(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(3.0);
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, box)) {
            NpcDef def = npc.def();
            if (def != null && def.hasRole("job_registration")) {
                return true;
            }
        }
        return false;
    }
}
