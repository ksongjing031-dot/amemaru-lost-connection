package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.job.def.BuffKind;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ナイトの「守り手の心」（Phase 6 その2）：まわりの仲間のHPが3割を切ると、誓気が20たまり、5秒受けるダメージが減る（20秒に1回）。
 */
public final class GuardianHeart {
    private static final double OATH = 20.0;
    private static final int PROTECT_TICKS = 100;
    private static final int COOLDOWN = 400;

    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private GuardianHeart() {
    }

    /** 仲間のHPが3割を切った。 */
    public static void onAllyLow(ServerPlayer ally, long now) {
        for (ServerPlayer knight : ally.serverLevel().players()) {
            if (knight == ally || !knight.isAlive()) {
                continue;
            }
            PlayerData data = PlayerDataCapability.get(knight).orElse(null);
            PassiveService.Active heart = data == null ? null : PassiveService.find(data, PassiveDef.Type.GUARDIAN_HEART);
            if (heart == null || knight.distanceToSqr(ally) > heart.radius() * heart.radius()) {
                continue;
            }
            Long ready = READY_AT.get(knight.getUUID());
            if (ready != null && now < ready) {
                continue;
            }
            READY_AT.put(knight.getUUID(), now + COOLDOWN);
            GaugeService.add(data, OATH);
            SkillExecutor.applyBuff(knight, BuffKind.PROTECT, PROTECT_TICKS, heart.value(), now, knight);
            knight.level().playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS,
                    0.4F, 1.6F);
        }
    }

    public static void clear() {
        READY_AT.clear();
    }
}
