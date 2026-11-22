package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * フリーマーケットの右クリック（Phase 6。11月22日に決定：施設は建築の場所から右クリックでGUIを開いて使う）。
 * 市場監督の小屋（書見台・呼び鈴は除く）で取引所（露店を借りられる）、露店の区画で、その露店の画面。
 * 区画のブロック（樽・看板など）のふつうの動きはしない。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class MarketEvents {
    private MarketEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().dimension() != Level.OVERWORLD || player.isSpectator()) {
            return;
        }
        Optional<BlockPos> origin = MarketService.origin(player.server);
        if (origin.isEmpty()) {
            return;
        }
        BlockPos rel = event.getPos().subtract(origin.get());
        MarketStalls.Box booth = MarketStalls.booth();
        if (booth != null && booth.contains(rel) && !MarketStalls.boothExcept(rel)) {
            cancel(event);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                MarketService.open(player, MarketService.Mode.BOOTH, 0);
            }
            return;
        }
        MarketStalls.Stall stall = MarketStalls.stallAt(rel);
        if (stall == null || !MarketService.stallActive(player.server, stall)) {
            return;
        }
        cancel(event);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        MarketData.StallRent rent = MarketData.get(player.server).stall(stall.num());
        if (rent == null) {
            player.displayClientMessage(Component.translatable("message.amemaru.market.vacant", stall.num(), stall.title())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MarketService.open(player, MarketService.Mode.STALL, stall.num());
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
