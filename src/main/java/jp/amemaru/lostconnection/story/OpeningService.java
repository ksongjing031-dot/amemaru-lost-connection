package jp.amemaru.lostconnection.story;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 召喚から始まるオープニング（Phase 0-A 4章・8章・11章の1日目、14章「遅れて届いた人のオープニング」）。
 *
 * <p>オープニングは<strong>プレイヤー一人ひとりの画面で</strong>流す（11月21日のご指示：ラグなどで入るのが遅れても弊害がないように）。</p>
 * <ol>
 *   <li>初めてログインすると、大結晶のそば（目覚める所）へ移し、画面を暗くしたまま待つ（クライアントには「まだ始めない」と送る）</li>
 *   <li>クライアントが、ワールドを読み込み、まわりを描けるようになったら「準備ができた」（{@code READY}）を返す。
 *       ここで初めて演出を送る（読み込みが遅い人でも、見えないうちに演出が進まない）。知らせが来なくても45秒で始める</li>
 *   <li>演出（暗転から明けて、大結晶の「……み、つけ……た」、駆け寄るリリア）は、その人の画面だけで、その人の速さで流す。
 *       ほかの人の画面にも時刻にも関わらない</li>
 *   <li>見終えたら、リリアとの会話（名前を尋ね、名簿に書く）。最後まで話すとオープニングが終わる（{@link PlayerStory#OPENING_DONE}）</li>
 *   <li>途中で抜けたら、次にログインしたときに続きから（目覚めた後なら、リリアとの出会いから）やり直す</li>
 * </ol>
 * オープニングの間は、その人はダメージを受けない。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class OpeningService {
    /** 初めて目覚めるときの演出（暗転・大結晶の声・リリア）。 */
    public static final ResourceLocation OPENING = AmemaruMod.id("ch1/opening");
    /** 目覚めた後に抜けた人の続き（リリアとの出会いから）。 */
    public static final ResourceLocation OPENING_AGAIN = AmemaruMod.id("ch1/opening_again");
    /** オープニングのリリアとの会話。 */
    public static final ResourceLocation OPENING_DIALOGUE = AmemaruMod.id("lilia/opening");
    /** サーバーで最初の1人が目覚めた（Phase 0-A 12章の ch1_awaken）。 */
    public static final ResourceLocation FLAG_AWAKEN = AmemaruMod.id("ch1/awaken");
    /** 本人：目覚めた。 */
    public static final ResourceLocation FLAG_AWAKENED = AmemaruMod.id("ch1/awakened");
    /** 本人：名簿に名前を書いた。 */
    public static final ResourceLocation FLAG_REGISTERED = AmemaruMod.id("ch1/registered");
    /** READY が来なくても始めるまでの時間。 */
    private static final int READY_TIMEOUT_TICKS = 20 * 45;

    /** 準備を待っている人（→ 待つ期限の tick）。 */
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    /** 目覚める所（足もと）と向き。 */
    public record WakeSpot(ServerLevel level, Vec3 pos, float yaw) {
    }

    private OpeningService() {
    }

    /** ログインしたときに、オープニングを自動で始めるか（config。CIの写真のときは、写真の途中で始めるので自動では始めない）。 */
    public static boolean autoStart() {
        return ServerConfig.OPENING_ON_FIRST_LOGIN.get() && !Boolean.getBoolean("amemaru.ci.screenshots");
    }

    // ---------------------------------------------------------------- ログイン・準備・毎秒

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !autoStart()) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || data.getStory().opening() >= PlayerStory.OPENING_DONE) {
            return;
        }
        begin(player);
    }

    /** オープニングを始める準備をする（目覚める所へ移し、クライアントの準備を待つ）。管理者のコマンドからも呼ぶ。 */
    public static void begin(ServerPlayer player) {
        WakeSpot spot = wakeSpot(player);
        player.teleportTo(spot.level(), spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 0.0F);
        PENDING.put(player.getUUID(), player.server.overworld().getGameTime() + READY_TIMEOUT_TICKS);
        PlayerDataCapability.get(player).ifPresent(data -> data.markDirty(PlayerData.SYNC_STORY));
        AmemaruMod.LOGGER.info("[amemaru] Opening prepared for {} (waiting for the client)", player.getGameProfile().getName());
    }

    /** クライアントの準備ができた（{@code SceneStateC2S(READY)}）。 */
    public static void onClientReady(ServerPlayer player) {
        if (PENDING.remove(player.getUUID()) == null) {
            return;
        }
        start(player);
    }

    private static void start(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        data.markDirty(PlayerData.SYNC_STORY);
        ResourceLocation event = data.getStory().opening() >= PlayerStory.OPENING_AWAKENED ? OPENING_AGAIN : OPENING;
        if (!StoryEventManager.play(player, event, false)) {
            // 演出の定義がないとき：会話だけ始める
            data.getStory().setOpening(PlayerStory.OPENING_AWAKENED);
            DialogueManager.open(player, AmemaruMod.id("lilia"), OPENING_DIALOGUE, false);
        }
        AmemaruMod.LOGGER.info("[amemaru] Opening started for {} ({})", player.getGameProfile().getName(), event);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(PENDING.entrySet())) {
            if (entry.getValue() > now) {
                continue;
            }
            PENDING.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                AmemaruMod.LOGGER.info("[amemaru] Opening for {} started without the client's ready signal",
                        player.getGameProfile().getName());
                start(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    /** オープニングの間（準備待ち・演出中）は、ダメージを受けない。 */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inOpening(player)) {
            event.setCanceled(true);
        }
    }

    /** 準備を待っているか（クライアントは、この間は画面を暗くしておく）。 */
    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static boolean inOpening(ServerPlayer player) {
        if (isPending(player)) {
            return true;
        }
        if (!StoryEventManager.isPlaying(player)) {
            return false;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        return data != null && data.getStory().opening() < PlayerStory.OPENING_DONE;
    }

    // ---------------------------------------------------------------- 進み

    /** 演出を見終えた（{@link StoryEventManager} から）。 */
    static void onSceneFinished(ServerPlayer player, ResourceLocation event, boolean replay) {
        if (replay || (!event.equals(OPENING) && !event.equals(OPENING_AGAIN))) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        if (data.getStory().opening() < PlayerStory.OPENING_AWAKENED) {
            data.getStory().setOpening(PlayerStory.OPENING_AWAKENED);
        }
        data.getStory().add(FLAG_AWAKENED);
        StoryState state = StoryState.get(player.server);
        if (state.add(FLAG_AWAKEN)) {
            AmemaruMod.LOGGER.info("[amemaru] {} is the first to awaken on this server", player.getGameProfile().getName());
        }
    }

    /** 名簿に名前を書いた（リリアとの会話の {@code amemaru:roster}）：オープニングの終わり。 */
    static void onRoster(ServerPlayer player) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            data.getStory().setOpening(PlayerStory.OPENING_DONE);
            data.getStory().add(FLAG_AWAKENED);
            data.getStory().add(FLAG_REGISTERED);
            GuideService.refresh(player, data);
        });
    }

    /** 会話が終わった（オープニングの会話を途中で閉じたら、手引きでリリアの所を示す）。 */
    static void onDialogueClosed(ServerPlayer player, ResourceLocation dialogue, boolean finished) {
        if (!dialogue.equals(OPENING_DIALOGUE) || finished) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> GuideService.refresh(player, data));
    }

    // ---------------------------------------------------------------- 目覚める所

    /**
     * 目覚める所：管理者が決めた所 → 街を建てていれば大結晶のそば（中央広場の真ん中から南へ12、大結晶を向く）→ その人の今の所。
     */
    public static WakeSpot wakeSpot(ServerPlayer player) {
        MinecraftServer server = player.server;
        StoryState state = StoryState.get(server);
        ServerLevel overworld = server.overworld();
        if (state.wakePos() != null) {
            return new WakeSpot(overworld, state.wakePos(), state.wakeYaw());
        }
        Optional<BlockPos> origin = CityCommands.builtOrigin(overworld);
        if (origin.isPresent()) {
            BlockPos o = origin.get();
            return new WakeSpot(overworld, new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + 12.5), 180.0F);
        }
        ServerLevel level = player.serverLevel();
        return new WakeSpot(level, player.position(), player.getYRot());
    }

    // ---------------------------------------------------------------- 管理者

    /** オープニングを飛ばす（終わった扱いにする）。 */
    public static void skip(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        PlayerDataCapability.get(player).ifPresent(data -> {
            data.getStory().setOpening(PlayerStory.OPENING_DONE);
            data.getStory().add(FLAG_AWAKENED);
            data.getStory().add(FLAG_REGISTERED);
            GuideService.refresh(player, data);
        });
    }

    /** オープニングを見ていない状態に戻す。 */
    public static void reset(ServerPlayer player) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            data.getStory().setOpening(PlayerStory.OPENING_NONE);
            data.getStory().remove(FLAG_AWAKENED);
            data.getStory().remove(FLAG_REGISTERED);
            data.getStory().setGuide(0);
        });
    }
}
