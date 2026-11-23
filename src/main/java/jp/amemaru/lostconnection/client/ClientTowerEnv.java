package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 塔の階の霧（Phase 8 その1。2階「忘れられた森」は霧が濃い。Phase 0-A 9章）。サーバーが、今いる階の霧を送る
 * （{@link jp.amemaru.lostconnection.network.s2c.TowerS2C} の {@code env}）。ボスの部屋が暗いときは、霧をさらに近くする
 * （{@code dark}。2階のボスは HP30% で森が暗くなる）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class ClientTowerEnv {
    private static float fogStart = -1;
    private static float fogEnd = -1;
    private static int fogColor = -1;
    private static boolean dark;
    private static float darkRadius = 6.0F;

    private ClientTowerEnv() {
    }

    public static void handle(String kind, CompoundTag data) {
        switch (kind) {
            case "env" -> {
                // 階が変わった：ボスの部屋の暗さは、その部屋の中だけのもの（11月24日：2階のボスの暗さが3階まで残り、
                // 3階が真っ暗に見えた。暗いままの部屋にいれば、サーバーがもう一度送る）
                dark = false;
                if (data.contains("fogEnd")) {
                    fogStart = data.getFloat("fogStart");
                    fogEnd = data.getFloat("fogEnd");
                    fogColor = data.contains("fogColor") ? data.getInt("fogColor") : -1;
                } else {
                    fogStart = -1;
                    fogEnd = -1;
                    fogColor = -1;
                }
            }
            case "dark" -> {
                dark = data.getBoolean("on");
                darkRadius = data.contains("radius") ? data.getFloat("radius") : 6.0F;
            }
            default -> {
            }
        }
    }

    /** 今、霧の決まりがあるか（自動の確かめ用）。 */
    public static boolean active() {
        return fogEnd > 0 || dark;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || (fogEnd <= 0 && !dark)) {
            return;
        }
        float near = fogStart;
        float far = fogEnd;
        if (dark) {
            // 暗い：灯りを持つ人のそばでも、見えるのは少しだけ（サーバーが、灯りから遠い人には目くらましもかける）
            near = 0.0F;
            far = darkRadius * 2.5F;
        }
        if (far <= 0) {
            return;
        }
        event.setNearPlaneDistance(Math.min(near, event.getNearPlaneDistance()));
        event.setFarPlaneDistance(Math.min(far, event.getFarPlaneDistance()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (fogEnd <= 0 && !dark) {
            return;
        }
        if (dark) {
            event.setRed(0.04F);
            event.setGreen(0.05F);
            event.setBlue(0.06F);
        } else if (fogColor >= 0) {
            event.setRed(((fogColor >> 16) & 0xFF) / 255.0F);
            event.setGreen(((fogColor >> 8) & 0xFF) / 255.0F);
            event.setBlue((fogColor & 0xFF) / 255.0F);
        }
    }
}
