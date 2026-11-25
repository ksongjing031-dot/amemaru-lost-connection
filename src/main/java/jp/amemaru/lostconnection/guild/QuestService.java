package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.PlayerStory;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * クエスト（Phase 6 その3。Phase 0-A 7章「ギルド：クエスト掲示板」・11章・13章・14章・19章）。
 *
 * <ul>
 *   <li>ギルドの掲示板（{@link GuildService}）で受けて、目標を満たし、掲示板か依頼したNPCに報告すると報酬</li>
 *   <li>街めぐり：1日目、職業を登録すると自動で受ける（全施設を回る。リリアに報告）</li>
 *   <li>デイリー：毎日3つ。1日に3回まで（Phase 0-A 13章「デイリークエスト：1日3回」）。日が変わると、受けていた分は消える【補完】</li>
 *   <li>上位クエスト：5日目から。昇格クエスト：11日目・Lv30から（終えると上位職に昇格。Phase 0-A 19章）</li>
 *   <li>NPCの個人的な依頼：親密度2・3で、そのNPCとの会話から受ける</li>
 * </ul>
 */
public final class QuestService {
    /** 同時に受けられるクエストの数【補完】。 */
    public static final int ACTIVE_MAX = 12;
    /** デイリーの数（掲示板に出る数と、1日に終えられる数）。 */
    public static final int DAILY_OFFER = 3;
    public static final int DAILY_MAX = 3;
    /** 街めぐりのクエスト。 */
    public static final ResourceLocation TOWN_TOUR = new ResourceLocation("amemaru", "town_tour");

    /** 掲示板での状態。 */
    public enum State {
        AVAILABLE, ACTIVE, DONE, LOCKED, NOT_TODAY
    }

    private QuestService() {
    }

    // ================================================================ 受けられるか

    /** 条件（進行日・物語フラグ・親密度・レベル・昇格クエストの決まり）を満たしているか。 */
    public static boolean requirementsMet(ServerPlayer player, PlayerData data, QuestDef def) {
        if (!def.requires().test(player, def.giver())) {
            return false;
        }
        if (data.getLevel() < def.levelMin()) {
            return false;
        }
        if (def.kind() == QuestDef.Kind.PROMOTION) {
            JobDef job = SkillRegistry.get().job(data.getCombatJob());
            if (job == null || job.upper() == null || data.isPromoted()) {
                return false;
            }
            int day = ProgressionData.get(player.server).day();
            return day >= job.upper().minDay() && data.getLevel() >= job.upper().minLevel();
        }
        return true;
    }

    public static State state(ServerPlayer player, PlayerData data, QuestDef def) {
        GuildData guild = data.getGuild();
        if (guild.quest(def.id()) != null) {
            return State.ACTIVE;
        }
        GuildData.Completed done = guild.completed(def.id());
        int day = ProgressionData.get(player.server).day();
        if (def.daily()) {
            if (!guild.dailyOffer().contains(def.id())) {
                return State.NOT_TODAY;
            }
            if (done != null && done.lastDay() == day) {
                return State.DONE;
            }
            if (guild.dailyDone() >= DAILY_MAX) {
                return State.DONE;
            }
        } else if (done != null) {
            return State.DONE;
        }
        return requirementsMet(player, data, def) ? State.AVAILABLE : State.LOCKED;
    }

    /** 日が変わっていたら、今日のデイリーを決め直す（昨日のデイリーは消える）。 */
    public static void ensureDaily(ServerPlayer player, PlayerData data) {
        int day = ProgressionData.get(player.server).day();
        GuildData guild = data.getGuild();
        if (guild.dailyDay() == day && !guild.dailyOffer().isEmpty()) {
            return;
        }
        for (ResourceLocation id : List.copyOf(guild.quests().keySet())) {
            QuestDef def = QuestDefs.get(id);
            if (def != null && def.daily()) {
                guild.dropQuest(id);
                player.displayClientMessage(Component.translatable("message.amemaru.quest.daily_expired", def.name())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        List<QuestDef> pool = new ArrayList<>();
        for (QuestDef def : QuestDefs.sorted()) {
            if (def.daily() && requirementsMet(player, data, def)) {
                pool.add(def);
            }
        }
        Random random = new Random(player.getUUID().getMostSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        List<ResourceLocation> offer = new ArrayList<>();
        while (!pool.isEmpty() && offer.size() < DAILY_OFFER) {
            offer.add(pool.remove(random.nextInt(pool.size())).id());
        }
        guild.resetDaily(day, offer);
    }

    // ================================================================ 受ける・やめる

    /** クエストを受ける。受けたら true。 */
    public static boolean accept(ServerPlayer player, ResourceLocation id, @Nullable ResourceLocation fromNpc) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        QuestDef def = QuestDefs.get(id);
        if (data == null || def == null) {
            return false;
        }
        ensureDaily(player, data);
        State state = state(player, data, def);
        if (state != State.AVAILABLE) {
            if (state != State.ACTIVE) {
                tell(player, "message.amemaru.quest.cannot_accept", def.name());
            }
            return false;
        }
        if (data.getGuild().quests().size() >= ACTIVE_MAX) {
            tell(player, "message.amemaru.quest.too_many", ACTIVE_MAX);
            return false;
        }
        data.getGuild().startQuest(id, def.objectives().size(), ProgressionData.get(player.server).day());
        player.displayClientMessage(Component.translatable("message.amemaru.quest.accepted", def.name()).withStyle(ChatFormatting.AQUA),
                false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
        // 受けた時点で満たしている目標（持っている品・見つけた記録など）は数えておく
        refresh(player, data);
        GuildService.syncSelf(player, data);
        return true;
    }

    public static boolean abandon(ServerPlayer player, ResourceLocation id) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        QuestDef def = QuestDefs.get(id);
        if (data == null || def == null || def.kind() == QuestDef.Kind.TOWN_TOUR) {
            return false;
        }
        if (!data.getGuild().dropQuest(id)) {
            return false;
        }
        tell(player, "message.amemaru.quest.abandoned", def.name());
        GuildService.syncSelf(player, data);
        return true;
    }

    // ================================================================ 進める

    /** 敵を倒した（パーティで近くにいた仲間にも数える。DropService から）。 */
    public static void onKill(ServerPlayer player, ResourceLocation enemy, int floor) {
        progress(player, (def, o) -> ("kill".equals(o.type()) && enemy.equals(o.target()) && (o.floor() == 0 || o.floor() == floor))
                || ("kill_any".equals(o.type()) && (o.floor() == 0 || o.floor() == floor)) ? 1 : 0);
    }

    /** 階のボスを倒した。 */
    public static void onBoss(List<ServerPlayer> winners, int floor) {
        for (ServerPlayer player : winners) {
            progress(player, (def, o) -> "boss".equals(o.type()) && (o.floor() == 0 || o.floor() == floor) ? 1 : 0);
        }
    }

    public static void onChest(ServerPlayer player) {
        progress(player, (def, o) -> "chest".equals(o.type()) ? 1 : 0);
    }

    public static void onRecord(ServerPlayer player) {
        progress(player, (def, o) -> "record".equals(o.type()) ? 1 : 0);
    }

    public static void onPillar(ServerPlayer player) {
        progress(player, (def, o) -> "pillar".equals(o.type()) ? 1 : 0);
    }

    /** NPCと話した：「話す」の目標を進め、そのNPCに報告するクエストで終えたものを報告する。 */
    public static void onTalk(ServerPlayer player, ResourceLocation npc) {
        progress(player, (def, o) -> "talk".equals(o.type()) && npc.equals(o.target()) ? 1 : 0);
        report(player, null, npc);
    }

    @FunctionalInterface
    private interface Step {
        int amount(QuestDef def, QuestDef.Objective objective);
    }

    private static void progress(ServerPlayer player, Step step) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || data.getGuild().quests().isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<ResourceLocation, GuildData.QuestProgress> entry : List.copyOf(data.getGuild().quests().entrySet())) {
            QuestDef def = QuestDefs.get(entry.getKey());
            if (def == null) {
                continue;
            }
            boolean wasDone = done(player, def, entry.getValue());
            for (int i = 0; i < def.objectives().size(); i++) {
                QuestDef.Objective o = def.objectives().get(i);
                int amount = step.amount(def, o);
                if (amount > 0 && data.getGuild().setQuestCount(def.id(), i, entry.getValue().count(i) + amount, o.count())) {
                    changed = true;
                }
            }
            if (!wasDone && done(player, def, entry.getValue())) {
                readyToReport(player, def);
            }
        }
        if (changed) {
            GuildService.syncSelf(player, data);
        }
    }

    /** 1秒ごと：場所へ行く目標・持っている品の目標を見直す。街めぐりを自動で受ける。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        // 街めぐり：職業を登録したら自動で受ける（1日目の「全施設を回る」。Phase 0-A 11章）
        if (data.getCombatJob() != null && data.getStory().opening() >= PlayerStory.OPENING_DONE) {
            QuestDef tour = QuestDefs.get(TOWN_TOUR);
            if (tour != null && data.getGuild().quest(TOWN_TOUR) == null && data.getGuild().completed(TOWN_TOUR) == null) {
                accept(player, TOWN_TOUR, null);
            }
        }
        if (data.getGuild().quests().isEmpty()) {
            return;
        }
        refresh(player, data);
    }

    /** 場所へ行く・持っている品の目標を見直す。 */
    private static void refresh(ServerPlayer player, PlayerData data) {
        Optional<BlockPos> origin = TowerService.cityOrigin(player.server);
        boolean changed = false;
        for (Map.Entry<ResourceLocation, GuildData.QuestProgress> entry : List.copyOf(data.getGuild().quests().entrySet())) {
            QuestDef def = QuestDefs.get(entry.getKey());
            if (def == null) {
                continue;
            }
            boolean wasDone = done(player, def, entry.getValue());
            for (int i = 0; i < def.objectives().size(); i++) {
                QuestDef.Objective o = def.objectives().get(i);
                if ("visit".equals(o.type()) && entry.getValue().count(i) < 1 && origin.isPresent() && o.pos() != null
                        && "conelia".equals(o.place()) && player.level() == player.server.overworld()) {
                    double x = origin.get().getX() + o.pos().x;
                    double y = origin.get().getY() + o.pos().y;
                    double z = origin.get().getZ() + o.pos().z;
                    double dx = player.getX() - x;
                    double dz = player.getZ() - z;
                    if (dx * dx + dz * dz <= o.radius() * o.radius() && Math.abs(player.getY() - y) < 10.0) {
                        changed |= data.getGuild().setQuestCount(def.id(), i, 1, 1);
                        player.displayClientMessage(Component.translatable("message.amemaru.quest.visited", objectiveText(o))
                                .withStyle(ChatFormatting.AQUA), true);
                    }
                }
                if ("collect".equals(o.type())) {
                    int have = Math.min(o.count(), countItem(player, o.target()));
                    changed |= data.getGuild().setQuestCount(def.id(), i, have, o.count());
                }
            }
            if (!wasDone && done(player, def, entry.getValue())) {
                readyToReport(player, def);
            }
        }
        if (changed) {
            GuildService.syncSelf(player, data);
        }
    }

    private static void readyToReport(ServerPlayer player, QuestDef def) {
        Component where = def.reportTo() == null ? Component.translatable("quest.amemaru.report.board")
                : Component.literal(npcName(def.reportTo()));
        player.displayClientMessage(Component.translatable("message.amemaru.quest.ready", def.name(), where)
                .withStyle(ChatFormatting.GREEN), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    // ================================================================ 終える

    /** 目標の今の数（持っている品は、持ち物から数える）。 */
    public static int objectiveValue(ServerPlayer player, QuestDef def, int index, GuildData.QuestProgress progress) {
        QuestDef.Objective o = def.objectives().get(index);
        if ("collect".equals(o.type())) {
            return Math.min(o.count(), countItem(player, o.target()));
        }
        // 階を決めた石柱・宝箱・古代記録は、一人一度きりなので、受ける前に済ませた分も数える（その階で今までに起動した・開けた・見つけた数）
        if (o.floor() > 0 && ("pillar".equals(o.type()) || "chest".equals(o.type()) || "record".equals(o.type()))) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                int have = switch (o.type()) {
                    case "pillar" -> Integer.bitCount(data.getStory().pillars(o.floor()));
                    case "chest" -> data.getStory().chestCount(o.floor());
                    default -> (int) data.getGuild().records().keySet().stream()
                            .filter(id -> RecordDefs.get(id) != null && RecordDefs.get(id).floor() == o.floor()).count();
                };
                return Math.min(o.count(), Math.max(have, progress.count(index)));
            }
        }
        return Math.min(o.count(), progress.count(index));
    }

    public static boolean done(ServerPlayer player, QuestDef def, GuildData.QuestProgress progress) {
        for (int i = 0; i < def.objectives().size(); i++) {
            if (objectiveValue(player, def, i, progress) < def.objectives().get(i).count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 報告する。{@code id} が null なら、{@code npc} に報告するクエスト（{@code npc} も null なら掲示板に報告するクエスト）のうち、
     * 終えたものをすべて報告する。報告した数を返す。
     */
    public static int report(ServerPlayer player, @Nullable ResourceLocation id, @Nullable ResourceLocation npc) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<ResourceLocation, GuildData.QuestProgress> entry : List.copyOf(data.getGuild().quests().entrySet())) {
            QuestDef def = QuestDefs.get(entry.getKey());
            if (def == null || (id != null && !id.equals(def.id()))) {
                continue;
            }
            boolean here = npc == null ? def.reportTo() == null : npc.equals(def.reportTo());
            if (!here) {
                if (id != null) {
                    tell(player, "message.amemaru.quest.report_elsewhere", def.name());
                }
                continue;
            }
            if (!done(player, def, entry.getValue())) {
                if (id != null) {
                    tell(player, "message.amemaru.quest.not_done", def.name());
                }
                continue;
            }
            if (complete(player, data, def, false)) {
                count++;
            }
        }
        if (count > 0) {
            GuildService.syncSelf(player, data);
        }
        return count;
    }

    /**
     * 管理者：受けていなければ受けたことにして、目標を果たしていなくても終える（渡す品は取らない）。報酬は渡す。終えたら true。
     */
    public static boolean forceComplete(ServerPlayer player, ResourceLocation id) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        QuestDef def = QuestDefs.get(id);
        if (data == null || def == null) {
            return false;
        }
        if (data.getGuild().quest(id) == null) {
            data.getGuild().startQuest(id, def.objectives().size(), ProgressionData.get(player.server).day());
        }
        boolean ok = complete(player, data, def, true);
        GuildService.syncSelf(player, data);
        return ok;
    }

    private static boolean complete(ServerPlayer player, PlayerData data, QuestDef def, boolean admin) {
        // 渡す品（consume）を先に取る。足りなければ終えない
        for (QuestDef.Objective o : def.objectives()) {
            if (!admin && "collect".equals(o.type()) && o.consume() && countItem(player, o.target()) < o.count()) {
                tell(player, "message.amemaru.quest.not_done", def.name());
                return false;
            }
        }
        for (QuestDef.Objective o : def.objectives()) {
            if (!admin && "collect".equals(o.type()) && o.consume()) {
                removeItem(player, o.target(), o.count());
            }
        }
        int day = ProgressionData.get(player.server).day();
        data.getGuild().completeQuest(def.id(), day);
        if (def.daily()) {
            data.getGuild().countDailyDone();
        }
        data.getGuild().addCounter(GuildCounters.QUESTS, 1);
        // Phase 7 その1：コネクション量（デイリー＝物語・1日3回／NPCの個人的な依頼＝街／ストーリークエスト＝物語）
        switch (def.kind()) {
            case DAILY -> jp.amemaru.lostconnection.connection.ConnectionService.award(player,
                    jp.amemaru.lostconnection.connection.ConnectionActions.DAILY_QUEST);
            case PERSONAL -> jp.amemaru.lostconnection.connection.ConnectionService.award(player,
                    jp.amemaru.lostconnection.connection.ConnectionActions.NPC_REQUEST);
            case STORY -> jp.amemaru.lostconnection.connection.ConnectionService.award(player,
                    jp.amemaru.lostconnection.connection.ConnectionActions.STORY_QUEST);
            default -> {
            }
        }
        QuestDef.Rewards r = def.rewards();
        if (r.knots() > 0) {
            Wallet.give(player, data, r.knots(), "quest " + def.id());
        }
        if (r.exp() > 0) {
            LevelService.addExp(player, data, r.exp(), LevelService.ExpSource.GAMEPLAY);
        }
        for (QuestDef.ItemReward item : r.items()) {
            Item it = ForgeRegistries.ITEMS.getValue(item.item());
            if (it != null) {
                ItemStack stack = new ItemStack(it, item.count());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        for (ResourceLocation flag : r.flags()) {
            data.getStory().add(flag);
        }
        // 演出（本人の画面）
        CompoundTag toast = new CompoundTag();
        toast.putString("type", "quest");
        toast.putString("name", def.name());
        toast.putLong("knots", r.knots());
        toast.putLong("exp", r.exp());
        ModNetwork.sendTo(player, new GuildS2C("toast", toast));
        player.displayClientMessage(Component.translatable("message.amemaru.quest.completed", def.name()).withStyle(ChatFormatting.GOLD),
                false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        AmemaruMod.LOGGER.info("[amemaru] Quest {} completed by {}", def.id(), player.getGameProfile().getName());
        for (Map.Entry<ResourceLocation, Integer> bond : r.affinity().entrySet()) {
            BondService.add(player, bond.getKey(), bond.getValue(), "quest " + def.id());
        }
        if (r.title() != null) {
            TitleService.grant(player, r.title(), "quest " + def.id());
        }
        if (r.promote()) {
            JobService.promote(player, data, false);
        }
        TitleService.check(player);
        return true;
    }

    // ================================================================ 品

    private static int countItem(ServerPlayer player, @Nullable ResourceLocation id) {
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeItem(ServerPlayer player, @Nullable ResourceLocation id, int amount) {
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return;
        }
        int left = amount;
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (left <= 0) {
                    break;
                }
                if (stack.is(item)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }

    // ================================================================ 文

    public static MutableComponent objectiveText(QuestDef.Objective o) {
        if (!o.label().isEmpty()) {
            return switch (o.type()) {
                case "visit" -> Component.translatable("quest.amemaru.objective.visit", o.label());
                case "talk" -> Component.translatable("quest.amemaru.objective.talk_at", o.label(), npcName(o.target()));
                default -> Component.literal(o.label());
            };
        }
        return switch (o.type()) {
            case "kill" -> {
                EnemyDef enemy = EnemyDefs.get(o.target());
                yield Component.translatable("quest.amemaru.objective.kill", enemy == null ? String.valueOf(o.target()) : enemy.name());
            }
            case "kill_any" -> o.floor() > 0 ? Component.translatable("quest.amemaru.objective.kill_any_floor", o.floor())
                    : Component.translatable("quest.amemaru.objective.kill_any");
            case "boss" -> Component.translatable("quest.amemaru.objective.boss", o.floor());
            case "collect" -> {
                Item item = o.target() == null ? null : ForgeRegistries.ITEMS.getValue(o.target());
                Component name = item == null ? Component.literal(String.valueOf(o.target())) : new ItemStack(item).getHoverName();
                yield Component.translatable(o.consume() ? "quest.amemaru.objective.deliver" : "quest.amemaru.objective.collect", name);
            }
            case "talk" -> Component.translatable("quest.amemaru.objective.talk", npcName(o.target()));
            case "visit" -> Component.translatable("quest.amemaru.objective.visit", String.valueOf(o.pos()));
            case "chest" -> Component.translatable("quest.amemaru.objective.chest");
            case "record" -> Component.translatable("quest.amemaru.objective.record");
            case "pillar" -> Component.translatable("quest.amemaru.objective.pillar");
            default -> Component.literal(o.type());
        };
    }

    static String npcName(@Nullable ResourceLocation npc) {
        NpcDef def = npc == null ? null : NpcDefs.get(npc);
        return def == null ? String.valueOf(npc) : def.name();
    }

    /** 1つのクエストの見た目（掲示板・受けている一覧）。 */
    public static CompoundTag questTag(ServerPlayer player, PlayerData data, QuestDef def) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", def.id().toString());
        tag.putString("name", def.name());
        tag.putString("kind", def.kind().key());
        tag.putString("description", GuildService.fill(player, def.description()));
        tag.putString("giver", def.giver() == null ? "" : npcName(def.giver()));
        tag.putString("report", def.reportTo() == null ? "" : npcName(def.reportTo()));
        tag.putString("state", state(player, data, def).name().toLowerCase(java.util.Locale.ROOT));
        GuildData.QuestProgress progress = data.getGuild().quest(def.id());
        ListTag objectives = new ListTag();
        for (int i = 0; i < def.objectives().size(); i++) {
            QuestDef.Objective o = def.objectives().get(i);
            CompoundTag one = new CompoundTag();
            one.putString("text", Component.Serializer.toJson(objectiveText(o)));
            one.putInt("max", o.count());
            one.putInt("count", progress == null ? 0 : objectiveValue(player, def, i, progress));
            objectives.add(one);
        }
        tag.put("objectives", objectives);
        tag.putBoolean("done", progress != null && done(player, def, progress));
        tag.putBoolean("tracked", progress != null && progress.tracked());
        QuestDef.Rewards r = def.rewards();
        tag.putLong("knots", r.knots());
        tag.putLong("exp", r.exp());
        ListTag items = new ListTag();
        for (QuestDef.ItemReward item : r.items()) {
            Item it = ForgeRegistries.ITEMS.getValue(item.item());
            if (it != null) {
                CompoundTag one = new CompoundTag();
                one.putString("name", Component.Serializer.toJson(new ItemStack(it).getHoverName()));
                one.putInt("count", item.count());
                items.add(one);
            }
        }
        tag.put("items", items);
        ListTag bonds = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> bond : r.affinity().entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("npc", npcName(bond.getKey()));
            one.putInt("amount", bond.getValue());
            bonds.add(one);
        }
        tag.put("bonds", bonds);
        tag.putBoolean("promote", r.promote());
        if (r.title() != null) {
            TitleDef title = TitleService.def(player.server, r.title());
            if (title != null) {
                tag.putString("title", title.name());
            }
        }
        return tag;
    }

    /** 受けているクエスト（画面の右上の一覧と、掲示板の「受けている」）。 */
    public static ListTag activeTag(ServerPlayer player, PlayerData data) {
        ListTag list = new ListTag();
        for (ResourceLocation id : data.getGuild().quests().keySet()) {
            QuestDef def = QuestDefs.get(id);
            if (def != null) {
                list.add(questTag(player, data, def));
            }
        }
        return list;
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), false);
    }
}
