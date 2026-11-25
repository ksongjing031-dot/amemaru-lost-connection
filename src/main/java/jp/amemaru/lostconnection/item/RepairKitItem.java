package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 携帯修理具（11月23日の直し。雑貨屋・道具屋・金物屋・革と鞄）。右クリックで、身につけている防具4つと左手の物、
 * 持ち物のいちばん下の段（ホットバー）の装備の耐久を、それぞれ最大の30%ずつ戻す【補完】（武器を持ちかえずに使えるように）。塔の中で工房に戻らずに直すための品。工房の修理（最大まで戻す）
 * より割高で、生産職に頼むほうが安い（Phase 0-A 20章「NPCの修理は高めで、生産職のプレイヤーに頼むと安い」はそのまま）。
 */
public class RepairKitItem extends Item {
    public static final double RATIO = 0.30;

    public RepairKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(kit);
        }
        int repaired = repairWorn(serverPlayer, kit);
        if (repaired <= 0) {
            serverPlayer.displayClientMessage(Component.translatable("message.amemaru.repair_kit.nothing").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(kit);
        }
        if (!serverPlayer.getAbilities().instabuild) {
            kit.shrink(1);
        }
        serverPlayer.getCooldowns().addCooldown(this, 20);
        serverPlayer.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        serverPlayer.displayClientMessage(Component.translatable("message.amemaru.repair_kit.done", repaired).withStyle(ChatFormatting.GREEN),
                true);
        PlayerDataCapability.get(serverPlayer).ifPresent(PlayerData::invalidateStats);
        return InteractionResultHolder.consume(kit);
    }

    /** 身につけている防具・左手の物・ホットバーの装備の耐久を、最大の30%ずつ戻す（修理具そのものは除く）。直した点数。 */
    public static int repairWorn(ServerPlayer player, ItemStack kit) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        java.util.List<ItemStack> targets = new java.util.ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.MAINHAND) {
                targets.add(player.getItemBySlot(slot));
            }
        }
        for (int i = 0; i < 9; i++) {
            targets.add(player.getInventory().items.get(i));
        }
        int repaired = 0;
        for (ItemStack stack : targets) {
            if (stack.isEmpty() || stack == kit) {
                continue;
            }
            EquipmentDef def = snapshot.get(stack);
            if (def == null || def.durability() <= 0) {
                continue;
            }
            int now = GearData.durability(stack, def);
            if (now >= def.durability()) {
                continue;
            }
            int add = Math.max(1, (int) Math.ceil(def.durability() * RATIO));
            GearData.setDurability(stack, def, now + add);
            repaired++;
        }
        return repaired;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.repair_kit", Math.round(RATIO * 100)).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
