package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryMenu;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.item.AmemaruWeaponItem;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 装備とアクセサリーの説明文（クライアント）。
 *
 * <ul>
 *   <li>装備：レアリティ・世代・種類、品質（★と平均の割合）、ステータスごとの「数値（幅）割合」、強化、特殊効果、
 *       使える条件（満たさなければ赤）、耐久、作り手（「〇〇作」）、取引の決まり、今の装備との比較</li>
 *   <li>アクセサリー：各ステータスの数値・幅・割合、品質、今付けている物との比較（上がる・下がる）【確定】</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class GearTooltips {
    private GearTooltips() {
    }

    /** 説明文の1行の幅の上限（画面の座標。これより長い行は折り返す）。 */
    private static final int MAX_LINE_WIDTH = 200;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !AmemaruMod.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        List<Component> lines = new ArrayList<>();
        if (stack.getItem() instanceof GearItem gear) {
            gearLines(stack, gear, lines);
        } else if (stack.getItem() instanceof AccessoryItem accessory) {
            accessoryLines(stack, accessory, lines);
        }
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
        wrapLongLines(tooltip);
    }

    /** このModの品の説明文で、長すぎる行を折り返す（説明・特殊効果の文が画面の端まで伸びないように）。色と書式は保つ。 */
    private static void wrapLongLines(List<Component> tooltip) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        for (int i = tooltip.size() - 1; i >= 1; i--) {
            Component line = tooltip.get(i);
            if (font.width(line) <= MAX_LINE_WIDTH) {
                continue;
            }
            List<net.minecraft.network.chat.FormattedText> parts = font.getSplitter().splitLines(line, MAX_LINE_WIDTH,
                    net.minecraft.network.chat.Style.EMPTY);
            List<Component> wrapped = new ArrayList<>(parts.size());
            for (net.minecraft.network.chat.FormattedText part : parts) {
                MutableComponent out = Component.empty();
                part.visit((style, text) -> {
                    out.append(Component.literal(text).setStyle(style));
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                wrapped.add(out);
            }
            tooltip.remove(i);
            tooltip.addAll(i, wrapped);
        }
    }

    // ---------------------------------------------------------------- 装備

    private static void gearLines(ItemStack stack, GearItem gear, List<Component> lines) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.client();
        EquipmentDef def = snapshot.get(stack);
        if (def == null) {
            lines.add(Component.translatable("tooltip.amemaru.gear.no_def").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        GearRules rules = snapshot.rules();
        Rarity rarity = GearData.rarity(stack, def);
        MutableComponent kind = stack.getItem() instanceof AmemaruWeaponItem weapon
                ? Component.translatable(weapon.kind().translationKey())
                : Component.translatable(def.slot().translationKey());
        MutableComponent header = rarity.displayName().append(Component.literal(" ・ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(kind.withStyle(ChatFormatting.GRAY));
        if (!def.series().isEmpty()) {
            header.append(Component.literal(" ・ ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(def.series()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(header);
        if (gear.isTwoHanded()) {
            lines.add(Component.translatable("tooltip.amemaru.weapon.two_handed").withStyle(ChatFormatting.DARK_GRAY));
        }

        boolean rolled = GearData.isRolled(stack);
        if (!def.fixed()) {
            if (rolled) {
                double average = GearData.averageRatio(stack, def, rules);
                lines.add(Component.translatable("tooltip.amemaru.quality", stars(GearData.quality(stack, def, rules)),
                        percent(average)).withStyle(ChatFormatting.GOLD));
            } else {
                lines.add(Component.translatable("tooltip.amemaru.unrolled").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        boolean broken = GearData.isBroken(stack, def);
        for (StatType type : def.stats().keySet()) {
            double value = GearData.value(stack, type, def, rules);
            MutableComponent line = Component.translatable("tooltip.amemaru.gear.stat", Component.translatable(type.translationKey()),
                    "+" + type.format(value));
            if (!def.fixed() && rolled) {
                line.append(Component.translatable("tooltip.amemaru.stat_range", type.format(GearData.minValue(stack, type, def, rules)),
                        type.format(GearData.maxValue(stack, type, def, rules)),
                        percent(GearData.ratio(stack, type, def, rules))).withStyle(ChatFormatting.DARK_AQUA));
            }
            lines.add(line.withStyle(broken ? ChatFormatting.DARK_GRAY : ChatFormatting.BLUE));
        }
        int enhance = GearData.enhance(stack);
        if (enhance > 0) {
            lines.add(Component.translatable("tooltip.amemaru.enhance", enhance,
                    String.format(Locale.ROOT, "%.0f", enhance * rules.enhancePercent())).withStyle(ChatFormatting.AQUA));
        }
        for (SpecialEffect effect : def.effects()) {
            lines.add(Component.translatable("tooltip.amemaru.effect", effect.describe()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (def.rare()) {
            lines.add(Component.translatable("tooltip.amemaru.rare_gear").withStyle(ChatFormatting.DARK_PURPLE));
        }

        // 使える条件
        ClientPlayerState state = ClientPlayerState.get();
        boolean levelOk = state.level() >= def.requiredLevel();
        MutableComponent requirement = Component.translatable("tooltip.amemaru.required_level", def.requiredLevel())
                .withStyle(levelOk ? ChatFormatting.GRAY : ChatFormatting.RED);
        if (def.hasJobRequirement()) {
            ResourceLocation current = currentJob();
            boolean jobOk = current != null && def.jobs().contains(current);
            List<String> names = new ArrayList<>();
            for (ResourceLocation id : def.jobs()) {
                names.add(jobName(id));
            }
            requirement.append(Component.literal(" ・ ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.join("・", names)).withStyle(jobOk ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        lines.add(requirement);

        if (def.durability() > 0) {
            int durability = GearData.durability(stack, def);
            lines.add(Component.translatable("tooltip.amemaru.durability", durability, def.durability())
                    .withStyle(broken ? ChatFormatting.RED : ChatFormatting.GRAY));
            if (broken) {
                lines.add(Component.translatable("tooltip.amemaru.broken").withStyle(ChatFormatting.RED));
            }
        } else {
            lines.add(Component.translatable("tooltip.amemaru.unbreakable").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (GearData.isGreat(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.great").withStyle(ChatFormatting.GOLD));
        }
        String crafter = GearData.crafterName(stack);
        if (crafter != null) {
            lines.add(Component.translatable("tooltip.amemaru.crafted_by", crafter).withStyle(ChatFormatting.DARK_GRAY,
                    ChatFormatting.ITALIC));
        }
        if (GearData.isRareDrop(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.rare_drop").withStyle(ChatFormatting.AQUA));
        }
        if (!def.description().isEmpty()) {
            lines.add(Component.literal(def.description()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (GearData.isBound(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.bound").withStyle(ChatFormatting.DARK_RED));
        } else if (GearData.bindsOnEquip(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.binds_on_equip").withStyle(ChatFormatting.GRAY));
        }
        compareGear(stack, def, rules, lines);
    }

    /** 今の装備（同じ場所）との比較。 */
    private static void compareGear(ItemStack stack, EquipmentDef def, GearRules rules, List<Component> lines) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        EquipmentSlot slot = def.slot() == GearSlot.WEAPON ? EquipmentSlot.MAINHAND : def.slot().vanilla();
        ItemStack equipped = player.getItemBySlot(slot);
        if (equipped == stack || equipped.isEmpty() || !(equipped.getItem() instanceof GearItem)) {
            return;
        }
        EquipmentDef other = EquipmentDefs.client().get(equipped);
        if (other == null) {
            return;
        }
        Map<StatType, Double> mine = new EnumMap<>(StatType.class);
        for (StatType type : def.stats().keySet()) {
            mine.put(type, GearData.value(stack, type, def, rules));
        }
        Map<StatType, Double> theirs = new EnumMap<>(StatType.class);
        if (!GearData.isBroken(equipped, other)) {
            for (StatType type : other.stats().keySet()) {
                theirs.put(type, GearData.value(equipped, type, other, rules));
            }
        }
        appendComparison(lines, mine, theirs, "tooltip.amemaru.compare_gear");
    }

    // ---------------------------------------------------------------- アクセサリー

    private static void accessoryLines(ItemStack stack, AccessoryItem item, List<Component> lines) {
        Rarity rarity = AccessoryData.rarity(stack, Rarity.UNCOMMON);
        lines.add(rarity.displayName().append(Component.literal(" ・ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(item.slot().translationKey()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ・ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("tooltip.amemaru.generation", AccessoryData.generation(stack)).withStyle(ChatFormatting.GRAY)));
        if (!AccessoryData.isRolled(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.unrolled").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int quality = AccessoryData.storedQuality(stack);
        lines.add(Component.translatable("tooltip.amemaru.quality", stars(quality), percent(AccessoryData.averageRatio(stack)))
                .withStyle(quality >= 5 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        if (AccessoryData.storedPerfect(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.perfect").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main != null) {
            MutableComponent mainLine = statLine("tooltip.amemaru.accessory.main", main).withStyle(ChatFormatting.BLUE);
            if (AccessoryData.enhance(stack) > 0) {
                // 強化でメインが伸びた分（Phase 6 その4）
                StatType type = main.type();
                mainLine.append(Component.translatable("tooltip.amemaru.accessory.main_enhanced",
                        type.format(AccessoryData.effectiveMain(stack, main)),
                        String.format(Locale.ROOT, "%.0f", AccessoryData.enhance(stack) * AccessoryData.mainPercent(stack)))
                        .withStyle(ChatFormatting.AQUA));
            }
            lines.add(mainLine);
        }
        for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
            MutableComponent subLine = statLine("tooltip.amemaru.accessory.sub", sub).withStyle(ChatFormatting.DARK_AQUA);
            if (sub.rolls() > 1) {
                subLine.append(Component.translatable("tooltip.amemaru.accessory.rolls", sub.rolls()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (sub.locked()) {
                subLine.append(Component.translatable("tooltip.amemaru.accessory.locked").withStyle(ChatFormatting.GOLD));
            }
            lines.add(subLine);
        }
        lines.add(Component.translatable("tooltip.amemaru.accessory.enhance", AccessoryData.enhance(stack),
                AccessoryData.rerolls(stack)).withStyle(ChatFormatting.DARK_GRAY));
        if (AccessoryData.isGreat(stack)) {
            lines.add(Component.translatable("tooltip.amemaru.great_accessory").withStyle(ChatFormatting.GOLD));
        }
        String crafter = AccessoryData.crafterName(stack);
        if (crafter != null) {
            lines.add(Component.translatable("tooltip.amemaru.crafted_by", crafter).withStyle(ChatFormatting.DARK_GRAY,
                    ChatFormatting.ITALIC));
        }
        if (item.slot().shownOnBody()) {
            lines.add(Component.translatable("tooltip.amemaru.accessory.shown").withStyle(ChatFormatting.DARK_GRAY));
        }
        compareAccessory(stack, item.slot(), lines);
    }

    private static MutableComponent statLine(String key, AccessoryData.StatLine line) {
        StatType type = line.type();
        return Component.translatable(key, Component.translatable(type.translationKey()), "+" + type.format(line.value()),
                type.format(line.min()), type.format(line.max()), percent(line.ratio()));
    }

    /** 今付けている物（同じ枠。指輪は2つのうち弱い方）との比較【確定】。 */
    private static void compareAccessory(ItemStack stack, AccessorySlot slot, List<Component> lines) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        List<ItemStack> worn = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var stacks = handler.getCurios().get(slot.curiosId());
            if (stacks != null) {
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack one = stacks.getStacks().getStackInSlot(i);
                    if (!one.isEmpty()) {
                        worn.add(one);
                    }
                }
            }
        });
        if (worn.isEmpty()) {
            if (!AccessoryMenu.class.isInstance(player.containerMenu)) {
                lines.add(Component.translatable("tooltip.amemaru.compare_empty").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        for (ItemStack other : worn) {
            if (other == stack) {
                return;
            }
        }
        ItemStack target = worn.get(0);
        if (slot == AccessorySlot.RING && worn.size() > 1
                && AccessoryData.averageRatio(worn.get(1)) < AccessoryData.averageRatio(worn.get(0))) {
            target = worn.get(1);
        }
        appendComparison(lines, totals(stack), totals(target), "tooltip.amemaru.compare_accessory");
    }

    private static Map<StatType, Double> totals(ItemStack stack) {
        Map<StatType, Double> map = new EnumMap<>(StatType.class);
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main != null) {
            map.merge(main.type(), AccessoryData.effectiveMain(stack, main), Double::sum);
        }
        for (AccessoryData.StatLine line : AccessoryData.subs(stack)) {
            map.merge(line.type(), line.value(), Double::sum);
        }
        return map;
    }

    // ---------------------------------------------------------------- 共通

    /** 比較の行（上がる・下がる）。 */
    private static void appendComparison(List<Component> lines, Map<StatType, Double> mine, Map<StatType, Double> theirs, String header) {
        List<Component> diffs = new ArrayList<>();
        for (StatType type : StatType.all()) {
            double a = mine.getOrDefault(type, 0.0);
            double b = theirs.getOrDefault(type, 0.0);
            double diff = a - b;
            if (Math.abs(diff) < 1.0E-6) {
                continue;
            }
            String text = (diff > 0 ? "+" : "−") + type.format(Math.abs(diff)) + (diff > 0 ? " ↑" : " ↓");
            diffs.add(Component.translatable("tooltip.amemaru.compare_line", Component.translatable(type.translationKey()), text)
                    .withStyle(diff > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        lines.add(Component.translatable(header).withStyle(ChatFormatting.GRAY));
        if (diffs.isEmpty()) {
            lines.add(Component.translatable("tooltip.amemaru.compare_same").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.addAll(diffs);
        }
    }

    public static String stars(int quality) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            builder.append(i <= quality ? '★' : '☆');
        }
        return builder.toString();
    }

    public static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    private static ResourceLocation currentJob() {
        int index = ClientPlayerState.get().jobIndex();
        SyncDefinitionsS2C.JobInfo job = index < 0 ? null : ClientDefinitions.get().job(index);
        return job == null ? null : job.id();
    }

    private static String jobName(ResourceLocation id) {
        for (SyncDefinitionsS2C.JobInfo job : ClientDefinitions.get().jobs()) {
            if (job.id().equals(id)) {
                return job.name();
            }
        }
        return id.getPath();
    }
}
