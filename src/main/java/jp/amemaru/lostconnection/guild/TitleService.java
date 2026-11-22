package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 称号の仕組み（Phase 0-A 22章、Phase 6 その3）。
 *
 * <ul>
 *   <li>持っている称号から一つを選んで表示する（表示しないこともできる）。表示する所は、頭の上の名前の近く・チャットの名前の前・Tabの一覧
 *       【確定】。名前の前に「【称号】」を足す（{@link GuildEvents}）</li>
 *   <li>手に入れたら本人の画面に演出。限定とユニークはサーバー全体に告知【確定】</li>
 *   <li>誰がどの称号を持ち、いつ取ったかを記録する【確定】（{@link GuildState#logTitle}）</li>
 *   <li>強さへの影響は持たせない。段階の最上位だけ、暮らしの便利さを少しだけ上げる（22章【提案】。{@link #effectAmount}）</li>
 * </ul>
 */
public final class TitleService {
    /** 名前の前の称号の長さの上限（頭の上とチャットが長くなりすぎないように）。 */
    public static final int PREFIX_MAX = 16;

    private TitleService() {
    }

    // ================================================================ 定義

    /** 称号の定義（データパックと、管理者が作ったユニーク称号）。 */
    public static Map<ResourceLocation, TitleDef> all(MinecraftServer server) {
        Map<ResourceLocation, TitleDef> map = new LinkedHashMap<>();
        for (TitleDef def : TitleDefs.sorted()) {
            map.put(def.id(), def);
        }
        for (GuildState.CustomTitle custom : GuildState.get(server).customs().values()) {
            map.put(custom.id(), customDef(custom));
        }
        return map;
    }

    @Nullable
    public static TitleDef def(MinecraftServer server, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        TitleDef def = TitleDefs.get(id);
        if (def != null) {
            return def;
        }
        GuildState.CustomTitle custom = GuildState.get(server).custom(id);
        return custom == null ? null : customDef(custom);
    }

    static TitleDef customDef(GuildState.CustomTitle custom) {
        boolean rainbow = custom.color() < 0;
        return new TitleDef(custom.id(), custom.name(), custom.description(), TitleDef.Kind.UNIQUE, TitleDef.Field.STORY, "", 1, 1,
                new TitleDef.Condition("admin", "", null, 0), "", null, 0, rainbow ? TitleDef.Kind.UNIQUE.color() : custom.color(),
                rainbow);
    }

    // ================================================================ 見た目

    /** 「【名前】」を、種類の色で。 */
    public static MutableComponent styled(TitleDef def) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(def.color()));
        if (def.kind() == TitleDef.Kind.LIMITED || def.kind() == TitleDef.Kind.UNIQUE) {
            style = style.withBold(true);
        }
        return Component.literal("【" + clip(def.name()) + "】").withStyle(style);
    }

    private static String clip(String name) {
        return name.length() <= PREFIX_MAX ? name : name.substring(0, PREFIX_MAX);
    }

    /** 名前の前に付ける称号（表示していなければ null）。 */
    @Nullable
    public static MutableComponent prefix(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return null;
        }
        TitleDef def = def(player.server, data.getGuild().displayedTitle());
        return def == null ? null : styled(def);
    }

    // ================================================================ 与える・取り消す

    /**
     * 称号を与える。初めてなら true。演出・告知・記録をする。
     *
     * @param source 理由（記録のため。例：{@code condition}・{@code admin}・{@code first}）
     */
    public static boolean grant(ServerPlayer player, ResourceLocation id, String source) {
        return grant(player, id, source, true);
    }

    private static boolean grant(ServerPlayer player, ResourceLocation id, String source, boolean announce) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        TitleDef def = def(player.server, id);
        if (data == null || def == null) {
            return false;
        }
        int day = ProgressionData.get(player.server).day();
        long now = System.currentTimeMillis();
        if (!data.getGuild().addTitle(id, day, now)) {
            return false;
        }
        GuildState.get(player.server).logTitle(player.getUUID(), player.getGameProfile().getName(), id, day, now);
        AmemaruMod.LOGGER.info("[amemaru] Title {} -> {} ({})", id, player.getGameProfile().getName(), source);
        // 本人の画面に演出
        CompoundTag toast = new CompoundTag();
        toast.putString("type", "title");
        toast.putString("name", def.name());
        toast.putInt("color", def.color());
        toast.putString("kind", def.kind().key());
        toast.putBoolean("rainbow", def.rainbow());
        toast.putString("description", def.description());
        ModNetwork.sendTo(player, new GuildS2C("toast", toast));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F,
                def.kind() == TitleDef.Kind.LIMITED || def.kind() == TitleDef.Kind.UNIQUE ? 1.0F : 1.25F);
        player.displayClientMessage(Component.translatable("message.amemaru.title.got", styled(def)).withStyle(ChatFormatting.GOLD),
                false);
        // 限定とユニークはサーバー全体に告知【確定】
        if (announce && (def.kind() == TitleDef.Kind.LIMITED || def.kind() == TitleDef.Kind.UNIQUE)) {
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.title.announce",
                    Component.literal(player.getGameProfile().getName()), styled(def)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        GuildService.syncSelf(player, data);
        return true;
    }

    public static boolean revoke(ServerPlayer player, ResourceLocation id) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        boolean shown = id.equals(data.getGuild().displayedTitle());
        if (!data.getGuild().removeTitle(id)) {
            return false;
        }
        if (shown) {
            refreshShown(player);
        }
        GuildService.syncSelf(player, data);
        return true;
    }

    /** 表示する称号を決める（null で表示しない）。 */
    public static boolean setDisplayed(ServerPlayer player, @Nullable ResourceLocation id) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        if (id != null && def(player.server, id) == null) {
            return false;
        }
        if (!data.getGuild().setDisplayedTitle(id)) {
            return false;
        }
        refreshShown(player);
        GuildService.syncSelf(player, data);
        return true;
    }

    /** 頭の上・チャット・Tabの一覧の名前を作り直し、ほかの人にも知らせる。 */
    public static void refreshShown(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
        ModNetwork.sendToAll(new GuildS2C("shown", shownTag(List.of(player))));
    }

    /** 表示している称号（ほかの人の頭の上に出すため）。 */
    public static CompoundTag shownTag(Collection<ServerPlayer> players) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ServerPlayer player : players) {
            CompoundTag one = new CompoundTag();
            one.putUUID("uuid", player.getUUID());
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            // 設定で称号を出さないサーバーでは、名前を送らない（頭の上に出さない）
            TitleDef def = data == null || !jp.amemaru.lostconnection.core.config.ServerConfig.GUILD_SHOW_TITLES.get() ? null
                    : def(player.server, data.getGuild().displayedTitle());
            if (def != null) {
                one.putString("name", clip(def.name()));
                one.putInt("color", def.color());
                one.putString("kind", def.kind().key());
                one.putBoolean("rainbow", def.rainbow());
            }
            list.add(one);
        }
        tag.put("players", list);
        return tag;
    }

    // ================================================================ 条件

    /** 称号の条件に使う数（もとの記録から数えるものも含む）。 */
    public static long counterValue(ServerPlayer player, PlayerData data, String key) {
        return switch (key) {
            case GuildCounters.KILLS -> data.getAdventure().totalKills();
            case GuildCounters.CRAFTED -> data.getAdventure().crafted();
            case GuildCounters.REVIVES -> data.getReviveCount();
            case GuildCounters.PARTNERS -> data.getGuild().partners();
            case GuildCounters.RECORDS_PERCENT -> RecordService.percent(data);
            // Phase 7 その2：魚図鑑の埋まり具合（釣り見習い・釣り名人・湖を知る者）
            case GuildCounters.FISH_PERCENT -> jp.amemaru.lostconnection.fishing.FishingService.percent(data);
            default -> data.getGuild().counter(key);
        };
    }

    /**
     * 条件の数。「組んだことのある人」は25人を基準に、参加人数（設定の {@code participants}）で変える
     * （Phase 0-A 22章「5／12／20人（25人基準）」。自分のほかの人数より多くはしない）。
     */
    public static long required(TitleDef def) {
        TitleDef.Condition c = def.condition();
        if ("counter".equals(c.type()) && GuildCounters.PARTNERS.equals(c.key())) {
            int participants = jp.amemaru.lostconnection.core.config.ServerConfig.value(jp.amemaru.lostconnection.core.config.ServerConfig.GUILD_PARTICIPANTS);
            long scaled = Math.max(1L, (long) Math.ceil(c.amount() * participants / 25.0));
            return Math.min(Math.max(1L, participants - 1L), scaled);
        }
        return c.amount();
    }

    /** 数・親密度・物語フラグ・稼ぎの条件を確かめ、満たした称号を与える。 */
    public static void check(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        StoryState story = StoryState.get(player.server);
        for (TitleDef def : TitleDefs.sorted()) {
            if (data.getGuild().hasTitle(def.id())) {
                continue;
            }
            TitleDef.Condition c = def.condition();
            boolean met = switch (c.type()) {
                case "counter" -> counterValue(player, data, c.key()) >= required(def);
                case "affinity" -> c.target() != null && BondService.stage(data, c.target()) >= c.amount();
                case "flag" -> c.target() != null && (story.has(c.target()) || data.getStory().has(c.target()));
                case "zero_earn" -> data.getGuild().earnedSinceZero() >= c.amount();
                default -> false;
            };
            if (met) {
                grant(player, def.id(), "condition");
            }
        }
    }

    /** できごと（{@code event} の条件）。 */
    public static void onEvent(ServerPlayer player, String event) {
        for (TitleDef def : TitleDefs.sorted()) {
            if ("event".equals(def.condition().type()) && event.equals(def.condition().key())) {
                grant(player, def.id(), "event:" + event);
            }
        }
    }

    /**
     * サーバーで最初のできごと（限定称号）。まだ誰も取っていなければ、{@code players} 全員に与える（1パーティ・売り手と買い手など）。
     * 与えたら true。
     */
    public static boolean onFirst(MinecraftServer server, String event, List<ServerPlayer> players) {
        List<UUID> uuids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : players) {
            uuids.add(player.getUUID());
            names.add(player.getGameProfile().getName());
        }
        return onFirst(server, event, uuids, names);
    }

    /** {@link #onFirst} の、いない人も含められる形（いない人には、次に入ったときに渡す）。 */
    public static boolean onFirst(MinecraftServer server, String event, List<UUID> uuids, List<String> names) {
        if (uuids.isEmpty()) {
            return false;
        }
        boolean any = false;
        GuildState state = GuildState.get(server);
        int day = ProgressionData.get(server).day();
        for (TitleDef def : TitleDefs.sorted()) {
            if (!"first".equals(def.condition().type()) || !event.equals(def.condition().key()) || state.limitedTaken(def.id())) {
                continue;
            }
            List<GuildState.Holder> holders = new ArrayList<>();
            for (int i = 0; i < uuids.size(); i++) {
                holders.add(new GuildState.Holder(uuids.get(i), i < names.size() ? names.get(i) : "?", day));
            }
            state.setHolders(def.id(), holders);
            for (UUID uuid : uuids) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    grant(player, def.id(), "first:" + event, false);
                } else {
                    CompoundTag pending = new CompoundTag();
                    pending.putString("type", "title");
                    pending.putString("id", def.id().toString());
                    state.addPending(uuid, pending);
                }
            }
            String joined = String.join("・", names);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.title.announce",
                    Component.literal(joined), styled(def)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            state.chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.limited_title", List.of(joined, def.name()));
            GuildService.sendCatalogToAll(server);
            any = true;
        }
        return any;
    }

    /**
     * ボスを倒した（{@code boss}・{@code boss_no_down}・{@code boss_guard} の条件）。
     *
     * @param winners     倒したときにボス戦にいた人
     * @param boss        ボスの敵のID
     * @param chapterBoss 章ボスか
     * @param downed      ボス戦の間に一度でもダウンした人
     * @param topGuard    ボス戦で一番多く攻撃を受け止めた人（いなければ null）
     */
    public static void onBossKilled(List<ServerPlayer> winners, ResourceLocation boss, boolean chapterBoss, Set<UUID> downed,
                                    @Nullable UUID topGuard) {
        for (TitleDef def : TitleDefs.sorted()) {
            TitleDef.Condition c = def.condition();
            boolean bossMatches = c.target() == null || c.target().equals(boss);
            boolean needsChapter = c.amount() > 0;
            if (!bossMatches || (needsChapter && !chapterBoss)) {
                continue;
            }
            for (ServerPlayer player : winners) {
                switch (c.type()) {
                    case "boss" -> grant(player, def.id(), "boss:" + boss);
                    case "boss_no_down" -> {
                        if (!downed.contains(player.getUUID())) {
                            grant(player, def.id(), "boss_no_down:" + boss);
                        }
                    }
                    case "boss_guard" -> {
                        if (player.getUUID().equals(topGuard)) {
                            grant(player, def.id(), "boss_guard:" + boss);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    // ================================================================ 効果

    /** 持っている称号の効果の合計（例：{@code listing_bonus} はフリマの出品の枠を増やす）。 */
    public static int effectAmount(ServerPlayer player, String type) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        int total = 0;
        for (ResourceLocation id : data.getGuild().titles().keySet()) {
            TitleDef def = TitleDefs.get(id);
            if (def != null && def.effect() != null && def.effect().type().equals(type)) {
                total += Math.max(1, def.effect().amount());
            }
        }
        return total;
    }

    public static boolean hasEffect(ServerPlayer player, String type) {
        return effectAmount(player, type) > 0;
    }

    /** 前のPhaseで預かっていた称号（完璧なアクセサリーなど）を、本物の称号にする。 */
    public static void claimPending(ServerPlayer player, PlayerData data) {
        for (String pending : data.getAdventure().pendingTitles()) {
            ResourceLocation id = ResourceLocation.tryParse(pending);
            if (id != null && TitleDefs.get(id) != null && !data.getGuild().hasTitle(id)) {
                grant(player, id, "pending");
            }
        }
    }

    /** 称号の数（一覧の表示用）。 */
    public static int ownedCount(PlayerData data) {
        return data.getGuild().titles().size();
    }

    /** 管理者：その場でユニーク称号を作る（Phase 0-A 22章【確定】）。 */
    public static GuildState.CustomTitle createUnique(MinecraftServer server, String name, String description, int color, boolean single,
                                                      String createdBy) {
        return GuildState.get(server).createCustom(name, description, color, single, createdBy, ProgressionData.get(server).day());
    }

    /** 1人限定のユニーク称号を、もう誰かが持っているか。 */
    public static boolean uniqueTaken(MinecraftServer server, ResourceLocation id) {
        GuildState.CustomTitle custom = GuildState.get(server).custom(id);
        return custom != null && custom.single() && GuildState.get(server).ownersOf(id) > 0;
    }

    /** 名前に使えない文字を除く（色の記号など）。 */
    public static String sanitize(String text, int max) {
        String cleaned = text.replace("§", "").replace("\n", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
