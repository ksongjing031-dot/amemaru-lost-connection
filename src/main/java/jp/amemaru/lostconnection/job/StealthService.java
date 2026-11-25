package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 潜伏（Phase 6 その2：ローグの煙玉・気配消し）。潜伏している間、まわりの魔物はその人を見失い、新しく狙うこともない。
 * 自分から攻撃しても、潜伏の時間が切れるまでは見失われたまま（煙玉は逃げるための技）。
 *
 * <p>気配消し：戦いの間、3秒攻撃しないでいると潜伏し、次の一撃のダメージが上がる（{@code HitHooks}）。攻撃すると解ける。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class StealthService {
    /** 見失わせる範囲。 */
    private static final double RANGE = 32.0;
    /** 気配消しで潜伏するまでの、攻撃しない時間（tick）。 */
    public static final int FADE_TICKS = 60;

    private StealthService() {
    }

    /** 潜伏させる（すでに潜伏していれば、長い方の時間）。 */
    public static void hide(ServerPlayer player, int ticks) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            long now = player.level().getGameTime();
            BuffState buffs = data.getBuffs();
            boolean fresh = !buffs.stealthed(now);
            buffs.stealthUntil = Math.max(buffs.stealthUntil, now + ticks);
            player.addEffect(new MobEffectInstance(ModEffects.HIDDEN.get(), (int) (buffs.stealthUntil - now), 0, false, false, true));
            loseTrack(player);
            if (fresh) {
                StatusService.sync(player, StatusFxS2C.Event.NONE);
            }
        });
    }

    /** 潜伏を解く（気配消しで潜んでいるときに攻撃した）。 */
    public static void reveal(ServerPlayer player, PlayerData data) {
        BuffState buffs = data.getBuffs();
        if (buffs.stealthUntil == 0) {
            return;
        }
        buffs.stealthUntil = 0;
        player.removeEffect(ModEffects.HIDDEN.get());
        StatusService.sync(player, StatusFxS2C.Event.NONE);
    }

    /** 潜伏しているか。 */
    public static boolean hidden(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        return PlayerDataCapability.get(player).map(data -> data.getBuffs().stealthed(player.level().getGameTime())).orElse(false);
    }

    /** まわりの魔物に、その人を見失わせる。 */
    private static void loseTrack(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), mob -> mob.getTarget() == player)) {
            if (mob instanceof AmemaruMob enemy) {
                enemy.forgetThreat(player.getUUID());
            } else {
                mob.setTarget(null);
            }
        }
    }

    /** 潜伏している人は、新しく狙われない。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && hidden(player)) {
            event.setNewTarget(null);
        }
    }

    /**
     * 毎 tick（{@code SkillService}）：気配消し。戦いの間、3秒攻撃しないでいると潜伏する。
     */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        if (PassiveService.value(data, PassiveDef.Type.FADE) <= 0) {
            return;
        }
        boolean inCombat = now - data.getLastCombatTick() < 200;
        long idle = now - HitHooks.lastAttack(player.getUUID());
        if (inCombat && idle >= FADE_TICKS && !data.getBuffs().stealthed(now)) {
            hide(player, 40);
        } else if (inCombat && idle >= FADE_TICKS) {
            // 潜んでいる間は、時間をのばし続ける（攻撃すると解ける）
            data.getBuffs().stealthUntil = Math.max(data.getBuffs().stealthUntil, now + 20);
        }
    }
}
