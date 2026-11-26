package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ひとり確認モード（11月26日のご指示「ボス戦までのギミックをソロでテストできるようにして動作確認できるように、おなじくボス戦での
 * ギミックの確認も」）【補完】。管理者が {@code /amemaru admin solo on} で入れ、{@code off} で切る。サーバーを止めても切れる
 * （ふだんの遊びに残らないように、保存しない）。
 *
 * <p>何人かで同時に動かす仕掛けを、ひとりでも動かして確かめられるようにする。仕掛けの決まり（何を・どの順に・どこで）は変えず、
 * 「何秒のうちに」と「何人で」だけをゆるめる：</p>
 * <ul>
 *   <li>時間を延ばす：2階のこだまが返るまで（4秒 → {@link #ECHO_SECONDS} 秒。鐘から離れる間がある）・3階の水門の2つのハンドル
 *       （3秒 → {@link #FLOODGATE_SECONDS} 秒。離れた部屋へ走れる）・3階のボスの部屋の排水のハンドル（3秒 →
 *       {@link #DRAIN_SECONDS} 秒）・8階の鐘の組（{@code seconds} の {@link #BELL_FACTOR} 倍）・8階の湖心の鳴り石
 *       （3秒 → {@link #VEIL_SECONDS} 秒。静寂の帳も 20秒 → {@link #VEIL_LAST_SECONDS} 秒）</li>
 *   <li>練習用の人形（{@code /amemaru admin dummy}）を、仲間の代わりに数える：8階の薄氷のひび（2人で乗ると割れる）・
 *       5階の接続判定（6ブロックのうちに味方がいるか）と切断判定（4ブロックのうちに人がいるか）・みんなで受ける一撃（レイドのボス。
 *       円の中の人数で分ける。人形は受けない）</li>
 *   <li>5階の接続判定と切断判定を、ひとりの挑戦者にもする（ふだんは、ひとりなら判定しない・切断判定は出ない）</li>
 * </ul>
 * <p>その階の仕掛けを朝の形に戻す・ボスの前まで進めるのは {@code /amemaru admin solo reset|ready <階>}（{@code SoloCommands}）。
 * 手順は {@code docs/solo_check.md}。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class SoloTest {
    public static final int ECHO_SECONDS = 10;
    public static final int FLOODGATE_SECONDS = 120;
    public static final int DRAIN_SECONDS = 30;
    public static final int BELL_FACTOR = 2;
    public static final int VEIL_SECONDS = 30;
    public static final int VEIL_LAST_SECONDS = 40;

    private static volatile boolean on;

    private SoloTest() {
    }

    /** ひとり確認モードが入っているか。 */
    public static boolean on() {
        return on;
    }

    /** 入れる・切る（管理者のコマンド・自動テスト）。 */
    public static void set(boolean value) {
        if (on != value) {
            AmemaruMod.LOGGER.info("[amemaru] Solo test mode {}", value ? "on" : "off");
        }
        on = value;
    }

    /** 「何秒のうちに」の秒。モードの間は {@code soloSeconds}（ふだんの方が長ければ、ふだんのまま）。 */
    public static int seconds(int seconds, int soloSeconds) {
        return on ? Math.max(seconds, soloSeconds) : seconds;
    }

    /** 「何秒のうちに」の秒（小数）。モードの間は {@code soloSeconds}（ふだんの方が長ければ、ふだんのまま）。 */
    public static double seconds(double seconds, double soloSeconds) {
        return on ? Math.max(seconds, soloSeconds) : seconds;
    }

    /**
     * 仲間の代わりに数える、練習用の人形の数（モードの間だけ。切っているときは0）。{@code center} から水平に {@code range}
     * ブロックのうち、高さの差が {@code height} より小さい所。
     */
    public static int standIns(ServerLevel level, Vec3 center, double range, double height) {
        if (!on) {
            return 0;
        }
        AABB box = new AABB(center.x - range, center.y - height, center.z - range, center.x + range, center.y + height, center.z + range);
        int count = 0;
        for (TrainingDummy dummy : level.getEntitiesOfClass(TrainingDummy.class, box, TrainingDummy::isAlive)) {
            double dx = dummy.getX() - center.x;
            double dz = dummy.getZ() - center.z;
            if (dx * dx + dz * dz <= range * range) {
                count++;
            }
        }
        return count;
    }

    /** モードが入っていることを、その人に知らせる（入れたとき・入っている間にログインしたとき）。 */
    public static void tell(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.amemaru.solo.on_notice").withStyle(ChatFormatting.GOLD));
    }

    /** みんなに知らせる。 */
    public static void tellAll(MinecraftServer server, boolean value) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (value) {
                tell(player);
            } else {
                player.sendSystemMessage(Component.translatable("message.amemaru.solo.off_notice").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (on && event.getEntity() instanceof ServerPlayer player) {
            tell(player);
        }
    }
}
