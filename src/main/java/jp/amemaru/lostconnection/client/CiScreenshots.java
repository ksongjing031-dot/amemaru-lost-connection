package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.NativeImage;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.SkillTreeScreen;
import jp.amemaru.lostconnection.client.screen.StatusScreen;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.c2s.UseSkillC2S;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Locale;

/**
 * GitHub Actions の自動の確認でだけ動く、画面写真の撮影（遊ぶときは動かない）。
 *
 * <p>Java の起動オプションに {@code -Damemaru.ci.screenshots=true} があるときだけ、シングルプレイのワールドに
 * 入った後で、次の写真を撮る。写真はPNGをBase64にしてログに書き、ワークフローがそれを取り出して保存する。</p>
 * <ul>
 *   <li>Phase 1：HUD（ふつう・HPが減った直後・HPが少ないとき）、ステータス画面</li>
 *   <li>Phase 2：ナイトのスキル枠と誓気・訓練人形のダメージの数字、スキルツリー、職業の登録の画面、
 *       ウィザードの詠唱（三人称・モーション）と流星の着弾</li>
 *   <li>スキルの拡張（11月17日）：ナイトの溜め（誓いの円陣の中で）と溜め斬り、敵の頭の上の状態の印、凍てつく波動（凍結の氷と棘）、
 *       氷柱で砕く、星爆、雷霆、光の帳</li>
 *   <li>奥義のもう一つ（11月17日）：天断ちの構えと断った瞬間、奥義のつまみ（2つから1つ）、氷華の檻の詠唱と花が咲いたところ</li>
 *   <li>建物の見本（11月17日）：コネリアの冒険者ギルド本館（{@code amemaru:map/conelia/guild_hall}）を空に置き、
 *       まわりの6か所から撮る（HUDは隠す）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class CiScreenshots {
    private static final boolean ENABLED = Boolean.getBoolean("amemaru.ci.screenshots");
    private static final int CHUNK = 4000;
    /**
     * 次の段階に進むまでに、少なくとも描くフレームの数。写真を撮った直後はゲームが遅れを取り戻すために1フレームで何tickも進むため、
     * tickだけで待つと、送ったスキルの結果がサーバーから届く前に次の写真を撮ってしまうことがある。
     */
    private static final int MIN_FRAMES = 4;

    private static int stage;
    private static int wait;
    private static int frames;
    /** 溜め斬りを放った後、放ったことが届くまで待ったtick。 */
    private static int releaseWait;
    /** 2回目の天断ちの構えが始まったのを見たか。 */
    private static boolean castSeen;
    @Nullable
    private static String pendingShot;
    /** 建物の見本を置いた所（構造物の角）。 */
    @Nullable
    private static BlockPos mapOrigin;
    /** 建物の写真の間は、HUD（ホットバー・チャットなど）を描かない。 */
    private static boolean hideHud;
    /** 建物の写真の前にいた所とゲームモード（撮った後で戻す。後で動く自動テストの場所を変えないため）。 */
    @Nullable
    private static BlockPos returnPos;
    private static String returnMode = "creative";

    /** 建物の見本の写真の名前と、撮る所：目の位置と見る所（構造物の中の座標。y=0 が地面の草）。 */
    private static final String[] MAP_SHOTS = {
            "map_guild_front", "map_guild_left", "map_guild_right", "map_guild_close", "map_guild_aerial", "map_guild_back"};
    private static final double[][] MAP_VIEWS = {
            {24, 21, 68, 24, 24, 20},
            {-8, 21, 54, 20, 19, 20},
            {58, 21, 54, 27, 18, 20},
            {12, 8, 46, 20, 9, 28},
            {58, 44, 64, 24, 14, 20},
            {52, 24, -18, 24, 18, 16},
    };
    /** 建物の見本の写真の段階の始まり。 */
    private static final int MAP_STAGE = 70;

    private CiScreenshots() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ClientPlayerState.get().hasData() || pendingShot != null) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (frames < MIN_FRAMES) {
            return;
        }
        switch (stage) {
            case 0 -> {
                if (minecraft.screen != null) {
                    return;
                }
                // 透明な画面を開いておく（自動の確認の側は、画面が開いている間は終了を待つ）。HUDはそのまま見える
                minecraft.setScreen(new HoldScreen());
                // 見本の状態：レベル12、経験値を半分ほど、HPと攻撃に振り分け（残りのポイントがあるので結晶が光る）
                command(minecraft, "amemaru admin level set @a 12");
                command(minecraft, "amemaru admin level exp @a 180");
                // ホットバーとオフハンドの見え方を確かめるためのアイテム
                command(minecraft, "give @a minecraft:diamond_sword");
                command(minecraft, "give @a minecraft:iron_pickaxe");
                command(minecraft, "give @a minecraft:bread 12");
                command(minecraft, "give @a minecraft:torch 64");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:shield");
                ModNetwork.sendToServer(new AllocateStatC2S(StatType.HP, 3));
                ModNetwork.sendToServer(new AllocateStatC2S(StatType.ATTACK, 2));
                // どんな地形に出ても同じ写真になるよう、30ブロック上の空に15×15の床を作る（立つのは次の段階。
                // 床が届く前に移ると、落ちることがあるため）
                command(minecraft, "time set noon");
                command(minecraft, "weather clear");
                // 床は雲の高さに近いことがあり、雲が演出に重なって見分けにくくなるため、雲を消す
                minecraft.options.cloudStatus().set(CloudStatus.OFF);
                command(minecraft, "execute as @p at @p run fill ~-7 ~29 ~-7 ~7 ~29 ~7 minecraft:deepslate_tiles");
                next(1, 20);
            }
            case 1 -> {
                command(minecraft, "execute as @p at @p run tp @s ~ ~30 ~");
                command(minecraft, "amemaru admin debug vitals @a 82 46");
                next(2, 40);
            }
            case 2 -> {
                // HPを減らした直後（減った分の残像が見える）
                command(minecraft, "amemaru admin debug vitals @a 61 46");
                next(3, 3);
            }
            case 3 -> {
                pendingShot = "hud";
                next(4, 2);
            }
            case 4 -> {
                minecraft.setScreen(new StatusScreen());
                next(5, 10);
            }
            case 5 -> {
                pendingShot = "status";
                next(6, 2);
            }
            case 6 -> {
                minecraft.setScreen(new HoldScreen());
                command(minecraft, "amemaru admin debug vitals @a 18 46");
                next(7, 40);
            }
            case 7 -> {
                pendingShot = "hud_low";
                next(8, 2);
            }
            // ---------------------------------------------------------------- Phase 2：ナイト
            case 8 -> {
                // コマンドの結果の文がチャットに並んで、写真のじゃまにならないようにする
                command(minecraft, "gamerule sendCommandFeedback false");
                command(minecraft, "gamerule logAdminCommands false");
                command(minecraft, "amemaru admin debug vitals @a 82 46");
                if (minecraft.player != null) {
                    minecraft.player.setXRot(15.0F);
                }
                // 操作の説明（チュートリアル）の小窓を出さない
                minecraft.getTutorial().setStep(TutorialSteps.NONE);
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_sword");
                command(minecraft, "item replace entity @a weapon.offhand with amemaru:apprentice_shield");
                // スキル枠を6つ埋める（呼び声・突進斬り・盾結び・城壁の構え・旋風断・不屈の誓い）
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/shield_bond 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/rampart 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/whirlwind 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/unyielding_vow 1");
                // 目の前（3ブロック先）に訓練人形
                command(minecraft, "execute as @p at @p run amemaru admin dummy 20");
                next(9, 30);
            }
            case 9 -> {
                // 誓気をためておく（旋風断が30使って強くなる）
                command(minecraft, "amemaru admin skill gauge @a 70");
                next(10, 6);
            }
            case 10 -> {
                useSkill(minecraft, 3); // 城壁の構え
                useSkill(minecraft, 0); // 呼び声
                useSkill(minecraft, 4); // 旋風断（訓練人形にダメージの数字）
                next(11, 8);
            }
            case 11 -> {
                clean(minecraft);
                pendingShot = "knight_skills";
                next(12, 2);
            }
            case 12 -> {
                minecraft.setScreen(new SkillTreeScreen());
                next(13, 10);
            }
            case 13 -> {
                clean(minecraft);
                pendingShot = "skill_tree";
                next(14, 2);
            }
            case 14 -> {
                // 誓約の結晶に手を当てたときと同じ画面（サーバーから開く）
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null && minecraft.player != null) {
                    java.util.UUID id = minecraft.player.getUUID();
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(id);
                        if (player != null) {
                            JobService.openJobScreen(player, player.blockPosition());
                        }
                    });
                }
                next(15, 15);
            }
            case 15 -> {
                clean(minecraft);
                pendingShot = "job_select";
                next(16, 2);
            }
            // ---------------------------------------------------------------- Phase 2：ウィザード
            case 16 -> {
                minecraft.setScreen(new HoldScreen());
                if (minecraft.player != null) {
                    // 少し下を向いて、陣と流星が近くに落ちるようにする
                    minecraft.player.setXRot(28.0F);
                }
                command(minecraft, "amemaru admin job set @a amemaru:wizard");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_staff");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:air");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/meteor_shower 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/chain_lightning 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/mana_thread 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/blink 1");
                next(17, 20);
            }
            case 17 -> {
                useSkill(minecraft, 1); // 氷結陣
                next(18, 10);
            }
            case 18 -> {
                useSkill(minecraft, 2); // 流星群（2秒の詠唱。狙いは使った瞬間に決まる）
                // 三人称（後ろから）にして、詠唱のモーション・足もとの陣・狙った地点の印が見えるようにする
                // （正面からだと、目の前の訓練人形がカメラと自分の間に入る）
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(18.0F);
                }
                next(19, 16);
            }
            case 19 -> {
                clean(minecraft);
                pendingShot = "wizard_cast";
                next(20, 2);
            }
            case 20 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(28.0F);
                }
                // 詠唱の残り（約22tick）＋着弾まで（12tick）
                next(21, 36);
            }
            case 21 -> {
                clean(minecraft);
                pendingShot = "wizard_impact";
                next(22, 2);
            }
            // ---------------------------------------------------------------- スキルの拡張：ナイト
            case 22 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                command(minecraft, "kill @e[type=amemaru:training_dummy]");
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_sword");
                command(minecraft, "item replace entity @a weapon.offhand with amemaru:apprentice_shield");
                for (String skill : new String[]{"heavy_slash", "oath_circle", "binding_chain", "parry", "oath_release", "leap_slash"}) {
                    command(minecraft, "amemaru admin skill learn @a amemaru:knight/" + skill + " 1");
                }
                next(23, 10);
            }
            case 23 -> {
                setLoadout(0, "knight/heavy_slash");
                setLoadout(1, "knight/oath_circle");
                setLoadout(2, "knight/binding_chain");
                setLoadout(3, "knight/parry");
                setLoadout(4, "knight/oath_release");
                setLoadout(5, "knight/leap_slash");
                spawnDummies(minecraft);
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                if (minecraft.player != null) {
                    minecraft.player.setXRot(12.0F);
                }
                next(24, 20);
            }
            case 24 -> {
                useSkill(minecraft, 1); // 誓いの円陣
                next(25, 10);
            }
            case 25 -> {
                // 溜め斬り：キーを押したまま（押した・離したを直接送る）
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(22.0F);
                }
                ModNetwork.sendToServer(new UseSkillC2S(9001, UseSkillC2S.PRESS, 0, -1, null));
                next(26, 34);
            }
            case 26 -> {
                clean(minecraft);
                pendingShot = "knight_charge";
                next(27, 2);
            }
            case 27 -> {
                ModNetwork.sendToServer(new UseSkillC2S(9002, UseSkillC2S.RELEASE, 0, -1, null));
                releaseWait = 0;
                next(28, 3);
            }
            case 28 -> {
                // 放ったこと（溜めが終わったこと）がサーバーから届くまで待つ（2秒まで）
                if (ClientPlayerState.get().charging() && releaseWait++ < 40) {
                    return;
                }
                clean(minecraft);
                pendingShot = "knight_heavy_slash";
                next(29, 2);
            }
            case 29 -> {
                // 奥義のもう一つ：天断ち（レベル30・上位職にして覚える）
                command(minecraft, "amemaru admin level set @a 30");
                command(minecraft, "amemaru admin job promote @a true");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/heaven_cleave 1");
                command(minecraft, "kill @e[type=amemaru:training_dummy]");
                next(30, 20);
            }
            case 30 -> {
                setLoadout(0, "knight/heaven_cleave");
                spawnDummies(minecraft);
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (minecraft.player != null) {
                    // 少し上を向いて、頭上に形づくられる光の大剣が写るようにする（断つ線は向きの上下に関係なく地面と平行）
                    minecraft.player.setXRot(-8.0F);
                }
                next(31, 16);
            }
            case 31 -> {
                useSkill(minecraft, 0); // 天断ち（1秒の構え）
                releaseWait = 0;
                next(32, 13);
            }
            case 32 -> {
                clean(minecraft);
                pendingShot = "knight_heaven_cleave_cast";
                next(33, 2);
            }
            case 33 -> {
                // 構えの写真を撮る間（画面を写して書き出す間）に断ってしまうため、演出が終わるのを待ち、
                // クールダウンを戻して、もう一度使う（今度は断った直後を写す）
                if (ClientPlayerState.get().castSkill() >= 0 && releaseWait++ < 60) {
                    return;
                }
                command(minecraft, "amemaru admin skill cooldown @a");
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                next(330, 40);
            }
            case 330 -> {
                useSkill(minecraft, 0);
                releaseWait = 0;
                castSeen = false;
                next(331, 1);
            }
            case 331 -> {
                // 構えが始まったのを見てから、終わる（断つ）のを待つ
                boolean casting = ClientPlayerState.get().castSkill() >= 0;
                castSeen |= casting;
                if ((casting || !castSeen) && releaseWait++ < 80) {
                    return;
                }
                // 断った線をななめから見るため、体の向きを右へ回し、地面を見下ろす（演出は地面に残ったまま）
                if (minecraft.player != null) {
                    float yaw = minecraft.player.getYRot() + 50.0F;
                    minecraft.player.setYRot(yaw);
                    minecraft.player.yRotO = yaw;
                    minecraft.player.setYHeadRot(yaw);
                    minecraft.player.setXRot(28.0F);
                    minecraft.player.xRotO = 28.0F;
                }
                next(34, 3);
            }
            case 34 -> {
                clean(minecraft);
                pendingShot = "knight_heaven_cleave";
                next(35, 2);
            }
            case 35 -> {
                // 奥義のつまみ：天断ちを選んだので、不落の城は選べない（暗い）
                SkillTreeScreen tree = new SkillTreeScreen();
                minecraft.setScreen(tree);
                int cleave = skillIndex("knight/heaven_cleave");
                if (cleave >= 0) {
                    tree.select(cleave);
                }
                next(36, 10);
            }
            case 36 -> {
                clean(minecraft);
                pendingShot = "knight_ultimate_tree";
                next(37, 2);
            }
            case 37 -> {
                minecraft.setScreen(new HoldScreen());
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                next(40, 30);
            }
            // ---------------------------------------------------------------- スキルの拡張：ウィザード
            case 40 -> {
                command(minecraft, "kill @e[type=amemaru:training_dummy]");
                command(minecraft, "amemaru admin job set @a amemaru:wizard");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_staff");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:air");
                for (String skill : new String[]{"icicle", "frost_wave", "thunderbolt", "star_burst", "light_veil", "lamp_barrage"}) {
                    command(minecraft, "amemaru admin skill learn @a amemaru:wizard/" + skill + " 1");
                }
                next(41, 10);
            }
            case 41 -> {
                setLoadout(0, "wizard/frost_wave");
                setLoadout(1, "wizard/frost_circle");
                setLoadout(2, "wizard/icicle");
                setLoadout(3, "wizard/thunderbolt");
                setLoadout(4, "wizard/star_burst");
                setLoadout(5, "wizard/light_veil");
                spawnDummies(minecraft);
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                next(42, 20);
            }
            case 42 -> {
                // 敵の頭の上の印：凍え4つ・星痕3つ・感電
                command(minecraft, "amemaru admin debug status @e[type=amemaru:training_dummy] chill 4");
                command(minecraft, "amemaru admin debug status @e[type=amemaru:training_dummy] brand 3");
                command(minecraft, "amemaru admin debug status @e[type=amemaru:training_dummy] shock 8");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(16.0F);
                }
                next(43, 8);
            }
            case 43 -> {
                clean(minecraft);
                pendingShot = "status_marks";
                next(44, 2);
            }
            case 44 -> {
                useSkill(minecraft, 0); // 凍てつく波動（凍えが3つ以上なので凍結）
                next(45, 5);
            }
            case 45 -> {
                clean(minecraft);
                pendingShot = "wizard_frost_wave";
                next(46, 2);
            }
            case 46 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(10.0F);
                }
                useSkill(minecraft, 2); // 氷柱（凍結を砕く）
                next(47, 3);
            }
            case 47 -> {
                clean(minecraft);
                pendingShot = "wizard_icicle";
                next(48, 2);
            }
            case 48 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (minecraft.player != null) {
                    minecraft.player.setXRot(16.0F);
                }
                useSkill(minecraft, 4); // 星爆
                next(49, 5);
            }
            case 49 -> {
                clean(minecraft);
                pendingShot = "wizard_star_burst";
                next(50, 2);
            }
            case 50 -> {
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                if (minecraft.player != null) {
                    minecraft.player.setXRot(24.0F);
                }
                useSkill(minecraft, 3); // 雷霆（0.4秒後に落ちる）
                next(51, 9);
            }
            case 51 -> {
                clean(minecraft);
                pendingShot = "wizard_thunderbolt";
                next(52, 2);
            }
            case 52 -> {
                useSkill(minecraft, 5); // 光の帳（味方がいないので自分に）
                next(53, 8);
            }
            case 53 -> {
                clean(minecraft);
                pendingShot = "wizard_light_veil";
                next(54, 2);
            }
            case 54 -> {
                // 奥義のもう一つ：氷華の檻（職業を替えたので、上位職にし直して覚える）
                command(minecraft, "amemaru admin job promote @a true");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/ice_garden 1");
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                next(55, 16);
            }
            case 55 -> {
                setLoadout(0, "wizard/ice_garden");
                if (minecraft.player != null) {
                    minecraft.player.setXRot(22.0F);
                }
                next(56, 6);
            }
            case 56 -> {
                useSkill(minecraft, 0); // 氷華の檻（2秒の詠唱）
                releaseWait = 0;
                next(57, 28);
            }
            case 57 -> {
                clean(minecraft);
                pendingShot = "wizard_ice_garden_cast";
                next(58, 2);
            }
            case 58 -> {
                // 詠唱が終わるまで待ち、着弾（0.4秒後）で花が咲いたところを写す
                if (ClientPlayerState.get().castSkill() >= 0 && releaseWait++ < 80) {
                    return;
                }
                next(59, 14);
            }
            case 59 -> {
                clean(minecraft);
                pendingShot = "wizard_ice_garden";
                next(60, 2);
            }
            case 60 -> {
                // ウィザードのスキルツリー（星灯のつまみ。星爆を選んで、組み合わせの説明を見せる）
                SkillTreeScreen tree = new SkillTreeScreen();
                minecraft.setScreen(tree);
                int starBurst = skillIndex("wizard/star_burst");
                if (starBurst >= 0) {
                    tree.select(starBurst);
                }
                next(61, 10);
            }
            case 61 -> {
                clean(minecraft);
                pendingShot = "wizard_skill_tree";
                next(62, 2);
            }
            case 62 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                next(MAP_STAGE, 0);
            }
            // ---- 建物の見本：空の高い所に草の広場を敷き、構造物を置いて、まわりから撮る
            case MAP_STAGE -> {
                clean(minecraft);
                if (minecraft.gameMode != null) {
                    returnMode = minecraft.gameMode.getPlayerMode().getName();
                }
                command(minecraft, "gamemode spectator @a");
                command(minecraft, "time set noon");
                BlockPos at = minecraft.player.blockPosition();
                returnPos = at;
                mapOrigin = new BlockPos(at.getX() - 24, 200, at.getZ() - 20);
                next(MAP_STAGE + 1, 60);
            }
            case MAP_STAGE + 1 -> {
                BlockPos o = mapOrigin;
                command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block",
                        o.getX() - 24, o.getY(), o.getZ() - 24, o.getX() + 71, o.getY(), o.getZ() + 63));
                command(minecraft, String.format(Locale.ROOT, "place template amemaru:map/conelia/guild_hall %d %d %d",
                        o.getX(), o.getY(), o.getZ()));
                hideHud = true;
                mapView(0);
                // 大きな構造物の形を作り直して描くまで、長めに待つ
                next(MAP_STAGE + 2, 300);
            }
            default -> {
                int k = stage - (MAP_STAGE + 2);
                if (k < 0 || k >= MAP_SHOTS.length * 2) {
                    return;
                }
                int view = k / 2;
                if (k % 2 == 0) {
                    clean(minecraft);
                    pendingShot = MAP_SHOTS[view];
                    next(stage + 1, 2);
                } else if (view + 1 < MAP_SHOTS.length) {
                    mapView(view + 1);
                    next(stage + 1, 80);
                } else {
                    hideHud = false;
                    if (returnPos != null) {
                        command(minecraft, String.format(Locale.ROOT, "tp @p %d %d %d",
                                returnPos.getX(), returnPos.getY(), returnPos.getZ()));
                    }
                    command(minecraft, "gamemode " + returnMode + " @a");
                    minecraft.setScreen(null);
                    next(MAP_STAGE + 2 + MAP_SHOTS.length * 2, 0);
                }
            }
        }
    }

    /** 建物の見本の撮る所へ、観戦モードのまま移る（目の高さは足もとの 1.62 上）。 */
    private static void mapView(int index) {
        double[] v = MAP_VIEWS[index];
        BlockPos o = mapOrigin;
        if (o == null) {
            return;
        }
        double dx = v[3] - v[0];
        double dy = v[4] - v[1];
        double dz = v[5] - v[2];
        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        command(Minecraft.getInstance(), String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f",
                o.getX() + v[0], o.getY() + v[1] - 1.62, o.getZ() + v[2], yaw, pitch));
    }

    /** 建物の写真の間は、HUDを描かない（開いたままの透明な画面があるので、F1 と同じ設定では消えない）。 */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ENABLED && hideHud) {
            event.setCanceled(true);
        }
    }

    /** 写真のじゃまになる、チャットの文と右上の小窓（実績・レシピ）を消す。 */
    private static void clean(Minecraft minecraft) {
        minecraft.gui.getChat().clearMessages(false);
        minecraft.getToasts().clear();
    }

    /** スキル枠に、職業のスキル（{@code knight/heavy_slash} の形）を入れる。 */
    /** 職業のスキル（{@code wizard/star_burst} の形）の番号。なければ -1。 */
    private static int skillIndex(String path) {
        for (SyncDefinitionsS2C.SkillInfo skill : ClientDefinitions.get().skillsOf(ClientPlayerState.get().jobIndex())) {
            if (skill.id().getPath().equals(path)) {
                return skill.index();
            }
        }
        return -1;
    }

    private static void setLoadout(int slot, String path) {
        ClientPlayerState state = ClientPlayerState.get();
        for (SyncDefinitionsS2C.SkillInfo skill : ClientDefinitions.get().skillsOf(state.jobIndex())) {
            if (skill.id().getPath().equals(path)) {
                ModNetwork.sendToServer(new SetLoadoutC2S(slot, skill.index()));
                return;
            }
        }
        AmemaruMod.LOGGER.warn("[amemaru] CI: skill {} not found for the loadout", path);
    }

    /** 目の前に、訓練人形を3体（真ん中と左右）置く。 */
    private static void spawnDummies(Minecraft minecraft) {
        command(minecraft, "execute as @p at @p run amemaru admin dummy 20");
        command(minecraft, "execute as @p at @p positioned ^2 ^ ^1 run amemaru admin dummy 20");
        command(minecraft, "execute as @p at @p positioned ^-2 ^ ^1 run amemaru admin dummy 20");
    }

    /** スキル枠のスキルを、キーを押したときと同じように使う。 */
    private static void useSkill(Minecraft minecraft, int slot) {
        if (minecraft.player != null) {
            SkillInput.use(minecraft, minecraft.player, slot);
        }
    }

    /** 描き終わったフレームを写す。 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            frames++;
            return;
        }
        if (pendingShot == null) {
            return;
        }
        String name = pendingShot;
        pendingShot = null;
        Minecraft minecraft = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            String base64 = Base64.getEncoder().encodeToString(image.asByteArray());
            int total = (base64.length() + CHUNK - 1) / CHUNK;
            AmemaruMod.LOGGER.info("[amemaru] CI screenshot '{}' {}x{} ({} chunks)", name, image.getWidth(), image.getHeight(), total);
            for (int i = 0; i < total; i++) {
                String part = base64.substring(i * CHUNK, Math.min(base64.length(), (i + 1) * CHUNK));
                AmemaruMod.LOGGER.info("AMEMARU_SHOT|{}|{}|{}|{}", name, i, total, part);
            }
        } catch (Exception e) {
            AmemaruMod.LOGGER.error("[amemaru] CI screenshot '{}' failed", name, e);
        }
    }

    private static void next(int nextStage, int ticks) {
        stage = nextStage;
        wait = ticks;
        frames = 0;
    }

    /** 何も描かない、ゲームを止めない画面。開いている間も、後ろのHUDは描かれる。 */
    private static final class HoldScreen extends Screen {
        HoldScreen() {
            super(Component.empty());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }

    private static void command(Minecraft minecraft, String command) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
}
