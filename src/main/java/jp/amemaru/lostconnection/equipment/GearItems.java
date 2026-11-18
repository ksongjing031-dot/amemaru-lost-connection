package jp.amemaru.lostconnection.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import javax.annotation.Nullable;

/**
 * 装備のアイテム（武器・盾・防具）に共通の動き：名前の色、耐久のバー、強化の光り方、持ち物に入ったときの抽選、
 * 装備したときの「本人専用」。
 */
public final class GearItems {
    private GearItems() {
    }

    /** 今のスレッドの側（サーバー／クライアント）の定義。 */
    public static EquipmentDefs.Snapshot defs() {
        return EquipmentDefs.of(EffectiveSide.get().isClient());
    }

    /** 名前：レアリティの色で、強化していれば「+3」をつける。 */
    public static Component name(ItemStack stack, Component base) {
        EquipmentDef def = defs().get(stack);
        Rarity rarity = def == null ? Rarity.COMMON : GearData.rarity(stack, def);
        MutableComponent name = base.copy();
        int enhance = GearData.enhance(stack);
        if (enhance > 0) {
            name = Component.translatable("item.amemaru.enhanced_name", name, enhance);
        }
        return rarity.paint(name);
    }

    /**
     * 持ち物の中で毎 tick 呼ぶ（サーバーだけ）：まだ抽選していなければ抽選する。装備していて「装備すると取引できなくなる」
     * 品なら、本人専用にする。
     */
    public static void inventoryTick(ItemStack stack, Level level, Entity entity) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) {
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(stack);
        if (def == null) {
            return;
        }
        GearData.ensureRolled(stack, def, snapshot.rules(), level.getRandom());
        if (GearData.bindsOnEquip(stack) && living.getItemBySlot(def.slot().vanilla()) == stack) {
            GearData.bind(stack);
            if (living instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.amemaru.gear.bound", stack.getHoverName())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    /** 耐久のバーを出すか（減っているときだけ）。 */
    public static boolean barVisible(ItemStack stack) {
        EquipmentDef def = defs().get(stack);
        return def != null && def.durability() > 0 && GearData.durability(stack, def) < def.durability();
    }

    public static int barWidth(ItemStack stack) {
        EquipmentDef def = defs().get(stack);
        if (def == null || def.durability() <= 0) {
            return 13;
        }
        return Math.round(13.0F * GearData.durability(stack, def) / def.durability());
    }

    public static int barColor(ItemStack stack) {
        EquipmentDef def = defs().get(stack);
        if (def == null || def.durability() <= 0) {
            return 0x55FF55;
        }
        float ratio = Math.max(0.0F, (float) GearData.durability(stack, def) / def.durability());
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    /** 強化の光り方の段階（0〜3。+4・+7・+10で一段ずつ）。 */
    public static int glowTier(ItemStack stack) {
        return defs().rules().glowTier(GearData.enhance(stack));
    }

    /** アイコンを光らせるか（強化+4から）。 */
    public static boolean foil(ItemStack stack) {
        return glowTier(stack) > 0;
    }

    /** その品のレアリティ（装備でなければ null）。 */
    @Nullable
    public static Rarity rarity(ItemStack stack) {
        EquipmentDef def = defs().get(stack);
        return def == null ? null : GearData.rarity(stack, def);
    }
}
