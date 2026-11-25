package jp.amemaru.lostconnection.social;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryRefine;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.accessory.RefineService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import jp.amemaru.lostconnection.economy.StorageService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SocialS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.workshop.WorkshopBlock;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 依頼台（Phase 6 その4。Phase 0-A 21章「装飾師による依頼の厳選」【提案】・24章「装飾師への厳選依頼の仕組み：依頼台で品物と素材を
 * サーバーが預かる。目標と回数の上限を決められる」）。
 *
 * <ol>
 *   <li>持ち主が装飾工房の依頼台に、アクセサリーと素材・ノットを預ける。「固定するサブ」「目標」「回数の上限」「手数料」を決める。
 *       素材とノットは、回数の上限まで振り直せる分（装飾師の値引きの前の額）を預かる</li>
 *   <li>装飾師のプレイヤーが依頼を受け、持ち主の素材で厳選する。結果はそのたび持ち主に届き、目標に届くか、持ち主が
 *       「ここで止める」を押すか、回数の上限で終わる</li>
 *   <li>品物はサーバーが預かるので、装飾師は持ち逃げできない。終わったら、品と残りの素材・ノットは持ち主へ、手数料は装飾師へ</li>
 *   <li>装飾師は手数料と「依頼実績」（称号「誰かのための槌」の進み具合）を得て、縁帳に残る（コネクション量は Phase 7）</li>
 *   <li>装飾師の熟練度が高いほど費用が下がる（熟練度1ごとに −2%、最大 −20%）。結果の確率は変えない（公平のため）</li>
 * </ol>
 *
 * <p>強化の依頼（目標の段まで）も受け付ける【補完】（強化は厳選の操作の一つのため）。</p>
 */
public final class CommissionBoard {
    /** 依頼台の画面を開いた作業台（操作のたびに、その近くにいるかを確かめる）。 */
    private static final Map<UUID, BlockPos> BENCH = new HashMap<>();
    /** 依頼台の画面を開いている人（変わったら一覧を送り直す）。 */
    private static final Set<UUID> VIEWING = new HashSet<>();
    /** 1人の装飾師が同時に受けられる依頼の数【補完】。 */
    public static final int WORKING_MAX = 3;
    /** 1回の「続けて振り直す」の回数。 */
    public static final int BATCH = 5;

    private CommissionBoard() {
    }

    // ================================================================ 画面

    /** 依頼台の画面を開く（装飾工房の作業台の画面の「依頼」から）。 */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!isBench(player, pos)) {
            player.displayClientMessage(Component.translatable("message.amemaru.commission.far").withStyle(ChatFormatting.RED), true);
            return;
        }
        BENCH.put(player.getUUID(), pos.immutable());
        VIEWING.add(player.getUUID());
        claimPayout(player);
        sendList(player, true);
    }

    public static void close(ServerPlayer player) {
        VIEWING.remove(player.getUUID());
    }

    public static void forget(UUID uuid) {
        VIEWING.remove(uuid);
        BENCH.remove(uuid);
    }

    public static void clearAll() {
        VIEWING.clear();
        BENCH.clear();
    }

    private static boolean isBench(ServerPlayer player, BlockPos pos) {
        double reach = ServerConfig.WORKSHOP_REACH.get();
        return player.level().isLoaded(pos) && player.level().getBlockState(pos).getBlock() instanceof WorkshopBlock block
                && block.type() == WorkshopType.ACCESSORY
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
    }

    /** 依頼台（開いた装飾工房の作業台）の近くにいるか。 */
    public static boolean nearBench(ServerPlayer player) {
        BlockPos pos = BENCH.get(player.getUUID());
        return pos != null && isBench(player, pos);
    }

    /** 装飾師か（生産職が装飾工房の職）。 */
    public static boolean isCrafter(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        return data != null && WorkshopService.mastery(player, data, WorkshopType.ACCESSORY) > 0;
    }

    // ================================================================ 出す

    /** 依頼の中身（画面から）。 */
    record Request(int slot, Commission.Kind kind, List<Integer> locks, Commission.TargetType targetType, @Nullable StatType targetStat,
                   int targetValue, int attempts, long fee) {
        static Request parse(CompoundTag args) {
            List<Integer> locks = new ArrayList<>();
            for (int index : args.getIntArray("locks")) {
                if (index >= 0 && index < 4 && !locks.contains(index)) {
                    locks.add(index);
                }
            }
            StatType stat = args.contains("stat") ? StatType.byId(args.getString("stat")).orElse(null) : null;
            return new Request(args.getInt("slot"), Commission.Kind.byName(args.getString("kind")), locks,
                    Commission.TargetType.byName(args.getString("target")), stat, args.getInt("value"), args.getInt("attempts"),
                    Math.max(0L, args.getLong("fee")));
        }
    }

    /** 預ける額（素材とノット。回数の上限まで振り直せる分。装飾師の値引きの前）。 */
    record Deposit(long knots, Map<ResourceLocation, Integer> items) {
    }

    /** 依頼の中身を確かめて、預けるアクセサリー（固定を入れた写し）を作る。だめなら理由の翻訳キーを返す。 */
    @Nullable
    private static String validate(ServerPlayer owner, Request request, ItemStack[] out) {
        if (request.slot() < 0 || request.slot() >= owner.getInventory().items.size()) {
            return "message.amemaru.commission.pick";
        }
        ItemStack stack = owner.getInventory().items.get(request.slot());
        if (!(stack.getItem() instanceof AccessoryItem) || !AccessoryData.isRolled(stack)) {
            return "message.amemaru.commission.pick";
        }
        AccessoryRules rules = AccessoryRules.current();
        ItemStack copy = stack.copy();
        copy.setCount(1);
        // 固定は、依頼で決めたとおりに付け直す
        AccessoryData.StatLine main = AccessoryData.main(copy);
        List<AccessoryData.StatLine> subs = new ArrayList<>(AccessoryData.subs(copy));
        if (main == null) {
            return "message.amemaru.commission.pick";
        }
        boolean enhance = request.kind() == Commission.Kind.ENHANCE;
        for (int i = 0; i < subs.size(); i++) {
            subs.set(i, subs.get(i).withLocked(!enhance && request.locks().contains(i)));
        }
        AccessoryData.replaceLines(copy, main, subs);
        if (!enhance) {
            if (AccessoryData.lockedCount(copy) > AccessoryRefine.maxLocks(copy, rules)) {
                return "message.amemaru.commission.too_many_locks";
            }
            if (!AccessoryRefine.canReroll(copy)) {
                return "message.amemaru.refine.all_locked";
            }
            int max = ServerConfig.COMMISSION_MAX_ATTEMPTS.get();
            if (request.attempts() < 1 || request.attempts() > max) {
                return "message.amemaru.commission.bad_attempts";
            }
            switch (request.targetType()) {
                case STAT -> {
                    if (request.targetStat() == null || request.targetStat() == main.type() || request.targetValue() < 50
                            || request.targetValue() > 100 || !rules.subs().containsKey(request.targetStat())) {
                        return "message.amemaru.commission.bad_target";
                    }
                    if (request.kind() == Commission.Kind.VALUES && subs.stream().noneMatch(s -> s.type() == request.targetStat())) {
                        return "message.amemaru.commission.bad_target_values";
                    }
                }
                case QUALITY -> {
                    if (request.targetValue() < 1 || request.targetValue() > 5) {
                        return "message.amemaru.commission.bad_target";
                    }
                }
                case PERFECT, NONE -> {
                }
                case ENHANCE -> {
                    return "message.amemaru.commission.bad_target";
                }
            }
        } else {
            int now = AccessoryData.enhance(copy);
            if (request.targetValue() <= now || request.targetValue() > rules.maxEnhance()) {
                return "message.amemaru.commission.bad_enhance";
            }
        }
        if (request.fee() > 1_000_000_000L) {
            return "message.amemaru.commission.bad_fee";
        }
        out[0] = copy;
        return null;
    }

    /** 回数の上限まで振り直す（強化なら目標の段まで上げる）のに要る素材とノット（値引きの前）。 */
    static Deposit deposit(ItemStack stack, Commission.Kind kind, int attempts, int targetLevel, int today) {
        AccessoryRules rules = AccessoryRules.current();
        double multiplier = RefineService.configMultiplier();
        ItemStack sim = stack.copy();
        long knots = 0;
        Map<ResourceLocation, Integer> items = new LinkedHashMap<>();
        if (kind == Commission.Kind.ENHANCE) {
            for (int level = AccessoryData.enhance(sim) + 1; level <= targetLevel; level++) {
                AccessoryRefine.Cost cost = AccessoryRefine.enhanceCost(sim, rules, multiplier, 0);
                if (cost == null) {
                    break;
                }
                knots += cost.knots();
                merge(items, cost.items());
                AccessoryData.setEnhance(sim, level);
            }
        } else {
            boolean types = kind == Commission.Kind.TYPES;
            for (int i = 0; i < attempts; i++) {
                AccessoryRefine.Cost cost = AccessoryRefine.rerollCost(sim, rules, types, today, multiplier, 0);
                knots += cost.knots();
                merge(items, cost.items());
                AccessoryData.addCostUp(sim, today, rules.refine().escalationStep(), rules.refine().escalationMax());
            }
        }
        return new Deposit(knots, items);
    }

    private static void merge(Map<ResourceLocation, Integer> items, List<EquipmentDef.Cost> costs) {
        for (EquipmentDef.Cost cost : costs) {
            items.merge(cost.item(), cost.count(), Integer::sum);
        }
    }

    /** 出す前の見積もり（画面に、預ける素材とノット・手数料を出す）。 */
    public static void quote(ServerPlayer owner, CompoundTag args) {
        Request request = Request.parse(args);
        ItemStack[] out = new ItemStack[1];
        String error = validate(owner, request, out);
        CompoundTag tag = new CompoundTag();
        if (error != null) {
            tag.putString("error", error);
        } else {
            int attempts = request.kind() == Commission.Kind.ENHANCE ? request.targetValue() - AccessoryData.enhance(out[0])
                    : request.attempts();
            Deposit deposit = deposit(out[0], request.kind(), attempts, request.targetValue(), ProgressionData.currentDay(owner.server));
            tag.putLong("knots", deposit.knots());
            tag.putLong("fee", request.fee());
            tag.put("items", itemsTag(deposit.items()));
            tag.putInt("attempts", attempts);
            PlayerData data = PlayerDataCapability.get(owner).orElse(null);
            tag.putBoolean("enoughKnots", data != null && Wallet.balance(data) >= deposit.knots() + request.fee());
            tag.putBoolean("enoughItems", WorkshopService.hasAll(owner, costs(deposit.items()), 1.0));
        }
        tag.putInt("slot", request.slot());
        ModNetwork.sendTo(owner, new SocialS2C("commission_quote", tag));
    }

    /** 依頼を出す（アクセサリー・素材・ノット・手数料を預ける）。 */
    public static boolean create(ServerPlayer owner, CompoundTag args) {
        if (!nearBench(owner)) {
            tell(owner, "message.amemaru.commission.far");
            return false;
        }
        CommissionData board = CommissionData.get(owner.server);
        if (board.activeOf(owner.getUUID()) >= ServerConfig.COMMISSION_MAX_OPEN.get()) {
            tell(owner, "message.amemaru.commission.too_many", ServerConfig.COMMISSION_MAX_OPEN.get());
            return false;
        }
        Request request = Request.parse(args);
        ItemStack[] out = new ItemStack[1];
        String error = validate(owner, request, out);
        if (error != null) {
            tell(owner, error, AccessoryRefine.maxLocks(owner.getInventory().items.get(Math.max(0, Math.min(owner.getInventory().items.size() - 1,
                    request.slot()))), AccessoryRules.current()));
            return false;
        }
        ItemStack item = out[0];
        if (reached(item, request.targetType(), request.targetStat(), request.targetValue())) {
            tell(owner, "message.amemaru.commission.already");
            return false;
        }
        int today = ProgressionData.currentDay(owner.server);
        int attempts = request.kind() == Commission.Kind.ENHANCE ? request.targetValue() - AccessoryData.enhance(item) : request.attempts();
        Deposit deposit = deposit(item, request.kind(), attempts, request.targetValue(), today);
        PlayerData data = PlayerDataCapability.get(owner).orElse(null);
        List<EquipmentDef.Cost> costs = costs(deposit.items());
        if (data == null || !WorkshopService.hasAll(owner, costs, 1.0)) {
            tell(owner, "message.amemaru.workshop.need_materials");
            return false;
        }
        long total = deposit.knots() + request.fee();
        if (Wallet.balance(data) < total) {
            tell(owner, "message.amemaru.commission.need_knots", KnotTransfer.format(total));
            return false;
        }
        // 預かる：品（持ち物の枠から）・素材・ノット（費用と手数料）
        ItemStack slotStack = owner.getInventory().items.get(request.slot());
        slotStack.shrink(1);
        if (slotStack.isEmpty()) {
            owner.getInventory().items.set(request.slot(), ItemStack.EMPTY);
        }
        if (!owner.getAbilities().instabuild) {
            WorkshopService.consumeAll(owner, costs, 1.0);
        }
        Wallet.hold(owner, data, total, "commission");
        Commission c = board.create();
        c.owner = owner.getUUID();
        c.ownerName = owner.getGameProfile().getName();
        c.kind = request.kind();
        c.item = item;
        c.targetType = request.kind() == Commission.Kind.ENHANCE ? Commission.TargetType.ENHANCE : request.targetType();
        c.targetStat = c.targetType == Commission.TargetType.STAT ? request.targetStat() : null;
        c.targetValue = request.targetValue();
        c.maxAttempts = attempts;
        c.fee = request.fee();
        c.escrowKnots = deposit.knots();
        c.escrowItems.putAll(deposit.items());
        c.createdDay = today;
        c.createdMillis = System.currentTimeMillis();
        board.changed();
        owner.inventoryMenu.broadcastChanges();
        owner.sendSystemMessage(Component.translatable("message.amemaru.commission.created", c.id, item.getHoverName(),
                KnotTransfer.format(c.fee)).withStyle(ChatFormatting.AQUA));
        AmemaruMod.LOGGER.info("[amemaru][commission] #{} {} {} {} target {} {} attempts {} fee {} held {}", c.id, c.ownerName, c.kind,
                item.getHoverName().getString(), c.targetType, c.targetValue, c.maxAttempts, c.fee, total);
        // 装飾師に知らせる
        for (ServerPlayer other : owner.server.getPlayerList().getPlayers()) {
            if (other != owner && isCrafter(other)) {
                other.sendSystemMessage(Component.translatable("message.amemaru.commission.new", c.ownerName, item.getHoverName(),
                        KnotTransfer.format(c.fee)).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        refreshViewers(owner.server);
        return true;
    }

    // ================================================================ 受ける・手放す

    public static boolean accept(ServerPlayer crafter, int id) {
        CommissionData board = CommissionData.get(crafter.server);
        Commission c = board.byId(id);
        if (c == null || c.state != Commission.State.OPEN) {
            tell(crafter, "message.amemaru.commission.gone");
            return false;
        }
        if (!nearBench(crafter)) {
            tell(crafter, "message.amemaru.commission.far");
            return false;
        }
        if (!isCrafter(crafter)) {
            tell(crafter, "message.amemaru.commission.not_crafter");
            return false;
        }
        if (c.owner.equals(crafter.getUUID())) {
            tell(crafter, "message.amemaru.commission.own");
            return false;
        }
        if (board.workingOf(crafter.getUUID()) >= WORKING_MAX) {
            tell(crafter, "message.amemaru.commission.busy", WORKING_MAX);
            return false;
        }
        c.state = Commission.State.WORKING;
        c.crafter = crafter.getUUID();
        c.crafterName = crafter.getGameProfile().getName();
        board.changed();
        crafter.sendSystemMessage(Component.translatable("message.amemaru.commission.accepted", c.id, c.ownerName).withStyle(ChatFormatting.AQUA));
        notifyOwner(crafter.server, c, Component.translatable("message.amemaru.commission.accepted_owner", c.id, c.crafterName));
        refreshViewers(crafter.server);
        return true;
    }

    public static boolean release(ServerPlayer crafter, int id) {
        CommissionData board = CommissionData.get(crafter.server);
        Commission c = board.byId(id);
        if (c == null || c.state != Commission.State.WORKING || !crafter.getUUID().equals(c.crafter)) {
            tell(crafter, "message.amemaru.commission.gone");
            return false;
        }
        String name = c.crafterName;
        c.state = Commission.State.OPEN;
        c.crafter = null;
        c.crafterName = "";
        board.changed();
        crafter.sendSystemMessage(Component.translatable("message.amemaru.commission.released", c.id).withStyle(ChatFormatting.GRAY));
        notifyOwner(crafter.server, c, Component.translatable("message.amemaru.commission.released_owner", c.id, name));
        refreshViewers(crafter.server);
        return true;
    }

    // ================================================================ 厳選する

    /** 受けた依頼で、厳選する（1回か、続けて {@link #BATCH} 回）。 */
    public static boolean attempt(ServerPlayer crafter, int id, int times) {
        MinecraftServer server = crafter.server;
        CommissionData board = CommissionData.get(server);
        Commission c = board.byId(id);
        if (c == null || c.state != Commission.State.WORKING || !crafter.getUUID().equals(c.crafter)) {
            tell(crafter, "message.amemaru.commission.gone");
            return false;
        }
        if (!nearBench(crafter)) {
            tell(crafter, "message.amemaru.commission.far");
            return false;
        }
        PlayerData data = PlayerDataCapability.get(crafter).orElse(null);
        if (data == null) {
            return false;
        }
        AccessoryRules rules = AccessoryRules.current();
        int discount = RefineService.discountOf(crafter, data);
        int today = ProgressionData.currentDay(server);
        int count = Math.max(1, Math.min(BATCH, times));
        for (int i = 0; i < count && c.state == Commission.State.WORKING; i++) {
            if (c.attempts >= c.maxAttempts) {
                finish(server, c, Commission.EndReason.LIMIT);
                break;
            }
            AccessoryRefine.Cost cost = c.kind == Commission.Kind.ENHANCE
                    ? AccessoryRefine.enhanceCost(c.item, rules, RefineService.configMultiplier(), discount)
                    : AccessoryRefine.rerollCost(c.item, rules, c.kind == Commission.Kind.TYPES, today, RefineService.configMultiplier(), discount);
            if (cost == null || !canPay(c, cost)) {
                finish(server, c, Commission.EndReason.NO_FUNDS);
                break;
            }
            payFromEscrow(server, c, cost);
            boolean wasPerfect = AccessoryData.storedPerfect(c.item);
            if (c.kind == Commission.Kind.ENHANCE) {
                RefineService.applyEnhance(crafter, c.item, rules);
            } else {
                RefineService.applyReroll(crafter, c.item, rules, c.kind == Commission.Kind.TYPES);
            }
            c.attempts++;
            Component line = resultLine(c);
            c.addLog(Component.Serializer.toJson(line));
            board.changed();
            if (!wasPerfect && AccessoryData.storedPerfect(c.item)) {
                onPerfect(server, c, crafter);
            }
            boolean done = reached(c.item, c.targetType, c.targetStat, c.targetValue);
            MutableComponent toOwner = Component.translatable("message.amemaru.commission.result", c.id, c.crafterName, line);
            if (!done && c.attempts < c.maxAttempts) {
                toOwner.append(" ").append(button("message.amemaru.commission.button_stop", "/amemaru commission stop " + c.id));
            }
            notifyOwner(server, c, toOwner);
            crafter.displayClientMessage(Component.translatable("message.amemaru.commission.result_crafter", c.id, c.attempts, c.maxAttempts),
                    true);
            if (done) {
                finish(server, c, Commission.EndReason.TARGET);
            } else if (c.attempts >= c.maxAttempts) {
                finish(server, c, Commission.EndReason.LIMIT);
            }
        }
        refreshViewers(server);
        return true;
    }

    private static boolean canPay(Commission c, AccessoryRefine.Cost cost) {
        if (c.escrowKnots < cost.knots()) {
            return false;
        }
        for (EquipmentDef.Cost item : cost.items()) {
            if (c.escrowCount(item.item()) < item.count()) {
                return false;
            }
        }
        return true;
    }

    private static void payFromEscrow(MinecraftServer server, Commission c, AccessoryRefine.Cost cost) {
        c.escrowKnots -= cost.knots();
        c.spentKnots += cost.knots();
        for (EquipmentDef.Cost item : cost.items()) {
            int left = c.escrowCount(item.item()) - item.count();
            if (left > 0) {
                c.escrowItems.put(item.item(), left);
            } else {
                c.escrowItems.remove(item.item());
            }
        }
        // 厳選の費用は、世界から消える（回収）。払ったのは持ち主（預かっていた分）
        Wallet.recordCollect(server, c.kind == Commission.Kind.ENHANCE ? "refine_enhance (commission)" : "refine_reroll (commission)",
                c.ownerName, cost.knots());
    }

    /** 目標に届いたか。 */
    static boolean reached(ItemStack stack, Commission.TargetType type, @Nullable StatType stat, int value) {
        AccessoryRules rules = AccessoryRules.current();
        return switch (type) {
            case STAT -> {
                for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
                    if (sub.type() == stat && sub.ratio() * 100.0 + 1.0E-6 >= value) {
                        yield true;
                    }
                }
                yield false;
            }
            case QUALITY -> AccessoryData.quality(stack, rules) >= value;
            case PERFECT -> AccessoryData.isPerfect(stack, rules);
            case ENHANCE -> AccessoryData.enhance(stack) >= value;
            case NONE -> false;
        };
    }

    /** 1回分の結果の文（サブの種類・値・割合と、品質）。 */
    private static Component resultLine(Commission c) {
        ItemStack stack = c.item;
        MutableComponent text = Component.translatable("message.amemaru.commission.result_head", c.attempts, c.maxAttempts);
        if (c.kind == Commission.Kind.ENHANCE) {
            text.append(Component.translatable("message.amemaru.commission.result_enhance", AccessoryData.enhance(stack)));
        }
        List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
        for (int i = 0; i < subs.size(); i++) {
            AccessoryData.StatLine sub = subs.get(i);
            text.append(i == 0 ? " " : "・");
            boolean hit = c.targetType == Commission.TargetType.STAT && sub.type() == c.targetStat;
            text.append(Component.translatable("message.amemaru.commission.result_sub", Component.translatable(sub.type().translationKey()),
                    sub.type().format(sub.value()), Math.round(sub.ratio() * 100.0))
                    .withStyle(hit ? ChatFormatting.GOLD : sub.locked() ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
        int quality = AccessoryData.storedQuality(stack);
        text.append(Component.translatable("message.amemaru.commission.result_quality", quality));
        return text;
    }

    /** 依頼で完璧になった：持ち主の画面の演出・告知・称号。装飾師は偉業（真眼の拡大鏡。Phase 8）に残す。 */
    private static void onPerfect(MinecraftServer server, Commission c, ServerPlayer crafter) {
        ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
        if (owner != null) {
            AccessoryService.checkPerfect(owner, c.item);
        } else {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.accessory.perfect_announce",
                    Component.literal(c.ownerName), c.item.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            CompoundTag pending = new CompoundTag();
            pending.putString("type", "event");
            pending.putString("event", "perfect_accessory");
            GuildState.get(server).addPending(c.owner, pending);
            TitleService.onFirst(server, "first_perfect_accessory", List.of(c.owner), List.of(c.ownerName));
        }
        int day = ProgressionData.currentDay(server);
        GuildState state = GuildState.get(server);
        if (state.markFeat("first_commission_perfect", new GuildState.Holder(crafter.getUUID(), crafter.getGameProfile().getName(), day))) {
            state.chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.first_commission_perfect",
                    List.of(crafter.getGameProfile().getName(), c.ownerName));
            AmemaruMod.LOGGER.info("[amemaru][commission] First perfect accessory by commission: {} for {}", c.crafterName, c.ownerName);
        }
    }

    // ================================================================ 止める・終える

    /** 持ち主が止める（受付中なら取り下げ。手数料も戻る）。 */
    public static boolean stop(ServerPlayer owner, int id) {
        CommissionData board = CommissionData.get(owner.server);
        Commission c = board.byId(id);
        if (c == null || c.state == Commission.State.DONE || !c.owner.equals(owner.getUUID())) {
            tell(owner, "message.amemaru.commission.gone");
            return false;
        }
        finish(owner.server, c, c.state == Commission.State.OPEN ? Commission.EndReason.CANCELLED : Commission.EndReason.STOPPED);
        refreshViewers(owner.server);
        return true;
    }

    /** 終える：品と残りの素材・ノットを持ち主へ、手数料を装飾師へ（一度も厳選していなければ持ち主へ）。 */
    static void finish(MinecraftServer server, Commission c, Commission.EndReason reason) {
        if (c.state == Commission.State.DONE) {
            return;
        }
        CommissionData board = CommissionData.get(server);
        int day = ProgressionData.currentDay(server);
        boolean worked = c.attempts > 0 && c.crafter != null;
        List<ItemStack> back = new ArrayList<>();
        back.add(c.item.copy());
        for (Map.Entry<ResourceLocation, Integer> entry : c.escrowItems.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.getKey());
            int left = entry.getValue();
            while (item != null && left > 0) {
                int size = Math.min(left, item.getMaxStackSize());
                back.add(new ItemStack(item, size));
                left -= size;
            }
        }
        long refund = c.escrowKnots + (worked ? 0 : c.fee);
        long fee = worked ? c.fee : 0;
        c.state = Commission.State.DONE;
        c.endReason = reason;
        c.endDay = day;
        c.escrowKnots = 0;
        c.escrowItems.clear();
        deliver(server, c.owner, c.ownerName, back, refund);
        if (fee > 0) {
            Wallet.recordMove(server, "commission_fee", c.ownerName, c.crafterName, fee);
            deliver(server, c.crafter, c.crafterName, List.of(), fee);
        }
        if (worked) {
            // 依頼実績（称号「誰かのための槌」：ほかの人のための制作・修理・厳選を合わせて30回）と縁帳
            addCounter(server, c.crafter, GuildCounters.HELPED, 1);
            connect(server, c.owner, c.crafter, c.crafterName, day);
            connect(server, c.crafter, c.owner, c.ownerName, day);
        }
        board.changed();
        Component itemName = c.item.getHoverName();
        Component end = Component.translatable("message.amemaru.commission.end." + reason.name().toLowerCase(java.util.Locale.ROOT));
        notifyOwner(server, c, Component.translatable("message.amemaru.commission.finished_owner", c.id, itemName, end,
                KnotTransfer.format(refund)));
        if (c.crafter != null) {
            ServerPlayer crafter = server.getPlayerList().getPlayer(c.crafter);
            if (crafter != null) {
                crafter.sendSystemMessage(Component.translatable("message.amemaru.commission.finished_crafter", c.id, end,
                        KnotTransfer.format(fee)).withStyle(ChatFormatting.AQUA));
            }
        }
        AmemaruMod.LOGGER.info("[amemaru][commission] #{} finished ({}) attempts {} spent {} refund {} fee {}", c.id, reason, c.attempts,
                c.spentKnots, refund, fee);
    }

    /** 品とノットを渡す（いれば持ち物と所持金へ。入らない品は受け取り箱へ。いなければ、入ったときに渡す）。 */
    private static void deliver(MinecraftServer server, UUID uuid, String name, List<ItemStack> items, long knots) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            CommissionData.get(server).addPayout(uuid, knots, items);
            return;
        }
        give(player, items, knots);
    }

    private static void give(ServerPlayer player, List<ItemStack> items, long knots) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data != null && knots > 0) {
            Wallet.receive(player, data, knots, "commission_return");
        }
        boolean toBox = false;
        for (ItemStack stack : items) {
            ItemStack copy = stack.copy();
            if (player.getInventory().add(copy) && copy.isEmpty()) {
                continue;
            }
            if (!copy.isEmpty()) {
                if (StorageService.deliver(player.server, player.getUUID(), player.getGameProfile().getName(), copy)) {
                    toBox = true;
                } else {
                    player.drop(copy, false);
                }
            }
        }
        if (toBox) {
            player.sendSystemMessage(Component.translatable("message.amemaru.commission.to_box").withStyle(ChatFormatting.YELLOW));
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** 入ったとき・依頼台を開いたとき：いない間に戻ってきた品・ノット・手数料を受け取る。 */
    public static void claimPayout(ServerPlayer player) {
        CommissionData.Payout payout = CommissionData.get(player.server).takePayout(player.getUUID());
        if (payout == null) {
            return;
        }
        give(player, payout.items(), payout.knots());
        player.sendSystemMessage(Component.translatable("message.amemaru.commission.payout", payout.items().size(),
                KnotTransfer.format(payout.knots())).withStyle(ChatFormatting.AQUA));
    }

    private static void addCounter(MinecraftServer server, @Nullable UUID uuid, String key, long amount) {
        if (uuid == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            PlayerDataCapability.get(player).ifPresent(data -> data.getGuild().addCounter(key, amount));
            TitleService.check(player);
        } else {
            CompoundTag pending = new CompoundTag();
            pending.putString("type", "counter");
            pending.putString("key", key);
            pending.putLong("amount", amount);
            GuildState.get(server).addPending(uuid, pending);
        }
    }

    private static void connect(MinecraftServer server, @Nullable UUID who, @Nullable UUID with, String withName, int day) {
        if (who == null || with == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player != null) {
            PlayerDataCapability.get(player).ifPresent(data -> data.getGuild().addConnection(with, withName,
                    GuildData.ConnectionKind.COMMISSION, day));
        } else {
            CompoundTag pending = new CompoundTag();
            pending.putString("type", "connection");
            pending.putUUID("with", with);
            pending.putString("name", withName);
            pending.putString("kind", GuildData.ConnectionKind.COMMISSION.name());
            pending.putInt("day", day);
            GuildState.get(server).addPending(who, pending);
        }
    }

    // ================================================================ 日が変わった

    /** 誰も受けないまま日がたった依頼を取り下げ、終わってから日がたった依頼を消す。 */
    public static void onDay(MinecraftServer server, int day) {
        CommissionData board = CommissionData.get(server);
        int expire = ServerConfig.COMMISSION_EXPIRE_DAYS.get();
        List<Integer> remove = new ArrayList<>();
        for (Commission c : new ArrayList<>(board.all())) {
            if (c.state == Commission.State.OPEN && day - c.createdDay >= expire) {
                finish(server, c, Commission.EndReason.EXPIRED);
            } else if (c.state == Commission.State.DONE && day - c.endDay > CommissionData.KEEP_DONE_DAYS) {
                remove.add(c.id);
            }
        }
        for (int id : remove) {
            board.remove(id);
        }
        refreshViewers(server);
    }

    // ================================================================ 送る

    /** 依頼台の一覧を送る（受付中・出した依頼・受けた依頼）。 */
    public static void sendList(ServerPlayer player, boolean openScreen) {
        CommissionData board = CommissionData.get(player.server);
        ListTag open = new ListTag();
        ListTag mine = new ListTag();
        ListTag working = new ListTag();
        for (Commission c : board.all()) {
            boolean own = c.owner.equals(player.getUUID());
            boolean mineWork = player.getUUID().equals(c.crafter);
            if (c.state == Commission.State.OPEN) {
                open.add(entry(c, own, false));
            }
            if (own) {
                mine.add(entry(c, true, true));
            } else if (mineWork) {
                working.add(entry(c, false, true));
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("open", open);
        tag.put("mine", mine);
        tag.put("working", working);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        tag.putBoolean("crafter", isCrafter(player));
        tag.putInt("discount", data == null ? 0 : RefineService.discountOf(player, data));
        tag.putInt("maxOpen", ServerConfig.COMMISSION_MAX_OPEN.get());
        tag.putInt("maxAttempts", ServerConfig.COMMISSION_MAX_ATTEMPTS.get());
        tag.putInt("expireDays", ServerConfig.COMMISSION_EXPIRE_DAYS.get());
        tag.putInt("maxEnhance", AccessoryRules.current().maxEnhance());
        tag.putInt("batch", BATCH);
        tag.putBoolean("screen", openScreen);
        ModNetwork.sendTo(player, new SocialS2C("commissions", tag));
    }

    private static CompoundTag entry(Commission c, boolean own, boolean detail) {
        CompoundTag one = new CompoundTag();
        one.putInt("id", c.id);
        one.putString("owner", c.ownerName);
        one.putString("crafter", c.crafterName);
        one.putString("state", c.state.name());
        one.putString("kind", c.kind.name());
        one.put("item", c.item.save(new CompoundTag()));
        one.putString("target", c.targetType.name());
        if (c.targetStat != null) {
            one.putString("stat", c.targetStat.id());
        }
        one.putInt("value", c.targetValue);
        one.putInt("max", c.maxAttempts);
        one.putInt("attempts", c.attempts);
        one.putLong("fee", c.fee);
        one.putInt("day", c.createdDay);
        one.putBoolean("own", own);
        one.putString("end", c.endReason.name());
        if (detail) {
            one.putLong("escrow", c.escrowKnots);
            one.putLong("spent", c.spentKnots);
            one.put("items", itemsTag(c.escrowItems));
            ListTag log = new ListTag();
            for (String line : c.log) {
                log.add(StringTag.valueOf(line));
            }
            one.put("log", log);
        }
        return one;
    }

    private static ListTag itemsTag(Map<ResourceLocation, Integer> items) {
        ListTag list = new ListTag();
        items.forEach((id, count) -> {
            CompoundTag one = new CompoundTag();
            one.putString("id", id.toString());
            one.putInt("count", count);
            list.add(one);
        });
        return list;
    }

    private static List<EquipmentDef.Cost> costs(Map<ResourceLocation, Integer> items) {
        List<EquipmentDef.Cost> list = new ArrayList<>();
        items.forEach((id, count) -> list.add(new EquipmentDef.Cost(id, count)));
        return list;
    }

    /** 画面を開いている人に、一覧を送り直す。 */
    public static void refreshViewers(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(VIEWING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                VIEWING.remove(uuid);
            } else {
                sendList(player, false);
            }
        }
    }

    // ================================================================ 知らせ

    private static void notifyOwner(MinecraftServer server, Commission c, Component text) {
        ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
        if (owner != null) {
            owner.sendSystemMessage(text.copy().withStyle(ChatFormatting.AQUA));
        }
    }

    private static Component button(String key, String command) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    /** 管理者の確かめ用：今ある依頼の一覧の文。 */
    public static List<Component> describe(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        for (Commission c : CommissionData.get(server).all()) {
            lines.add(Component.literal("#" + c.id + " " + c.state + " " + c.kind + " " + c.ownerName + " → "
                    + (c.crafterName.isEmpty() ? "-" : c.crafterName) + " " + c.item.getHoverName().getString() + " " + c.attempts + "/"
                    + c.maxAttempts + " fee " + c.fee + " held " + c.escrowKnots + " " + c.endReason));
        }
        return lines;
    }

    /** 管理者が依頼を終わらせる（品・素材・ノットは持ち主へ戻す）。 */
    public static boolean adminCancel(MinecraftServer server, int id) {
        Commission c = CommissionData.get(server).byId(id);
        if (c == null || c.state == Commission.State.DONE) {
            return false;
        }
        finish(server, c, c.attempts > 0 ? Commission.EndReason.STOPPED : Commission.EndReason.CANCELLED);
        refreshViewers(server);
        return true;
    }

    /** テスト用：開いた作業台を覚えさせる。 */
    public static void setBenchForTest(UUID uuid, BlockPos pos) {
        BENCH.put(uuid, pos.immutable());
    }

    /** テスト用：中身を読む。 */
    @Nullable
    public static Commission get(MinecraftServer server, int id) {
        return CommissionData.get(server).byId(id);
    }
}
