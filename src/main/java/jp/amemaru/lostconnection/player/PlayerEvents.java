package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.SkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * プレイヤーデータの付与・引き継ぎ・同期と、毎 tick の処理（Forgeのイベントバス）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PlayerEvents {
    private PlayerEvents() {
    }

    /**
     * プレイヤーにデータを付ける。
     *
     * <p>体が消えるときの「無効化」の通知（{@code event.addListener}）は、わざと登録しない。
     * 死亡してリスポーンするとき、Forgeは古い体の入り口を閉じてから {@link PlayerEvent.Clone} を出す。
     * {@code reviveCaps()} で開け直せるのは体の側だけで、ここで無効化したデータの入り口は戻らないため、
     * 登録すると新しい体にレベルや振り分けを写せなくなる（Phase 1で見つかった不具合）。</p>
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PlayerDataCapability.ID, new PlayerDataProvider());
        }
    }

    /** 死亡・エンドからの帰還で新しい体ができたとき、全項目を写す（Phase 0-B 4.1）。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        Optional<PlayerData> oldData = PlayerDataCapability.get(original);
        Optional<PlayerData> newData = PlayerDataCapability.get(event.getEntity());
        if (oldData.isPresent() && newData.isPresent()) {
            newData.get().copyFrom(oldData.get());
        } else {
            AmemaruMod.LOGGER.error("[amemaru] Could not copy player data on respawn for {} (old={}, new={})",
                    event.getEntity().getGameProfile().getName(), oldData.isPresent(), newData.isPresent());
        }
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                LevelService.recompute(player, data);
                // 追いつき補正の中央値に入れる（Phase 3）
                LevelService.recordLevel(player, data);
                // ダウン中に抜けていたら、教会へ戻す（Phase 4）
                DownHandler.onLoggedIn(player, data);
                PlayerSync.syncAll(player, data);
            });
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                LevelService.recompute(player, data);
                // バニラの死亡（奈落・/kill）からのリスポーン：教会の呼び戻しの円へ（代価と衰弱。Phase 4）
                if (!event.isEndConquered() && !DownHandler.onRespawn(player, data)) {
                    data.setHp(data.getStats().maxHp());
                    data.setMp(data.getStats().maxMp());
                    VitalsService.pushVanillaHealth(player, data);
                }
                PlayerSync.syncAll(player, data);
            });
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> PlayerSync.syncAll(player, data));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (event.player instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                VitalsService.tick(player, data);
                SkillService.tick(player, data);
                jp.amemaru.lostconnection.equipment.GearEffects.tick(player, data);
                // ダウン中：起こす人を探し、時間切れなら教会へ（Phase 4）
                DownHandler.tick(player, data);
                PlayerSync.flush(player, data);
            });
        }
    }

    /**
     * 攻撃したら「戦闘中」にする（自然回復の量が変わる）。攻撃のたまり具合も覚えておく
     * （近接攻撃のダメージに使う。バニラは攻撃した後でたまり具合を0に戻すため、ここで読む）。
     */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                data.markCombat(player.level().getGameTime());
                data.setLastAttackStrength(player.getAttackStrengthScale(0.5F));
            });
        }
    }

    /** ログアウトしたら、詠唱をやめ、パッシブの記録を消す。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> SkillService.cancelCast(player, data, false));
            PassiveService.forget(player.getUUID());
            jp.amemaru.lostconnection.job.ReactiveSkills.forget(player.getUUID());
        }
    }
}
