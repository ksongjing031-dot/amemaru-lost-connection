package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 敵視を集める（ナイトの「呼び声」）。決まった時間、魔物の狙いをナイトに固定する。
 *
 * <p>Phase 3でこのModの敵がヘイトの表を持つようになったら、表の一番上に置く形に変える。それまでは、バニラの魔物の
 * 「狙い」を書きかえる。狙いを変えようとしたら、ナイトに戻す。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class TauntService {
    private record Taunt(UUID taunter, long until) {
    }

    private static final Map<UUID, Taunt> TAUNTS = new HashMap<>();

    private TauntService() {
    }

    /** 魔物の狙いを、決まった時間 {@code taunter} に向ける。 */
    public static void taunt(LivingEntity target, ServerPlayer taunter, int ticks, long now) {
        if (!(target instanceof Mob mob)) {
            return;
        }
        TAUNTS.put(mob.getUUID(), new Taunt(taunter.getUUID(), now + ticks));
        mob.setTarget(taunter);
        if (mob instanceof NeutralMob neutral) {
            neutral.setPersistentAngerTarget(taunter.getUUID());
            neutral.startPersistentAngerTimer();
        }
    }

    /** 今その魔物を引きつけているプレイヤー（いなければ null）。 */
    public static UUID taunterOf(Entity entity, long now) {
        Taunt taunt = TAUNTS.get(entity.getUUID());
        return taunt != null && now < taunt.until ? taunt.taunter : null;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Taunt taunt = TAUNTS.get(entity.getUUID());
        if (taunt == null || level.getGameTime() >= taunt.until) {
            return;
        }
        ServerPlayer taunter = level.getServer().getPlayerList().getPlayer(taunt.taunter);
        if (taunter == null || !taunter.isAlive() || taunter.level() != level) {
            return;
        }
        if (event.getNewTarget() != taunter) {
            event.setNewTarget(taunter);
        }
    }

    /** 時間切れを消し、狙いがずれた魔物を戻す（0.5秒ごと）。 */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Taunt>> it = TAUNTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Taunt> entry = it.next();
            Taunt taunt = entry.getValue();
            ServerPlayer taunter = server.getPlayerList().getPlayer(taunt.taunter);
            if (taunter == null || !taunter.isAlive()) {
                it.remove();
                continue;
            }
            ServerLevel level = taunter.serverLevel();
            if (level.getGameTime() >= taunt.until) {
                it.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if (mob.getTarget() != taunter) {
                mob.setTarget(taunter);
            }
        }
    }

    public static void clear() {
        TAUNTS.clear();
    }
}
