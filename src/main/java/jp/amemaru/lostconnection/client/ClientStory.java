package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.network.s2c.SyncStoryS2C;
import jp.amemaru.lostconnection.network.s2c.WorldStateS2C;
import jp.amemaru.lostconnection.tower.FloorSealBlock;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * クライアントの物語と進行の様子（Phase 5）：進行日と開いた階・予告のカウントダウン、本人のオープニングの進みと起動した石柱、
 * はじめの手引き、画面に出す文（章題・字幕・古代文字・手がかり・知らせ）。
 */
public final class ClientStory {
    private static WorldStateS2C world = new WorldStateS2C(1, 1, 0L, 0, List.of());
    /** 予告の残りを、受け取った時刻から数える。 */
    private static long worldReceived = Util.getMillis();
    private static SyncStoryS2C story = new SyncStoryS2C(2, false, Map.of(), Map.of(), -1, 0, "", null, null);
    private static boolean storyReceived;

    /** 画面に出す文（古いものから順に）。 */
    public record ShownText(StoryTextS2C.Kind kind, String speaker, String text, long start, long durationMillis) {
        public float age() {
            return (Util.getMillis() - start) / 1000.0F;
        }

        public boolean expired() {
            return Util.getMillis() - start > durationMillis;
        }
    }

    /**
     * 知らせ（帯）。{@code start} は初めて画面に出た時刻（まだ出ていなければ −1）。演出の間はHUDを隠すので、演出の間に届いた知らせ
     * （ボスを倒した人の、初撃破の知らせなど）は、演出が終わって画面に出たときから数える。
     */
    public record ShownAnnouncement(AnnouncementS2C.Kind kind, String text, long start) {
    }

    /** 知らせを出しておく長さ */
    public static final long ANNOUNCE_MILLIS = 7000L;

    private static final Deque<ShownText> TEXTS = new ArrayDeque<>();
    private static final Deque<ShownAnnouncement> ANNOUNCEMENTS = new ArrayDeque<>();

    static {
        // 封印を通れるか（クライアントでも、サーバーと同じに決める）
        FloorSealBlock.clientPass = (player, floor) -> isLocal(player) && pillarsComplete(floor);
    }

    private ClientStory() {
    }

    /** クライアントの準備のときに呼ぶ（封印の判定を先に入れておく）。 */
    public static void init() {
        // 静的な初期化だけ
    }

    private static boolean isLocal(Player player) {
        return player == Minecraft.getInstance().player;
    }

    // ---------------------------------------------------------------- 受け取る

    public static void apply(WorldStateS2C message) {
        world = message;
        worldReceived = Util.getMillis();
    }

    public static void apply(SyncStoryS2C message) {
        story = message;
        storyReceived = true;
    }

    public static void show(StoryTextS2C message) {
        long duration = Math.max(1000L, message.ticks() * 50L);
        if (message.kind() == StoryTextS2C.Kind.ANCIENT) {
            duration += 1500L;
        }
        // 同じ種類の古い文は消す（上書き）
        TEXTS.removeIf(t -> t.kind() == message.kind());
        long now = Util.getMillis();
        long start = now;
        // 11月28日：ボスの名前などの題（真ん中の大きな字）が出ている間は、左上の手がかりを出さずに待たせ、題が消えてから出す
        // （CI の写真で、9階のボスの名前と、戦いのはじめの手がかりが重なって読めなかった。どのボスも同じ）
        if (message.kind() == StoryTextS2C.Kind.TIP) {
            for (ShownText shown : TEXTS) {
                if (shown.kind() == StoryTextS2C.Kind.TITLE && !shown.expired()) {
                    start = Math.max(start, shown.start() + shown.durationMillis());
                }
            }
        } else if (message.kind() == StoryTextS2C.Kind.TITLE) {
            // 先に出ていた手がかりは、題のあとで出しなおす
            long after = now + duration;
            List<ShownText> waiting = new ArrayList<>();
            TEXTS.removeIf(t -> {
                if (t.kind() == StoryTextS2C.Kind.TIP && !t.expired()) {
                    waiting.add(new ShownText(t.kind(), t.speaker(), t.text(), after, t.durationMillis()));
                    return true;
                }
                return false;
            });
            TEXTS.addAll(waiting);
        }
        TEXTS.add(new ShownText(message.kind(), message.speaker(), message.text(), start, duration));
    }

    /** 手がかりを {@code millis} だけ後ろへずらす（ボスのはじまりの名前が出ている間、手がかりの時計を進めない）。 */
    public static void delayTips(long millis) {
        if (TEXTS.stream().noneMatch(t -> t.kind() == StoryTextS2C.Kind.TIP)) {
            return;
        }
        List<ShownText> list = new ArrayList<>(TEXTS);
        TEXTS.clear();
        for (ShownText t : list) {
            TEXTS.add(t.kind() == StoryTextS2C.Kind.TIP ? new ShownText(t.kind(), t.speaker(), t.text(), t.start() + millis, t.durationMillis())
                    : t);
        }
    }

    public static void showText(StoryTextS2C.Kind kind, String speaker, String text, int ticks) {
        show(new StoryTextS2C(kind, speaker, text, ticks));
    }

    public static void announce(AnnouncementS2C message) {
        ANNOUNCEMENTS.add(new ShownAnnouncement(message.kind(), message.text(), -1L));
        while (ANNOUNCEMENTS.size() > 3) {
            ANNOUNCEMENTS.pollFirst();
        }
    }

    // ---------------------------------------------------------------- 読む

    public static WorldStateS2C world() {
        return world;
    }

    /** 予告の残り（ミリ秒。受け取ってからの時間を引く）。 */
    public static long remaining(WorldStateS2C.Countdown countdown) {
        return Math.max(0L, countdown.remainingMillis() - (Util.getMillis() - worldReceived));
    }

    public static SyncStoryS2C story() {
        return story;
    }

    public static boolean storyReceived() {
        return storyReceived;
    }

    public static boolean openingPending() {
        return story.openingPending();
    }

    public static boolean pillarsComplete(int floor) {
        Integer bits = story.pillars().get(floor);
        int total = story.pillarTotals().getOrDefault(floor, 3);
        if (total <= 0) {
            return true;
        }
        int all = (1 << total) - 1;
        return bits != null && (bits & all) == all;
    }

    public static boolean pillarActivated(int floor, int index) {
        Integer bits = story.pillars().get(floor);
        return bits != null && (bits & (1 << index)) != 0;
    }

    /** 手引きの行き先（同じディメンションにいなければ null）。 */
    @Nullable
    public static Vec3 guideTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation dimension = story.guideDimension();
        if (dimension == null || story.guideTarget() == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(dimension)) {
            return null;
        }
        return story.guideTarget();
    }

    public static List<ShownText> texts() {
        TEXTS.removeIf(ShownText::expired);
        return new ArrayList<>(TEXTS);
    }

    /** 画面に出す知らせ。HUDを描くときだけ呼ぶ（まだ出ていなかった知らせは、ここで出た時刻を入れる）。 */
    public static List<ShownAnnouncement> announcements() {
        long now = Util.getMillis();
        List<ShownAnnouncement> list = new ArrayList<>();
        for (ShownAnnouncement shown : ANNOUNCEMENTS) {
            ShownAnnouncement started = shown.start() < 0 ? new ShownAnnouncement(shown.kind(), shown.text(), now) : shown;
            if (now - started.start() <= ANNOUNCE_MILLIS) {
                list.add(started);
            }
        }
        ANNOUNCEMENTS.clear();
        ANNOUNCEMENTS.addAll(list);
        return list;
    }

    /** ワールドを出たとき：本人の物語の様子を消す（次に入るワールドで混ざらないように）。 */
    public static void clear() {
        world = new WorldStateS2C(1, 1, 0L, 0, List.of());
        story = new SyncStoryS2C(2, false, Map.of(), Map.of(), -1, 0, "", null, null);
        storyReceived = false;
        TEXTS.clear();
        ANNOUNCEMENTS.clear();
    }
}
