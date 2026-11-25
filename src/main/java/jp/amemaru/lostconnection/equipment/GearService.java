package jp.amemaru.lostconnection.equipment;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.GeoWeaponItem;
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
    /**
     * 11月25日：防具を一式（頭・胴・脚・足を同じシリーズで）そろえているときの、一式の効果（胴の品の {@code set_effects}）。
     * 4つとも使える（レベル・職業）・壊れていないときだけ。そろっていなければ空。
     */
    public static List<SpecialEffect> setEffects(Player player, PlayerData data) {
        String series = null;
        EquipmentDef chest = null;
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof jp.amemaru.lostconnection.item.AmemaruArmorItem armor)) {
                return List.of();
            }
            if (series == null) {
                series = armor.series();
            } else if (!series.equals(armor.series())) {
                return List.of();
            }
            EquipmentDef def = snapshot.get(stack);
            if (def == null || !usable(data, def) || GearData.isBroken(stack, def)) {
                return List.of();
            }
            if (slot == EquipmentSlot.CHEST) {
                chest = def;
            }
        }
        return chest == null ? List.of() : chest.setEffects();
    }

    /** 着ている防具のうち、そのシリーズの数（一式の説明文に「今 n/4」と出す）。 */
    public static int seriesWorn(Player player, String series) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (player.getItemBySlot(slot).getItem() instanceof jp.amemaru.lostconnection.item.AmemaruArmorItem armor
                    && armor.series().equals(series)) {
                count++;
            }
        }
        return count;
    }

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
            // Phase 7 その1：手渡し（作り手とちがう人が初めて身に着けた）
            jp.amemaru.lostconnection.connection.ConnectionHandover.onEquip(player, event.getTo());
        }
    }

    // ---------------------------------------------------------------- 耐久

    /** 武器の耐久を減らす（攻撃が当たったとき・スキルを使ったとき）。動く武器は「使った」のアニメーションも流す。 */
    public static void wearWeapon(ServerPlayer player, boolean skill) {
        GeoWeaponItem.playUse(player);
        // 決闘の間は、武器の耐久は減らない（11月22日に決定。Phase 6 その4）
        if (player.getAbilities().instabuild || jp.amemaru.lostconnection.social.DuelService.isolated(player)) {
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
        // Phase 7 その1：使った人を持ち主として覚える（ほかの人が修理したら「他人の装備の修理」）
        jp.amemaru.lostconnection.connection.ConnectionHandover.markUser(player, main);
        if (GearData.wear(main, def, amount)) {
            onBroken(player, main);
        }
    }

    /**
     * 敵の攻撃を受けたとき、防具と盾の耐久を減らす（利き手の武器は減らない）。
     *
     * <p>11月24日のご指示（「防具を着ているときは耐久力が減るが、なかなか体力が減らない」）：1回ごとではなく、受けたダメージに
     * 合わせて減らす。最大HPの {@code armor_hp_percent}% を受けるごとに {@code armor_hit} ずつ（端数は確率で）。</p>
     *
     * @param hpFraction 受けたダメージの、最大HPに対する割合（0.1 なら10%）
     */
        public static void wearArmor(ServerPlayer player, double hpFraction) {
        // 決闘の間は、防具と盾の耐久は減らない（11月22日に決定。Phase 6 その4）
        if (player.getAbilities().instabuild || jp.amemaru.lostconnection.social.DuelService.isolated(player)) {
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        int amount = armorLoss(snapshot.rules(), hpFraction, player.getRandom().nextDouble());
        if (amount <= 0) {
            return;
        }
        for (ItemStack stack : activeGear(player)) {
            if (stack == player.getMainHandItem()) {
                continue;
            }
            EquipmentDef def = snapshot.get(stack);
            if (def == null) {
                continue;
            }
            jp.amemaru.lostconnection.connection.ConnectionHandover.markUser(player, stack);
            if (GearData.wear(stack, def, amount)) {
                onBroken(player, stack);
            }
        }
    }

    /**
     * 1回の攻撃で、防具と盾の耐久がいくつ減るか。{@code armor_hp_percent} が0なら、いつも {@code armor_hit}（前の形）。
     *
     * @param roll 端数を切り上げるかを決める、0以上1未満の数
     */
    public static int armorLoss(GearRules rules, double hpFraction, double roll) {
        if (rules.armorHpPercent() <= 0.0) {
            return rules.armorHitLoss();
        }
        double exact = rules.armorHitLoss() * Math.max(0.0, hpFraction) * 100.0 / rules.armorHpPercent();
        int whole = (int) Math.floor(exact);
        return whole + (roll < exact - whole ? 1 : 0);
    }

    private static void onBroken(ServerPlayer player, ItemStack stack) {
        player.displayClientMessage(Component.translatable("message.amemaru.gear.broken", stack.getHoverName())
                .withStyle(ChatFormatting.RED), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
    }
}
