package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.RefineQuoteS2C;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 装飾工房の厳選（Phase 6 その4。Phase 0-A 21章）：台に置いたアクセサリーを振り直す・固定する・強化する・分解する。
 * 費用を確かめて払い、品を書きかえ、知らせて数える。費用と結果の計算は {@link AccessoryRefine}。
 *
 * <p>厳選は誰でもできる（Phase 0-A 7章「装飾工房：アクセサリーの制作・厳選・依頼台（制作は装飾師のみ）」）。装飾師なら、
 * 熟練度1ごとに費用が −2%（最大 −20%。21章）【補完】。依頼による厳選（{@code jp.amemaru.lostconnection.social}）では、
 * 受けた装飾師の熟練度で下がる。</p>
 */
public final class RefineService {
    private RefineService() {
    }

    /** 操作の結果（依頼の厳選でも使う）。 */
    public enum Outcome {
        DONE, NOT_ACCESSORY, UNROLLED, ALL_LOCKED, MAX_ENHANCE, NEED_MATERIALS, NEED_KNOTS
    }

    // ---------------------------------------------------------------- 費用の見積もり

    /** config の厳選の費用の倍率（Phase 0-B 7章：アクセサリー「厳選の費用の倍率」1.0）。 */
    public static double configMultiplier() {
        return ServerConfig.REFINE_COST_MULTIPLIER.get();
    }

    /** この人の値引き（装飾師なら熟練度で。%）。 */
    public static int discountOf(ServerPlayer player, PlayerData data) {
        return AccessoryRefine.discountPercent(AccessoryRules.current().refine(),
                WorkshopService.mastery(player, data, WorkshopType.ACCESSORY));
    }

    public static int today(ServerPlayer player) {
        return ProgressionData.currentDay(player.server);
    }

    /** 台の品の見積もり（画面に出す費用・固定の数・振り直すほどの上乗せ・分解で戻る素材）。 */
    public static CompoundTag quote(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || stack.isEmpty() || !(stack.getItem() instanceof AccessoryItem) || !AccessoryData.isRolled(stack)) {
            tag.putBoolean("has", false);
            return tag;
        }
        AccessoryRules rules = AccessoryRules.current();
        AccessoryRules.Refine refine = rules.refine();
        int discount = discountOf(player, data);
        int today = today(player);
        double multiplier = configMultiplier();
        tag.putBoolean("has", true);
        tag.putInt("mastery", WorkshopService.mastery(player, data, WorkshopType.ACCESSORY));
        tag.putInt("discount", discount);
        tag.putInt("costUp", AccessoryData.costUp(stack, today));
        tag.putInt("costStep", refine.escalationStep());
        tag.putInt("costMax", refine.escalationMax());
        tag.putInt("locks", AccessoryData.lockedCount(stack));
        tag.putInt("maxLocks", AccessoryRefine.maxLocks(stack, rules));
        tag.putBoolean("canReroll", AccessoryRefine.canReroll(stack));
        tag.put("values", costTag(AccessoryRefine.rerollCost(stack, rules, false, today, multiplier, discount)));
        tag.put("types", costTag(AccessoryRefine.rerollCost(stack, rules, true, today, multiplier, discount)));
        AccessoryRefine.Cost enhance = AccessoryRefine.enhanceCost(stack, rules, multiplier, discount);
        if (enhance != null) {
            tag.put("enhance", costTag(enhance));
        }
        tag.putInt("maxEnhance", rules.maxEnhance());
        tag.putInt("nextMilestone", refine.nextMilestone(AccessoryData.enhance(stack)));
        tag.putDouble("mainPercent", refine.mainPercent());
        ListTag yield = new ListTag();
        for (ItemStack one : AccessoryRefine.dismantleYield(stack, rules)) {
            yield.add(one.save(new CompoundTag()));
        }
        tag.put("yield", yield);
        return tag;
    }

    public static CompoundTag costTag(AccessoryRefine.Cost cost) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("knots", cost.knots());
        ListTag items = new ListTag();
        for (EquipmentDef.Cost item : cost.items()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", item.item().toString());
            one.putInt("count", item.count());
            items.add(one);
        }
        tag.put("items", items);
        return tag;
    }

    /** 工房の画面を開いている人に、台の品の見積もりを送る。 */
    public static void sendQuote(ServerPlayer player, WorkshopMenu menu) {
        if (menu.type() != WorkshopType.ACCESSORY) {
            return;
        }
        ModNetwork.sendTo(player, new RefineQuoteS2C(quote(player, menu.workStack())));
    }

    // ---------------------------------------------------------------- 操作（自分の品）

    /** 画面の操作を行う（台の品）。 */
    public static void perform(ServerPlayer player, WorkshopMenu menu, AccessoryRefine.Op op) {
        ItemStack stack = menu.workStack();
        Outcome outcome = switch (op) {
            case REROLL_VALUES -> reroll(player, stack, false);
            case REROLL_TYPES -> reroll(player, stack, true);
            case ENHANCE -> enhance(player, stack);
            case DISMANTLE -> dismantle(player, menu);
        };
        tell(player, outcome, stack);
        sendQuote(player, menu);
    }

    /** 台の品のサブの固定を切りかえる。 */
    public static void toggleLock(ServerPlayer player, WorkshopMenu menu, int index) {
        ItemStack stack = menu.workStack();
        if (!(stack.getItem() instanceof AccessoryItem) || !AccessoryData.isRolled(stack)) {
            tell(player, Outcome.NOT_ACCESSORY, stack);
            return;
        }
        AccessoryRules rules = AccessoryRules.current();
        List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
        AccessoryRefine.LockResult result = AccessoryRefine.toggleLock(stack, rules, index);
        switch (result) {
            case LOCKED, UNLOCKED -> {
                Component name = Component.translatable(subs.get(index).type().translationKey());
                player.displayClientMessage(Component.translatable(result == AccessoryRefine.LockResult.LOCKED
                        ? "message.amemaru.refine.locked" : "message.amemaru.refine.unlocked", name).withStyle(ChatFormatting.AQUA), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS,
                        0.6F, result == AccessoryRefine.LockResult.LOCKED ? 1.4F : 1.0F);
            }
            case TOO_MANY -> player.displayClientMessage(Component.translatable("message.amemaru.refine.too_many_locks",
                    AccessoryRefine.maxLocks(stack, rules)).withStyle(ChatFormatting.RED), true);
            case NO_SUB -> {
            }
        }
        menu.broadcastChanges();
        sendQuote(player, menu);
    }

    /** 自分で振り直す（費用を払う）。 */
    public static Outcome reroll(ServerPlayer player, ItemStack stack, boolean types) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        Outcome check = checkItem(stack);
        if (data == null || check != Outcome.DONE) {
            return data == null ? Outcome.NOT_ACCESSORY : check;
        }
        if (!AccessoryRefine.canReroll(stack)) {
            return Outcome.ALL_LOCKED;
        }
        AccessoryRules rules = AccessoryRules.current();
        AccessoryRefine.Cost cost = AccessoryRefine.rerollCost(stack, rules, types, today(player), configMultiplier(), discountOf(player, data));
        Outcome paid = pay(player, data, cost, types ? "refine_types" : "refine_values");
        if (paid != Outcome.DONE) {
            return paid;
        }
        if (applyReroll(player, stack, rules, types)) {
            onBecamePerfect(player, stack);
        }
        return Outcome.DONE;
    }

    /**
     * 振り直しを書きこむ（費用は払い済み）。振り直すほどの上乗せを足し、厳選の回数を数える。
     *
     * @param worker 振り直した人（回数を数える。依頼の厳選では、受けた装飾師）
     * @return この振り直しで完璧になったか（知らせるのは呼んだ側。依頼なら持ち主に）
     */
    public static boolean applyReroll(ServerPlayer worker, ItemStack stack, AccessoryRules rules, boolean types) {
        boolean wasPerfect = AccessoryData.storedPerfect(stack);
        if (types) {
            AccessoryRefine.rerollTypes(stack, rules, worker.getRandom());
        } else {
            AccessoryRefine.rerollValues(stack, rules, worker.getRandom());
        }
        AccessoryData.addCostUp(stack, today(worker), rules.refine().escalationStep(), rules.refine().escalationMax());
        countReroll(worker);
        worker.level().playSound(null, worker.getX(), worker.getY(), worker.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8F, types ? 0.8F : 1.2F);
        return !wasPerfect && AccessoryData.storedPerfect(stack);
    }

    /** 厳選の回数（称号「宝石研ぎ」50・「結晶細工師」300・「輝きの探求者」1,000）。 */
    public static void countReroll(ServerPlayer player) {
        PlayerDataCapability.get(player).ifPresent(data -> data.getGuild().addCounter(GuildCounters.REROLLS, 1));
        TitleService.check(player);
    }

    /** 自分で強化する（失敗なし）。 */
    public static Outcome enhance(ServerPlayer player, ItemStack stack) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        Outcome check = checkItem(stack);
        if (data == null || check != Outcome.DONE) {
            return data == null ? Outcome.NOT_ACCESSORY : check;
        }
        AccessoryRules rules = AccessoryRules.current();
        AccessoryRefine.Cost cost = AccessoryRefine.enhanceCost(stack, rules, configMultiplier(), discountOf(player, data));
        if (cost == null) {
            return Outcome.MAX_ENHANCE;
        }
        Outcome paid = pay(player, data, cost, "refine_enhance");
        if (paid != Outcome.DONE) {
            return paid;
        }
        boolean wasPerfect = AccessoryData.storedPerfect(stack);
        applyEnhance(player, stack, rules);
        if (!wasPerfect && AccessoryData.storedPerfect(stack)) {
            onBecamePerfect(player, stack);
        }
        return Outcome.DONE;
    }

    /**
     * 強化を書きこむ（費用は払い済み）。サブが増えた・伸びたら、強化した人に知らせる（完璧になったかは、呼んだ側が確かめる）。
     *
     * @return 強化の結果（もう上げられなければ null）
     */
    @Nullable
    public static AccessoryRefine.EnhanceResult applyEnhance(ServerPlayer player, ItemStack stack, AccessoryRules rules) {
        AccessoryRefine.EnhanceResult result = AccessoryRefine.enhance(stack, rules, player.getRandom());
        if (result == null) {
            return null;
        }
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.ENHANCED, stack.copy()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
        if (result.added() != null) {
            player.displayClientMessage(Component.translatable("message.amemaru.refine.sub_added",
                    Component.translatable(result.added().translationKey())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else if (result.grown() != null) {
            player.displayClientMessage(Component.translatable("message.amemaru.refine.sub_grown",
                    Component.translatable(result.grown().translationKey())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        return result;
    }

    /** 分解する（台の品が消え、素材が持ち物に入る）。 */
    public static Outcome dismantle(ServerPlayer player, WorkshopMenu menu) {
        ItemStack stack = menu.workStack();
        Outcome check = checkItem(stack);
        if (check != Outcome.DONE) {
            return check;
        }
        List<ItemStack> yield = AccessoryRefine.dismantleYield(stack, AccessoryRules.current());
        ItemStack gone = stack.copy();
        menu.clearWork();
        for (ItemStack one : yield) {
            if (!player.getInventory().add(one)) {
                player.drop(one, false);
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                0.9F, 0.8F);
        AmemaruMod.LOGGER.info("[amemaru][refine] {} dismantled {} -> {}", player.getGameProfile().getName(),
                gone.getHoverName().getString(), yield);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- 共通

    private static Outcome checkItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof AccessoryItem)) {
            return Outcome.NOT_ACCESSORY;
        }
        return AccessoryData.isRolled(stack) ? Outcome.DONE : Outcome.UNROLLED;
    }

    /** 素材とノットを払う（両方そろっているときだけ。クリエイティブは素材を使わない）。 */
    private static Outcome pay(ServerPlayer player, PlayerData data, AccessoryRefine.Cost cost, String reason) {
        if (!WorkshopService.hasAll(player, cost.items(), 1.0)) {
            return Outcome.NEED_MATERIALS;
        }
        if (Wallet.balance(data) < cost.knots()) {
            return Outcome.NEED_KNOTS;
        }
        WorkshopService.consumeAll(player, cost.items(), 1.0);
        Wallet.pay(player, data, cost.knots(), reason);
        return Outcome.DONE;
    }

    /** 厳選で完璧になった：本人の画面の演出・告知・称号（Phase 0-A 21章【確定】）。 */
    public static void onBecamePerfect(ServerPlayer player, ItemStack stack) {
        AccessoryService.checkPerfect(player, stack);
    }

    /** 結果を知らせる（うまくいったときは、操作ごとの短い文）。 */
    private static void tell(ServerPlayer player, Outcome outcome, @Nullable ItemStack stack) {
        Component text = switch (outcome) {
            case DONE -> null;
            case NOT_ACCESSORY -> Component.translatable("message.amemaru.refine.not_accessory");
            case UNROLLED -> Component.translatable("message.amemaru.refine.unrolled");
            case ALL_LOCKED -> Component.translatable("message.amemaru.refine.all_locked");
            case MAX_ENHANCE -> Component.translatable("message.amemaru.refine.max_enhance", AccessoryRules.current().maxEnhance());
            case NEED_MATERIALS -> Component.translatable("message.amemaru.workshop.need_materials");
            case NEED_KNOTS -> Component.translatable("message.amemaru.refine.need_knots");
        };
        if (text != null) {
            player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    /** ノットの額の書き方（1,234）。 */
    public static String knots(long amount) {
        return KnotTransfer.format(amount);
    }
}
