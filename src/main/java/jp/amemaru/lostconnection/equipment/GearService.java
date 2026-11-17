package jp.amemaru.lostconnection.equipment;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 装備の仕組み（サーバー）：装備と結晶果のステータス、使える条件、耐久の減り方。
 *
 * <ul>
 *   <li>武器：利き手に持っている間だけ効く。両手持ちの武器を持っていると、もう片方の手の盾は効かない</li>
 *   <li>盾：もう片方の手に持っている間だけ効く</li>
 *   <li>防具：その場所に着ている間だけ効く</li>
 *   <li>必要レベル・職業の条件を満たさない品や、壊れた品は、持っていてもステータスが上がらない</li>
 *   <li>結晶果：食べた数 ×「ステータスポイント1つ分の伸び」</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class GearService {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GearService() {
    }

    // ---------------------------------------------------------------- ステータス

    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        PlayerData data = event.getData();
        if (player == null || data == null) {
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        for (ItemStack stack : activeGear(player)) {
            EquipmentDef def = snapshot.get(stack);
            if (def != null && usable(data, def) && !GearData.isBroken(stack, def)) {
                for (StatType type : def.stats().keySet()) {
                    event.addFlat(type, GearData.value(stack, type, def, snapshot.rules()));
                }
            }
        }
        // 結晶果（1つで、ステータスポイント1つ分）
        GrowthDef growth = GrowthRules.current();
        for (StatType type : StatType.all()) {
            int eaten = data.getAdventure().fruits(type);
            if (eaten > 0) {
                event.addFlat(type, eaten * growth.perPoint(type));
            }
        }
    }

    /**
     * いま効いている装備（利き手の武器・もう片方の手の盾・着ている防具）。両手持ちの武器を持っていれば盾は入れない。
     * 必要レベルなどの条件はここでは見ない（{@link #usable}）。
     */
    public static List<ItemStack> activeGear(Player player) {
        List<ItemStack> list = new ArrayList<>(6);
        ItemStack main = player.getMainHandItem();
        boolean twoHanded = false;
        if (main.getItem() instanceof GearItem gear && gear.gearSlot() == GearSlot.WEAPON) {
            list.add(main);
            twoHanded = gear.isTwoHanded();
        }
        ItemStack off = player.getOffhandItem();
        if (!twoHanded && off.getItem() instanceof GearItem gear && gear.gearSlot() == GearSlot.OFFHAND) {
            list.add(off);
        }
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof GearItem gear && gear.gearSlot().vanilla() == slot) {
                list.add(stack);
            }
        }
        return list;
    }

    /** 必要レベルと職業の条件を満たしているか。 */
    public static boolean usable(PlayerData data, EquipmentDef def) {
        if (data.getLevel() < def.requiredLevel()) {
            return false;
        }
        return !def.hasJobRequirement() || (data.getCombatJob() != null && def.jobs().contains(data.getCombatJob()));
    }

    /** 持ち物を持ちかえた・防具を着がえた・耐久が変わったら、装備の分を計算し直す。 */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        }
    }

    // ---------------------------------------------------------------- 耐久

    /** 武器の耐久を減らす（攻撃が当たったとき・スキルを使ったとき）。 */
    public static void wearWeapon(ServerPlayer player, boolean skill) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof GearItem gear) || gear.gearSlot() != GearSlot.WEAPON) {
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(main);
        if (def == null) {
            return;
        }
        int amount = skill ? snapshot.rules().skillUseLoss() : snapshot.rules().weaponHitLoss();
        if (GearData.wear(main, def, amount)) {
            onBroken(player, main);
        }
    }

    /** 防具と盾の耐久を減らす（敵の攻撃を受けたとき）。 */
    public static void wearArmor(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        int amount = snapshot.rules().armorHitLoss();
        for (ItemStack stack : activeGear(player)) {
            if (stack == player.getMainHandItem()) {
                continue;
            }
            EquipmentDef def = snapshot.get(stack);
            if (def != null && GearData.wear(stack, def, amount)) {
                onBroken(player, stack);
            }
        }
    }

    private static void onBroken(ServerPlayer player, ItemStack stack) {
        player.displayClientMessage(Component.translatable("message.amemaru.gear.broken", stack.getHoverName())
                .withStyle(ChatFormatting.RED), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
    }
}
