package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.DialogueManager;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 冒険者ギルド（Phase 0-A 7章「職業選択・転職、クエスト掲示板、パーティ募集、称号の授与、冒険日誌」。Phase 6 その3）。
 *
 * <p>ギルドの窓口の画面（クエスト・パーティ募集・称号・冒険日誌）は、リリアとの会話か、ギルドの1階の「依頼の掲示板」（北の面は依頼、
 * 南の面はパーティ募集）を右クリックして開く（11月22日の決定「施設は右クリックで画面を開いて使う」）。クエストを受ける・報告する・
 * 募集を出すのは、ギルドの建物の中でだけできる。称号を選ぶのは、どこでも（ステータスの画面から）。</p>
 */
public final class GuildService {
    /** ギルドの建物の真ん中（中央広場の真ん中からの位置。建物は x 6〜42、z −100〜−59）。 */
    public static final Vec3 GUILD_CENTER = new Vec3(24.0, 3.0, -80.0);
    /** ギルドの中とみなす距離。 */
    public static final double GUILD_RADIUS = 28.0;
    /** 依頼の掲示板の壁（中央広場の真ん中からの位置。x 23〜29、y 3〜6、z −72〜−70）。北の面（z −72）が依頼、南の面（z −70）がパーティ募集。 */
    public static final int BOARD_X_MIN = 23;
    public static final int BOARD_X_MAX = 29;
    public static final int BOARD_Y_MIN = 3;
    public static final int BOARD_Y_MAX = 7;
    public static final int BOARD_Z_NORTH = -72;
    public static final int BOARD_Z_SOUTH = -70;
    /** 冒険日誌で送る行の数。 */
    public static final int CHRONICLE_SEND = 120;
    /** 縁帳で送る人の数。 */
    public static final int CONNECTIONS_SEND = 120;

    private GuildService() {
    }

    // ================================================================ 同期

    /** 本人の称号・古代記録・親密度・クエスト・縁帳の要約を送る。 */
    public static void syncSelf(ServerPlayer player, PlayerData data) {
        QuestService.ensureDaily(player, data);
        ModNetwork.sendTo(player, new GuildS2C("self", selfTag(player, data)));
    }

    public static CompoundTag selfTag(ServerPlayer player, PlayerData data) {
        GuildData guild = data.getGuild();
        CompoundTag tag = new CompoundTag();
        // 称号
        ListTag titles = new ListTag();
        for (Map.Entry<ResourceLocation, GuildData.OwnedTitle> entry : guild.titles().entrySet()) {
            TitleDef def = TitleService.def(player.server, entry.getKey());
            if (def == null) {
                continue;
            }
            CompoundTag one = new CompoundTag();
            one.putString("id", entry.getKey().toString());
            one.putInt("day", entry.getValue().day());
            // 隠し称号・ユニーク称号は、持っている人にだけ名前と説明を送る
            one.putString("name", def.name());
            one.putString("description", def.description());
            one.putInt("color", def.color());
            one.putString("kind", def.kind().key());
            one.putBoolean("rainbow", def.rainbow());
            titles.add(one);
        }
        tag.put("titles", titles);
        tag.putString("displayed", guild.displayedTitle() == null ? "" : guild.displayedTitle().toString());
        CompoundTag counters = new CompoundTag();
        for (String key : GuildCounters.ALL) {
            counters.putLong(key, TitleService.counterValue(player, data, key));
        }
        tag.put("counters", counters);
        // 古代記録
        tag.putBoolean("recordBook", guild.hasRecordBook());
        tag.putInt("recordsFound", guild.records().size());
        tag.putInt("recordsTotal", RecordDefs.all().size());
        int unread = 0;
        for (GuildData.RecordEntry entry : guild.records().values()) {
            if (!entry.deciphered()) {
                unread++;
            }
        }
        tag.putInt("recordsUnread", unread);
        // 親密度
        ListTag bonds = new ListTag();
        for (NpcDef def : bondNpcs()) {
            GuildData.Bond bond = guild.bond(def.id());
            CompoundTag one = new CompoundTag();
            one.putString("id", def.id().toString());
            one.putString("name", def.name());
            one.putString("title", def.title());
            int points = bond == null ? 0 : bond.points();
            one.putInt("points", points);
            one.putInt("stage", BondService.stage(data, def.id()));
            one.putInt("toNext", BondService.toNext(points));
            one.putBoolean("talked", bond != null && bond.lastTalkDay() == ProgressionData.get(player.server).day());
            bonds.add(one);
        }
        tag.put("bonds", bonds);
        // クエスト
        tag.put("quests", QuestService.activeTag(player, data));
        tag.putInt("dailyDone", guild.dailyDone());
        tag.putInt("dailyMax", QuestService.DAILY_MAX);
        tag.putInt("connections", guild.connections().size());
        tag.putInt("partners", guild.partners());
        return tag;
    }

    /** 親密度のあるNPCを、主要NPCの紹介の順（8章：リリア・ガルド・ドラン・セレナ・ノア・エルネスト）で。ほかはIDの順で後ろに。 */
    public static List<NpcDef> bondNpcs() {
        List<String> order = List.of("lilia", "gard", "doran", "serena", "noa", "ernesto");
        List<NpcDef> list = new ArrayList<>();
        for (NpcDef def : NpcDefs.all().values()) {
            if (def.bond() != null) {
                list.add(def);
            }
        }
        list.sort(Comparator.comparingInt((NpcDef def) -> {
            int i = order.indexOf(def.id().getPath());
            return i < 0 ? order.size() : i;
        }).thenComparing(def -> def.id().toString()));
        return list;
    }

    /** 称号の一覧（だれでも見られる分。隠し称号は名前を「？？？」にして、条件を送らない）。 */
    public static CompoundTag catalogTag(MinecraftServer server) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (TitleDef def : TitleService.all(server).values()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", def.id().toString());
            boolean hidden = def.kind() == TitleDef.Kind.HIDDEN;
            one.putString("name", hidden ? "？？？" : def.name());
            one.putString("description", hidden ? "" : def.description());
            one.putString("kind", def.kind().key());
            one.putString("field", def.field().key());
            one.putInt("color", def.color());
            one.putBoolean("rainbow", def.rainbow());
            one.putInt("tier", def.tier());
            one.putInt("tiers", def.tiers());
            one.putString("series", def.series());
            one.putInt("order", def.order());
            if (!hidden) {
                one.putString("condType", def.condition().type());
                one.putString("condKey", def.condition().key());
                one.putLong("condAmount", TitleService.required(def));
                if (def.condition().target() != null) {
                    one.putString("condTarget", def.condition().target().toString());
                }
            }
            if (def.kind() == TitleDef.Kind.LIMITED) {
                List<GuildState.Holder> holders = GuildState.get(server).holders(def.id());
                ListTag names = new ListTag();
                for (GuildState.Holder holder : holders) {
                    names.add(StringTag.valueOf(holder.name()));
                }
                one.put("holders", names);
            }
            if (def.effect() != null) {
                one.putString("effect", def.effect().type());
                one.putInt("effectAmount", def.effect().amount());
            }
            list.add(one);
        }
        tag.put("titles", list);
        return tag;
    }

    public static void sendCatalog(ServerPlayer player) {
        ModNetwork.sendTo(player, new GuildS2C("catalog", catalogTag(player.server)));
    }

    public static void sendCatalogToAll(MinecraftServer server) {
        ModNetwork.sendToAll(new GuildS2C("catalog", catalogTag(server)));
    }

    // ================================================================ 画面

    /** ギルドの中か（クエストを受ける・報告する・募集を出す）。 */
    public static boolean inGuild(ServerPlayer player) {
        Optional<BlockPos> origin = TowerService.cityOrigin(player.server);
        if (origin.isEmpty() || player.level() != player.server.overworld()) {
            return player.isCreative();
        }
        Vec3 center = Vec3.atLowerCornerOf(origin.get()).add(GUILD_CENTER);
        return player.position().distanceToSqr(center) <= GUILD_RADIUS * GUILD_RADIUS || player.isCreative();
    }

    /** 右クリックしたブロックが依頼の掲示板なら、その面のタブ（quests・party）。違えば null。 */
    @Nullable
    public static String boardTab(MinecraftServer server, BlockPos pos, Vec3 playerPos) {
        Optional<BlockPos> origin = TowerService.cityOrigin(server);
        if (origin.isEmpty()) {
            return null;
        }
        int x = pos.getX() - origin.get().getX();
        int y = pos.getY() - origin.get().getY();
        int z = pos.getZ() - origin.get().getZ();
        if (x < BOARD_X_MIN || x > BOARD_X_MAX || y < BOARD_Y_MIN || y > BOARD_Y_MAX || z < BOARD_Z_NORTH || z > BOARD_Z_SOUTH) {
            return null;
        }
        double side = playerPos.z - (origin.get().getZ() + (BOARD_Z_NORTH + BOARD_Z_SOUTH) / 2.0 + 0.5);
        return side < 0 ? "quests" : "party";
    }

    /** ギルドの窓口の画面を開く（{@code tab}：quests・party・titles・chronicle・bonds）。 */
    public static void openGuild(ServerPlayer player, String tab) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        QuestService.ensureDaily(player, data);
        CompoundTag tag = new CompoundTag();
        tag.putString("screen", "guild");
        tag.putString("tab", tab);
        tag.put("board", boardTag(player, data));
        tag.put("party", PartyBoard.tag(player));
        tag.put("chronicle", chronicleTag(player.server));
        tag.putBoolean("inGuild", inGuild(player));
        syncSelf(player, data);
        ModNetwork.sendTo(player, new GuildS2C("screen", tag));
    }

    /** 掲示板のクエスト（今日のデイリー・受けられるもの・受けているもの・鍵のかかったもの）。 */
    public static ListTag boardTag(ServerPlayer player, PlayerData data) {
        ListTag list = new ListTag();
        for (QuestDef def : QuestDefs.sorted()) {
            if (!def.board()) {
                continue;
            }
            QuestService.State state = QuestService.state(player, data, def);
            if (state == QuestService.State.NOT_TODAY) {
                continue;
            }
            // 鍵のかかったクエストは、物語の先の分を見せないよう、上位・昇格のときだけ「まだ受けられない」で出す
            if (state == QuestService.State.LOCKED && def.kind() != QuestDef.Kind.UPPER && def.kind() != QuestDef.Kind.PROMOTION) {
                continue;
            }
            list.add(QuestService.questTag(player, data, def));
        }
        return list;
    }

    /** 冒険日誌（新しい順）。 */
    public static ListTag chronicleTag(MinecraftServer server) {
        ListTag list = new ListTag();
        List<GuildState.ChronicleEntry> entries = GuildState.get(server).chronicle();
        for (int i = entries.size() - 1; i >= 0 && list.size() < CHRONICLE_SEND; i--) {
            GuildState.ChronicleEntry entry = entries.get(i);
            CompoundTag one = new CompoundTag();
            one.putInt("day", entry.day());
            one.putLong("at", entry.epochMillis());
            one.putString("key", entry.key());
            ListTag args = new ListTag();
            for (String arg : entry.args()) {
                args.add(StringTag.valueOf(arg));
            }
            one.put("args", args);
            list.add(one);
        }
        return list;
    }

    /** 称号の一覧の画面。 */
    public static void openTitles(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        sendCatalog(player);
        syncSelf(player, data);
        CompoundTag tag = new CompoundTag();
        tag.putString("screen", "titles");
        ModNetwork.sendTo(player, new GuildS2C("screen", tag));
    }

    /** 縁帳の画面（Phase 0-A 15章【提案】：組んだ人・取引した人・助けてくれた人）。 */
    public static void openConnections(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        List<Map.Entry<UUID, GuildData.Connection>> list = new ArrayList<>(data.getGuild().connections().entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<UUID, GuildData.Connection> e) -> e.getValue().lastDay()).reversed());
        ListTag people = new ListTag();
        for (int i = 0; i < Math.min(CONNECTIONS_SEND, list.size()); i++) {
            GuildData.Connection c = list.get(i).getValue();
            CompoundTag one = new CompoundTag();
            one.putString("name", c.name() == null ? "?" : c.name());
            one.putInt("party", c.party());
            one.putInt("trades", c.trades());
            one.putInt("revived", c.revived());
            one.putInt("revivedBy", c.revivedBy());
            one.putInt("commissions", c.commissions());
            one.putInt("duels", c.duels());
            one.putInt("first", c.firstDay());
            one.putInt("last", c.lastDay());
            one.putBoolean("online", player.server.getPlayerList().getPlayer(list.get(i).getKey()) != null);
            // Phase 7 その1：結び札（今日この人へ贈ったか）
            one.putUUID("id", list.get(i).getKey());
            one.putBoolean("thanked", data.getConnection().targetToday(
                    jp.amemaru.lostconnection.connection.ConnectionActions.THANKS_SEND, list.get(i).getKey()) > 0);
            people.add(one);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("screen", "connections");
        tag.put("people", people);
        tag.putInt("total", list.size());
        data.getConnection().rollover(jp.amemaru.lostconnection.connection.ConnectionService.today());
        tag.putInt("thanksLeft", Math.max(0, jp.amemaru.lostconnection.connection.ConnectionService.thanksDailyLimit()
                - data.getConnection().actionsToday(jp.amemaru.lostconnection.connection.ConnectionActions.THANKS_SEND)));
        ModNetwork.sendTo(player, new GuildS2C("screen", tag));
    }

    // ================================================================ 操作

    /** クライアントからの操作（{@link jp.amemaru.lostconnection.network.c2s.GuildActionC2S}）。 */
    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        switch (action) {
            case "title_display" -> {
                String id = args.getString("id");
                TitleService.setDisplayed(player, id.isEmpty() ? null : ResourceLocation.tryParse(id));
            }
            case "titles_open" -> openTitles(player);
            case "record_book" -> RecordService.openBook(player);
            case "connections_open" -> openConnections(player);
            case "guild_refresh" -> {
                if (inGuild(player)) {
                    openGuild(player, args.getString("tab"));
                }
            }
            case "quest_accept", "quest_abandon", "quest_report" -> {
                ResourceLocation id = ResourceLocation.tryParse(args.getString("id"));
                if (id == null) {
                    return;
                }
                if ("quest_abandon".equals(action)) {
                    QuestService.abandon(player, id);
                } else if (!inGuild(player)) {
                    player.displayClientMessage(Component.translatable("message.amemaru.guild.not_here").withStyle(ChatFormatting.RED), true);
                    return;
                } else if ("quest_accept".equals(action)) {
                    QuestService.accept(player, id, null);
                } else {
                    QuestService.report(player, id, null);
                }
                openGuild(player, "quests");
            }
            case "quest_track" -> {
                ResourceLocation id = ResourceLocation.tryParse(args.getString("id"));
                if (id != null) {
                    data.getGuild().setQuestTracked(id, args.getBoolean("on"));
                    syncSelf(player, data);
                }
            }
            case "party_post", "party_cancel", "party_apply", "party_answer" -> {
                if (!inGuild(player)) {
                    player.displayClientMessage(Component.translatable("message.amemaru.guild.not_here").withStyle(ChatFormatting.RED), true);
                    return;
                }
                switch (action) {
                    case "party_post" -> PartyBoard.post(player, args.getString("text"), args.getInt("floor"), args.getInt("minLevel"));
                    case "party_cancel" -> PartyBoard.cancel(player);
                    case "party_apply" -> {
                        if (args.hasUUID("owner")) {
                            PartyBoard.apply(player, args.getUUID("owner"));
                        }
                    }
                    default -> {
                        if (args.hasUUID("applicant")) {
                            PartyBoard.answer(player, args.getUUID("applicant"), args.getBoolean("accept"));
                        }
                    }
                }
                openGuild(player, "party");
            }
            case "donate" -> {
                // 寄付できたときは donate が画面を作り直す。できなかったときも、今の額で作り直す
                if (!StallDonation.donate(player, args.getLong("amount"))) {
                    StallDonation.open(player);
                }
            }
            case "donation_open" -> StallDonation.open(player);
            default -> {
            }
        }
    }

    /** 文の中の {@code {player}} を、本人の名前に置き換える。 */
    public static String fill(ServerPlayer player, String text) {
        return DialogueManager.fill(player, text);
    }
}
