package jp.amemaru.lostconnection.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.core.data.DefinitionException;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 会話の選択肢・場面・ストーリーイベントの「処理」（Phase 0-B 6章「actions：フラグを立てる、親密度を上げる、アイテムを渡す、
 * クエストを始める、店を開く、CPを足す など」のうち Phase 5 の分）。
 *
 * <ul>
 *   <li>{@code amemaru:set_flags}：物語フラグを立てる・消す（{@code scope} が {@code player} なら本人だけ、{@code server} なら全体）</li>
 *   <li>{@code amemaru:roster}：冒険者名簿に名前を書く（オープニングの終わり。Phase 0-A 8章「目覚めたばかりのプレイヤーの名前を尋ね、名簿に書き込む」）</li>
 *   <li>{@code amemaru:open_job_screen}：職業の登録の画面を開く（ギルドの受付。Phase 0-A 7章）</li>
 *   <li>{@code amemaru:open_floor_select}：塔の門で行く階を選ぶ画面を開く（門番）</li>
 *   <li>{@code amemaru:guide}：はじめの手引きに知らせる（{@code event}）</li>
 *   <li>{@code amemaru:play_event}：ストーリーイベントの演出を流す</li>
 *   <li>{@code amemaru:give_item}：アイテムを渡す</li>
 *   <li>{@code amemaru:close}：会話を終える</li>
 *   <li>Phase 6 その3：{@code amemaru:open_guild}（ギルドの窓口。{@code tab}）・{@code amemaru:start_quest}・{@code amemaru:report_quest}
 *       （クエストを受ける・報告する。{@code quest}）・{@code amemaru:bond}（親密度を足す。{@code npc}・{@code amount}）・
 *       {@code amemaru:give_record_book}（記録帳を渡す）・{@code amemaru:decipher_records}（古代記録を解読する）・
 *       {@code amemaru:grant_title}（称号を与える。{@code title}）・{@code amemaru:open_titles}（称号の一覧）・
 *       {@code amemaru:open_record_book}（記録帳）・{@code amemaru:open_connections}（縁帳）・{@code amemaru:open_donation}（露店の寄付）</li>
 *   <li>Phase 7 その2：{@code amemaru:open_fishing}（ポッカ爺の釣り具屋の画面。{@code tab}：dex・sell・trade・tournament）・
 *       {@code amemaru:rent_rod}（竿を借りる）</li>
 *   <li>Phase 7 その3：{@code amemaru:open_casino}（カジノの画面。{@code tab}：roulette・highlow・fishrace・cashier・prizes）</li>
 *   <li>Phase 7 その4：{@code amemaru:open_restaurant}（ペトラ・ポムの料理屋の画面）</li>
 *   <li>11月25日（7日目）：{@code amemaru:greet_lilia}（リリアに「ただいま」と声をかける。{@link jp.amemaru.lostconnection.npc.LiliaMemoryService}）</li>
 * </ul>
 * 大結晶の「記憶の再生」で見返すときは、処理を行わない（見るだけ）。
 */
public record StoryAction(String type, JsonObject params) {
    private static final Set<String> TYPES = Set.of("amemaru:set_flags", "amemaru:roster", "amemaru:open_job_screen",
            "amemaru:open_floor_select", "amemaru:guide", "amemaru:play_event", "amemaru:give_item", "amemaru:close",
            "amemaru:open_guild", "amemaru:start_quest", "amemaru:report_quest", "amemaru:bond", "amemaru:give_record_book",
            "amemaru:decipher_records", "amemaru:grant_title", "amemaru:open_titles", "amemaru:open_record_book",
            "amemaru:open_connections", "amemaru:open_donation", "amemaru:open_fishing", "amemaru:rent_rod",
            "amemaru:open_casino", "amemaru:open_restaurant", "amemaru:greet_lilia", "amemaru:open_gacha");

    public static List<StoryAction> parseList(JsonArray array) {
        List<StoryAction> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject json = Json.asObject(array.get(i), "actions");
            String type = Json.parseId(Json.string(json, "type"), "actions.type").toString();
            if (!TYPES.contains(type)) {
                throw new DefinitionException("「actions」の「" + type + "」は知らない処理です（使えるのは " + String.join("・", TYPES) + "）");
            }
            if (type.equals("amemaru:set_flags")) {
                Json.idList(json, "add");
                Json.idList(json, "remove");
            }
            list.add(new StoryAction(type, json));
        }
        return List.copyOf(list);
    }

    public boolean closes() {
        return type.equals("amemaru:close");
    }

    /** 処理を行う（{@code npc} は話しているNPC。オープニングの役者のように、NPCがいないときは null）。 */
    public void run(ServerPlayer player, @Nullable NpcEntity npc) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        switch (type) {
            case "amemaru:set_flags" -> {
                boolean server = "server".equals(Json.optionalString(params, "scope", "player"));
                StoryState state = StoryState.get(player.server);
                for (ResourceLocation flag : Json.idList(params, "add")) {
                    if (server) {
                        state.add(flag);
                    } else if (data != null) {
                        data.getStory().add(flag);
                    }
                }
                for (ResourceLocation flag : Json.idList(params, "remove")) {
                    if (server) {
                        state.remove(flag);
                    } else if (data != null) {
                        data.getStory().remove(flag);
                    }
                }
            }
            case "amemaru:roster" -> OpeningService.onRoster(player);
            case "amemaru:open_job_screen" -> {
                // NPCの前（受付のリリア）か、本人のいる所（オープニングの大結晶のそば）で開く
                BlockPos at = npc != null ? npc.blockPosition() : player.blockPosition();
                JobService.openJobScreen(player, at, true);
            }
            case "amemaru:open_floor_select" -> TowerService.openFloorSelect(player, true);
            case "amemaru:guide" -> GuideService.event(player, Json.optionalString(params, "event", ""));
            case "amemaru:play_event" -> {
                ResourceLocation event = Json.optionalId(params, "event");
                if (event != null) {
                    StoryEventManager.play(player, event, false);
                }
            }
            case "amemaru:give_item" -> {
                ResourceLocation id = Json.optionalId(params, "item");
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, Json.optionalInt(params, "count", 1, 1, 64));
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }
            // ---- Phase 6 その3：ギルド・クエスト・親密度・古代記録・称号
            case "amemaru:open_guild" -> jp.amemaru.lostconnection.guild.GuildService.openGuild(player,
                    Json.optionalString(params, "tab", "quests"));
            case "amemaru:greet_lilia" -> jp.amemaru.lostconnection.npc.LiliaMemoryService.greet(player);
            case "amemaru:start_quest" -> {
                ResourceLocation quest = Json.optionalId(params, "quest");
                if (quest != null) {
                    jp.amemaru.lostconnection.guild.QuestService.accept(player, quest, npc == null ? null : npc.npcId());
                }
            }
            case "amemaru:report_quest" -> {
                ResourceLocation quest = Json.optionalId(params, "quest");
                jp.amemaru.lostconnection.guild.QuestService.report(player, quest, npc == null ? null : npc.npcId());
            }
            case "amemaru:bond" -> {
                ResourceLocation who = Json.optionalId(params, "npc");
                if (who == null && npc != null) {
                    who = npc.npcId();
                }
                if (who != null) {
                    jp.amemaru.lostconnection.guild.BondService.add(player, who, Json.optionalInt(params, "amount", 0, -1000, 1000),
                            "dialogue");
                }
            }
            case "amemaru:give_record_book" -> jp.amemaru.lostconnection.guild.RecordService.giveBook(player);
            case "amemaru:decipher_records" -> jp.amemaru.lostconnection.guild.RecordService.decipher(player);
            case "amemaru:grant_title" -> {
                ResourceLocation title = Json.optionalId(params, "title");
                if (title != null) {
                    jp.amemaru.lostconnection.guild.TitleService.grant(player, title, "dialogue");
                }
            }
            case "amemaru:open_titles" -> jp.amemaru.lostconnection.guild.GuildService.openTitles(player);
            case "amemaru:open_record_book" -> jp.amemaru.lostconnection.guild.RecordService.openBook(player);
            case "amemaru:open_connections" -> jp.amemaru.lostconnection.guild.GuildService.openConnections(player);
            case "amemaru:open_donation" -> jp.amemaru.lostconnection.guild.StallDonation.open(player);
            case "amemaru:open_fishing" -> jp.amemaru.lostconnection.fishing.FishingService.openShop(player,
                    Json.optionalString(params, "tab", "dex"), false);
            case "amemaru:rent_rod" -> jp.amemaru.lostconnection.fishing.FishingService.rentRod(player);
            case "amemaru:open_casino" -> jp.amemaru.lostconnection.casino.CasinoService.open(player,
                    Json.optionalString(params, "tab", "roulette"), false);
            case "amemaru:open_restaurant" -> jp.amemaru.lostconnection.town.RestaurantService.open(player, false);
            // 11月27日：祈願所の巫女（ガチャ）
            case "amemaru:open_gacha" -> jp.amemaru.lostconnection.gacha.GachaService.open(player,
                    Json.optionalString(params, "banner", ""), false);
            default -> {
                // close は会話の側で扱う
            }
        }
    }
}
