package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * 相手の「独自のHP」と防御の読み方（Phase 0-B 2章：敵とボスも独自のHPを持つ）。
 *
 * <ul>
 *   <li>プレイヤー：プレイヤーデータのHPと防御。</li>
 *   <li>訓練人形：人形が持つHPと防御。</li>
 *   <li>このModの敵（Phase 3）：敵が持つ独自のHPと、敵の定義の防御。</li>
 *   <li>そのほかの魔物（バニラ）：バニラの体力 × config の倍率をHPとし、防具の値 × 倍率を防御とする。
 *       独自のHPとバニラの体力はいつも同じ割合なので、ダメージは「独自のダメージ ÷ 倍率」をバニラの体力から引けばよい。</li>
 * </ul>
 */
public final class TargetVitals {
    private TargetVitals() {
    }

    public static double maxHp(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return data(player).map(data -> data.getStats().maxHp()).orElse((double) player.getMaxHealth());
        }
        if (entity instanceof TrainingDummy dummy) {
            return dummy.getDummyMaxHp();
        }
        if (entity instanceof AmemaruMob mob) {
            return mob.getEnemyMaxHp();
        }
        return entity.getMaxHealth() * vanillaMultiplier();
    }

    public static double hp(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return data(player).map(PlayerData::getHp).orElse((double) player.getHealth());
        }
        if (entity instanceof TrainingDummy dummy) {
            return dummy.getDummyHp();
        }
        if (entity instanceof AmemaruMob mob) {
            return mob.getEnemyHp();
        }
        return entity.getHealth() * vanillaMultiplier();
    }

    /**
     * 受けるダメージの倍率（Phase 5：「結晶の加護」。管理者が上げると、その階のボスへの与ダメージが +5% ずつ、最大 +20%。
     * Phase 0-A 11章）。
     */
    public static double takenMultiplier(LivingEntity entity) {
        return takenMultiplier(entity, null);
    }

    /**
     * 受けるダメージの倍率。{@code attacker} は打った人（分からなければ null）。Phase 8 その2（3階）：敵ごとの受け方
     * （{@link AmemaruMob#damageTakenMultiplier}：正面の装甲・水の中・ボスの炉心）も掛ける。
     */
    public static double takenMultiplier(LivingEntity entity, @javax.annotation.Nullable net.minecraft.world.entity.Entity attacker) {
        double multiplier = 1.0;
        if (entity instanceof jp.amemaru.lostconnection.enemy.boss.AmemaruBoss boss && boss.room() != null && boss.getServer() != null) {
            multiplier *= jp.amemaru.lostconnection.progression.ProgressionService.blessingMultiplier(boss.getServer(),
                    boss.room().floor().number());
        }
        if (entity instanceof AmemaruMob mob) {
            multiplier *= mob.damageTakenMultiplier(attacker);
        }
        return multiplier;
    }

    /** 防御（防御崩しの間は {@link StatusService#ARMOR_BREAK_DEFENSE} だけ下がる）。 */
    public static double defense(LivingEntity entity) {
        double base;
        if (entity instanceof ServerPlayer player) {
            base = data(player).map(data -> data.getStats().get(StatType.DEFENSE)).orElse(0.0);
        } else if (entity instanceof TrainingDummy dummy) {
            base = dummy.getDummyDefense();
        } else if (entity instanceof AmemaruMob mob) {
            base = mob.defense();
        } else {
            base = entity.getArmorValue() * ServerConfig.VANILLA_ARMOR_TO_DEFENSE.get();
        }
        if (StatusService.armorBroken(entity)) {
            base *= 1.0 - StatusService.ARMOR_BREAK_DEFENSE;
        }
        return base;
    }

    /** バニラの魔物のHPの倍率。 */
    public static double vanillaMultiplier() {
        return Math.max(0.1, ServerConfig.VANILLA_MOB_HP_MULTIPLIER.get());
    }

    private static Optional<PlayerData> data(ServerPlayer player) {
        return PlayerDataCapability.get(player).filter(PlayerData::isStatsReady);
    }
}
