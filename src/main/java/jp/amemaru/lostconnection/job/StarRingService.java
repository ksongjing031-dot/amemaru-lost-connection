package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 星の環（ウィザード。Phase 6 その2）：自分のまわりを星が巡り、近づいた敵にぶつかる。1つぶつかるごとに星は減る。
 */
public final class StarRingService {
    /** 星がぶつかる距離。 */
    private static final double REACH = 3.0;
    /** ぶつかるのを見る間（tick）。 */
    private static final int INTERVAL = 5;

    /** 星の環を使ったときのスキルの情報（ダメージの持ち主・演出に使う）。 */
    private static final Map<UUID, SkillContext> CONTEXTS = new HashMap<>();

    private StarRingService() {
    }

    static void start(ServerPlayer player, SkillContext ctx) {
        CONTEXTS.put(player.getUUID(), ctx);
    }

    /** 毎 tick（{@code SkillService}）。 */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        BuffState buffs = data.getBuffs();
        if (!buffs.starRing(now)) {
            if (CONTEXTS.remove(player.getUUID()) != null) {
                player.removeEffect(ModEffects.STAR_RING.get());
                StatusService.sync(player, StatusFxS2C.Event.NONE);
            }
            return;
        }
        if (player.tickCount % INTERVAL != 0) {
            return;
        }
        SkillContext ctx = CONTEXTS.get(player.getUUID());
        if (ctx == null) {
            return;
        }
        List<LivingEntity> near = SkillTargeting.enemiesNear(player, player.serverLevel(), player.position(), REACH);
        if (near.isEmpty()) {
            return;
        }
        LivingEntity target = near.get(0);
        buffs.starRingCount--;
        ctx.currentMagic = true; // 星の環は魔力のダメージ
        double dealt = CombatService.dealSkillDamage(ctx, target, buffs.starRingPower * ctx.passiveDamageMultiplier(target), null);
        ctx.currentMagic = false;
        if (dealt > 0) {
            StatusService.addBrand(target, 1, 160, player);
        }
        new SkillFxS2C(SkillFxS2C.Phase.TRIGGER, player.getId(), ctx.skillIndex, player.getEyePosition(),
                target.getBoundingBox().getCenter(), 0.5F, buffs.starRingCount, new int[]{target.getId()}, 1.0F)
                .sendNear(player.level().dimension(), player.position());
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_HIT,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        if (buffs.starRingCount <= 0) {
            buffs.starRingUntil = 0;
            CONTEXTS.remove(player.getUUID());
            player.removeEffect(ModEffects.STAR_RING.get());
        }
        StatusService.sync(player, StatusFxS2C.Event.NONE);
    }

    public static void forget(UUID player) {
        CONTEXTS.remove(player);
    }

    public static void clear() {
        CONTEXTS.clear();
    }
}
