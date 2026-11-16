package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
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

/**
 * プレイヤーデータの付与・引き継ぎ・同期と、毎 tick の処理（Forgeのイベントバス）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PlayerEvents {
    private PlayerEvents() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerDataProvider provider = new PlayerDataProvider();
            event.addCapability(PlayerDataCapability.ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    /** 死亡・エンドからの帰還で新しい体ができたとき、全項目を写す（Phase 0-B 4.1）。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        PlayerDataCapability.get(original).ifPresent(oldData ->
                PlayerDataCapability.get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                LevelService.recompute(player, data);
                PlayerSync.syncAll(player, data);
            });
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                LevelService.recompute(player, data);
                if (!event.isEndConquered()) {
                    // Phase 1では、死亡からの復活はHPとMPが全快。Phase 4で教会での復活とデスペナルティに置き換える
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
                PlayerSync.flush(player, data);
            });
        }
    }

    /** 攻撃したら「戦闘中」にする（自然回復の量が変わる）。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(data -> data.markCombat(player.level().getGameTime()));
        }
    }
}
