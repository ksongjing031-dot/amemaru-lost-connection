package jp.amemaru.lostconnection.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import jp.amemaru.lostconnection.core.data.Json;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ReplayListS2C;
import jp.amemaru.lostconnection.network.s2c.SceneS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ストーリーイベントを起こす（Phase 0-B 8章「物語の場面は StoryEventManager が起こし」、9章「ボスの初撃破から物語イベントまで」）。
 *
 * <ul>
 *   <li>演出は、見る人それぞれの画面で流す（{@link SceneS2C} で手順をまとめて送り、クライアントが自分の速さで流す）。
 *       同じ時刻にそろえないので、ラグで遅れた人も、後から入った人も、同じ演出を最初から見られる</li>
 *   <li>見終えた知らせ（{@code DONE}）が来たら、物語の処理（フラグ・会話・手引き）を行う。知らせが来ないとき（落ちた・通信が切れた）は、
 *       演出の長さに余裕を足した時間で打ち切る</li>
 *   <li>大結晶の「記憶の再生」：本人が見たか、サーバーで起きたイベントを、自分の画面だけで見直す（処理は行わない）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class StoryEventManager {
    private static final DefinitionLoader<StoryEventDef> LOADER = new DefinitionLoader<>("story_events", StoryEventDef::parse);
    /** 見終えた知らせを待つ時間の余裕（演出の長さに足す）。 */
    private static final int GRACE_TICKS = 20 * 30;
    /** 記憶の石に触れてから、再生を選べる時間。 */
    private static final int REPLAY_WINDOW_TICKS = 20 * 120;

    private record Playing(int scene, ResourceLocation event, boolean replay, long deadline) {
    }

    private static final Map<UUID, Playing> PLAYING = new HashMap<>();
    private static final Map<UUID, Long> REPLAY_WINDOW = new HashMap<>();
    private static int nextScene = 1;

    private StoryEventManager() {
    }

    public static DefinitionLoader<StoryEventDef> loader() {
        return LOADER;
    }

    @Nullable
    public static StoryEventDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Collection<StoryEventDef> all() {
        return LOADER.all().values();
    }

    // ---------------------------------------------------------------- 流す

    /** 演出を流している途中か（この間は、NPCに話しかけても会話を始めない）。 */
    public static boolean isPlaying(ServerPlayer player) {
        return PLAYING.containsKey(player.getUUID());
    }

    /** 流している演出の番号（流していなければ −1。自動テストで、見終えた知らせを送るときに使う）。 */
    public static int sceneOf(ServerPlayer player) {
        Playing playing = PLAYING.get(player.getUUID());
        return playing == null ? -1 : playing.scene();
    }

    /** 流している演出（流していなければ null。自動テスト用）。 */
    @Nullable
    public static ResourceLocation eventOf(ServerPlayer player) {
        Playing playing = PLAYING.get(player.getUUID());
        return playing == null ? null : playing.event();
    }

    /**
     * その人の画面で演出を流す。{@code replay} なら記憶の再生（物語の処理は行わない）。流せなければ false。
     */
    public static boolean play(ServerPlayer player, ResourceLocation eventId, boolean replay) {
        StoryEventDef def = get(eventId);
        if (def == null) {
            AmemaruMod.LOGGER.warn("[amemaru] Unknown story event {}", eventId);
            return false;
        }
        JsonArray steps = new JsonArray();
        for (StoryEventDef.Step step : def.steps()) {
            if (step.client()) {
                steps.add(resolve(player, step.json().deepCopy()));
            }
        }
        int scene = nextScene++;
        long now = player.server.overworld().getGameTime();
        PLAYING.put(player.getUUID(), new Playing(scene, eventId, replay, now + def.estimatedTicks() + GRACE_TICKS));
        ModNetwork.sendTo(player, new SceneS2C(scene, eventId, def.title(), steps.toString(), replay));
        if (!replay) {
            StoryState.get(player.server).markHappened(eventId);
        }
        return true;
    }

    /** 見終えた（クライアントからの知らせ、または待つ時間の打ち切り）。 */
    public static void onSceneDone(ServerPlayer player, int scene) {
        Playing playing = PLAYING.get(player.getUUID());
        if (playing == null || playing.scene() != scene) {
            return;
        }
        finish(player, playing);
    }

    private static void finish(ServerPlayer player, Playing playing) {
        PLAYING.remove(player.getUUID());
        StoryEventDef def = get(playing.event());
        if (def == null) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> data.getStory().markSeen(def.id()));
        for (StoryEventDef.Step step : def.steps()) {
            if (step.client()) {
                continue;
            }
            JsonObject json = step.json();
            switch (step.type()) {
                case "amemaru:set_flags" -> {
                    if (!playing.replay()) {
                        new StoryAction("amemaru:set_flags", json).run(player, null);
                    }
                }
                case "amemaru:guide" -> {
                    if (!playing.replay()) {
                        GuideService.event(player, Json.optionalString(json, "event", ""));
                    }
                }
                case "amemaru:open_dialogue" -> {
                    ResourceLocation npc = Json.optionalId(json, "npc");
                    if (npc != null) {
                        ResourceLocation dialogue = Json.optionalId(json, "dialogue");
                        if (playing.replay() && dialogue == null) {
                            dialogue = replayDialogue(player, json);
                        }
                        DialogueManager.open(player, npc, dialogue, playing.replay());
                    }
                }
                default -> {
                    // 画面の演出はクライアントが流した
                }
            }
        }
        OpeningService.onSceneFinished(player, def.id(), playing.replay());
    }

    /**
     * 記憶の再生で開く会話。手順の {@code replay_dialogues}（そのときに出た会話の候補）のうち、本人が最後まで読んだもの
     * （いちばん後に読んだもの）。読んだ記録がなければ1つ目。候補がなければ null（今の状態で選ぶ）。
     * <p>会話の多くは「オープニングがまだ」などの条件で選ばれるので、終わったあとに今の状態で選ぶと、ほかの会話になる
     * （「目覚め」を見返すと、リリアの会話がギルドでのあいさつになっていた。11月22日）。
     */
    @Nullable
    static ResourceLocation replayDialogue(ServerPlayer player, JsonObject step) {
        List<ResourceLocation> candidates = new ArrayList<>();
        for (JsonElement element : Json.optionalArray(step, "replay_dialogues")) {
            ResourceLocation id = element.isJsonPrimitive() ? ResourceLocation.tryParse(element.getAsString()) : null;
            if (id != null) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        ResourceLocation chosen = null;
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data != null) {
            for (ResourceLocation read : data.getStory().readDialogues()) {
                if (candidates.contains(read)) {
                    chosen = read;
                }
            }
        }
        return chosen != null ? chosen : candidates.get(0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PLAYING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) {
            return;
        }
        for (Map.Entry<UUID, Playing> entry : new ArrayList<>(PLAYING.entrySet())) {
            if (entry.getValue().deadline() > now) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                PLAYING.remove(entry.getKey());
            } else {
                AmemaruMod.LOGGER.info("[amemaru] Scene {} for {} timed out; continuing the story", entry.getValue().event(),
                        player.getGameProfile().getName());
                finish(player, entry.getValue());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYING.remove(event.getEntity().getUUID());
        REPLAY_WINDOW.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- きっかけ

    /**
     * きっかけが起きた（{@code type}：floor_first_kill・floor_enter など）。{@code players} はきっかけを作った人たち
     * （本人・パーティ）。合うイベントを、それぞれの画面で流す。
     */
    public static void trigger(MinecraftServer server, String type, int floor, List<ServerPlayer> players) {
        StoryState state = StoryState.get(server);
        for (StoryEventDef def : all()) {
            if (!def.triggerType().equals(type) || (def.triggerFloor() != 0 && def.triggerFloor() != floor)) {
                continue;
            }
            if (def.once() && state.happened(def.id())) {
                continue;
            }
            List<ServerPlayer> viewers = switch (def.scope()) {
                case "all" -> server.getPlayerList().getPlayers();
                default -> players;
            };
            for (ServerPlayer viewer : viewers) {
                if (def.conditions().test(viewer) && !isPlaying(viewer)) {
                    play(viewer, def.id(), false);
                }
            }
            state.markHappened(def.id());
        }
    }

    /**
     * 物語の場所に入った（Phase 8 その1。{@link jp.amemaru.lostconnection.tower.FloorEnvService} が1秒ごとに見る）。
     * {@code floor_zone} のイベントのうち、場所の ID が合うものを流す。初めて起きたときは、{@code announce} を全員に知らせる。
     * 流したら true。
     */
    public static boolean triggerZone(MinecraftServer server, int floor, String zone, List<ServerPlayer> players) {
        StoryState state = StoryState.get(server);
        boolean played = false;
        for (StoryEventDef def : all()) {
            if (!def.triggerType().equals("floor_zone") || !def.triggerZone().equals(zone)
                    || (def.triggerFloor() != 0 && def.triggerFloor() != floor)) {
                continue;
            }
            boolean first = !state.happened(def.id());
            if (def.once() && !first) {
                continue;
            }
            List<ServerPlayer> viewers = def.scope().equals("all") ? server.getPlayerList().getPlayers() : players;
            for (ServerPlayer viewer : viewers) {
                if (def.conditions().test(viewer) && !isPlaying(viewer)) {
                    played |= play(viewer, def.id(), false);
                }
            }
            state.markHappened(def.id());
            if (first && !def.announce().isEmpty()) {
                jp.amemaru.lostconnection.progression.ProgressionService.broadcast(server,
                        net.minecraft.network.chat.Component.literal(def.announce()),
                        jp.amemaru.lostconnection.network.s2c.AnnouncementS2C.Kind.STORY);
            }
        }
        return played;
    }

    /** その場所の {@code floor_zone} のイベントが、まだ起きる（一度きりで、もう起きた物は起きない）か。 */
    public static boolean zonePending(MinecraftServer server, int floor, String zone) {
        StoryState state = StoryState.get(server);
        for (StoryEventDef def : all()) {
            if (def.triggerType().equals("floor_zone") && def.triggerZone().equals(zone)
                    && (def.triggerFloor() == 0 || def.triggerFloor() == floor) && !(def.once() && state.happened(def.id()))) {
                return true;
            }
        }
        return false;
    }

    /** 管理者が起こす（{@code /amemaru admin story trigger}）。 */
    public static int adminTrigger(ResourceLocation id, Collection<ServerPlayer> players, boolean replay) {
        int count = 0;
        for (ServerPlayer player : players) {
            if (play(player, id, replay)) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- 記憶の再生

    /** 大結晶の記憶の石に触れた：見直せるイベントの一覧を送る。 */
    public static void openReplayList(ServerPlayer player) {
        REPLAY_WINDOW.put(player.getUUID(), player.server.overworld().getGameTime() + REPLAY_WINDOW_TICKS);
        List<ReplayListS2C.Entry> entries = new ArrayList<>();
        for (StoryEventDef def : all()) {
            if (canReplay(player, def)) {
                entries.add(new ReplayListS2C.Entry(def.id(), def.title()));
            }
        }
        ModNetwork.sendTo(player, new ReplayListS2C(List.copyOf(entries)));
    }

    private static boolean canReplay(ServerPlayer player, StoryEventDef def) {
        if (!def.replayable()) {
            return false;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        boolean seen = data != null && data.getStory().seen(def.id());
        return seen || StoryState.get(player.server).happened(def.id());
    }

    public static void onReplayRequest(ServerPlayer player, ResourceLocation id) {
        Long until = REPLAY_WINDOW.get(player.getUUID());
        if (until == null || until < player.server.overworld().getGameTime() || isPlaying(player)) {
            return;
        }
        StoryEventDef def = get(id);
        if (def == null || !canReplay(player, def)) {
            return;
        }
        play(player, id, true);
    }

    // ---------------------------------------------------------------- 位置を世界の座標に直す

    /** 手順の中の位置（{@code {"anchor": "wake", "offset": [x, y, z]}}）を、世界の座標の [x, y, z] に直す。文の {player} も直す。 */
    private static JsonObject resolve(ServerPlayer player, JsonObject step) {
        for (String key : List.of("pos", "to", "look_at", "from")) {
            JsonElement value = step.get(key);
            if (value != null && value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                Vec3 base = anchor(player, Json.optionalString(obj, "anchor", "player"));
                JsonArray offset = Json.optionalArray(obj, "offset");
                double ox = offset.size() == 3 ? offset.get(0).getAsDouble() : 0.0;
                double oy = offset.size() == 3 ? offset.get(1).getAsDouble() : 0.0;
                double oz = offset.size() == 3 ? offset.get(2).getAsDouble() : 0.0;
                JsonArray world = new JsonArray();
                world.add(base.x + ox);
                world.add(base.y + oy);
                world.add(base.z + oz);
                step.add(key, world);
            }
        }
        for (String key : List.of("text", "speaker", "title", "subtitle")) {
            JsonElement value = step.get(key);
            if (value != null && value.isJsonPrimitive()) {
                step.addProperty(key, DialogueManager.fill(player, value.getAsString()));
            }
        }
        return step;
    }

    /** 名前のついた場所：wake（目覚める所）・crystal（大結晶の真ん中の道の高さ）・floor（今いる階の原点）・player（本人）。 */
    public static Vec3 anchor(ServerPlayer player, String name) {
        switch (name) {
            case "wake" -> {
                return OpeningService.wakeSpot(player).pos();
            }
            case "crystal" -> {
                Optional<BlockPos> origin = CityCommands.builtOrigin(player.server.overworld());
                if (origin.isPresent()) {
                    BlockPos o = origin.get();
                    return new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
                }
                return OpeningService.wakeSpot(player).pos();
            }
            case "floor" -> {
                FloorDef floor = FloorDefs.floorAt(player.level(), player.position());
                if (floor != null) {
                    return floor.originVec();
                }
                return player.position();
            }
            default -> {
                return player.position();
            }
        }
    }
}
