package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.AmemaruWeaponItem;
import jp.amemaru.lostconnection.item.WeaponKind;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.BasicShotC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 弓の通常の射撃（Phase 6 その2。クライアント専用）：弓を持っているときの左クリックは、近接攻撃やブロックの破壊のかわりに、
 * 矢を射る要求をサーバーに送る。押し続けると、攻撃の速さの間隔ごとに射続ける（間隔はサーバーでも確かめる）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class BowInput {
    private static int cooldown;

    private BowInput() {
    }

    /** 弓を持っているか。 */
    public static boolean holdingBow(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof AmemaruWeaponItem weapon && weapon.kind() == WeaponKind.BOW;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !holdingBow(player)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        shoot(player);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !holdingBow(player)) {
            return;
        }
        if (minecraft.options.keyAttack.isDown()) {
            shoot(player);
        }
    }

    private static void shoot(LocalPlayer player) {
        if (cooldown > 0 || player.isSpectator()) {
            return;
        }
        cooldown = Math.max(8, Math.round(player.getCurrentItemAttackStrengthDelay()));
        ModNetwork.sendToServer(new BasicShotC2S());
    }
}
