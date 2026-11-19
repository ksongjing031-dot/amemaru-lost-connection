package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 持っている装備の光（クライアント）：エピックは小さな光の粒、レジェンダリーは常に出る粒（Phase 0-A 20章）。
 * 強化+7・+10も、手元に光の粒が出る（+4は武器の光沢）。まわりの人の分も出す（32ブロック以内）。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class GearSparkles {
    private GearSparkles() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        try {
            if (!ClientConfig.GEAR_SPARKLES.get()) {
                return;
            }
        } catch (IllegalStateException notLoaded) {
            return;
        }
        for (Player player : level.players()) {
            if (player.distanceToSqr(minecraft.player) > 32 * 32 || player.isInvisible()) {
                continue;
            }
            sparkle(level, player, player.getMainHandItem(), true);
            sparkle(level, player, player.getOffhandItem(), false);
        }
    }

    private static void sparkle(ClientLevel level, Player player, ItemStack stack, boolean mainHand) {
        if (stack.isEmpty()) {
            return;
        }
        Rarity rarity = GearItems.rarity(stack);
        if (rarity == null) {
            return;
        }
        int tier = GearItems.glowTier(stack);
        RandomSource random = level.random;
        int interval;
        if (rarity.alwaysSparkles()) {
            interval = 3;
        } else if (rarity.sparkles() || tier >= 3) {
            interval = 8;
        } else if (tier >= 2) {
            interval = 16;
        } else {
            return;
        }
        if (random.nextInt(interval) != 0) {
            return;
        }
        double yaw = Math.toRadians(player.yBodyRot);
        double side = mainHand == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) ? 1.0 : -1.0;
        double rightX = -Math.cos(yaw) * side;
        double rightZ = -Math.sin(yaw) * side;
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double x = player.getX() + rightX * 0.38 + forwardX * 0.25 + (random.nextDouble() - 0.5) * 0.3;
        double y = player.getY() + (player.isCrouching() ? 0.55 : 0.75) + random.nextDouble() * 0.5;
        double z = player.getZ() + rightZ * 0.38 + forwardZ * 0.25 + (random.nextDouble() - 0.5) * 0.3;
        int color = rarity.colorAt(System.currentTimeMillis());
        if (rarity.sparkles()) {
            Vector3f rgb = new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
            level.addParticle(new DustParticleOptions(rgb, 0.6F), x, y, z, 0.0, 0.01, 0.0);
        } else {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.01, 0.0);
        }
        if (rarity.alwaysSparkles() && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
