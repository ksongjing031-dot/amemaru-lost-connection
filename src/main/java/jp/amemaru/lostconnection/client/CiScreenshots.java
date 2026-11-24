package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.NativeImage;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.client.screen.BestiaryScreen;
import jp.amemaru.lostconnection.client.screen.SkillTreeScreen;
import jp.amemaru.lostconnection.client.screen.StatusScreen;
import jp.amemaru.lostconnection.client.screen.WorkshopScreen;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.c2s.UseSkillC2S;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
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
import java.util.List;
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
 *   <li>Phase 3：1階の4種類の敵、敵の予兆（突進の帯・叩きつけの円）、レアドロップの光の柱と知らせ、手に持った3Dの武器
 *       （一人称）、重鎧と法衣を着た姿（三人称。首飾り・腕輪・お守りを体に表示）、装備とアクセサリーの説明文、
 *       アクセサリーの画面、武器の工房（作る・強化）、図鑑</li>
 *   <li>建物の見本（11月19日、PHASE 1 の設計）：コネリアの冒険者ギルド（{@code amemaru:map/conelia/guild_hall}。
 *       正面は西）を空に置き、まわりの6か所から撮る（HUDは隠す）</li>
 *   <li>建物の見本（11月19日、PHASE 2 の設計）：コネリアの武器工房（{@code amemaru:map/conelia/weapon_workshop}。
 *       正面は南）。ギルドを片づけた同じ所に置き、まわりの6か所から撮る</li>
 *   <li>建物の見本（11月19日、PHASE 3 の設計）：コネリアの防具工房（{@code amemaru:map/conelia/armor_workshop}。
 *       正面は北）。武器工房を片づけた同じ所に置き、まわりの6か所から撮る</li>
 *   <li>建物の見本（11月20日、PHASE 4 の設計）：コネリアの装飾工房（{@code amemaru:map/conelia/accessory_workshop}。
 *       正面は南）。防具工房を片づけた同じ所に置き、まわりの6か所から撮る</li>
 *   <li>建物の見本（11月20日、PHASE 5 の設計）：商店街の北（料理屋が開く前と、開いたあとを重ねた形）と南</li>
 *   <li>建物の見本（11月20日、PHASE 6 の設計）：個人倉庫（{@code amemaru:map/conelia/personal_storage}）。
 *       商店街を片づけてギルドを置き直し、その東に受付棟と地下の保管庫を置く（ギルドの東の扉から渡り廊下でつながる形）。
 *       外の6か所と、1階・地下1層・ギルドの下の保管庫・地下2層の中の7か所から撮る</li>
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

    /** 建物の見本の写真の名前と、撮る所：目の位置と見る所（構造物の中の座標。y=0 が地面）。
     *  はじめの6枚はギルド（tools/mapgen/build_guild.py の VIEWS と同じ：正面・左前・右前・入口の近く・上から・裏）。
     *  {@link #WEAPON_FIRST} からの6枚は武器工房（tools/mapgen/build_weapon.py の VIEWS と同じ：
     *  正面・東門の方・西・入口の近く・上から・裏）。
     *  {@link #ARMOR_FIRST} からの6枚は防具工房（tools/mapgen/build_armor.py の VIEWS と同じ：
     *  正面・交差点の角・環状路（西）・入口の近く・上から・裏）。
     *  {@link #ACCESSORY_FIRST} からの6枚は装飾工房（tools/mapgen/build_accessory.py の VIEWS と同じ：
     *  正面・広場の角・中央広場（西）・入口の近く・上から・裏）。
     *  {@link #MARKET_NORTH_FIRST} からの6枚は商店街の北（tools/mapgen/build_market.py の VIEWS_NORTH と同じ：
     *  環状路から北へ・西の店の並び・東の店の並び・中央広場から・上から・西の裏通り）、
     *  {@link #MARKET_OPEN_SHOT} の1枚は、寄付で開いた料理屋を重ねて置いたあと（中央広場の側の席）、
     *  {@link #MARKET_SOUTH_FIRST} からの6枚は商店街の南（VIEWS_SOUTH と同じ：環状路から南へ・渡り廊下・
     *  フリーマーケットへの通り抜け・南門の前の小広場・上から・環状路の交差点）。
     *  {@link #STORAGE_FIRST} からの13枚は個人倉庫（tools/mapgen/build_storage.py の CI_SHOTS と同じ：正面・入口・
     *  北東の角から・荷揚げの妻・裏・上から・広間・ハーヴィの立ち位置から窓口ごしに広間・地下1層の廊下・小部屋・鉄格子から保管庫・保管庫の中・
     *  地下2層の古代の間）。座標はギルドを置いた所から（個人倉庫は {@link #STORAGE_AT} だけずらして置く）。
     *  {@link #FREE_FIRST} からの7枚はフリーマーケットのはじめの形（tools/mapgen/build_freemarket.py の CI_SHOTS と同じ：
     *  北の門・東の門（通り抜けから）・中央通路・泉・市場監督の小屋・休み場・上から）、{@link #FREE_MORE_SHOT} からの2枚は
     *  寄付で露店が増えたあと（上から・北の島の並び）。座標はフリーマーケットを置いた所から（商店街の南を
     *  {@link #FREE_SOUTH_AT} だけずらして東に並べる）。
     *  {@link #CHURCH_FIRST} からの12枚は結びの教会（tools/mapgen/build_church.py の CI_SHOTS と同じ：正面・鐘楼・南・上から・
     *  裏・身廊・内陣・南の側廊・鐘の間・療養所の2階・記憶の庭・名を刻む石）、{@link #CHURCH_COUNSEL_SHOT} の1枚は、
     *  6日目に開く記憶の相談所を重ねたあと（南の側廊から書庫の扉）。座標は教会を置いた所から（記憶の庭を
     *  {@link #CHURCH_GARDEN_AT} だけずらして南に並べる）。
     *  {@link #FISH_FIRST} からの11枚は釣り堀（tools/mapgen/build_fishing.py の CI_SHOTS と同じ：入口の門・上から・上の台・
     *  下の桟橋の先・アーチの下の桟橋・湧き口・西の岸・釣り具屋のカウンター・水門の内側・水門の外の面・路地）、
     *  {@link #FISH_LAKESIDE_SHOT} からの3枚は寄付で開いた湖畔の釣り場を重ねたあと（岸の板の道・長い桟橋の先から・
     *  開いた水門の内側）、{@link #FISH_FROZEN_SHOT} の1枚は第2章の氷を重ねたあと。座標は釣り堀の地面の所から
     *  （釣り堀は {@link #FISH_POND_AT}、水門と湖は {@link #FISH_GATE_AT}、路地は {@link #FISH_ALLEY_AT} だけずらして置く）。
     *  {@link #CASINO_FIRST} からの9枚はカジノ（tools/mapgen/build_casino.py の CI_SHOTS と同じ：正面・南門の内から・上から・
     *  背面・大広間のアーチから・酒場・VIPルーム・地下の噂の間・2階の宴の間）、{@link #CASINO_HIGHLOW_SHOT} の1枚は
     *  共同事業②の「ハイ＆ロー」を重ねたあと（カードの卓）、{@link #CASINO_FISHRACE_SHOT} の1枚は共同事業⑥の「魚レース」を
     *  重ねたあと（宴の間の水の走路）。座標はカジノの地面の所から（カジノは {@link #CASINO_AT} だけずらして置く）。
     *  {@link #TOWER_FIRST} からの11枚は塔の入口（tools/mapgen/build_tower.py の CI_SHOTS と同じ：北門の外から・広場の南西から・
     *  上から・東から・門の前から通路ごし・広間の入口から転移門・台の上から輪の向こうの大扉・塔の灯りとバルドの書見台・受付・
     *  控えの間・門の上の歩廊）、{@link #TOWER_MONUMENT_SHOT} の1枚は、1週目の共同の目標のあとの記念碑を重ねたあと。
     *  座標は塔の入口を置いた所の足もとの地面（世界の x −22, y 0, z −177）から（塔の入口は {@link #TOWER_AT}、塔前広場は
     *  {@link #TOWER_PLAZA_AT} だけずらして置く）。 */
    private static final String[] MAP_SHOTS = {
            "map_guild_front", "map_guild_left", "map_guild_right", "map_guild_close", "map_guild_aerial", "map_guild_back",
            "map_weapon_front", "map_weapon_gate", "map_weapon_west", "map_weapon_close", "map_weapon_aerial",
            "map_weapon_back",
            "map_armor_front", "map_armor_corner", "map_armor_west", "map_armor_close", "map_armor_aerial",
            "map_armor_back",
            "map_accessory_front", "map_accessory_corner", "map_accessory_west", "map_accessory_close",
            "map_accessory_aerial", "map_accessory_back",
            "map_market_north_street", "map_market_north_west", "map_market_north_east", "map_market_north_plaza",
            "map_market_north_aerial", "map_market_north_back",
            "map_market_restaurant_open",
            "map_market_south_street", "map_market_south_west", "map_market_south_arch", "map_market_south_gate",
            "map_market_south_aerial", "map_market_south_corner",
            "map_storage_front", "map_storage_entrance", "map_storage_east", "map_storage_hoist", "map_storage_back",
            "map_storage_aerial", "map_storage_hall", "map_storage_counter", "map_storage_corridor", "map_storage_room",
            "map_storage_grille", "map_storage_vault", "map_storage_ancient",
            "map_freemarket_north_gate", "map_freemarket_east_gate", "map_freemarket_lane", "map_freemarket_fountain",
            "map_freemarket_booth", "map_freemarket_rest", "map_freemarket_aerial",
            "map_freemarket_more_aerial", "map_freemarket_more_north",
            "map_church_front", "map_church_tower", "map_church_south", "map_church_aerial", "map_church_back",
            "map_church_nave", "map_church_chancel", "map_church_aisle", "map_church_bell", "map_church_sanatorium",
            "map_church_garden", "map_church_memorial", "map_church_counsel",
            "map_fishing_entrance", "map_fishing_aerial", "map_fishing_deck", "map_fishing_pier", "map_fishing_arch",
            "map_fishing_spring", "map_fishing_west", "map_fishing_shop", "map_fishing_gate_in", "map_fishing_gate_out",
            "map_fishing_alley", "map_fishing_lakeside", "map_fishing_lakeside_pier", "map_fishing_gate_open",
            "map_fishing_frozen",
            "map_casino_front", "map_casino_gate", "map_casino_aerial", "map_casino_back", "map_casino_in_hall",
            "map_casino_in_bar", "map_casino_vip", "map_casino_rumor", "map_casino_banquet", "map_casino_highlow",
            "map_casino_fishrace",
            "map_tower_approach", "map_tower_plaza", "map_tower_aerial", "map_tower_east", "map_tower_passage",
            "map_tower_hall", "map_tower_ring", "map_tower_board", "map_tower_reception", "map_tower_waiting",
            "map_tower_walk", "map_tower_monument"};
    private static final double[][] MAP_VIEWS = {
            {-30, 16, 21, 18, 13, 21},
            {-22, 22, -18, 16, 12, 16},
            {-20, 18, 62, 16, 12, 24},
            {-15, 11, 21, 6, 13, 21},
            {-16, 42, 54, 20, 12, 21},
            {66, 26, -14, 20, 12, 18},
            {0, 5, 36, 9, 10, 14},
            {38, 7, 34, 14, 8, 11},
            {-22, 6, 30, 9, 8, 11},
            {11, 2.8, 25, 10, 4, 18},
            {-6, 30, 36, 11, 8, 10},
            {2, 15, -16, 12, 9, 8},
            {11, 5, -22, 11, 9, 9},
            {-9, 6, -11, 8, 8, 8},
            {-20, 5, 14, 2, 6, 10},
            {4, 2.8, -9, 7, 3.5, 2},
            {-7, 25, -12, 9, 8, 10},
            {24, 14, 34, 10, 8, 12},
            {10, 5, 38, 10, 8.5, 12},
            {-12, 6, 33, 9, 8, 12},
            {-22, 6, 13, 3, 8, 12},
            {3, 2.8, 28, 4, 3.2, 19},
            {-8, 27, 34, 9, 9, 12},
            {26, 14, -12, 9, 8, 10},
            {18.5, 9, 38, 18.5, 4, 0},
            {22.5, 3, 22, 10, 6, 9},
            {17.5, 4, 26, 27, 6, 8},
            {18.5, 5, -14, 18.5, 5, 10},
            {38, 36, 48, 16, 2, 14},
            {-6, 12, 38, 6, 7, 16},
            {15, 3.8, -6, 6.5, 5.5, 5},
            {18.5, 9, -8, 18.5, 3, 30},
            {23, 4, 16.5, 10, 6.5, 16.5},
            {19, 2.6, 17, 8, 3.5, 17},
            {9, 6.5, 50, 8, 2.5, 38},
            {36, 34, -6, 8, 2, 24},
            {27, 5, -10, 16, 4, 4},
            {39, 8, 59, 41, 7.5, 32},
            {43.5, 4.6, 46.5, 43.5, 6.2, 37},
            {64, 9, 48, 50, 8.5, 29},
            {61, 7, 36, 51, 12.5, 29},
            {58, 14, 4, 42, 8, 28},
            {62, 32, 58, 39, 6, 26},
            {43.5, 4.62, 36.4, 43.5, 4.2, 28},
            {43.5, 4.62, 29.2, 43.5, 4.2, 37},
            {46.3, 0.62, 35.4, 30, -0.5, 34.5},
            {41.5, 0.62, 31.9, 41.5, -0.6, 29},
            {33.8, 0.62, 34.8, 18, -0.2, 35},
            {29.4, 0.62, 22.5, 17, -0.5, 28},
            {44.5, -3.38, 22.5, 35, -4.2, 27.5},
            {14.5, 3.6, -6, 14.5, 2.8, 14},
            {38, 2.62, 17, 15, 3, 17},
            {27.5, 2.62, 17, 12, 2.6, 17},
            {21.4, 3.3, 23.6, 12.5, 2.2, 13},
            {14.5, 2.62, 9.5, 19.5, 3, 3.6},
            {14.5, 3.3, 21.4, 14.5, 4, 30},
            {-8, 21, 41, 15.5, 0, 14.5},
            {-8, 21, 41, 15.5, 0, 14.5},
            {1.6, 2.62, 7.5, 28, 2.4, 7.5},
            {60, 5, 22.5, 28, 14, 20.5},
            {48, 8, -7, 29, 20, 8},
            {44, 6, 44, 15, 9, 28},
            {70, 50, 70, 20, 8, 24},
            {-14, 10, -6, 8, 10, 14},
            {32.5, 4.62, 22.5, 6, 6.5, 22.5},
            {15.5, 4.62, 22.5, 2, 7.5, 22.5},
            {24.5, 4.62, 24.5, 31.5, 4, 30.5},
            {26.5, 22.62, 10.5, 31, 24.5, 6},
            {5.5, 8.62, 9.5, 5.5, 8.4, 3},
            {21.5, 2.62, 39.5, 21.5, 3, 52},
            {21.5, 2.62, 47.2, 21.5, 3.6, 51.5},
            {14.3, 4.62, 28.4, 9, 4.3, 29.8},
            {44, 4, 34, 22, 0, 25},
            {20, 35, 10, 15, -4, 29},
            {27.6, 3.9, 21.6, 9, -2, 31},
            {11.5, 0.62, 26.5, 28, 2, 20},
            {16.5, 2.62, 12.5, 16.5, -2, 24},
            {8.4, 1.4, 22.4, 5.5, -0.4, 16.6},
            {2, 2.62, 28.5, 25, 1, 22},
            {26.5, 3.62, 19.5, 30.5, 2.4, 17.5},
            {14, 2.62, 47, -1, 3, 40},
            {-18, 2.62, 52, -1, 4, 41},
            {36, 2.62, 60, 30, 0, 45},
            {-8, 2.62, 47, -18, -2, 54},
            {-22, 0.62, 52.5, -2, 1, 52.5},
            {14, 2.62, 47, -1, 3, 40},
            {22.5, 6.5, 13, 13, -3, 21.5},
            {-11, 2.62, 16.5, 8, 12, 16.5},
            {-6, 2.62, 36, 15, 10, 18},
            {-9, 30, 42, 22, 6, 16},
            {55, 14, -2, 25, 8, 18},
            {15.5, 4.62, 16.5, 30, 5, 16.5},
            {18.5, 4.62, 21.5, 27, 4.5, 6},
            {30.5, 4.62, 16.5, 36.5, 3.6, 18.5},
            {31.6, 0.62, 15.6, 36.6, -0.1, 18},
            {16.6, 11.62, 16.5, 29, 13, 16.5},
            {20.4, 5.4, 24.6, 17.6, 3.6, 26.8},
            {16.5, 12.62, 16.5, 27.5, 10.2, 17},
            {22.5, 2.62, 59.5, 22.5, 20, 12},
            {9.5, 2.62, 60.5, 30, 9, 31},
            {-14, 44, 69, 22, 14, 27},
            {66, 12, 49, 30, 18, 17},
            {22.5, 5.62, 29.5, 22.5, 9, 5},
            {22.5, 5.62, 18.5, 22.5, 11, 4},
            {22.5, 8.62, 10.5, 22.5, 10, 0},
            {26.5, 8.62, 10.5, 35, 8, 12},
            {15.5, 5.62, 21.5, 10, 5, 26},
            {6.5, 5.62, 18.5, 3, 4.5, 11},
            {34.5, 16.62, 23.5, 16, 18, 23.5},
            {17.5, 3.62, 51.5, 9, 5, 47},
    };
    /** 武器工房の写真の始まり。ここで、ギルドを片づけて武器工房を置く。 */
    private static final int WEAPON_FIRST = 6;
    /** 防具工房の写真の始まり。ここで、武器工房を片づけて防具工房を置く。 */
    private static final int ARMOR_FIRST = 12;
    /** 装飾工房の写真の始まり。ここで、防具工房を片づけて装飾工房を置く。 */
    private static final int ACCESSORY_FIRST = 18;
    /** 商店街の北の写真の始まり。ここで、装飾工房を片づけて商店街の北（中央広場の縁から環状路まで）を置く。 */
    private static final int MARKET_NORTH_FIRST = 24;
    /** 寄付で開いた料理屋の写真。ここで、商店街の北の上に、開いたあとの料理屋を重ねて置く（片づけない）。 */
    private static final int MARKET_OPEN_SHOT = 30;
    /** 商店街の南の写真の始まり。ここで、北を片づけて商店街の南（環状路から南門の前まで）を置く。 */
    private static final int MARKET_SOUTH_FIRST = 31;
    /** 重ねて置く構造物（前の建物を片づけない）。 */
    private static final String MARKET_OPEN = "market_restaurant_open";
    /** 個人倉庫の写真の始まり。ここで、商店街の南を片づけてギルドを置き直し、その東に個人倉庫を重ねて置く。 */
    private static final int STORAGE_FIRST = 37;
    /** 個人倉庫の構造物。ギルドの後に置く（ギルドの東の扉の外の段と、地下の保管庫の所のギルドの土台を上書きする）。 */
    private static final String STORAGE = "personal_storage";
    /** 個人倉庫を置く所（ギルドを置いた所から）：世界の (x 22, 道の下 7, z -82) とギルドの (x 6, 道, z -100) の差。 */
    private static final int[] STORAGE_AT = {16, -7, 18};
    /** フリーマーケットの写真の始まり。ここで、ギルドと個人倉庫（地下も）を広く片づけ、フリーマーケットを置き、その東に商店街の南を並べる。 */
    private static final int FREE_FIRST = 50;
    /** フリーマーケットの構造物（はじめの形。露店12区画）。 */
    private static final String FREE = "free_market";
    /** 寄付で露店が増えたあとの写真。ここで、はじめの形の上に、増えた8区画を同じ所から重ねて置く（片づけない）。 */
    private static final int FREE_MORE_SHOT = 57;
    /** 重ねて置く構造物（増えた8区画の露店。区画の外は structure_void）。 */
    private static final String FREE_MORE = "free_market_expanded";
    /** 商店街の南を置く所（フリーマーケットを置いた所から）：世界の (x −18, 道, z 60) とフリーマーケットの (x −48, 道, z 60) の差。 */
    private static final int[] FREE_SOUTH_AT = {30, 0, 0};
    /**
     * フリーマーケットの前に片づける範囲（建物を置いた所から。東西・高さ・南北の始まりと終わり）：ギルド（0〜35、0〜37、0〜40）と、
     * その東の個人倉庫（16〜55、−7〜22、18〜40）を両方包む。
     */
    private static final int[] FREE_CLEAR = {0, -7, 0, 55, 37, 43};
    /** 結びの教会の写真の始まり。ここで、フリーマーケットと商店街の南を広く片づけ、教会を置き、その南に記憶の庭を並べる。 */
    private static final int CHURCH_FIRST = 59;
    /** 結びの教会の構造物（礼拝堂・鐘楼・療養所の棟と、教会前広場・北の庭・南の小道。46×41×38）。 */
    private static final String CHURCH = "church";
    /** 記憶の庭の構造物（25×12×18）。 */
    private static final String CHURCH_GARDEN = "church_garden";
    /** 記憶の庭を置く所（教会を置いた所から）：世界の (x −96, 道, z 16) と教会の (x −105, 道, z −22) の差。 */
    private static final int[] CHURCH_GARDEN_AT = {9, 0, 38};
    /** 記憶の相談所が開いたあとの写真。ここで、教会の上に、相談所の看板・待つ長椅子・腰かけを同じ所から重ねて置く（片づけない）。 */
    private static final int CHURCH_COUNSEL_SHOT = 71;
    /** 重ねて置く構造物（6日目に開く記憶の相談所。ほかのマスは書いていないので、教会を消さない）。 */
    private static final String CHURCH_COUNSEL = "church_counsel";
    /**
     * 教会の前に片づける範囲（建物を置いた所から）：フリーマーケット（0〜29、0〜9、0〜31）と、その東の商店街の南
     * （30〜66、0〜37、0〜43）を包み、教会（0〜45、0〜40、0〜37）と記憶の庭（9〜33、0〜11、38〜55）の所まで広げる。
     */
    private static final int[] CHURCH_CLEAR = {0, 0, 0, 66, 40, 55};
    /** 釣り堀の写真の始まり。ここで、教会と記憶の庭を広く片づけ、釣り堀・水門と湖・路地を並べて置く。 */
    private static final int FISH_FIRST = 72;
    /** 釣り堀の構造物（池・古い石の縁とアーチ・湧き口・上の台と釣り具屋・桟橋・水辺の小屋・入口の広場。36×25×44。地面は Y 9）。 */
    private static final String FISH = "fishing_pond";
    /** 水門と湖の構造物（水門の門の建物と、そのまわりの城壁・城壁沿いの道・城壁の外の湖。48×34×48）。 */
    private static final String FISH_GATE = "water_gate";
    /** 釣り堀の南東の出口から、フリーマーケットの西の門への路地の構造物（18×8×14。地面は Y 1）。 */
    private static final String FISH_ALLEY = "fishing_alley";
    /** 釣り堀を置く所：地面のブロック（Y 9）を、片づけたあとの草の高さにそろえる。 */
    private static final int[] FISH_POND_AT = {0, -9, 0};
    /** 水門と湖を置く所（釣り堀の地面の所から）：世界の (x −131, z 48) と釣り堀の (x −95, z 23) の差。高さは釣り堀と同じ。 */
    private static final int[] FISH_GATE_AT = {-36, -9, 25};
    /** 路地を置く所：世界の (x −66, z 66) と釣り堀の (x −95, z 23) の差。路地の地面は Y 1。 */
    private static final int[] FISH_ALLEY_AT = {29, -1, 43};
    /** 寄付で開いた湖畔の釣り場の写真の始まり。ここで、水門と湖の上に、開いたあとの形を同じ所から重ねて置く（片づけない）。 */
    private static final int FISH_LAKESIDE_SHOT = 83;
    /** 重ねて置く構造物（湖畔の釣り場。古い堰・水門の堰板・落とし格子を開け、岸の板の道・大会の台・長い桟橋・受付の小屋を足す）。 */
    private static final String FISH_LAKESIDE = "fishing_lakeside";
    /** 第2章の氷の写真。ここで、釣り堀の上に、凍った池の北の半分を同じ所から重ねて置く（片づけない）。 */
    private static final int FISH_FROZEN_SHOT = 86;
    /** 重ねて置く構造物（第2章の氷。氷のマスだけを書く）。 */
    private static final String FISH_FROZEN = "fishing_pond_frozen";
    /**
     * 釣り堀の前に片づける範囲（建物を置いた所から）：教会と記憶の庭（0〜66、0〜40、0〜55）を包み、釣り堀（0〜35、−9〜15、0〜43）・
     * 水門と湖（−36〜11、−9〜24、25〜72）・路地（29〜46、−1〜6、43〜56）の所まで広げる。
     */
    private static final int[] FISH_CLEAR = {-36, -9, 0, 66, 40, 72};
    /** カジノの写真の始まり。ここで、釣り堀・水門と湖・路地を広く片づけ（{@link #FISH_CLEAR}）、カジノを置く。 */
    private static final int CASINO_FIRST = 87;
    /** カジノの構造物（建物・前の小広場・北の路地・東の裏の通路・南の小道と、地下の秘密の階。43×29×36。地面は Y 3）。 */
    private static final String CASINO = "casino";
    /** カジノを置く所：地面のブロック（Y 3）を、片づけたあとの草の高さにそろえる。 */
    private static final int[] CASINO_AT = {0, -3, 0};
    /** 共同事業②の「ハイ＆ロー」の写真。ここで、カジノの上に、カードの卓を同じ所から重ねて置く（片づけない）。 */
    private static final int CASINO_HIGHLOW_SHOT = 96;
    /** 重ねて置く構造物（ハイ＆ロー。南の側廊のカードの卓の布と仕切りと札、卓のカードとチップの額縁だけを書く）。 */
    private static final String CASINO_HIGHLOW = "casino_highlow";
    /** 共同事業⑥の「魚レース」の写真。ここで、カジノの上に、宴の間の水の走路を同じ所から重ねて置く（片づけない）。 */
    private static final int CASINO_FISHRACE_SHOT = 97;
    /** 重ねて置く構造物（魚レース。宴の間の真ん中の走路と灯りと札だけを書く）。 */
    private static final String CASINO_FISHRACE = "casino_fishrace";
    /** 塔の入口の写真の始まり。ここで、カジノを広く片づけ（{@link #TOWER_CLEAR}）、塔の入口と、その南に塔前広場を置く。 */
    private static final int TOWER_FIRST = 98;
    /** 塔の入口の構造物（門の棟・広間・転移門・両翼・塔の基部の壁。45×48×30。足もとの地面の1つ上から置く）。 */
    private static final String TOWER = "tower_entrance";
    /** 塔前広場の構造物（47×18×32。地面は Y 1）。 */
    private static final String TOWER_PLAZA = "tower_plaza";
    /** 塔の入口を置く所：足もとの地面（草）の1つ上から。 */
    private static final int[] TOWER_AT = {0, 1, 0};
    /** 塔前広場を置く所：世界の (x −23, y −1, z −147) と、塔の入口の足もとの (x −22, y 0, z −177) の差。 */
    private static final int[] TOWER_PLAZA_AT = {-1, -1, 30};
    /** 1週目の共同の目標のあとの写真。ここで、塔前広場の上に、記念碑を同じ所から重ねて置く（片づけない）。 */
    private static final int TOWER_MONUMENT_SHOT = 109;
    /** 重ねて置く構造物（記念碑。台と碑と碑文の看板だけを書く）。 */
    private static final String TOWER_MONUMENT = "tower_plaza_monument";
    /** 塔の入口の前に片づける範囲（建物を置いた所から）：カジノ（0〜42、−3〜25、0〜35）を包み、塔前広場の所（−1〜45、30〜61）まで広げる。 */
    private static final int[] TOWER_CLEAR = {-1, -3, 0, 45, 25, 61};
    /** 片づける範囲（東西・高さ・南北）：いちばん大きい構造物（東西は商店街の37、南北は商店街の南の44）を包む大きさ。 */
    private static final int[] CLEAR_SIZE = {37, 38, 44};
    /**
     * 建物の見本の写真の段階の始まり（Phase 3 の写真の後に撮る）。写真1枚に2段階を使い、建物が増えると段階も増えるので、
     * ほかの段階（0〜62・100〜124・330〜331）と重ならない所から始める。
     */
    private static final int MAP_STAGE = 400;
    /** Phase 3（敵・ドロップ・装備・工房・図鑑）の写真の段階の始まり（建物の見本の前に撮る）。 */
    private static final int P3 = 100;
    /** Phase 3の工房の写真で置いた台の場所。 */
    @Nullable
    private static BlockPos workbench;
    /** Phase 3の写真の前にいた場所（撮り終えたら戻る）。 */
    @Nullable
    private static BlockPos p3Return;
    /** Phase 3の写真の床の高さ（その場所の真上の空）。 */
    private static final int P3_FLOOR = 229;
    /** 装備のデザイン（水門の鍵盾）の写真。 */
    private static final int GEAR = 140;
    /** 装備のデザインの写真の前にいた場所（撮り終えたら戻る）。 */
    @Nullable
    private static BlockPos gearReturn;
    /**
     * 街全体の写真（11月21日）の段階の始まり。建物の見本の写真の後に、確かめ用の {@code /amemaru admin city build} で
     * 11施設を地図の位置に並べて空に建て、{@code /amemaru admin city tp} の場所から撮る。
     */
    private static final int CITY = 700;
    /** 街を建てる高さ（道のブロックの高さ。空に建てる） */
    private static final int CITY_Y = 200;
    /** 街を建てた所（中央広場の真ん中） */
    @Nullable
    private static BlockPos cityOrigin;
    /** 建て終わるのを待つ：建てる前の回数と、待った回数 */
    private static int cityBuilds;
    private static int cityWaits;
    private static boolean cityDone;
    /**
     * 街全体の写真：{写真の名前, city tp の場所, 移ってから待つ tick}。場所が null の行は、写真の代わりに夜にする（night）・
     * コマンドを送る（cmd。4つめがコマンド）・はじめの形を建て直す（rebuild）・育ったあとの形を建て直す（grown）。
     * 遠くへ移ると、まわりの形を作り直すのに時間がかかる（上からと斜めは長めに待つ）。
     */
    private static final String[][] CITY_SHOTS = {
            {"city_top_n", "top_n", "200"}, {"city_top_s", "top_s", "200"},
            {"city_aerial_se", "aerial_se", "240"}, {"city_aerial_nw", "aerial_nw", "240"},
            {"city_plaza", "plaza", "100"}, {"city_guild", "guild", "100"}, {"city_storage", "storage", "100"},
            {"city_weapon", "weapon", "100"}, {"city_armor", "armor", "100"}, {"city_accessory", "accessory", "100"},
            {"city_market", "market", "100"}, {"city_free_market", "free_market", "100"}, {"city_church", "church", "100"},
            {"city_fishing", "fishing", "100"}, {"city_lake", "lake", "100"}, {"city_casino", "casino", "100"},
            {"city_tower", "tower", "100"},
            // Phase 5：街並み（家並み・南の門・倉庫地区・角の塔と城壁）
            {"city_houses", "houses", "140"}, {"city_gate_south", "gate_south", "140"}, {"city_warehouses", "warehouses", "140"},
            {"city_wall", "wall", "100"},
            // 直しの書き出しを通しで確かめる（ログで見る）。直しがなければ「新しい直しはありませんでした」（自然に変わる所を数えない）。
            // ギルドの上の空に金のブロックを置くと、書き出す前の建て直しは止まる。書き出すと、建て直しても残る。空気に戻して書き出すと、
            // 直しのファイルは消える（Modの形と同じになった直しを外す）
            {"cmd", null, "60", "amemaru admin city export"},
            {"cmd", null, "20", "setblock {guild} minecraft:gold_block"},
            {"cmd", null, "60", "amemaru admin city build at {origin}"},
            {"cmd", null, "60", "amemaru admin city export"},
            {"rebuild", null, "100"},
            {"cmd", null, "20", "execute if block {guild} minecraft:gold_block run say city_edit_kept"},
            {"cmd", null, "20", "setblock {guild} minecraft:air"},
            {"cmd", null, "60", "amemaru admin city export"},
            {"rebuild", null, "600"},
            {"night", null, "60"},
            {"city_top_n_night", "top_n", "200"}, {"city_top_s_night", "top_s", "200"}, {"city_plaza_night", "plaza", "100"},
            {"city_market_night", "market", "100"}, {"city_church_night", "church", "100"}, {"city_tower_night", "tower", "100"},
            {"city_houses_night", "houses", "140"}, {"city_gate_south_night", "gate_south", "140"},
            {"grown", null, "600"},
            {"city_grown_top_n", "top_n", "200"}, {"city_grown_top_s", "top_s", "200"},
            // 北から東・南・西へ回る（遠くへ移るほど長めに待つ）
            {"city_grown_monument", "monument", "200"}, {"city_grown_forge", "forge", "200"},
            {"city_grown_highlow", "highlow", "200"}, {"city_grown_fishrace", "fishrace", "100"},
            {"city_grown_restaurant", "restaurant", "140"}, {"city_grown_stalls", "stalls", "140"},
            {"city_grown_frozen_pond", "frozen_pond", "140"}, {"city_grown_lake", "lake", "140"},
            // Phase 4：パーティのHUDと画面（見本の様子）、起こす手がかり（見本：名前の付いた防具立てをダウンに見せる）、
            // ダウン（本当にダウンにする）・教会へ戻る（育ったあとの街の教会の呼び戻しの円）
            {"hud", null, "5", "on"},
            {"cmd", null, "20", "gamemode adventure @p"},
            {"cmd", null, "60", "amemaru admin city tp plaza @p"},
            {"party_preview", null, "10"},
            {"snap", null, "20", "phase4_party_hud"},
            {"screen", null, "20", "party"},
            {"snap", null, "10", "phase4_party_screen"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "20", "execute at @p run summon minecraft:armor_stand ~0.8 ~ ~-1.6 {CustomName:'\"Ren\"',NoGravity:1b,ShowArms:1b}"},
            {"fake_down_near", null, "10"},
            {"snap", null, "10", "phase4_revive_hint"},
            {"fake_reviving", null, "5"},
            {"snap", null, "5", "phase4_reviving"},
            {"fake_clear", null, "5"},
            {"cmd", null, "10", "kill @e[type=minecraft:armor_stand,name=Ren]"},
            {"cmd", null, "40", "amemaru admin down @p"},
            {"snap", null, "10", "phase4_down"},
            {"view", null, "10", "third"},
            {"snap", null, "10", "phase4_down_third"},
            {"view", null, "5", "first"},
            {"cmd", null, "30", "amemaru admin down @p church"},
            {"snap_keep", null, "5", "phase4_church"},
            {"party_clear", null, "5"},
            {"cmd", null, "10", "effect clear @p"},
            // ---- Phase 5：街のNPC・オープニング（その人の画面だけで流れる）・会話と名簿・職業の画面・進行の知らせ・塔の門・
            // 1階（入口・石橋・石柱・封印・石板）・ボス（戦い・予兆・初撃破）・出口の円・記憶の再生・1階を上から
            // keep_hold：ここから終わりまで、画面が閉じたら何も描かない画面を開き直す（オープニングの場面は開いている画面を閉じ、
            // 会話の画面も閉じると何もない画面になる。何もない画面になると、自動テストが写真の途中で始まり、終わるとゲームが閉じる）
            {"keep_hold", null, "5"},
            {"hud", null, "5", "on"},
            {"cmd", null, "10", "gamemode creative @p"},
            // 11月24日：ボスの「みんなで受ける一撃」は、ひとりで受けると倒れる。CIはひとりなので、印と演出だけにする
            {"cmd", null, "5", "amemaru admin boss share safe"},
            {"p5_build_floor", null, "20"},
            {"await_floor", null, "0"},
            {"cmd", null, "80", "tp @p {at:21.5,3,-78.5} -90 6"},
            {"snap", null, "40", "phase5_npc_guild"},
            // バルドは書見台の東に立つので、書見台の陰にならない南の斜めから（11月22日。広間の柱は (±8, −161)・(±8, −169) の
            // 3×3 で、台の段は x 10 まで。x 11 の列は空いている）
            {"cmd", null, "80", "tp @p {at:11.5,4,-161.5} -162 11"},
            {"snap", null, "40", "phase5_npc_bard"},
            // 11月22日：立体のモデルになったNPC（教会のセレナ・武器工房のドラン）
            {"cmd", null, "80", "tp @p {at:-95.0,4,0.5} 90 4"},
            {"snap", null, "40", "phase5_npc_serena"},
            {"cmd", null, "80", "tp @p {at:88.5,2,-12.5} 180 6"},
            {"snap", null, "40", "phase5_npc_doran"},
            {"cmd", null, "10", "gamemode adventure @p"},
            {"cmd", null, "1", "amemaru admin story opening start @p"},
            // 準備を待っている間（真っ暗に「……」）。まわりが届いてから30 tick 待って始まるので、その間に撮る
            {"await_pending", null, "0"},
            {"snap", null, "2", "phase5_opening_wait"},
            {"await_scene", null, "0"},
            // 声の字幕が出てから撮る（字幕は時計の時間で消える。CIの描画が遅いと、tick で数えた待ちの間に消えていた）
            {"await_text", null, "0"},
            {"snap", null, "10", "phase5_opening_voice"},
            // 字幕のあと暗転が明けて大結晶を見上げ（場面の190〜250 tick）、リリアが駆け寄る（250〜）
            {"snap", null, "195", "phase5_opening_awake"},
            {"snap", null, "50", "phase5_opening_lilia"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase5_dialogue"},
            {"dialogue_end", null, "10"},
            {"snap", null, "10", "phase5_dialogue_choices"},
            {"dialogue_choose", null, "20", "0"},
            {"dialogue_end", null, "10"},
            {"snap", null, "10", "phase5_dialogue_roster"},
            {"dialogue_finish", null, "30"},
            {"snap", null, "20", "phase5_job_screen"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "40", "amemaru admin floor unlock 2 in 30"},
            {"snap", null, "10", "phase5_announce"},
            // 転移の台の上の段（円の真ん中から3ブロック。円は半径2.6）から、円と奥の門を見下ろす
            {"cmd", null, "80", "tp @p {at:0.5,7,-164.5} 180 20"},
            {"snap", null, "30", "phase5_tower_gate"},
            {"cmd", null, "10", "tp @p {at:0.5,7,-167.5} 180 12"},
            {"await_screen", null, "0", "floor_select"},
            {"snap", null, "20", "phase5_floor_select"},
            {"floor_select", null, "40", "1"},
            {"snap", null, "10", "phase5_floor1_arrival"},
            {"snap", null, "130", "phase5_floor1_entrance"},
            {"cmd", null, "100", "execute in amemaru:tower run tp @p {f1:184.5,24,252.5} 165 18"},
            {"snap", null, "40", "phase5_floor1_bridge"},
            // 11月22日に濃くした1階：西の丘（崖と巻き道と洞）・群れの長・石柱の課題・北西の林・東の遺跡（仕掛け石と門）・古代の宝箱
            {"cmd", null, "80", "execute in amemaru:tower run tp @p {f1:112.5,15,262.5} 124 -8"},
            {"snap", null, "60", "phase5_floor1_hill"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f1:42.5,20,239.5} 150 0"},
            {"snap", null, "40", "phase5_floor1_hill_path"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:80.5,14,232.5} 102 6"},
            {"snap", null, "40", "phase5_floor1_cave"},
            // 台地に上がると、群れの長が出る（人が40ブロックの中に来たら。1秒ごとに見る）。石柱の陰にならない東から、出る所を見る
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:66.5,28,222.5} 101 6"},
            {"snap", null, "70", "phase5_guardian"},
            {"cmd", null, "10", "amemaru admin floor tp 1 pillar1 @p"},
            {"p5_pillar_try", null, "20", "0"},
            {"snap", null, "10", "phase5_pillar_hint"},
            {"cmd", null, "10", "execute in amemaru:tower run kill @e[type=amemaru:tsunomofu,distance=..80]"},
            {"cmd", null, "40", "amemaru admin floor tp 1 pillar1 @p"},
            {"p5_pillar", null, "20", "0"},
            {"snap", null, "10", "phase5_pillar_text"},
            {"snap", null, "70", "phase5_pillar_lit"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:112.5,17,104.5} 90 4"},
            {"snap", null, "50", "phase5_floor1_woods"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:292.5,10,195.5} -90 -6"},
            {"snap", null, "50", "phase5_floor1_ruins"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:297.5,10,179.5} 153 28"},
            {"p5_switch", null, "15", "0"},
            {"snap", null, "5", "phase5_ruins_switch"},
            {"p5_switch", null, "10", "1"},
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f1:306.5,10,195.5} -90 2"},
            {"snap", null, "30", "phase5_ruins_gate_open"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f1:336.5,10,202.5} -90 32"},
            {"p5_chest", null, "20", "2"},
            {"snap", null, "10", "phase5_chest_open"},
            {"cmd", null, "80", "amemaru admin floor tp 1 boss @p"},
            {"snap", null, "40", "phase5_boss_gate_sealed"},
            {"cmd", null, "10", "amemaru admin pillars complete 1 @p"},
            {"snap", null, "40", "phase5_boss_gate_open"},
            {"cmd", null, "30", "execute in amemaru:tower run tp @p {f1:192.5,15,107.5} 180 8"},
            {"snap", null, "40", "phase5_tablet"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f1:192.5,15,68.5} 180 4"},
            // 11月25日：戦いのはじまり（上下の黒い帯と、ボスの名前・階の名前）と、流す曲（記録に書く）
            {"snap", null, "25", "phase8_boss_intro"},
            {"p8_bossfx", null, "0", "f1"},
            {"snap", null, "50", "phase5_boss_fight"},
            // 11月22日：技ごとの当たる所。ボスを出る所（{f1:192.5,15,51.5}。南を向く）に戻し、決まった所に立って、管理者のコマンドで
            // 技を出させ、打つ前に撮る（技は見ている人の方を向いて出る）。跳んでいる途中のボスは戻しても跳び続けるので、先に踏みつけを
            // 出させて（その場で1.2秒ためる）止めてから戻す。踏みつけは、次の技を出させると取り消される。間でHPを戻す。
            // 突進の通り道の線（14南。前は自然に出た最初の予兆を撮っていたが、大跳躍のときは円が足もとに出て写らない）
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,65.5} 180 10"},
            {"cmd", null, "0", "amemaru admin boss skill 1 charge"},
            {"await_mark", null, "2", "phase5_boss_telegraph"},
            // 薙ぎ払いの扇（5南。届く長さ5.5）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,56.5} 180 20"},
            {"cmd", null, "0", "amemaru admin boss skill 1 sweep"},
            {"await_mark", null, "5", "phase5_boss_sweep"},
            // 角の突き上げの帯（9南。帯は長さ7）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,60.5} 180 14"},
            // 突き上げの予兆は0.6秒。CIの機械では1行に1秒ほどかかることがあるので、帯が見えたらすぐ撮る
            {"cmd", null, "0", "amemaru admin boss skill 1 upthrust"},
            {"await_mark", null, "2", "phase5_boss_upthrust"},
            // 後ろ蹴りの後ろの扇（ボスの後ろ＝4北に立ち、南のボスを見る）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,47.5} 0 22"},
            // 後ろ蹴りの予兆は0.7秒。扇が見えたらすぐ撮る
            {"cmd", null, "0", "amemaru admin boss skill 1 kick"},
            {"await_mark", null, "5", "phase5_boss_kick"},
            // 大跳躍の降りる円（12南。円は立っている所に出るので、下を向いて前の半分とボスを写す）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,63.5} 180 40"},
            {"cmd", null, "12", "amemaru admin boss skill 1 leap"},
            {"snap", null, "0", "phase5_boss_leap"},
            // 根の牙の足もとの円（10南。降りてくるのを待ってから。真下に近い向きで円を写す）
            {"cmd", null, "60", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "amemaru admin boss skill 1 stomp"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @e[type=amemaru:veldhorn,limit=1] {f1:192.5,15,51.5} 0 0"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,61.5} 180 55"},
            {"cmd", null, "10", "amemaru admin boss skill 1 roots"},
            {"snap", null, "0", "phase5_boss_roots"},
            // 11月25日：固有のギミック「大地の咆哮」（角を突き立てた構え・部屋じゅうの赤い円・止めるまでの残り）。
            // みんなで受ける一撃（11月24日）は、レイドのボスだけになった
            {"cmd", null, "50", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f1:192.5,15,63.5} 180 25"},
            {"cmd", null, "0", "amemaru admin boss skill 1 earth_roar"},
            {"snap", null, "40", "phase8_f1_boss_earth_roar"},
            // 11月25日：ギミックの残り3秒を切ると、画面のふちが濃く脈打つ（時を刻む音）
            {"snap", null, "105", "phase8_boss_danger"},
            {"cmd", null, "95", "amemaru admin debug vitals @a 100 100"},
            // 11月25日：段階が変わる（HP45%：ボスから光の波と柱・白い光・「第2段階」）と、追いこんだ（HP25%：字と赤い気配）
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f1:192.5,15,66.5} 180 12"},
            {"cmd", null, "0", "amemaru admin boss hp 1 45"},
            {"snap", null, "6", "phase8_boss_phase"},
            {"cmd", null, "60", "amemaru admin boss hp 1 25"},
            {"snap", null, "12", "phase8_boss_intense"},
            {"p8_bossfx", null, "0", "f1_intense"},
            // 自分のHPが少ない（心臓の音と、画面のふちの赤い脈。HPが一気に減ったので、揺れと赤い光も）
            {"cmd", null, "20", "amemaru admin debug vitals @a 20 100"},
            {"snap", null, "12", "phase8_boss_low_hp"},
            {"cmd", null, "20", "amemaru admin debug vitals @a 100 100"},
            // 戦っている人のまま（アドベンチャー。クリエイティブにすると戦いから外れる）倒す。
            // 倒してすぐ：ボスの名前の大きな字（下に出口の円の知らせ）。初撃破の場面がHUDを隠す（50 tick 待って白く光り、125〜215 tick に
            // 「始まりの草原　踏破」、215〜295 tick に字幕）。場面が終わると、初撃破の知らせの帯が出る（演出の間に届いた知らせは、演出の後に出す）
            {"cmd", null, "5", "kill @e[type=amemaru:veldhorn]"},
            {"snap_keep", null, "30", "phase5_boss_defeated"},
            {"snap_keep", null, "120", "phase5_boss_title"},
            {"await_idle", null, "0"},
            {"snap_keep", null, "15", "phase5_boss_first_kill"},
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f1:192.5,15,38.5} 180 10"},
            {"snap", null, "70", "phase5_return_gate"},
            {"p5_replay", null, "20"},
            {"snap", null, "10", "phase5_replay"},
            {"screen", null, "10", "hold"},
            // 11月22日：「目覚め」を見返すと、そのときのリリアの会話が開く（直す前は、ギルドでのあいさつになっていた）
            {"cmd", null, "10", "amemaru admin story replay amemaru:ch1/opening @p"},
            {"await_dialogue", null, "0"},
            {"snap", null, "40", "phase5_replay_opening"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "10", "gamemode spectator @p"},
            // 1階を上から：CIの描く距離は短いので、階の外から全体ではなく、入口の上から北（2本目の小川と石橋・古代の道）と、
            // 東の遺跡の上から（奥の院と庭と回廊）を撮る（11月22日。前の階の外からの写真は、空しか写っていなかった）
            // 11月22日（run 133）：ここでは街（ボスの後に帰った）から塔へ、別の世界をまたいで移る。そのテレポートでは向きが
            // 反映されず、南（階の端の格子）を水平に見ていた。先に塔へ移って世界を読み込ませ、同じ世界の中でもう一度
            // テレポートして向きを決める（同じ世界の中の移動なら向きは反映される。遺跡の上からの写真はそうなっていた）
            {"cmd", null, "100", "execute in amemaru:tower run tp @p {f1:192.5,44,372.5} 180 34"},
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f1:192.5,44,372.5} 180 34"},
            {"hud", null, "5", "off"},
            {"snap", null, "200", "phase5_floor1_aerial"},
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f1:318.5,46,244.5} 180 46"},
            {"snap", null, "160", "phase5_floor1_ruins_aerial"},
            // Phase 6（11月22日）：街へ戻る（世界をまたぐテレポートは向きが反映されないので、同じ世界の中でもう一度）。
            // 雑貨屋（マルタ・ポム）の呼び鈴の前で、店の画面を開き、回復薬（小）を2つ買う
            {"cmd", null, "5", "gamemode adventure @p"},
            {"hud", null, "5", "on"},
            {"cmd", null, "100", "execute in minecraft:overworld run tp @p {at:7.5,2,28.5} -90 12"},
            {"cmd", null, "10", "tp @p {at:7.5,2,28.5} -90 12"},
            {"snap", null, "40", "phase6_general_store"},
            {"cmd", null, "5", "amemaru admin money @p set 5000"},
            {"p6_shop", null, "20"},
            {"snap", null, "20", "phase6_shop"},
            {"p6_shop_buy", null, "20"},
            {"snap", null, "10", "phase6_shop_bought"},
            {"screen", null, "10", "hold"},
            // 11月23日の直し：布と仕立ての机（ほかの店と同じ、1段低い天板と呼び鈴）と、見た目の服の店の画面・薬屋の画面
            {"cmd", null, "40", "tp @p {at:7.5,2,50.5} -90 14"},
            {"snap", null, "40", "fix_cloth_counter"},
            {"p6_shop_at", null, "20", "cloth", "9", "3", "50"},
            {"snap", null, "20", "fix_cloth_shop"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "40", "tp @p {at:-7.5,2,50.5} 90 14"},
            {"p6_shop_at", null, "20", "apothecary", "-9", "3", "50"},
            {"snap", null, "20", "fix_apothecary_shop"},
            {"screen", null, "10", "hold"},
            // フリーマーケット：2日目に開く。市場の手帳を持ち、見本の出品と露店を置いて、取引所を開く
            {"cmd", null, "20", "amemaru admin day set 2"},
            {"cmd", null, "5", "give @p amemaru:market_notebook"},
            {"p6_market_seed", null, "20"},
            {"p6_market", null, "20", "NOTEBOOK"},
            {"snap", null, "30", "phase6_market_browse"},
            {"p6_market_tab", null, "20", "3"},
            {"snap", null, "20", "phase6_market_history"},
            {"screen", null, "10", "hold"},
            {"p6_market", null, "20", "BOOTH"},
            {"p6_market_tab", null, "20", "4"},
            {"snap", null, "20", "phase6_market_stalls"},
            {"screen", null, "10", "hold"},
            {"p6_market", null, "20", "STALL", "2"},
            {"snap", null, "30", "phase6_market_stall_screen"},
            {"screen", null, "10", "hold"},
            // 露店（区画2「蒼の小物屋」）を、通路の上から：屋根の上に店名、台の上に出品した品が浮かぶ
            {"cmd", null, "5", "gamemode spectator @p"},
            {"cmd", null, "60", "tp @p {at:-23.5,5,74.5} 0 18"},
            {"snap", null, "40", "phase6_stall"},
            // 閉じた南門と東門（城壁の内から）。11月22日に決定
            {"cmd", null, "60", "tp @p {at:0.5,3,99.5} 0 4"},
            {"snap", null, "60", "phase6_south_gate"},
            {"cmd", null, "60", "tp @p {at:99.5,3,0.5} -90 4"},
            {"snap", null, "60", "phase6_east_gate"},
            // 塔前広場と塔の入口のまわりの塀（上から）。11月22日に決定
            {"hud", null, "5", "off"},
            {"cmd", null, "80", "tp @p {at:44.5,46,-104.5} 140 38"},
            {"snap", null, "120", "phase6_tower_walls"},
            // Phase 6 その2（11月22日）：4つの職業。広場で、訓練人形に向けてスキルを使い、HUD（ゲージ）とスキルツリーを撮る
            {"cmd", null, "5", "gamemode creative @p"},
            {"hud", null, "5", "on"},
            {"cmd", null, "80", "amemaru admin city tp plaza @p"},
            {"cmd", null, "5", "kill @e[type=amemaru:training_dummy]"},
            {"cmd", null, "20", "execute as @p at @p run amemaru admin dummy 20"},
            // ヒーラー：癒しの鈴（自分を癒す）と、加護の灯
            {"cmd", null, "5", "amemaru admin job set @a amemaru:healer"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_bell_staff"},
            {"cmd", null, "5", "item replace entity @a weapon.offhand with minecraft:air"},
            {"cmd", null, "5", "amemaru admin skill learn @a amemaru:healer/blessing_light 1"},
            {"cmd", null, "5", "amemaru admin skill learn @a amemaru:healer/healing_rain 1"},
            {"view", null, "5", "third"},
            {"p6_use", null, "6", "0"},
            {"p6_use", null, "6", "2"},
            {"snap", null, "6", "phase6_healer_skills"},
            {"p6_tree", null, "10", "healer/great_call"},
            {"snap", null, "10", "phase6_healer_tree"},
            {"screen", null, "5", "hold"},
            {"p6_jobs", null, "20"},
            {"snap", null, "10", "phase6_job_select"},
            {"screen", null, "5", "hold"},
            // アーチャー：弓で射る・三連矢（集中のゲージ）
            {"cmd", null, "5", "amemaru admin job set @a amemaru:archer"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_bow"},
            {"cmd", null, "5", "amemaru admin skill learn @a amemaru:archer/triple_shot 1"},
            {"cmd", null, "5", "amemaru admin skill gauge @a 60"},
            {"p6_shoot", null, "3"},
            {"p6_use", null, "4", "2"},
            {"snap", null, "4", "phase6_archer_skills"},
            {"p6_tree", null, "10", "archer/shooting_star"},
            {"snap", null, "10", "phase6_archer_tree"},
            {"screen", null, "5", "hold"},
            // ローグ：乱れ刃と煙玉（気配のゲージは粒で）
            {"cmd", null, "5", "amemaru admin job set @a amemaru:rogue"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_daggers"},
            {"cmd", null, "5", "amemaru admin skill learn @a amemaru:rogue/smoke_bomb 1"},
            {"cmd", null, "5", "amemaru admin skill gauge @a 3"},
            {"p6_use", null, "6", "0"},
            {"p6_use", null, "8", "2"},
            {"snap", null, "6", "phase6_rogue_skills"},
            {"p6_tree", null, "10", "rogue/thousand_blades"},
            {"snap", null, "10", "phase6_rogue_tree"},
            {"screen", null, "5", "hold"},
            // モンク：連打→掌打で、連撃2（HUD の「連撃 2」と、残り時間の帯）
            {"cmd", null, "5", "amemaru admin job set @a amemaru:monk"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_gauntlets"},
            {"cmd", null, "5", "amemaru admin skill gauge @a 40"},
            {"p6_use", null, "6", "0"},
            {"p6_use", null, "4", "1"},
            {"snap", null, "4", "phase6_monk_combo"},
            {"p6_tree", null, "10", "monk/hundred_bond"},
            {"snap", null, "10", "phase6_monk_tree"},
            {"screen", null, "5", "hold"},
            // 古代石シリーズ（軽装）を着て、正面から
            {"cmd", null, "5", "item replace entity @a armor.head with amemaru:ancient_stone_circlet"},
            {"cmd", null, "5", "item replace entity @a armor.chest with amemaru:ancient_stone_jerkin"},
            {"cmd", null, "5", "item replace entity @a armor.legs with amemaru:ancient_stone_breeches"},
            {"cmd", null, "5", "item replace entity @a armor.feet with amemaru:ancient_stone_shoes"},
            // 正面のカメラと自分の間に的が入らないよう、先に的を片づける
            {"cmd", null, "5", "kill @e[type=amemaru:training_dummy]"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase6_light_armor"},
            {"view", null, "5", "first"},
            // 11月23日：籠手は両手にはめる・双短剣は両手に持つ（一人称と正面）。弓は弦が自分の側（一人称と正面）
            {"snap", null, "10", "phase6_monk_first"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_daggers"},
            {"snap", null, "15", "phase6_rogue_first"},
            {"p6_front", null, "10"},
            {"snap", null, "15", "phase6_rogue_front"},
            {"cmd", null, "5", "item replace entity @a weapon.mainhand with amemaru:apprentice_bow"},
            {"snap", null, "15", "phase6_archer_front"},
            {"view", null, "5", "first"},
            {"snap", null, "15", "phase6_archer_first"},
            // Phase 6 その3（11月22日）：ギルド。4日目にして（ノアが話す・エルネストがいる）、NPCを置き直す
            {"cmd", null, "10", "amemaru admin day set 4"},
            // Phase 8 その2：エルネストは3階の記録を持ち帰ると来る（ここでは、来たことにする）
            {"cmd", null, "5", "amemaru admin flag add amemaru:story/ernesto_arrived"},
            {"cmd", null, "20", "amemaru admin npc place"},
            // ノア：塔前広場の石柱の陰（2日目から。4日目から話す）
            {"cmd", null, "80", "tp @p {at:16.5,3,-133.5} 180 8"},
            {"snap", null, "40", "phase6g_npc_noa"},
            {"p6g_talk", null, "10", "noa"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase6g_noa_dialogue"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            // エルネスト：ギルドの2階の資料室（3階の記録を持ち帰ると来る）。初めて会うと、記録帳を渡す
            {"cmd", null, "80", "tp @p {at:30.5,10,-66.5} -90 8"},
            {"snap", null, "40", "phase6g_npc_ernesto"},
            {"p6g_talk", null, "10", "ernesto"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase6g_ernesto_dialogue"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            // 記録帳：1階の石板（解読済み）と2階の日誌（未解読）
            {"cmd", null, "5", "amemaru admin record find @p amemaru:f01_waystone"},
            {"cmd", null, "5", "amemaru admin record decipher @p"},
            {"cmd", null, "5", "amemaru admin record find @p amemaru:f02_forester"},
            {"p6g_book", null, "20"},
            {"snap", null, "20", "phase6g_record_book"},
            {"p6g_book_read", null, "10", "amemaru:f01_waystone"},
            {"snap", null, "10", "phase6g_record_read"},
            {"screen", null, "10", "hold"},
            // 称号：いくつか与えて（知らせの札）、限定称号を表示する。称号の一覧
            {"cmd", null, "5", "amemaru admin title grant @p amemaru:hunter_1"},
            {"cmd", null, "5", "amemaru admin title grant @p amemaru:trader_3"},
            {"cmd", null, "5", "amemaru admin title grant @p amemaru:first_step"},
            // 右上の札（称号の知らせ）を消さずに撮る
            {"snap_keep", null, "20", "phase6g_title_toast"},
            {"cmd", null, "5", "amemaru admin title show @p amemaru:first_step"},
            {"p6g_titles", null, "20"},
            {"snap", null, "20", "phase6g_titles"},
            {"screen", null, "10", "hold"},
            // ギルドの窓口（1階の受付の前）：クエスト・パーティ募集・冒険日誌・縁
            {"cmd", null, "80", "tp @p {at:21.5,3,-78.5} -90 6"},
            {"cmd", null, "5", "amemaru admin quest start @p amemaru:guild_first_floor"},
            {"cmd", null, "5", "amemaru admin bond set @p amemaru:lilia 90"},
            {"cmd", null, "5", "amemaru admin bond set @p amemaru:ernesto 35"},
            {"cmd", null, "5", "amemaru admin quest open @p quests"},
            {"snap", null, "20", "phase6g_guild_quests"},
            {"p6g_tab", null, "10", "party"},
            {"snap", null, "10", "phase6g_guild_party"},
            {"p6g_tab", null, "10", "chronicle"},
            {"snap", null, "10", "phase6g_guild_chronicle"},
            {"p6g_tab", null, "10", "bonds"},
            {"snap", null, "10", "phase6g_guild_bonds"},
            {"screen", null, "10", "hold"},
            // 受けているクエストの一覧（右上）と、露店の区画の寄付の画面
            {"snap", null, "20", "phase6g_quest_tracker"},
            {"p6g_donation", null, "20"},
            {"snap", null, "10", "phase6g_donation"},
            {"screen", null, "10", "hold"},
            // Phase 6 その4（11月23日）：厳選と決闘。装飾工房のカウンターの作業台で、品質82%・サブ4つの闘環を厳選する。装飾師（熟練度5）
            {"cmd", null, "80", "tp @p {at:27.5,2,-11.5} 180 35"},
            {"cmd", null, "5", "clear @p"},
            {"cmd", null, "5", "amemaru admin production set @p accessory 5"},
            {"cmd", null, "5", "amemaru admin accessory give @p amemaru:moss_iron_battle_ring 4 82"},
            {"cmd", null, "5", "give @p amemaru:echo_fragment 40"},
            {"cmd", null, "5", "give @p amemaru:wavering_crystal 4"},
            {"cmd", null, "5", "give @p amemaru:binding_thread 8"},
            {"cmd", null, "5", "give @p amemaru:polishing_powder 30"},
            {"cmd", null, "5", "amemaru admin money @p set 60000"},
            {"p6r_bench", null, "10"},
            // 11月23日：作業台の新しい見た目（カウンターの上の台を、上から）
            {"snap", null, "20", "phase6r_bench"},
            {"screen", null, "5", "hold"},
            {"p6r_open", null, "15"},
            // 11月23日：作るタブ（作る品の一覧・台・説明の文が重ならない）。台に置く前に撮る
            {"p6r_tab", null, "10", "0"},
            {"snap", null, "15", "phase6r_craft"},
            {"p6r_place", null, "10"},
            {"p6r_tab", null, "15", "1"},
            {"snap", null, "15", "phase6r_refine"},
            {"p6r_lock", null, "10", "0"},
            {"p6r_reroll", null, "10", "values"},
            {"snap", null, "8", "phase6r_refine_locked"},
            {"p6r_tab", null, "10", "2"},
            {"snap", null, "10", "phase6r_enhance"},
            {"p6r_tab", null, "10", "3"},
            {"p6r_confirm", null, "5"},
            {"snap", null, "5", "phase6r_dismantle"},
            {"p6r_tab", null, "10", "4"},
            {"snap", null, "10", "phase6r_commission_tab"},
            {"p6r_commission", null, "20"},
            {"p6r_ctab", null, "20", "create"},
            {"snap", null, "20", "phase6r_commission_create"},
            {"p6r_create", null, "20"},
            {"p6r_ctab", null, "20", "mine"},
            {"snap", null, "10", "phase6r_commission_mine"},
            {"screen", null, "10", "hold"},
            // 決闘：決闘の画面と、決闘場（観客席から・上から）
            {"p6r_duel", null, "20"},
            {"snap", null, "10", "phase6r_duel_screen"},
            {"screen", null, "10", "hold"},
            // 決闘場へ行くコマンドは、プレイヤーとして打つ（サーバーとして打つと「プレイヤーが要る」で止まる）。別の世界をまたぐ
            // テレポートでは向きが反映されないので、塔の空間に移ってから、もう一度同じ所へ移る
            {"cmd", null, "80", "execute as @p at @p run amemaru admin duel arena 0"},
            {"cmd", null, "40", "execute as @p at @p run amemaru admin duel arena 0"},
            {"snap", null, "40", "phase6r_arena"},
            {"cmd", null, "60", "execute as @p at @p run amemaru admin duel arena 0 above"},
            {"snap", null, "40", "phase6r_arena_above"},
            // Phase 7 その1（11月23日）：コネクション量と大結晶の輝き。中央広場の大結晶の台の西、「記憶の石」の前で
            // （決闘場から、別の世界をまたいで戻る。向きは同じ世界の中でもう一度テレポートして決める）
            {"cmd", null, "5", "gamemode creative @p"},
            {"hud", null, "5", "on"},
            {"cmd", null, "5", "amemaru admin connection reset"},
            {"cmd", null, "100", "execute in minecraft:overworld run tp @p {at:-11.5,1,0.5} -90 -6"},
            {"cmd", null, "10", "tp @p {at:-11.5,1,0.5} -90 -6"},
            // 今日のCPの見本（いくつかの行動をしたことにする）と、サーバー全体の累計
            {"cmd", null, "5", "amemaru admin connection award @p amemaru:story_quest 2"},
            {"cmd", null, "5", "amemaru admin connection award @p amemaru:link_combo 12"},
            {"cmd", null, "5", "amemaru admin connection award @p amemaru:npc_request 3"},
            {"cmd", null, "5", "amemaru admin connection award @p amemaru:market_trade 1"},
            {"cmd", null, "5", "amemaru admin connection add 1200"},
            {"snap", null, "30", "phase7_memory_stone"},
            {"p7_stone", null, "20"},
            {"snap", null, "20", "phase7_glow_mine"},
            {"p7_glow_tab", null, "10", "everyone"},
            {"snap", null, "10", "phase7_glow_everyone"},
            {"screen", null, "10", "hold"},
            // 段階が上がる：大結晶の一言（画面の真ん中）と、新しい効果の知らせ
            {"cmd", null, "5", "amemaru admin connection stage 3"},
            {"snap_keep", null, "30", "phase7_stage_up"},
            {"p7_glow", null, "60"},
            {"snap", null, "20", "phase7_glow_stage3"},
            {"screen", null, "10", "hold"},
            // 夜の大結晶（段階に合わせた光の粒）と、段階2から塔前広場のふちに立つ光の幕（結界が広がった）
            {"cmd", null, "5", "time set 18000"},
            {"cmd", null, "5", "gamemode spectator @p"},
            {"hud", null, "5", "off"},
            // 中央広場の中（半径22.5）の西南西から、大結晶を見上げる
            {"cmd", null, "40", "tp @p {at:-16.5,3,7.5} -114 -28"},
            {"snap", null, "80", "phase7_crystal_glow"},
            // 北門の内の空から、北の塔前広場を見下ろす（城壁と門の塔より高く）
            {"cmd", null, "60", "tp @p {at:0.5,30,-96.5} 180 40"},
            {"snap", null, "100", "phase7_plaza_barrier"},
            {"cmd", null, "5", "time set noon"},
            // 縁帳から結び札を贈る（見本の縁を3人。いない人へは、次に入ったときに届く）
            {"cmd", null, "5", "gamemode creative @p"},
            {"hud", null, "5", "on"},
            {"cmd", null, "5", "amemaru admin connection sample @p Ren"},
            {"cmd", null, "5", "amemaru admin connection sample @p Mio"},
            {"cmd", null, "5", "amemaru admin connection sample @p Kaito"},
            {"p7_book", null, "20"},
            {"snap", null, "20", "phase7_book_thanks"},
            {"p7_thanks", null, "30", "0"},
            {"snap", null, "20", "phase7_book_thanked"},
            {"screen", null, "10", "hold"},
            // 11月23日：3つの作業台の新しい見た目（空に置いて、斜め上から）と、ホットバーの作業台
            {"cmd", null, "5", "execute positioned {origin} run fill ~-4 ~60 ~36 ~4 ~60 ~44 minecraft:smooth_stone"},
            {"cmd", null, "5", "execute positioned {origin} run setblock ~-2 ~61 ~40 amemaru:weapon_workbench[facing=south]"},
            {"cmd", null, "5", "execute positioned {origin} run setblock ~0 ~61 ~40 amemaru:armor_workbench[facing=south]"},
            {"cmd", null, "5", "execute positioned {origin} run setblock ~2 ~61 ~40 amemaru:accessory_workbench[facing=south]"},
            {"cmd", null, "5", "clear @p"},
            {"cmd", null, "5", "item replace entity @p hotbar.0 with amemaru:weapon_workbench"},
            {"cmd", null, "5", "item replace entity @p hotbar.1 with amemaru:armor_workbench"},
            {"cmd", null, "5", "item replace entity @p hotbar.2 with amemaru:accessory_workbench"},
            {"cmd", null, "40", "tp @p {at:0.5,61,44.5} 180 22"},
            {"snap", null, "40", "phase7_workbenches"},
            // Phase 7 その2（11月23日）：釣り。釣り堀のポッカ爺の釣り具屋（カウンターの前から、東の店の中のポッカ爺を見る）
            {"cmd", null, "20", "amemaru admin npc place"},
            {"cmd", null, "100", "tp @p {at:-68.5,2,40.5} -90 4"},
            {"snap", null, "60", "phase7f_pokka"},
            {"p6g_talk", null, "10", "pokka"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase7f_dialogue"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            // 何匹か釣ったことにして（図鑑・大物・記憶魚）、売る魚も持たせて、釣り具屋の画面のタブを順に
            {"cmd", null, "5", "clear @p"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:pebble_dace"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:moss_crucian"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:silver_stripe"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:crystal_trout"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:amber_carp 78.4"},
            {"cmd", null, "5", "amemaru admin fishing catch @p amemaru:memory_dawn"},
            {"cmd", null, "5", "give @p amemaru:fish_pebble_dace 7"},
            {"cmd", null, "5", "give @p amemaru:fish_moss_crucian 4"},
            {"cmd", null, "20", "amemaru admin fishing open @p dex"},
            {"p7f_select", null, "10", "6"},
            {"snap", null, "20", "phase7f_dex"},
            {"p7f_select", null, "5", "9"},
            {"snap", null, "10", "phase7f_dex_memory"},
            {"p7f_tab", null, "10", "sell"},
            {"snap", null, "10", "phase7f_sell"},
            {"p7f_tab", null, "10", "trade"},
            {"snap", null, "10", "phase7f_trade"},
            {"screen", null, "10", "hold"},
            // 糸の張りのミニゲーム（写真のために、張りを帯の真ん中に保つよう自動で巻く）
            {"p7f_auto", null, "5", "on"},
            {"cmd", null, "10", "amemaru admin fishing hook @p amemaru:amber_carp"},
            {"snap_keep", null, "25", "phase7f_minigame"},
            {"snap_keep", null, "90", "phase7f_caught"},
            {"p7f_auto", null, "5", "off"},
            // 釣り大会（会場の釣り堀で。始まるまでの秒は0）
            {"cmd", null, "10", "amemaru admin fishing tournament start 3 0"},
            {"cmd", null, "40", "amemaru admin fishing catch @p amemaru:amber_carp 71.0"},
            {"snap_keep", null, "30", "phase7f_tournament_hud"},
            {"cmd", null, "10", "amemaru admin fishing open @p tournament"},
            {"snap", null, "20", "phase7f_tournament_tab"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "10", "amemaru admin event stop fishing_tournament"},
            {"snap_keep", null, "40", "phase7f_tournament_end"},
            // Phase 7 その3（11月23日）：カジノ。大広間のルーレットの卓の西から、卓と支配人ジゼルを見る（進行日は Phase 6 で 4日目）
            {"cmd", null, "100", "tp @p {at:22.5,3,84.5} -90 10"},
            {"snap", null, "60", "phase7c_hall"},
            // 話せるのは5ブロックまでなので、ジゼルの南（卓の東）へ寄る
            {"cmd", null, "40", "tp @p {at:31.5,3,88.0} 180 5"},
            {"p6g_talk", null, "10", "gisele"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase7c_dialogue"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            // ルーレットの卓を右クリックすると、カジノの画面が開く。チップを持たせて、一点・赤・ダズンに賭けて、17で回す
            {"cmd", null, "5", "amemaru admin casino chips @p 500"},
            {"cmd", null, "40", "tp @p {at:22.5,3,84.5} -90 10"},
            {"p7c_use", null, "30"},
            {"snap", null, "20", "phase7c_roulette"},
            {"p7c_chip", null, "5", "50"},
            {"p7c_bet", null, "10", "number:17"},
            {"p7c_bet", null, "10", "red:0"},
            {"p7c_chip", null, "5", "10"},
            {"p7c_bet", null, "10", "dozen:2"},
            {"snap", null, "20", "phase7c_roulette_bets"},
            {"cmd", null, "5", "amemaru admin casino next roulette 17"},
            {"cmd", null, "5", "amemaru admin casino spin roulette"},
            {"snap_keep", null, "45", "phase7c_roulette_spin"},
            {"snap_keep", null, "100", "phase7c_roulette_result"},
            {"p7c_tab", null, "10", "cashier"},
            {"snap", null, "20", "phase7c_cashier"},
            {"p7c_tab", null, "10", "prizes"},
            {"snap", null, "20", "phase7c_prizes"},
            // ハイ＆ロー（共同事業②の印のあと）：今のカードを6にして、ハイに賭け、次は J
            {"cmd", null, "5", "amemaru admin casino unlock highlow true"},
            {"cmd", null, "20", "amemaru admin casino open @p highlow"},
            {"cmd", null, "20", "amemaru admin casino card highlow_a 6"},
            {"snap", null, "20", "phase7c_highlow"},
            {"p7c_bet", null, "10", "high:0"},
            {"cmd", null, "5", "amemaru admin casino next highlow_a 11"},
            {"cmd", null, "5", "amemaru admin casino spin highlow_a"},
            {"snap_keep", null, "150", "phase7c_highlow_result"},
            // 魚レース（共同事業⑥の印のあと）：持っている魚を出して、2番に賭ける
            {"cmd", null, "5", "amemaru admin casino unlock fishrace true"},
            {"cmd", null, "20", "amemaru admin casino open @p fishrace"},
            {"p7c_enter", null, "20"},
            {"snap", null, "20", "phase7c_fishrace"},
            {"p7c_bet", null, "10", "lane:1"},
            {"cmd", null, "5", "amemaru admin casino next fishrace 2"},
            {"cmd", null, "5", "amemaru admin casino spin fishrace"},
            {"snap_keep", null, "60", "phase7c_fishrace_run"},
            {"snap_keep", null, "100", "phase7c_fishrace_result"},
            // カジノナイト（催しのコマンド）
            {"screen", null, "10", "hold"},
            {"cmd", null, "10", "amemaru admin event start casino_night 5"},
            {"snap_keep", null, "30", "phase7c_night"},
            {"cmd", null, "10", "amemaru admin event stop casino_night"},
            // Phase 7 その4（11月23日）：寄付と共同事業。中央広場の南東の寄付の台座（進行日は4日目なので、①湖畔の釣り場の受付中）
            {"cmd", null, "5", "amemaru admin money @p set 50000"},
            {"cmd", null, "100", "tp @p {at:12.5,1,12.5} -45 30"},
            {"snap", null, "60", "phase7p_stand"},
            {"p7p_use", null, "30"},
            {"snap", null, "20", "phase7p_screen"},
            {"p7p_donate", null, "20", "1000"},
            {"snap", null, "20", "phase7p_donated"},
            {"screen", null, "10", "hold"},
            // ①が完成して、湖畔の釣り場ができる（管理者が完成にして、すぐ施設を変える。ふつうは完成した次の朝）
            {"cmd", null, "100", "tp @p {at:-99.5,1,88.5} 140 15"},
            {"cmd", null, "40", "amemaru admin project complete amemaru:lakeside"},
            {"cmd", null, "5", "amemaru admin project build amemaru:lakeside"},
            {"snap_keep", null, "50", "phase7p_lakeside"},
            // ②ハイ＆ロー：カジノの南の側廊のカードの卓
            {"cmd", null, "100", "tp @p {at:22.5,3,92.5} 0 20"},
            {"cmd", null, "40", "amemaru admin project build amemaru:highlow"},
            {"snap_keep", null, "50", "phase7p_highlow"},
            {"cmd", null, "20", "amemaru admin project open @p amemaru:highlow"},
            {"snap", null, "20", "phase7p_screen_after"},
            {"screen", null, "10", "hold"},
            // ③料理屋の開店：中央広場の西の角の料理屋が開き、ペトラ・ポムが現れる。魚と野菜でスープを作ってもらう
            {"cmd", null, "100", "tp @p {at:2.5,1,28.5} 90 -5"},
            {"cmd", null, "40", "amemaru admin project build amemaru:restaurant"},
            {"snap_keep", null, "50", "phase7p_restaurant"},
            {"cmd", null, "40", "tp @p {at:-6.5,2,28.5} 90 5"},
            {"p6g_talk", null, "20", "petra"},
            {"await_dialogue", null, "0"},
            {"snap", null, "30", "phase7p_petra"},
            {"dialogue_finish", null, "10"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "5", "give @p minecraft:potato 2"},
            {"cmd", null, "5", "give @p minecraft:carrot 2"},
            {"cmd", null, "20", "amemaru admin project restaurant @p"},
            {"snap", null, "20", "phase7p_menu"},
            {"p7p_cook", null, "20", "1"},
            {"snap", null, "20", "phase7p_cooked"},
            {"screen", null, "10", "hold"},
            // ---- Phase 8 その1：2階「忘れられた森」。階を建て、霧の入口・森番の広場・迷いの森（鐘・こだま・結び目の灯）・
            // 2階の敵・胞子の窪地・白い木立（霧の奥の少女）・管の谷・封印の門・森番の古樹（節・技・怯み・暗い森）・古樹の枝杖
            {"cmd", null, "10", "gamemode creative @p"},
            {"p5_build_floor", null, "20", "2"},
            {"await_floor", null, "0"},
            {"cmd", null, "10", "gamemode adventure @p"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "100", "amemaru admin floor tp 2 entrance @p"},
            {"cmd", null, "10", "amemaru admin floor tp 2 entrance @p"},
            {"snap", null, "120", "phase8_f2_entrance"},
            {"cmd", null, "60", "amemaru admin floor tp 2 waypoint1 @p"},
            {"snap", null, "80", "phase8_f2_hub"},
            // 2階の敵（広場の石だたみに、動かないように並べる。北に並べて、南から見る）
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:mayoi_bi {f2:208.5,20.5,250.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:juhihei {f2:212.5,19,250.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:pokodake {f2:217.5,19,250.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:hakage_kitsune {f2:221.5,19,250.5} {NoAI:1b,Rotation:[20f,0f]}"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f2:215.5,20,258.5} 180 10"},
            {"snap", null, "40", "phase8_f2_enemies"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:mayoi_bi,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:juhihei,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:pokodake,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:hakage_kitsune,distance=..30]"},
            // 迷いの森・奥：鐘を鳴らし、出口の方へ離れてこだまを聞き、正しい灯台に結び目の灯を置く
            {"cmd", null, "40", "amemaru admin floor woods 2 reset"},
            {"cmd", null, "60", "amemaru admin floor tp 2 woods_north @p"},
            {"snap", null, "60", "phase8_f2_woods"},
            {"p8_bell", null, "10", "north"},
            {"snap", null, "10", "phase8_f2_bell"},
            {"p8_listen", null, "100", "north"},
            // こだまはチャットに出るので、チャットを消さずに撮る
            {"snap_keep", null, "10", "phase8_f2_echo"},
            {"p8_knot", null, "20", "north"},
            {"snap", null, "40", "phase8_f2_knot_lit"},
            {"cmd", null, "10", "amemaru admin floor woods 2 tie all"},
            // 11月24日：封印の門は、名のある魔物を2体とも倒した印も要る
            {"cmd", null, "10", "amemaru admin floor guardians 2 done @p"},
            // 胞子の窪地・白い木立・管の谷（中継の灯の番号：1 広場・2 窪地・3 木立・4 谷・5 空き地の前）
            {"cmd", null, "60", "amemaru admin floor tp 2 waypoint2 @p"},
            {"snap", null, "80", "phase8_f2_hollow"},
            {"cmd", null, "60", "amemaru admin floor tp 2 waypoint3 @p"},
            {"snap", null, "80", "phase8_f2_grove"},
            {"cmd", null, "60", "amemaru admin floor tp 2 waypoint4 @p"},
            {"snap", null, "80", "phase8_f2_ravine"},
            // 霧の奥の少女（物語の場所に入ると、最初のパーティにだけ流れる。全員に知らせ）
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f2:84.5,27,72.5} 180 2"},
            {"await_scene", null, "0"},
            {"snap_keep", null, "70", "phase8_f2_noa"},
            {"await_idle", null, "0"},
            {"snap_keep", null, "20", "phase8_f2_noa_announce"},
            // 封印の門（3つともつないだので開いている）と、森番の古樹
            {"cmd", null, "60", "amemaru admin floor tp 2 boss @p"},
            {"snap", null, "40", "phase8_f2_gate"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            // 部屋に入ると戦いが始まる（真ん中から20ブロック）。始まったら12ブロックまで近づいて、少し見上げて撮る
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f2:216.5,19,74.5} 180 12"},
            {"cmd", null, "20", "execute in amemaru:tower run tp @p {f2:216.5,19,68.5} 180 -12"},
            {"snap", null, "60", "phase8_f2_boss"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f2:216.5,19,70.5} 180 14"},
            // 地割れの線（予兆の形2：線）
            {"cmd", null, "0", "amemaru admin boss skill 2 crack"},
            {"await_mark", null, "2", "phase8_f2_boss_crack"},
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f2:216.5,19,66.5} 180 30"},
            // 根の檻（立っている人の足もとの円。地面の印が出たらすぐ撮る）
            {"cmd", null, "0", "amemaru admin boss skill 2 cage"},
            {"await_ground", null, "0", "phase8_f2_boss_cage"},
            // 11月25日：固有のギミック「芽吹きの種」（緑の円に、光る種が根づく。育つまでの残り）
            {"cmd", null, "50", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f2:216.5,19,70.5} 180 25"},
            {"cmd", null, "0", "amemaru admin boss skill 2 seeds"},
            {"await_ground", null, "0", "phase8_f2_boss_seeds"},
            {"p8_bossfx", null, "0", "f2"},
            {"snap", null, "40", "phase8_f2_boss_seeds_rooted"},
            // 光る節を3つとも壊すと怯む（傾いた幹が写るように、少し見上げる）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f2:216.5,19,68.5} 180 -14"},
            {"cmd", null, "20", "execute in amemaru:tower run kill @e[type=amemaru:old_ward_knot]"},
            {"snap", null, "30", "phase8_f2_boss_stun"},
            {"cmd", null, "150", "amemaru admin debug vitals @a 100 100"},
            // 暗い森：足もとの地面とボスの根もとが写るように、少し見下ろす（灯を持つと、まわりの地面が明るくなる）
            {"cmd", null, "10", "execute in amemaru:tower run tp @p {f2:216.5,19,66.5} 180 16"},
            {"cmd", null, "10", "amemaru admin boss skill 2 dark"},
            {"snap", null, "40", "phase8_f2_boss_dark"},
            {"p8_hold", null, "40", "warden_light"},
            {"snap", null, "20", "phase8_f2_boss_dark_lit"},
            {"cmd", null, "5", "kill @e[type=amemaru:old_ward]"},
            {"snap_keep", null, "30", "phase8_f2_boss_defeated"},
            {"snap_keep", null, "120", "phase8_f2_boss_title"},
            {"await_idle", null, "0"},
            // 古樹の枝杖（一人称）
            {"p8_hold", null, "20", "elder_branch_staff"},
            {"snap", null, "30", "phase8_f2_staff"},
            {"p8_hold", null, "5", "-"},
            {"screen", null, "10", "hold"},
            // ---- Phase 8 その2：3階「古代の水路」。階を建て、入口の船着き場・大水路（白い空白に消える水）・管理局の広間と
            // 古代記録・3階の敵・西の貯水槽・東の水車の間・水門①（2つのハンドル）・水の引いた沈んだ区画・牙ナマズの跳ね上がり・
            // 大水門の前の回廊・白い流れ・大水門の番人ハイドラム（水の大砲・渦・大放流・排水で炉心がむき出し）
            {"cmd", null, "10", "gamemode creative @p"},
            {"p5_build_floor", null, "20", "3"},
            {"await_floor", null, "0"},
            {"cmd", null, "10", "gamemode adventure @p"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "100", "amemaru admin floor tp 3 entrance @p"},
            {"cmd", null, "10", "amemaru admin floor tp 3 entrance @p"},
            {"snap", null, "120", "phase8_f3_entrance"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f3:226.5,27,424.5} 170 6"},
            {"snap", null, "60", "phase8_f3_canal"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f3:259.5,27,343.5} -90 12"},
            {"snap", null, "40", "phase8_f3_canal_lost"},
            {"cmd", null, "60", "amemaru admin floor tp 3 waypoint1 @p"},
            {"snap", null, "60", "phase8_f3_hub"},
            // 古代記録「水路管理局の掲示」（机に近づくと読める）
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f3:252.5,27,248.5} 180 12"},
            {"snap_keep", null, "40", "phase8_f3_record"},
            // 3階の敵（広間の南西の石だたみに、動かないように並べる。北に並べて、南から見る）
            // 11月24日：広間の西の床に並べ、東から見る（前は通路の壁の陰になって写らなかった）
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:suimon_nezumi {f3:218.5,27,255.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:yodomi_slime {f3:217.5,27,259.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:soujikihei {f3:217.5,27,264.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:kiba_namazu {f3:218.5,27,269.5} {NoAI:1b,Rotation:[250f,0f]}"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f3:229.5,28,262.5} 90 8"},
            {"snap", null, "40", "phase8_f3_enemies"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:suimon_nezumi,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:yodomi_slime,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:soujikihei,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:kiba_namazu,distance=..30]"},
            {"cmd", null, "60", "amemaru admin floor tp 3 waypoint2 @p"},
            {"snap", null, "80", "phase8_f3_cistern"},
            {"cmd", null, "60", "amemaru admin floor tp 3 waypoint3 @p"},
            {"snap", null, "80", "phase8_f3_waterwheel"},
            // 水門①：西のハンドルを回す（「もう片方を待っている」）→ 東のハンドルも3秒のうちに回す → 開く
            {"cmd", null, "40", "amemaru admin floor floodgate 3 reset"},
            {"p8_valve", null, "20", "first:0"},
            {"snap_keep", null, "10", "phase8_f3_valve_waiting"},
            {"p8_valve", null, "20", "first:1"},
            {"snap_keep", null, "20", "phase8_f3_gate_opened"},
            // 沈んだ区画の水が引く（広間の北の坂の上から見下ろす）
            {"cmd", null, "200", "execute in amemaru:tower run tp @p {f3:240.5,27,235.5} 180 28"},
            {"snap", null, "60", "phase8_f3_sunken_drained"},
            {"cmd", null, "60", "amemaru admin floor tp 3 waypoint4 @p"},
            {"snap", null, "60", "phase8_f3_sunken"},
            // 牙ナマズ：深い水路のふちに立つと、線の予兆のあとで跳ねる
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:kiba_namazu {f3:252.5,12,162.5}"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "20", "execute in amemaru:tower run tp @p {f3:252.5,15,168.5} 180 25"},
            {"await_mark", null, "2", "phase8_f3_namazu_leap"},
            {"cmd", null, "30", "execute in amemaru:tower run kill @e[type=amemaru:kiba_namazu]"},
            // 水門②・③（ここでは管理者のコマンドで）と、名のある魔物の印。大水門の前の回廊（堀の水が引いた）
            {"cmd", null, "10", "amemaru admin floor floodgate 3 open all"},
            {"cmd", null, "10", "amemaru admin floor guardians 3 done @p"},
            {"cmd", null, "200", "amemaru admin floor tp 3 waypoint5 @p"},
            {"snap", null, "60", "phase8_f3_gallery"},
            // 白い流れ（物語の場所に入ると、一人一度だけ流れる）
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f3:50.5,15,66.5} 90 8"},
            {"await_scene", null, "0"},
            {"snap_keep", null, "60", "phase8_f3_white"},
            {"await_idle", null, "0"},
            // 大水門（封印は開いている）と、大水門の番人ハイドラム
            {"cmd", null, "60", "amemaru admin floor tp 3 boss @p"},
            {"snap", null, "40", "phase8_f3_seal"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f3:240.5,15,60.5} 180 -10"},
            {"cmd", null, "20", "execute in amemaru:tower run tp @p {f3:240.5,15,54.5} 180 -12"},
            {"snap", null, "60", "phase8_f3_boss"},
            // 水の大砲（線）
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f3:240.5,15,60.5} 180 16"},
            {"cmd", null, "0", "amemaru admin boss skill 3 cannon"},
            {"await_mark", null, "2", "phase8_f3_boss_cannon"},
            // 渦（足もとの円）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f3:246.5,15,56.5} 180 35"},
            {"cmd", null, "0", "amemaru admin boss skill 3 whirl"},
            {"await_ground", null, "0", "phase8_f3_boss_whirl"},
            // 11月25日：固有のギミック「大放水」（番人のまわりに青銅の堤がせり上がる。堤のかげに隠れる）
            {"cmd", null, "50", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f3:234.5,15,60.5} 180 25"},
            {"cmd", null, "0", "amemaru admin boss skill 3 flood"},
            {"snap", null, "30", "phase8_f3_boss_flood"},
            {"p8_bossfx", null, "0", "f3"},
            {"cmd", null, "130", "amemaru admin debug vitals @a 100 100"},
            // 排水：東と西の排水のハンドルを回す → 水が引き、炉心がむき出しになる
            {"cmd", null, "60", "amemaru admin debug vitals @a 100 100"},
            {"p8_valve", null, "10", "drain:0"},
            {"p8_valve", null, "60", "drain:1"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f3:240.5,15,52.5} 180 -14"},
            {"snap", null, "30", "phase8_f3_boss_exposed"},
            {"cmd", null, "5", "kill @e[type=amemaru:hydram]"},
            {"snap_keep", null, "30", "phase8_f3_boss_defeated"},
            {"snap_keep", null, "120", "phase8_f3_boss_title"},
            {"await_idle", null, "0"},
            {"p8_hold", null, "20", "floodgate_key_shield"},
            {"snap", null, "30", "phase8_f3_shield"},
            {"p8_hold", null, "5", "-"},
            {"screen", null, "10", "hold"},
            // Phase 8 その2：3階からの職業ごとの装備「流紋シリーズ」。6職業の防具を着て武器を持ち、明るい街の広場で前と後ろから
            {"cmd", null, "40", "amemaru admin city tp plaza @p"},
            {"cmd", null, "5", "time set noon"},
            {"cmd", null, "5", "kill @e[type=amemaru:training_dummy]"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:knight"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_knight_helm"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_knight_cuirass"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_knight_greaves"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_knight_sabatons"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_sword"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with amemaru:ryumon_shield"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_knight"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_knight_back"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:wizard"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_wizard_hat"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_wizard_robe"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_wizard_legwear"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_wizard_shoes"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_staff"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_wizard"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_wizard_back"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:healer"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_healer_hood"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_healer_robe"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_healer_legwear"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_healer_shoes"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_bell_staff"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_healer"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_healer_back"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:archer"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_archer_cap"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_archer_jerkin"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_archer_leggings"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_archer_boots"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_bow"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_archer"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_archer_back"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:rogue"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_rogue_mask"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_rogue_garb"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_rogue_leggings"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_rogue_tabi"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_daggers"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_rogue"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_rogue_back"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:monk"},
            {"cmd", null, "3", "item replace entity @a armor.head with amemaru:ryumon_monk_headband"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:ryumon_monk_gi"},
            {"cmd", null, "3", "item replace entity @a armor.legs with amemaru:ryumon_monk_hakama"},
            {"cmd", null, "3", "item replace entity @a armor.feet with amemaru:ryumon_monk_sandals"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:ryumon_gauntlets"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_ryumon_monk"},
            {"view", null, "5", "third"},
            {"snap", null, "15", "phase8_ryumon_monk_back"},
            {"view", null, "5", "first"},
            {"cmd", null, "3", "item replace entity @a weapon.offhand with minecraft:air"},
            // 防具工房の作る品の一覧：初めは自分の職業（ナイト）の品だけ。右上のボタンで職業を変えられる
            {"cmd", null, "5", "amemaru admin job set @p amemaru:knight"},
            {"cmd", null, "5", "amemaru admin production set @p armor 3"},
            {"p8_bench", null, "10", "armor"},
            {"p8_bench_open", null, "20"},
            {"snap", null, "20", "phase8_ryumon_workshop"},
            {"screen", null, "10", "hold"},
            // ---- 11月24日：4階「崩壊した神殿」。階を建て、入口・参道・前庭・壁画（崩れた絵と、戻した絵）・祈りの間の音の順番・
            // 浮石の庭・聖獣の庭・納骨堂の野営の跡（結晶騎士団の遺品）・白い壁画・4階の敵・祈りの残響カンターレ（歌と嘆きの波）
            {"cmd", null, "10", "gamemode creative @p"},
            {"p5_build_floor", null, "20", "4"},
            {"await_floor", null, "0"},
            {"cmd", null, "10", "gamemode adventure @p"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "10", "time set noon"},
            {"cmd", null, "100", "amemaru admin floor tp 4 entrance @p"},
            {"cmd", null, "10", "amemaru admin floor tp 4 entrance @p"},
            {"snap", null, "120", "phase8_f4_entrance"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f4:264.5,25,340.5} 180 4"},
            {"snap", null, "60", "phase8_f4_avenue"},
            {"cmd", null, "60", "amemaru admin floor tp 4 waypoint4 @p"},
            {"snap", null, "60", "phase8_f4_plaza"},
            {"cmd", null, "10", "amemaru admin floor murals 4 reset"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f4:459.5,25,237.5} 180 -8"},
            {"snap", null, "60", "phase8_f4_mural"},
            {"cmd", null, "20", "amemaru admin floor murals 4 solve"},
            {"snap", null, "40", "phase8_f4_mural_solved"},
            {"cmd", null, "60", "amemaru admin floor tp 4 waypoint5 @p"},
            {"snap", null, "60", "phase8_f4_chapel"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f4:420.5,25,127.5} 180 24"},
            {"snap", null, "40", "phase8_f4_song"},
            {"cmd", null, "60", "amemaru admin floor tp 4 waypoint1 @p"},
            {"snap", null, "80", "phase8_f4_float"},
            {"cmd", null, "60", "amemaru admin floor tp 4 waypoint3 @p"},
            {"snap", null, "80", "phase8_f4_terrace"},
            {"cmd", null, "60", "amemaru admin floor tp 4 waypoint2 @p"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f4:265.5,11,311.5} 180 12"},
            {"await_scene", null, "0"},
            {"snap_keep", null, "60", "phase8_f4_knight_camp"},
            {"await_idle", null, "0"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f4:406.5,25,382.5} 180 -4"},
            {"await_scene", null, "0"},
            {"snap_keep", null, "60", "phase8_f4_white_murals"},
            {"await_idle", null, "0"},
            // 4階の敵（前庭の西に並べ、東から見る）
            {"cmd", null, "40", "amemaru admin floor tp 4 waypoint4 @p"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:silent_statue {f4:236.5,25,240.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:prayer_echo {f4:236.5,26,245.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:crystal_beetle {f4:236.5,25,250.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:broken_beast {f4:236.5,25,256.5} {NoAI:1b,Rotation:[270f,0f]}"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f4:247.5,25,248.5} 90 8"},
            {"snap", null, "40", "phase8_f4_enemies"},
            // 11月24日：ロックオン（B）。まん中の結晶甲虫をロックすると、照準の印と、名前・距離・HPが出る
            {"p8_lock", null, "10"},
            {"snap", null, "20", "phase8_lock_on"},
            {"p8_lock", null, "10"},
            {"snap", null, "20", "phase8_lock_on_next"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:silent_statue,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:prayer_echo,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:crystal_beetle,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:broken_beast,distance=..30]"},
            // 歌の大広間（封印は開いている）と、祈りの残響カンターレ
            {"cmd", null, "10", "amemaru admin floor guardians 4 done @p"},
            {"cmd", null, "60", "amemaru admin floor tp 4 boss @p"},
            {"snap", null, "40", "phase8_f4_seal"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f4:264.5,25,96.5} 180 -6"},
            {"cmd", null, "20", "execute in amemaru:tower run tp @p {f4:264.5,25,92.5} 180 -8"},
            {"snap", null, "60", "phase8_f4_boss"},
            {"cmd", null, "0", "amemaru admin boss skill 4 song"},
            {"snap", null, "50", "phase8_f4_boss_song"},
            {"cmd", null, "60", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f4:264.5,25,82.5} 180 18"},
            {"cmd", null, "0", "amemaru admin boss skill 4 wave"},
            {"await_mark", null, "5", "phase8_f4_boss_wave"},
            // 11月25日：固有のギミック「残響の色」（頭の上の色と、色ごとの床のまわりの円。自分の色の床のそばへ）
            {"cmd", null, "60", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f4:264.5,25,88.5} 180 30"},
            {"cmd", null, "0", "amemaru admin boss skill 4 echo"},
            {"snap", null, "30", "phase8_f4_boss_echo"},
            {"p8_bossfx", null, "0", "f4"},
            {"cmd", null, "130", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "40", "kill @e[type=amemaru:cantare]"},
            {"snap_keep", null, "30", "phase8_f4_boss_defeated"},
            {"snap_keep", null, "120", "phase8_f4_boss_title"},
            {"await_idle", null, "0"},
            // 11月24日：倒したあと、出口の円の横に金の縁の「次の階への円」（5階はまだ開いていない）
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f4:268.0,25,64.5} 180 12"},
            {"snap", null, "40", "phase8_next_circle"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:healer"},
            {"p8_hold", null, "20", "prayer_bell_staff"},
            {"snap", null, "30", "phase8_f4_bell"},
            {"p8_hold", null, "5", "-"},
            {"screen", null, "10", "hold"},
            // ---- 11月24日：5階「古代守護者の祭壇」。入口の岩棚・監視の回廊・分かれ道・防衛兵の砦・修復の工廠・5階の敵・
            // 祭壇の前庭（格子の空）・古代守護者（結界と結び台・接続判定）・守護者シリーズ
            {"cmd", null, "10", "gamemode creative @p"},
            {"p5_build_floor", null, "20", "5"},
            {"await_floor", null, "0"},
            {"cmd", null, "10", "gamemode adventure @p"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "10", "amemaru admin floor alert 5 clear"},
            {"cmd", null, "100", "amemaru admin floor tp 5 entrance @p"},
            {"cmd", null, "10", "amemaru admin floor tp 5 entrance @p"},
            {"snap", null, "120", "phase8_f5_entrance"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f5:251.5,49,488.5} 160 6"},
            {"snap", null, "60", "phase8_f5_gallery"},
            {"cmd", null, "60", "amemaru admin floor tp 5 waypoint1 @p"},
            {"snap", null, "60", "phase8_f5_junction"},
            {"cmd", null, "60", "amemaru admin floor tp 5 waypoint2 @p"},
            {"snap", null, "80", "phase8_f5_bastion"},
            {"cmd", null, "60", "amemaru admin floor tp 5 waypoint3 @p"},
            {"snap", null, "80", "phase8_f5_workshop"},
            // 5階の敵（分かれ道の台に並べ、南から見る）
            {"cmd", null, "40", "amemaru admin floor tp 5 waypoint1 @p"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:watcher {f5:283.5,51,384.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:sentinel {f5:288.5,49,384.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "5", "execute in amemaru:tower run summon amemaru:repair_drone {f5:293.5,51,384.5} {NoAI:1b,Rotation:[0f,0f]}"},
            {"cmd", null, "40", "execute in amemaru:tower run tp @p {f5:288.5,49,393.5} 180 8"},
            {"snap", null, "40", "phase8_f5_enemies"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:watcher,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:sentinel,distance=..30]"},
            {"cmd", null, "5", "execute in amemaru:tower run kill @e[type=amemaru:repair_drone,distance=..30]"},
            {"cmd", null, "60", "amemaru admin floor tp 5 waypoint4 @p"},
            {"await_scene", null, "0"},
            {"snap_keep", null, "60", "phase8_f5_lattice"},
            {"await_idle", null, "0"},
            // 祭壇（接続の柱を4本とも灯し、封印は開いている）と、古代守護者
            {"cmd", null, "10", "amemaru admin pillars complete 5 @p"},
            {"cmd", null, "60", "amemaru admin floor tp 5 boss @p"},
            {"snap", null, "40", "phase8_f5_seal"},
            {"cmd", null, "10", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "60", "execute in amemaru:tower run tp @p {f5:288.5,49,104.5} 180 -10"},
            {"cmd", null, "20", "execute in amemaru:tower run tp @p {f5:288.5,49,98.5} 180 -12"},
            {"snap", null, "60", "phase8_f5_boss"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,80.5} 180 20"},
            {"cmd", null, "0", "amemaru admin boss skill 5 beam"},
            {"await_mark", null, "2", "phase8_f5_boss_beam"},
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,82.5} 180 30"},
            {"cmd", null, "0", "amemaru admin boss skill 5 slam"},
            {"await_ground", null, "0", "phase8_f5_boss_slam"},
            // 結界（4本の柱から光が伸び、守護者は無敵。結び台が灯る）→ 南の結び台に乗る（ひとりなら1つで足りる）→ 3秒で解け、よろめく
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,96.5} 180 -8"},
            {"cmd", null, "0", "amemaru admin boss skill 5 barrier"},
            {"snap", null, "30", "phase8_f5_boss_barrier"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49.3,90.5} 180 -4"},
            {"snap_keep", null, "30", "phase8_f5_boss_dais"},
            {"snap_keep", null, "60", "phase8_f5_boss_broken"},
            // 接続判定（ひとりひとりのまわりに6ブロックの輪。味方がいなければ赤い。ひとりの挑戦者は判定しない）
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,86.5} 180 35"},
            {"cmd", null, "0", "amemaru admin boss skill 5 judge"},
            {"snap", null, "30", "phase8_f5_boss_judge"},
            // 11月25日：固有のギミック「切断判定」（白い輪。ほかの人が中にいると赤い。ひとりの挑戦者は判定しない）
            {"cmd", null, "70", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,86.5} 180 35"},
            {"cmd", null, "0", "amemaru admin boss skill 5 sever"},
            {"snap", null, "30", "phase8_f5_boss_sever"},
            {"p8_bossfx", null, "0", "f5"},
            {"cmd", null, "70", "amemaru admin debug vitals @a 100 100"},
            // 最後：HPが尽きても倒れず、祭壇の中央の円に全員が集まるのを待つ → 集まると膝をつき、倒れる
            {"cmd", null, "40", "amemaru admin debug vitals @a 100 100"},
            {"cmd", null, "20", "kill @e[type=amemaru:ancient_guardian]"},
            {"cmd", null, "2", "execute in amemaru:tower run tp @p {f5:288.5,49,86.5} 180 20"},
            {"snap", null, "40", "phase8_f5_boss_gather"},
            {"cmd", null, "5", "execute in amemaru:tower run tp @p {f5:288.5,49,75.5} 180 -20"},
            {"snap_keep", null, "40", "phase8_f5_boss_kneel"},
            {"snap_keep", null, "100", "phase8_f5_boss_defeated"},
            {"snap_keep", null, "120", "phase8_f5_boss_title"},
            {"await_idle", null, "0"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:knight"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:guardian_knight_shell"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:guardian_greatsword"},
            {"cmd", null, "40", "amemaru admin city tp plaza @p"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_f5_guardian_knight"},
            {"cmd", null, "5", "amemaru admin job set @p amemaru:wizard"},
            {"cmd", null, "3", "item replace entity @a armor.chest with amemaru:guardian_wizard_shell"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with amemaru:guardian_staff"},
            {"p6_front", null, "10"},
            {"snap", null, "20", "phase8_f5_guardian_wizard"},
            {"cmd", null, "3", "item replace entity @a armor.chest with minecraft:air"},
            {"cmd", null, "3", "item replace entity @a weapon.mainhand with minecraft:air"},
            // 第七の門鍵（お守りの枠）：左の腰に下がる翠の立方体と、まわりを回る細い環。広場の台に腰が隠れないよう、
            // 40ブロック上の空に小さな床を作って、その上で撮る（前と後ろ）
            {"cmd", null, "5", "time set noon"},
            {"cmd", null, "20", "execute as @p at @p run fill ~-4 ~39 ~-4 ~4 ~39 ~4 minecraft:smooth_stone"},
            {"cmd", null, "20", "execute as @p at @p run tp @s ~ ~40 ~ 180 0"},
            {"p8_charm", null, "10", "seventh_gate_key"},
            {"p6_front", null, "10"},
            {"snap", null, "30", "phase8_f5_gate_key"},
            {"view", null, "5", "third"},
            {"snap", null, "20", "phase8_f5_gate_key_back"},
            {"view", null, "5", "first"},
            {"p8_charm", null, "5", "-"},
            {"cmd", null, "10", "execute as @p at @p run tp @s ~ ~-40 ~"},
            {"screen", null, "10", "hold"},
            {"cmd", null, "10", "gamemode spectator @p"},
            {"hud", null, "5", "off"}};

    private CiScreenshots() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (keepHold && !cityDone && minecraft.player != null && minecraft.screen == null) {
            minecraft.setScreen(new HoldScreen());
        }
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
        if (stage >= CITY) {
            cityStage(minecraft);
            return;
        }
        switch (stage) {
            case 0 -> {
                if (minecraft.screen != null) {
                    return;
                }
                // 透明な画面を開いておく（自動の確認の側は、画面が開いている間は終了を待つ）。HUDはそのまま見える
                minecraft.setScreen(new HoldScreen());
                // 見本の状態：レベル12、経験値を半分ほど（11月24日からステータスもスキルツリーで振るため、振り分けはしない。
                // ツリーのポイントが残っているので結晶が光る）
                command(minecraft, "amemaru admin level set @a 12");
                command(minecraft, "amemaru admin level exp @a 180");
                // ホットバーとオフハンドの見え方を確かめるためのアイテム
                command(minecraft, "give @a minecraft:diamond_sword");
                command(minecraft, "give @a minecraft:iron_pickaxe");
                command(minecraft, "give @a minecraft:bread 12");
                command(minecraft, "give @a minecraft:torch 64");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:shield");
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
                // 11月24日：ツリーの見本として、取れているスキルのとなりのステータスの節を少し取っておく
                takeTreeNodesForShot(6);
                minecraft.setScreen(new SkillTreeScreen());
                treeShot = 0;
                next(13, 10);
            }
            case 13 -> {
                // 11月24日：星座のスキルツリーを、開いたとき・大きな節を選んだとき・全体・ステータスのつまみで撮る
                SkillTreeScreen tree = minecraft.screen instanceof SkillTreeScreen open ? open : null;
                switch (treeShot++) {
                    case 0 -> {
                        clean(minecraft);
                        pendingShot = "skill_tree";
                        next(13, 3);
                    }
                    case 1 -> {
                        if (tree != null) {
                            tree.selectNode(firstTreeNode(jp.amemaru.lostconnection.job.def.SkillTreeDef.Kind.NOTABLE));
                        }
                        next(13, 6);
                    }
                    case 2 -> {
                        clean(minecraft);
                        pendingShot = "skill_tree_node";
                        next(13, 3);
                    }
                    case 3 -> {
                        if (tree != null) {
                            tree.showWholeTree();
                        }
                        next(13, 6);
                    }
                    case 4 -> {
                        clean(minecraft);
                        pendingShot = "skill_tree_all";
                        next(13, 3);
                    }
                    case 5 -> {
                        if (tree != null) {
                            tree.showStats();
                        }
                        next(13, 6);
                    }
                    default -> {
                        clean(minecraft);
                        pendingShot = "skill_tree_stats";
                        next(14, 2);
                    }
                }
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
                // 奥義のもう一つ：天断ち（上位職にして覚える。管理者のコマンドはレベルを見ないので、Lv30 のまま撮る）
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
                // 奥義：天断ちを選び、ツリーのまん中に見せる（11月24日から、奥義は流派の先端の節）
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
                // ウィザードのスキルツリー（星爆を選んで、組み合わせの説明を見せる）
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
                // スキルツリーを閉じ、透明な画面に替える（画面を開いたままにして、自動の確認の終了を待たせる）
                minecraft.setScreen(new HoldScreen());
                next(P3, 0);
            }
            // ---------------------------------------------------------------- Phase 3：敵・ドロップ・装備・工房・図鑑
            case P3 -> {
                clean(minecraft);
                command(minecraft, "kill @e[type=amemaru:training_dummy]");
                command(minecraft, "execute as @p at @p run amemaru admin enemy clear");
                command(minecraft, "gamemode creative @a");
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                command(minecraft, "amemaru admin production set @a weapon 3");
                // 前の写真の演出（氷華の檻の花など）が届かない、山や木より高い所（同じ場所の真上の y=230）に床を敷いて移る
                // （敵が動いても落ちない広さ。真上なら、その場所はもう読み込まれている）。撮り終えたら床を消して元の場所へ戻る。南（+Z）を向く
                p3Return = minecraft.player == null ? null : minecraft.player.blockPosition();
                if (p3Return != null) {
                    command(minecraft, String.format(Locale.ROOT,
                            "fill %d %d %d %d %d %d minecraft:deepslate_tiles replace minecraft:air",
                            p3Return.getX() - 10, P3_FLOOR, p3Return.getZ() - 10, p3Return.getX() + 10, P3_FLOOR, p3Return.getZ() + 10));
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12",
                            p3Return.getX() + 0.5, P3_FLOOR + 1, p3Return.getZ() + 0.5));
                }
                // 1階の4種類の敵を、こちらを向かせて並べる（動かない）
                command(minecraft, "execute as @p at @p run summon amemaru:tsunomofu ~-3.2 ~ ~5 {NoAI:1b,Rotation:[200f,0f]}");
                command(minecraft, "execute as @p at @p run summon amemaru:ishikure_soldier ~-1 ~ ~6.5 {NoAI:1b,Rotation:[180f,0f]}");
                command(minecraft, "execute as @p at @p run summon amemaru:kakekiba_wolf ~1.6 ~ ~5 {NoAI:1b,Rotation:[150f,0f]}");
                command(minecraft, "execute as @p at @p run summon amemaru:crystal_bee ~3.6 ~1.3 ~5.6 {NoAI:1b,Rotation:[205f,0f]}");
                CiPhase3.onServer(minecraft, CiPhase3::fillBestiary);
                next(P3 + 1, 60);
            }
            case P3 + 1 -> {
                clean(minecraft);
                pendingShot = "enemies";
                next(P3 + 2, 2);
            }
            case P3 + 2 -> {
                // 石くれ兵と欠け牙の狼を動かし、こちらに気づかせる（クリエイティブでは狙われないため、アドベンチャーにする）
                command(minecraft, "data merge entity @e[type=amemaru:ishikure_soldier,limit=1,sort=nearest] {NoAI:0b}");
                command(minecraft, "data merge entity @e[type=amemaru:kakekiba_wolf,limit=1,sort=nearest] {NoAI:0b}");
                command(minecraft, "gamemode adventure @a");
                releaseWait = 0;
                next(P3 + 3, 5);
            }
            case P3 + 3 -> {
                // 予兆（突進の帯・叩きつけの円）が出るまで待つ（出なくても、15秒で次へ）
                if (!CiPhase3.anyTelegraph(minecraft) && releaseWait++ < 300) {
                    return;
                }
                next(P3 + 4, 7);
            }
            case P3 + 4 -> {
                clean(minecraft);
                pendingShot = "enemy_telegraph";
                next(P3 + 5, 2);
            }
            case P3 + 5 -> {
                command(minecraft, "gamemode creative @a");
                command(minecraft, "execute as @p at @p run amemaru admin enemy clear");
                command(minecraft, "kill @e[type=amemaru:loot_item]");
                command(minecraft, "amemaru admin debug vitals @a 100 100");
                command(minecraft, "execute as @p at @p run tp @s ~ ~ ~ 0 25");
                CiPhase3.onServer(minecraft, CiPhase3::rareDrop);
                next(P3 + 6, 24);
            }
            case P3 + 6 -> {
                clean(minecraft);
                pendingShot = "rare_drop";
                next(P3 + 7, 2);
            }
            case P3 + 7 -> {
                command(minecraft, "kill @e[type=amemaru:loot_item]");
                command(minecraft, "execute as @p at @p run tp @s ~ ~ ~ 0 8");
                CiPhase3.onServer(minecraft, CiPhase3::equipPlate);
                // レアドロップの知らせが消えるまで待つ
                next(P3 + 8, 60);
            }
            case P3 + 8 -> {
                clean(minecraft);
                pendingShot = "weapon_first_person";
                next(P3 + 9, 2);
            }
            case P3 + 9 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next(P3 + 10, 12);
            }
            case P3 + 10 -> {
                clean(minecraft);
                pendingShot = "gear_plate";
                next(P3 + 11, 2);
            }
            case P3 + 11 -> {
                command(minecraft, "amemaru admin job set @a amemaru:wizard");
                CiPhase3.onServer(minecraft, CiPhase3::equipRobe);
                next(P3 + 12, 30);
            }
            case P3 + 12 -> {
                clean(minecraft);
                pendingShot = "gear_robe";
                next(P3 + 13, 2);
            }
            case P3 + 13 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.setScreen(new CiPhase3.TooltipScreen(CiPhase3.tooltipSamples(minecraft)));
                next(P3 + 14, 10);
            }
            case P3 + 14 -> {
                clean(minecraft);
                pendingShot = "gear_tooltips";
                next(P3 + 15, 2);
            }
            case P3 + 15 -> {
                ModNetwork.sendToServer(new AccessoryActionC2S(AccessoryActionC2S.Action.OPEN, 0, false));
                next(P3 + 16, 15);
            }
            case P3 + 16 -> {
                clean(minecraft);
                pendingShot = "accessory_screen";
                next(P3 + 17, 2);
            }
            case P3 + 17 -> {
                if (minecraft.player != null) {
                    minecraft.player.closeContainer();
                }
                minecraft.setScreen(new HoldScreen());
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                CiPhase3.onServer(minecraft, CiPhase3::prepareWorkshop);
                // 目の前（南へ2ブロック）に武器の工房の台。こちら（北）を向かせる
                workbench = minecraft.player == null ? null : minecraft.player.blockPosition().south(2);
                command(minecraft, "execute as @p at @p run setblock ~ ~ ~2 amemaru:weapon_workbench[facing=north]");
                next(P3 + 18, 15);
            }
            case P3 + 18 -> {
                if (workbench != null) {
                    CiPhase3.useWorkbench(minecraft, workbench);
                }
                next(P3 + 19, 15);
            }
            case P3 + 19 -> {
                clean(minecraft);
                pendingShot = "workshop";
                next(P3 + 20, 2);
            }
            case P3 + 20 -> {
                CiPhase3.placeOnWorkbench(minecraft);
                if (minecraft.screen instanceof WorkshopScreen workshop) {
                    workshop.selectTab(1);
                }
                next(P3 + 21, 10);
            }
            case P3 + 21 -> {
                clean(minecraft);
                pendingShot = "workshop_enhance";
                next(P3 + 22, 2);
            }
            case P3 + 22 -> {
                if (minecraft.player != null) {
                    minecraft.player.closeContainer();
                }
                BestiaryScreen bestiary = new BestiaryScreen();
                minecraft.setScreen(bestiary);
                bestiary.select(AmemaruMod.id("kakekiba_wolf"));
                next(P3 + 23, 12);
            }
            case P3 + 23 -> {
                clean(minecraft);
                pendingShot = "bestiary";
                next(P3 + 24, 2);
            }
            case P3 + 24 -> {
                // 後片づけ（台・装備・アクセサリー・持ち物）。元の場所へ戻って、建物の写真へ
                minecraft.setScreen(new HoldScreen());
                command(minecraft, "execute as @p at @p run setblock ~ ~ ~2 minecraft:air");
                command(minecraft, "amemaru admin production clear @a");
                CiPhase3.onServer(minecraft, CiPhase3::cleanup);
                if (p3Return != null) {
                    // 床を消してから戻る（後の建物の見本が、同じ場所の上に建つため）
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f", p3Return.getX() + 0.5, p3Return.getY(),
                            p3Return.getZ() + 0.5));
                    command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                            p3Return.getX() - 10, P3_FLOOR, p3Return.getZ() - 10, p3Return.getX() + 10, P3_FLOOR, p3Return.getZ() + 10));
                }
                next(GEAR, 40);
            }
            // ---------------------------------------------------------------- 装備のデザイン：水門の鍵盾（docs/gear/floodgate_key_shield.md）
            case GEAR -> {
                clean(minecraft);
                command(minecraft, "gamemode creative @a");
                command(minecraft, "time set noon");
                command(minecraft, "weather clear");
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                // Phase 3と同じく、同じ場所の真上（読み込み済み）の空に床を敷いて撮る。南（+Z）を向く
                gearReturn = minecraft.player == null ? null : minecraft.player.blockPosition();
                if (gearReturn != null) {
                    command(minecraft, String.format(Locale.ROOT,
                            "fill %d %d %d %d %d %d minecraft:smooth_stone replace minecraft:air",
                            gearReturn.getX() - 8, P3_FLOOR, gearReturn.getZ() - 4, gearReturn.getX() + 8, P3_FLOOR, gearReturn.getZ() + 12));
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 10",
                            gearReturn.getX() + 0.5, P3_FLOOR + 1, gearReturn.getZ() + 0.5));
                }
                CiPhase3.onServer(minecraft, CiGear::equipShield);
                next(GEAR + 1, 40);
            }
            case GEAR + 1 -> {
                clean(minecraft);
                pendingShot = "shield_first_person";
                next(GEAR + 2, 2);
            }
            case GEAR + 2 -> {
                CiGear.block(minecraft, true);
                next(GEAR + 3, 12);
            }
            case GEAR + 3 -> {
                clean(minecraft);
                pendingShot = "shield_blocking";
                next(GEAR + 4, 2);
            }
            case GEAR + 4 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                next(GEAR + 5, 10);
            }
            case GEAR + 5 -> {
                clean(minecraft);
                pendingShot = "shield_third_person_front";
                next(GEAR + 6, 2);
            }
            case GEAR + 6 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                next(GEAR + 7, 10);
            }
            case GEAR + 7 -> {
                clean(minecraft);
                pendingShot = "shield_third_person_back";
                next(GEAR + 8, 2);
            }
            case GEAR + 8 -> {
                // 盾を下ろし、ゲームの中の4方向（表示用のエンティティ）を、少し離れて正面から撮る。
                // 左から正面・側面・背面・反対の側面（90度ずつ回す）。手が写らないよう、観戦モードにする
                CiGear.block(minecraft, false);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                hideHud = true;
                command(minecraft, "gamemode spectator @a");
                if (gearReturn != null) {
                    double x = gearReturn.getX() + 0.5;
                    double z = gearReturn.getZ() + 0.5;
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0", x, P3_FLOOR + 1, z));
                    // 南を向くと、画面の左は東（+X）。向き0で表が北（こちら）を向く
                    float[] yaws = {0.0F, 90.0F, 180.0F, 270.0F};
                    for (int k = 0; k < yaws.length; k++) {
                        command(minecraft, CiGear.turnaround(x + 4.5 - 3.0 * k, P3_FLOOR + 2.62, z + 7.0, yaws[k]));
                    }
                }
                next(GEAR + 9, 30);
            }
            case GEAR + 9 -> {
                clean(minecraft);
                pendingShot = "shield_turnaround";
                next(GEAR + 10, 2);
            }
            case GEAR + 10 -> {
                command(minecraft, "time set midnight");
                next(GEAR + 11, 30);
            }
            case GEAR + 11 -> {
                clean(minecraft);
                pendingShot = "shield_turnaround_night";
                next(GEAR + 12, 2);
            }
            case GEAR + 12 -> {
                // 作り直した装備（見習い・古代石）：表示用のエンティティで横に並べる（正面）
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                command(minecraft, "time set noon");
                lineup(minecraft, 0.0F);
                next(GEAR + 13, 30);
            }
            case GEAR + 13 -> {
                clean(minecraft);
                pendingShot = "gear_lineup_redesign";
                next(GEAR + 14, 2);
            }
            case GEAR + 14 -> {
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                lineup(minecraft, 90.0F);
                next(GEAR + 15, 20);
            }
            case GEAR + 15 -> {
                clean(minecraft);
                pendingShot = "gear_lineup_redesign_side";
                next(GEAR + 16, 2);
            }
            case GEAR + 16 -> {
                // 三人称の持ち方：防具立てに持たせる（右手に武器、左手に盾）
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                if (gearReturn != null) {
                    double x = gearReturn.getX() + 0.5;
                    double z = gearReturn.getZ() + 4.5;
                    String[][] hands = {{"apprentice_sword", "apprentice_shield"}, {"apprentice_greatsword", null},
                            {"apprentice_staff", null}, {"ancient_stone_sword", "ancient_stone_shield"},
                            {"ancient_stone_greatsword", null}, {"ancient_stone_staff", null}};
                    for (int k = 0; k < hands.length; k++) {
                        command(minecraft, CiGear.stand(hands[k][0], hands[k][1], x + 4.5 - 1.8 * k, P3_FLOOR + 1, z, 180.0F));
                    }
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 12", x, P3_FLOOR + 1, gearReturn.getZ() + 0.5));
                }
                next(GEAR + 17, 30);
            }
            case GEAR + 17 -> {
                clean(minecraft);
                pendingShot = "gear_stands_redesign";
                next(GEAR + 18, 2);
            }
            case GEAR + 18 -> {
                // 一人称：見習いの剣と盾
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                command(minecraft, "gamemode creative @a");
                hideHud = false;
                if (gearReturn != null) {
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 8", gearReturn.getX() + 0.5, P3_FLOOR + 1,
                            gearReturn.getZ() + 0.5));
                }
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "apprentice_sword", "apprentice_shield"));
                next(GEAR + 19, 30);
            }
            case GEAR + 19 -> {
                clean(minecraft);
                pendingShot = "gear_fp_apprentice";
                next(GEAR + 20, 2);
            }
            case GEAR + 20 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "ancient_stone_greatsword", null));
                next(GEAR + 21, 20);
            }
            case GEAR + 21 -> {
                clean(minecraft);
                pendingShot = "gear_fp_ancient_greatsword";
                next(GEAR + 22, 2);
            }
            case GEAR + 22 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "ancient_stone_staff", null));
                next(GEAR + 23, 20);
            }
            case GEAR + 23 -> {
                clean(minecraft);
                pendingShot = "gear_fp_ancient_staff";
                next(GEAR + 24, 2);
            }
            case GEAR + 24 -> {
                // 作り直した防具：防具立てに着せる（重鎧の前・後ろ、法衣の前・後ろ）
                armorStands(minecraft, 180.0F, 0.0F);
                next(GEAR + 25, 40);
            }
            case GEAR + 25 -> {
                clean(minecraft);
                pendingShot = "gear_armor_redesign";
                next(GEAR + 26, 2);
            }
            case GEAR + 26 -> {
                // 斜めから（前は右斜め前、後ろは左斜め後ろ）
                armorStands(minecraft, 145.0F, -35.0F);
                next(GEAR + 27, 30);
            }
            case GEAR + 27 -> {
                clean(minecraft);
                pendingShot = "gear_armor_redesign_34";
                next(GEAR + 28, 2);
            }
            case GEAR + 28 -> {
                // ⑤1階のレア装備：表示用のエンティティで並べる（昼と夜。光る所を見る）
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                lineup(minecraft, 0.0F, CiGear.REDESIGN_RARE, 2.2);
                next(GEAR + 29, 30);
            }
            case GEAR + 29 -> {
                clean(minecraft);
                pendingShot = "gear_lineup_rare";
                next(GEAR + 30, 2);
            }
            case GEAR + 30 -> {
                command(minecraft, "time set midnight");
                next(GEAR + 31, 30);
            }
            case GEAR + 31 -> {
                clean(minecraft);
                pendingShot = "gear_lineup_rare_night";
                next(GEAR + 32, 2);
            }
            case GEAR + 32 -> {
                // 一人称：見張りの石刃
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                command(minecraft, "time set noon");
                command(minecraft, "gamemode creative @a");
                hideHud = false;
                if (gearReturn != null) {
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 8", gearReturn.getX() + 0.5, P3_FLOOR + 1,
                            gearReturn.getZ() + 0.5));
                }
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "sentinel_blade", null));
                next(GEAR + 33, 30);
            }
            case GEAR + 33 -> {
                clean(minecraft);
                pendingShot = "gear_fp_sentinel";
                next(GEAR + 34, 2);
            }
            case GEAR + 34 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "chipped_fang_greatsword", null));
                next(GEAR + 35, 20);
            }
            case GEAR + 35 -> {
                clean(minecraft);
                pendingShot = "gear_fp_fang";
                next(GEAR + 36, 2);
            }
            case GEAR + 36 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "bee_crystal_staff", null));
                next(GEAR + 37, 20);
            }
            case GEAR + 37 -> {
                clean(minecraft);
                pendingShot = "gear_fp_bee";
                next(GEAR + 38, 2);
            }
            case GEAR + 38 -> {
                // 蜂晶の杖の「使った」のアニメーション（羽が開き、核がふくらむ）
                CiPhase3.onServer(minecraft, jp.amemaru.lostconnection.item.GeoWeaponItem::playUse);
                next(GEAR + 39, 5);
            }
            case GEAR + 39 -> {
                clean(minecraft);
                pendingShot = "gear_fp_bee_use";
                next(GEAR + 41, 2);
            }
            case GEAR + 41 -> {
                // ⑥アクセサリー：防具なしで付ける（三人称の前から。HUDなし）
                hideHud = true;
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                CiPhase3.onServer(minecraft, player -> CiGear.wearAccessories(player, null));
                next(GEAR + 42, 30);
            }
            case GEAR + 42 -> {
                clean(minecraft);
                pendingShot = "gear_accessories";
                next(GEAR + 43, 2);
            }
            case GEAR + 43 -> {
                // 重鎧の上に付けたとき（防具の上に見えるように広げて描く）
                CiPhase3.onServer(minecraft, player -> CiGear.wearAccessories(player, CiGear.PLATE));
                next(GEAR + 44, 30);
            }
            case GEAR + 44 -> {
                clean(minecraft);
                pendingShot = "gear_accessories_plate";
                next(GEAR + 45, 2);
            }
            case GEAR + 45 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.wearAccessories(player, CiGear.ROBE));
                next(GEAR + 46, 30);
            }
            case GEAR + 46 -> {
                clean(minecraft);
                pendingShot = "gear_accessories_robe";
                next(GEAR + 47, 2);
            }
            case GEAR + 47 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                next(GEAR + 48, 2);
            }
            case GEAR + 48 -> {
                // 持ったときの大きさと刃の向き（docs/gear/hold_sheet.png）：一人称の残りの2つ（古代石の剣・見習いの大剣）
                hideHud = false;
                CiPhase3.onServer(minecraft, CiPhase3::cleanup);
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "ancient_stone_sword", null));
                if (gearReturn != null) {
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 8", gearReturn.getX() + 0.5, P3_FLOOR + 1,
                            gearReturn.getZ() + 0.5));
                }
                next(GEAR + 49, 30);
            }
            case GEAR + 49 -> {
                clean(minecraft);
                pendingShot = "gear_fp_ancient_sword";
                next(GEAR + 50, 2);
            }
            case GEAR + 50 -> {
                CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "apprentice_greatsword", null));
                next(GEAR + 51, 20);
            }
            case GEAR + 51 -> {
                clean(minecraft);
                pendingShot = "gear_fp_apprentice_greatsword";
                next(GEAR + 52, 2);
            }
            case GEAR + 52 -> {
                // 三人称を横から：防具立てに右手で持たせ、右の側をこちら（北）へ向ける（西を向く。刃の先は画面の右）
                CiPhase3.onServer(minecraft, CiPhase3::cleanup);
                holdSide(minecraft, CiGear.HOLD_SWORDS);
                next(GEAR + 53, 30);
            }
            case GEAR + 53 -> {
                clean(minecraft);
                pendingShot = "gear_hold_side_swords";
                next(GEAR + 54, 2);
            }
            case GEAR + 54 -> {
                holdSide(minecraft, CiGear.HOLD_GREATSWORDS);
                next(GEAR + 55, 30);
            }
            case GEAR + 55 -> {
                clean(minecraft);
                pendingShot = "gear_hold_side_greatswords";
                next(GEAR + 40, 2);
            }
            case GEAR + 40 -> {
                // 後片づけ：表示用のエンティティ・持ち物・床。元の場所へ戻って、建物の写真へ
                hideHud = false;
                command(minecraft, "kill @e[tag=amemaru_ci_gear]");
                command(minecraft, "time set noon");
                command(minecraft, "gamemode creative @a");
                CiPhase3.onServer(minecraft, CiPhase3::cleanup);
                if (gearReturn != null) {
                    command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f", gearReturn.getX() + 0.5, gearReturn.getY(),
                            gearReturn.getZ() + 0.5));
                    command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                            gearReturn.getX() - 8, P3_FLOOR, gearReturn.getZ() - 4, gearReturn.getX() + 8, P3_FLOOR, gearReturn.getZ() + 12));
                }
                next(MAP_STAGE, 40);
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
                // 草の下に土を1段（構造物の足もとの砂利が、空へ落ちないように）
                command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:dirt",
                        o.getX() - 24, o.getY() - 1, o.getZ() - 24, o.getX() + 71, o.getY() - 1, o.getZ() + 63));
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
                } else if (buildingFrom(view + 1) != null) {
                    String name = buildingFrom(view + 1);
                    if (MARKET_OPEN.equals(name) || FREE_MORE.equals(name) || CHURCH_COUNSEL.equals(name)) {
                        overlay(minecraft, name);
                    } else if (STORAGE.equals(name)) {
                        replaceBuilding(minecraft, "guild_hall");
                        overlayAt(minecraft, STORAGE, STORAGE_AT);
                    } else if (FISH.equals(name)) {
                        // 水と桟橋と灯りを見せるので、日の高いうちに撮る
                        command(minecraft, "time set noon");
                        clearWide(minecraft, FISH_CLEAR);
                        overlayAt(minecraft, FISH, FISH_POND_AT);
                        overlayAt(minecraft, FISH_GATE, FISH_GATE_AT);
                        overlayAt(minecraft, FISH_ALLEY, FISH_ALLEY_AT);
                    } else if (FISH_LAKESIDE.equals(name)) {
                        overlayAt(minecraft, FISH_LAKESIDE, FISH_GATE_AT);
                    } else if (FISH_FROZEN.equals(name)) {
                        overlayAt(minecraft, FISH_FROZEN, FISH_POND_AT);
                    } else if (CASINO.equals(name)) {
                        // 正面の灯りと中の大きな吊り灯を見せるので、日の高いうちに撮る
                        command(minecraft, "time set noon");
                        clearWide(minecraft, FISH_CLEAR);
                        overlayAt(minecraft, CASINO, CASINO_AT);
                    } else if (CASINO_HIGHLOW.equals(name) || CASINO_FISHRACE.equals(name)) {
                        overlayAt(minecraft, name, CASINO_AT);
                    } else if (TOWER.equals(name)) {
                        // 白い石と、広場の結晶灯・転移門の輪を見せるので、日の高いうちに撮る
                        command(minecraft, "time set noon");
                        clearWide(minecraft, TOWER_CLEAR);
                        overlayAt(minecraft, TOWER, TOWER_AT);
                        overlayAt(minecraft, TOWER_PLAZA, TOWER_PLAZA_AT);
                    } else if (TOWER_MONUMENT.equals(name)) {
                        overlayAt(minecraft, TOWER_MONUMENT, TOWER_PLAZA_AT);
                    } else if (CHURCH.equals(name)) {
                        // 色ガラスと中の灯りを見せるので、日の高いうちに撮る
                        command(minecraft, "time set noon");
                        clearWide(minecraft, CHURCH_CLEAR);
                        overlayAt(minecraft, CHURCH, new int[]{0, 0, 0});
                        overlayAt(minecraft, CHURCH_GARDEN, CHURCH_GARDEN_AT);
                    } else if (FREE.equals(name)) {
                        // 屋根のない広場なので、日の高いうちに撮る（建物の写真は長いので、ここまでで日が傾いている）
                        command(minecraft, "time set noon");
                        clearWide(minecraft, FREE_CLEAR);
                        overlayAt(minecraft, FREE, new int[]{0, 0, 0});
                        overlayAt(minecraft, "market_south", FREE_SOUTH_AT);
                    } else {
                        replaceBuilding(minecraft, name);
                    }
                    mapView(view + 1);
                    // 置き替えた構造物の形を作り直して描くまで、長めに待つ
                    next(stage + 1, 300);
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
                    // 街全体の写真へ（透明な画面は開いたまま。撮り終えたら閉じる）
                    next(CITY, 40);
                }
            }
        }
    }

    /**
     * 街全体の写真（11月21日）：11施設を地図の位置に空に建て（{@code /amemaru admin city build at}）、上から・斜めから・各施設の前・
     * 夜・育ったあとの形を撮る。街の箱（x −144〜135、z −184〜127）の真ん中が、建物の写真の後に戻った所の真上に来るように建てる
     * （まわりの区画は読み込み済み。描く距離12でも、真上から街全体が入る）。
     */
    private static void cityStage(Minecraft minecraft) {
        int k = stage - CITY;
        if (k == 0) {
            clean(minecraft);
            hideHud = true;
            command(minecraft, "gamemode spectator @a");
            command(minecraft, "time set noon");
            command(minecraft, "weather clear");
            BlockPos at = minecraft.player.blockPosition();
            cityOrigin = new BlockPos(at.getX() + 4, CITY_Y, at.getZ() + 28);
            command(minecraft, String.format(Locale.ROOT, "tp @p %d %d %d 180 90", at.getX(), CITY_Y + 120, at.getZ()));
            next(CITY + 1, 100);
            return;
        }
        if (k == 1) {
            cityBuild(minecraft, false);
            next(CITY + 2, 20);
            return;
        }
        if (k == 2) {
            // 建て終わるまで待つ（サーバーは同じJVM。建てている間もクライアントは動く）。そのあと、光の計算と形の作り直しを待つ
            if (CityCommands.builds == cityBuilds && cityWaits++ < 150) {
                next(CITY + 2, 20);
            } else {
                next(CITY + 3, 600);
            }
            return;
        }
        int j = k - 3;
        if (j >= CITY_SHOTS.length * 2) {
            if (!cityDone) {
                cityDone = true;
                keepHold = false;
                hideHud = false;
                command(minecraft, "time set noon");
                if (returnPos != null) {
                    command(minecraft, String.format(Locale.ROOT, "tp @p %d %d %d",
                            returnPos.getX(), returnPos.getY(), returnPos.getZ()));
                }
                command(minecraft, "gamemode " + returnMode + " @a");
                minecraft.setScreen(null);
            }
            return;
        }
        String[] shot = CITY_SHOTS[j / 2];
        boolean first = j % 2 == 0;
        int waitTicks = Integer.parseInt(shot[2]);
        if (shot[1] != null) {
            if (first) {
                // CIのコマンドはサーバーとして動くので、移る人を @p で渡す
                command(minecraft, "amemaru admin city tp " + shot[1] + " @p");
                next(stage + 1, waitTicks);
            } else {
                clean(minecraft);
                pendingShot = shot[0];
                next(stage + 1, 2);
            }
        } else if ("night".equals(shot[0])) {
            if (first) {
                command(minecraft, "time set midnight");
            }
            next(stage + 1, first ? waitTicks : 0);
        } else if ("cmd".equals(shot[0])) {
            if (first) {
                command(minecraft, cityCommand(shot[3]));
            }
            next(stage + 1, first ? waitTicks : 0);
        } else if ("snap".equals(shot[0]) || "snap_keep".equals(shot[0])) {
            // その場で撮る（移らない）。snap_keep はチャットを消さない
            if (first) {
                next(stage + 1, waitTicks);
            } else {
                if ("snap".equals(shot[0])) {
                    clean(minecraft);
                }
                pendingShot = shot[3];
                next(stage + 1, 2);
            }
        } else if (shot[0].startsWith("await_")) {
            // 待つ：まだなら同じ行をもう一度（5 tick ごと。長く待っても来なければ、あきらめて次へ）。
            // await_mark は、予兆の形（行の3つ目の数）が出たらすぐ撮る（毎 tick 見る。短い予兆を逃さない。11月22日）
            boolean mark = "await_mark".equals(shot[0]) || "await_ground".equals(shot[0]);
            if (first) {
                if (!awaitReady(minecraft, shot) && awaitTicks < awaitLimit(shot[0])) {
                    int step = mark ? 1 : 5;
                    awaitTicks += step;
                    next(stage, step);
                } else {
                    if (awaitTicks >= awaitLimit(shot[0])) {
                        AmemaruMod.LOGGER.warn("[amemaru] CI: gave up waiting ({})", shot[0]);
                    }
                    awaitTicks = 0;
                    if (mark && shot.length > 3) {
                        clean(minecraft);
                        pendingShot = shot[3];
                        next(stage + 2, 2);
                    } else {
                        next(stage + 2, 0);
                    }
                }
            }
        } else if (CiPhase6.action(minecraft, shot, first) || CiPhase6Guild.action(minecraft, shot, first)
                || CiPhase6Refine.action(minecraft, shot, first) || CiPhase7.action(minecraft, shot, first)
                || CiPhase8.action(minecraft, shot, first)) {
            next(stage + 1, first ? waitTicks : 0);
        } else if (phase5Action(minecraft, shot, first)) {
            next(stage + 1, first ? waitTicks : 0);
        } else if (phase4Action(minecraft, shot, first)) {
            next(stage + 1, first ? waitTicks : 0);
        } else if (first) {
            // 建て直す：grown は育ったあとの形（はじめから建て直して、重ねる形も置く）、rebuild ははじめの形（書き出した直しを重ねる）
            command(minecraft, "time set noon");
            cityBuild(minecraft, "grown".equals(shot[0]));
            next(stage + 1, 20);
        } else if (CityCommands.builds == cityBuilds && cityWaits++ < 150) {
            next(stage, 20);
        } else {
            if (CityCommands.builds == cityBuilds) {
                // 建て直しが止まった（書き出していない直しがある、など）。続く写真は前の形のまま
                AmemaruMod.LOGGER.warn("[amemaru] CI: city build did not run ({})", shot[0]);
            }
            next(stage + 1, waitTicks);
        }
    }

    // ---------------------------------------------------------------- Phase 5 の写真の小物

    private static int awaitTicks;
    private static int floorBuilds;
    /** Phase 5 の写真の間（keep_hold の行から終わりまで）。画面が閉じたら、何も描かない画面を開き直す */
    private static boolean keepHold;

    /**
     * 画面を閉じるときに開く画面。CIで写真を撮っている間（keep_hold から）は、何も描かない画面（何もない画面になると、自動テストが
     * 写真の途中で始まるため）。ふだんは null（閉じる）。
     */
    public static net.minecraft.client.gui.screens.Screen screenAfterClose() {
        return ENABLED && keepHold && !cityDone ? new HoldScreen() : null;
    }

    /** CIの、何も描かない画面か（場面が始まるときに閉じない） */
    public static boolean isHoldScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof HoldScreen;
    }

    private static int awaitLimit(String kind) {
        return switch (kind) {
            // 2階（81区画）は1階より大きいので、5分まで待つ（Phase 8 その1）
            case "await_floor" -> 20 * 300;
            case "await_scene" -> 20 * 60;
            case "await_dialogue" -> 20 * 40;
            case "await_telegraph" -> 20 * 25;
            case "await_idle" -> 20 * 40;
            default -> 20 * 20;
        };
    }

    private static boolean awaitReady(Minecraft minecraft, String[] shot) {
        return switch (shot[0]) {
            case "await_floor" -> jp.amemaru.lostconnection.tower.FloorBuild.builds > floorBuilds;
            case "await_scene" -> ScenePlayer.isPlaying();
            case "await_dialogue" -> minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen;
            case "await_screen" -> minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.FloorSelectScreen;
            case "await_telegraph" -> CiPhase3.anyTelegraph(minecraft);
            // 行の3つ目の数が予兆の形（2 線・3 円・5 扇）
            case "await_mark" -> CiPhase3.anyTelegraphOf(minecraft, Integer.parseInt(shot[2]));
            // 地面の印（ボスの体でなく、地面に置く円。2階の根の檻など）が出た
            case "await_ground" -> CiPhase8.anyGroundMarker(minecraft);
            // オープニングの準備待ち（場面が始まる前の真っ暗な間）
            case "await_pending" -> ClientStory.openingPending() && !ScenePlayer.isActive();
            // 場面の字幕が出ている
            case "await_text" -> ClientStory.texts().stream()
                    .anyMatch(t -> t.kind() == jp.amemaru.lostconnection.network.s2c.StoryTextS2C.Kind.SUBTITLE);
            // 場面が終わった
            case "await_idle" -> !ScenePlayer.isActive();
            default -> true;
        };
    }

    /** Phase 5 の写真の段取り（階を建てる・会話を進める・階を選ぶ・石柱を起動する・記憶の再生）。その種類の行なら true。 */
    private static boolean phase5Action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("keep_hold", "p5_build_floor", "dialogue_end", "dialogue_choose", "dialogue_finish", "floor_select", "p5_pillar",
                "p5_pillar_try", "p5_switch", "p5_chest", "p5_replay").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        jp.amemaru.lostconnection.client.screen.DialogueScreen dialogue =
                minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen d ? d : null;
        switch (kind) {
            case "keep_hold" -> {
                keepHold = true;
                if (minecraft.screen == null) {
                    minecraft.setScreen(new HoldScreen());
                }
            }
            case "p5_build_floor" -> {
                // Phase 8 その1：4つ目に階の番号（ないときは1階）
                floorBuilds = jp.amemaru.lostconnection.tower.FloorBuild.builds;
                command(minecraft, "amemaru admin map build floor " + (shot.length > 3 ? shot[3] : "1"));
            }
            case "dialogue_end" -> {
                if (dialogue != null) {
                    dialogue.ciToEnd();
                }
            }
            case "dialogue_choose" -> {
                if (dialogue != null) {
                    dialogue.ciChoose(Integer.parseInt(shot[3]));
                }
            }
            case "dialogue_finish" -> {
                if (dialogue != null) {
                    dialogue.ciFinish();
                }
            }
            case "floor_select" -> {
                ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.FloorSelectC2S(Integer.parseInt(shot[3])));
                minecraft.setScreen(new HoldScreen());
            }
            case "p5_pillar", "p5_pillar_try" -> {
                // p5_pillar_try：課題を終える前に触れる（手がかりが出る）。p5_pillar：課題を終えたことにしてから触れる（11月22日）
                int index = Integer.parseInt(shot[3]);
                boolean wake = kind.equals("p5_pillar");
                CiPhase3.onServer(minecraft, player -> {
                    jp.amemaru.lostconnection.tower.FloorDef def = jp.amemaru.lostconnection.tower.FloorDefs.byNumber(1);
                    if (def != null && index < def.pillars().size()) {
                        if (wake) {
                            jp.amemaru.lostconnection.player.PlayerDataCapability.get(player)
                                    .ifPresent(data -> data.getStory().wakePillar(1, index));
                        }
                        jp.amemaru.lostconnection.tower.TowerService.activatePillar(player, 1, index,
                                def.world(def.pillars().get(index).pos()));
                    }
                });
            }
            case "p5_switch" -> {
                // 東の遺跡の仕掛け石を押す（11月22日）
                int index = Integer.parseInt(shot[3]);
                CiPhase3.onServer(minecraft, player -> {
                    jp.amemaru.lostconnection.tower.FloorDef def = jp.amemaru.lostconnection.tower.FloorDefs.byNumber(1);
                    if (def != null && !def.extras().switches().isEmpty()) {
                        jp.amemaru.lostconnection.tower.FloorDef.SwitchGroup group = def.extras().switches().get(0);
                        if (index < group.stones().size()) {
                            jp.amemaru.lostconnection.tower.TowerService.pressSwitch(player, 1, group.id(), index,
                                    def.world(group.stones().get(index)));
                        }
                    }
                });
            }
            case "p5_chest" -> {
                // 古代の宝箱を開ける（11月22日）
                int index = Integer.parseInt(shot[3]);
                CiPhase3.onServer(minecraft, player -> {
                    jp.amemaru.lostconnection.tower.FloorDef def = jp.amemaru.lostconnection.tower.FloorDefs.byNumber(1);
                    if (def != null && index < def.extras().chests().size()) {
                        jp.amemaru.lostconnection.tower.TowerService.openChest(player, 1, index,
                                def.world(def.extras().chests().get(index).pos()));
                    }
                });
            }
            default -> CiPhase3.onServer(minecraft, jp.amemaru.lostconnection.story.StoryEventManager::openReplayList);
        }
        return true;
    }

    // ---------------------------------------------------------------- Phase 4 の写真の小物

    /** 防具立てをダウンに見せている間、その「もの」の番号（見本） */
    private static int fakeDownId = -1;

    /** Phase 4 の写真の段取り（HUD・見本のパーティ・画面・視点・見本のダウン）。その種類の行なら true。 */
    private static boolean phase4Action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("hud", "party_preview", "party_clear", "screen", "view", "fake_down_near", "fake_reviving", "fake_clear")
                .contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "hud" -> hideHud = !"on".equals(shot[3]);
            case "party_preview" -> ClientParty.apply(samplePartyState(minecraft));
            case "party_clear" -> ClientParty.reset();
            case "screen" -> minecraft.setScreen("party".equals(shot[3])
                    ? new jp.amemaru.lostconnection.client.screen.PartyScreen() : new HoldScreen());
            case "view" -> minecraft.options.setCameraType("third".equals(shot[3]) ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            case "fake_down_near" -> fakeDown(minecraft, false);
            case "fake_reviving" -> fakeDown(minecraft, true);
            default -> {
                if (fakeDownId >= 0) {
                    ClientDown.apply(new jp.amemaru.lostconnection.network.s2c.DownStateS2C(fakeDownId, false, 0, 0, 0, 0, -1));
                }
                fakeDownId = -1;
            }
        }
        return true;
    }

    /** 見本のパーティ：自分（リーダー）・HPが減った人・ダウンした人・いない人と、届いている誘い。 */
    private static jp.amemaru.lostconnection.network.s2c.PartyStateS2C samplePartyState(Minecraft minecraft) {
        java.util.UUID self = minecraft.player.getUUID();
        java.util.function.Function<String, java.util.UUID> id = name ->
                java.util.UUID.nameUUIDFromBytes(("amemaru-ci:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Member> members = List.of(
                new jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Member(self, minecraft.player.getGameProfile().getName(),
                        true, true, 12, 0.92F, false, 0, 0, true),
                new jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Member(id.apply("aoi"), "Aoi", false, true, 14, 0.64F, false,
                        0, 0, true),
                new jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Member(id.apply("ren"), "Ren", false, true, 11, 0.0F, true,
                        360, 600, true),
                new jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Member(id.apply("kai"), "Kai", false, false, 0, 0.0F, false,
                        0, 0, false));
        List<jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Invite> invites = List.of(
                new jp.amemaru.lostconnection.network.s2c.PartyStateS2C.Invite(id.apply("kotone"), "Kotone", 900));
        return new jp.amemaru.lostconnection.network.s2c.PartyStateS2C(true, members, invites);
    }

    /** 近くの「Ren」という名前の防具立てを、ダウンしている人に見せる（reviving なら、自分が起こしている途中）。 */
    private static void fakeDown(Minecraft minecraft, boolean reviving) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        net.minecraft.world.entity.Entity found = null;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.getEntities(minecraft.player,
                minecraft.player.getBoundingBox().inflate(6.0))) {
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand && entity.hasCustomName()
                    && "Ren".equals(entity.getCustomName().getString())) {
                found = entity;
            }
        }
        if (found == null) {
            AmemaruMod.LOGGER.warn("[amemaru][ci] no armor stand named Ren near the player");
            return;
        }
        fakeDownId = found.getId();
        ClientDown.apply(new jp.amemaru.lostconnection.network.s2c.DownStateS2C(fakeDownId, true, reviving ? 340 : 360, 600,
                reviving ? 36 : 0, 60, reviving ? minecraft.player.getId() : -1));
    }

    /** CIのコマンドの {origin}（街の真ん中）と {guild}（ギルドの構造物の上の空。構造物の中の (1, 37, 1)。はじめは空気）を埋める */
    private static String cityCommand(String template) {
        BlockPos o = cityOrigin == null ? BlockPos.ZERO : cityOrigin;
        BlockPos guild = o.offset(6 + 1, 37, -100 + 1);
        String out = template.replace("{origin}", o.getX() + " " + o.getY() + " " + o.getZ())
                .replace("{guild}", guild.getX() + " " + guild.getY() + " " + guild.getZ());
        // {at:x,y,z}：街の真ん中からの位置。{f1:x,y,z}：塔の1階の (0, 0, 0) からの位置（Phase 5）。{f2:…} は2階（Phase 8 その1）
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(at|f[0-9]+):(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+)\\}").matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            BlockPos base = o;
            if (!"at".equals(m.group(1))) {
                int number = Integer.parseInt(m.group(1).substring(1));
                jp.amemaru.lostconnection.tower.FloorDef floor = jp.amemaru.lostconnection.tower.FloorDefs.byNumber(number);
                base = floor != null ? floor.origin() : new BlockPos(number * 4096 - 192, 60, -192);
            }
            String pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", base.getX() + Double.parseDouble(m.group(2)),
                    base.getY() + Double.parseDouble(m.group(3)), base.getZ() + Double.parseDouble(m.group(4)));
            m.appendReplacement(sb, pos);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void cityBuild(Minecraft minecraft, boolean grown) {
        BlockPos o = cityOrigin;
        if (o == null) {
            return;
        }
        cityBuilds = CityCommands.builds;
        cityWaits = 0;
        command(minecraft, String.format(Locale.ROOT, "amemaru admin city build at %d %d %d%s",
                o.getX(), o.getY(), o.getZ(), grown ? " grown force" : ""));
    }

    /** 建物の見本の写真 {@code view} から次の建物に移るなら、その構造物の名前（{@code map/conelia/} の後ろ）。移らないなら null。 */
    @Nullable
    private static String buildingFrom(int view) {
        if (view == WEAPON_FIRST) {
            return "weapon_workshop";
        }
        if (view == ARMOR_FIRST) {
            return "armor_workshop";
        }
        if (view == ACCESSORY_FIRST) {
            return "accessory_workshop";
        }
        if (view == MARKET_NORTH_FIRST) {
            return "market_north";
        }
        if (view == MARKET_OPEN_SHOT) {
            return MARKET_OPEN;
        }
        if (view == MARKET_SOUTH_FIRST) {
            return "market_south";
        }
        if (view == STORAGE_FIRST) {
            return STORAGE;
        }
        if (view == FREE_FIRST) {
            return FREE;
        }
        if (view == FREE_MORE_SHOT) {
            return FREE_MORE;
        }
        if (view == CHURCH_FIRST) {
            return CHURCH;
        }
        if (view == CHURCH_COUNSEL_SHOT) {
            return CHURCH_COUNSEL;
        }
        if (view == FISH_FIRST) {
            return FISH;
        }
        if (view == FISH_LAKESIDE_SHOT) {
            return FISH_LAKESIDE;
        }
        if (view == FISH_FROZEN_SHOT) {
            return FISH_FROZEN;
        }
        if (view == CASINO_FIRST) {
            return CASINO;
        }
        if (view == CASINO_HIGHLOW_SHOT) {
            return CASINO_HIGHLOW;
        }
        if (view == CASINO_FISHRACE_SHOT) {
            return CASINO_FISHRACE;
        }
        if (view == TOWER_FIRST) {
            return TOWER;
        }
        if (view == TOWER_MONUMENT_SHOT) {
            return TOWER_MONUMENT;
        }
        return null;
    }

    /** 今の建物の横に、構造物 {@code name} を {@code at}（建物を置いた所から）ずらして重ねて置く（個人倉庫。片づけない）。 */
    private static void overlayAt(Minecraft minecraft, String name, int[] at) {
        BlockPos o = mapOrigin;
        if (o == null) {
            return;
        }
        command(minecraft, "gamerule doTileDrops false");
        command(minecraft, String.format(Locale.ROOT, "place template amemaru:map/conelia/%s %d %d %d",
                name, o.getX() + at[0], o.getY() + at[1], o.getZ() + at[2]));
        command(minecraft, "gamerule doTileDrops true");
    }

    /** 今の建物の上に、構造物 {@code name} を同じ所から重ねて置く（寄付で開いた料理屋など。片づけない）。 */
    private static void overlay(Minecraft minecraft, String name) {
        BlockPos o = mapOrigin;
        if (o == null) {
            return;
        }
        command(minecraft, "gamerule doTileDrops false");
        command(minecraft, String.format(Locale.ROOT, "place template amemaru:map/conelia/%s %d %d %d",
                name, o.getX(), o.getY(), o.getZ()));
        command(minecraft, "gamerule doTileDrops true");
    }

    /**
     * 前の建物を片づけて（額縁などのもの・ブロック・足もとの石畳を草に戻す）、同じ所に次の建物（{@code name}）を置く。
     * 次の建物の写真に前の建物が映らないようにするため。片づける範囲は {@link #CLEAR_SIZE}（いちばん大きい構造物を包む）。
     * 1回の fill は 32768 ブロックまでなので、南北に2つに分けて片づける。
     */
    private static void replaceBuilding(Minecraft minecraft, String name) {
        BlockPos o = mapOrigin;
        if (o == null) {
            return;
        }
        int x0 = o.getX();
        int y0 = o.getY();
        int z0 = o.getZ();
        int x1 = x0 + CLEAR_SIZE[0] - 1;
        int y1 = y0 + CLEAR_SIZE[1] - 1;
        int z1 = z0 + CLEAR_SIZE[2] - 1;
        int zm = z0 + CLEAR_SIZE[2] / 2;
        // 2つに分けて片づけると、境目をまたいで掛かった灯りなどが支えを失って落ち、アイテムになって草の上に散らばる。
        // 片づけて置き直す間だけ、壊れたブロックがアイテムを落とさないようにする（終わったら元に戻す）
        command(minecraft, "gamerule doTileDrops false");
        command(minecraft, String.format(Locale.ROOT, "kill @e[type=!minecraft:player,x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]",
                x0, y0, z0, CLEAR_SIZE[0], CLEAR_SIZE[1], CLEAR_SIZE[2]));
        command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x0, y0 + 1, z0, x1, y1, zm - 1));
        command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x0, y0 + 1, zm, x1, y1, z1));
        command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", x0, y0, z0, x1, y0, z1));
        command(minecraft, String.format(Locale.ROOT, "place template amemaru:map/conelia/%s %d %d %d",
                name, x0, y0, z0));
        command(minecraft, "gamerule doTileDrops true");
    }

    /**
     * 建物を置いた所から {@code box}（東西・高さ・南北の始まりと終わり）の範囲を片づけ、土と草の地面を敷き直す（地下のある建物の後）。
     * 1回の fill は 32768 ブロックまでなので、南北に薄く分けて片づける。もの（額縁など）は先に消す。
     */
    private static void clearWide(Minecraft minecraft, int[] box) {
        BlockPos o = mapOrigin;
        if (o == null) {
            return;
        }
        int x0 = o.getX() + box[0];
        int y0 = o.getY() + box[1];
        int z0 = o.getZ() + box[2];
        int x1 = o.getX() + box[3];
        int y1 = o.getY() + box[4];
        int z1 = o.getZ() + box[5];
        int slice = Math.max(1, 32768 / ((x1 - x0 + 1) * (y1 - y0 + 1)));
        command(minecraft, "gamerule doTileDrops false");
        command(minecraft, String.format(Locale.ROOT, "kill @e[type=!minecraft:player,x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]",
                x0, y0, z0, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1));
        for (int z = z0; z <= z1; z += slice) {
            command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                    x0, y0, z, x1, y1, Math.min(z1, z + slice - 1)));
        }
        command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:dirt",
                x0, o.getY() - 1, z0, x1, o.getY() - 1, z1));
        command(minecraft, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block",
                x0, o.getY(), z0, x1, o.getY(), z1));
        command(minecraft, "gamerule doTileDrops true");
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

    /** 作り直した装備を、表示用のエンティティで横一列に並べる（南を向いて撮る。画面の左は東）。観戦モード・HUDなし。 */
    /** 防具立て4つ（重鎧の前・後ろ・法衣の前・後ろ）。前向きは front、後ろ向きは back の向き。 */
    private static void armorStands(Minecraft minecraft, float front, float back) {
        hideHud = true;
        command(minecraft, "gamemode spectator @a");
        command(minecraft, "kill @e[tag=amemaru_ci_gear]");
        if (gearReturn == null) {
            return;
        }
        double x = gearReturn.getX() + 0.5;
        double z = gearReturn.getZ() + 4.0;
        command(minecraft, CiGear.armoredStand("sentinel_blade", "ancient_stone_shield", CiGear.PLATE, x + 3.3, P3_FLOOR + 1, z, front));
        command(minecraft, CiGear.armoredStand("chipped_fang_greatsword", null, CiGear.PLATE, x + 1.1, P3_FLOOR + 1, z, back));
        command(minecraft, CiGear.armoredStand("bee_crystal_staff", null, CiGear.ROBE, x - 1.1, P3_FLOOR + 1, z, front));
        command(minecraft, CiGear.armoredStand("ancient_stone_staff", null, CiGear.ROBE, x - 3.3, P3_FLOOR + 1, z, back));
        command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %.1f %.1f 0 8", x, P3_FLOOR + 1.0, gearReturn.getZ() + 0.8));
    }

    /** 剣・大剣の3つを、防具立てで横から撮る用意（間は3.4ブロック。刃の先は西＝画面の右）。 */
    private static void holdSide(Minecraft minecraft, String[] blades) {
        hideHud = true;
        command(minecraft, "gamemode spectator @a");
        command(minecraft, "kill @e[tag=amemaru_ci_gear]");
        if (gearReturn == null) {
            return;
        }
        double x = gearReturn.getX() + 0.5;
        double z = gearReturn.getZ() + 5.5;
        for (int k = 0; k < blades.length; k++) {
            command(minecraft, CiGear.holdingStand(blades[k], x + 4.2 - 3.4 * k, P3_FLOOR + 1, z, 90.0F));
        }
        command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 10", x - 0.1, P3_FLOOR + 1, gearReturn.getZ() + 0.5));
    }

    private static void lineup(Minecraft minecraft, float yaw) {
        lineup(minecraft, yaw, CiGear.REDESIGN_1, 1.6);
    }

    private static void lineup(Minecraft minecraft, float yaw, java.util.List<CiGear.Piece> pieces, double step) {
        hideHud = true;
        command(minecraft, "gamemode spectator @a");
        if (gearReturn == null) {
            return;
        }
        double x = gearReturn.getX() + 0.5;
        double z = gearReturn.getZ() + 0.5;
        command(minecraft, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0", x, P3_FLOOR + 1, z));
        int n = pieces.size();
        for (int k = 0; k < n; k++) {
            double px = x + step * (n - 1) / 2.0 - step * k;
            command(minecraft, CiGear.display(pieces.get(k), px, P3_FLOOR + 2.4, z + 7.5, yaw, 1.0F));
        }
    }

    /** 建物の写真の間は、HUDを描かない（開いたままの透明な画面があるので、F1 と同じ設定では消えない）。 */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ENABLED && hideHud) {
            event.setCanceled(true);
        }
    }

    /** 写真のじゃまになる、チャットの文と右上の小窓（実績・レシピ）を消す。 */
    /** スキルツリーの撮影の、何枚目か（11月24日）。 */
    private static int treeShot;

    /**
     * 撮影の見本：取れている節のとなりの、ステータスの節を {@code count} 個まで取る（サーバーが確かめる。11月24日）。
     * 核に近い順に取るので、核から道がのびて見える。
     */
    private static void takeTreeNodesForShot(int count) {
        ClientPlayerState state = ClientPlayerState.get();
        jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeInfo tree = ClientDefinitions.get().tree(state.jobIndex());
        if (tree == null) {
            return;
        }
        List<jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeNodeInfo> nodes = tree.nodes();
        boolean[] taken = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeNodeInfo node = nodes.get(i);
            taken[i] = switch (node.kindEnum()) {
                case CORE -> true;
                case SKILL -> node.skill() >= 0 && state.rank(node.skill()) > 0;
                default -> state.hasTreeNode(node.id());
            };
        }
        int[] links = tree.links();
        for (int sent = 0; sent < count; sent++) {
            int best = -1;
            for (int k = 0; k + 1 < links.length; k += 2) {
                for (int side = 0; side < 2; side++) {
                    int from = links[k + side];
                    int to = links[k + 1 - side];
                    if (from < 0 || to < 0 || from >= nodes.size() || to >= nodes.size() || !taken[from] || taken[to]) {
                        continue;
                    }
                    jp.amemaru.lostconnection.job.def.SkillTreeDef.Kind kind = nodes.get(to).kindEnum();
                    if (kind != jp.amemaru.lostconnection.job.def.SkillTreeDef.Kind.STAT
                            && kind != jp.amemaru.lostconnection.job.def.SkillTreeDef.Kind.NOTABLE) {
                        continue;
                    }
                    if (best < 0 || Math.abs(nodes.get(to).y()) < Math.abs(nodes.get(best).y())) {
                        best = to;
                    }
                }
            }
            if (best < 0) {
                return;
            }
            taken[best] = true;
            ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.TakeTreeNodeC2S(nodes.get(best).id()));
        }
    }

    /** 今の職業のツリーの、その種類の最初の節の名前（なければ空）。 */
    private static String firstTreeNode(jp.amemaru.lostconnection.job.def.SkillTreeDef.Kind kind) {
        jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeInfo tree = ClientDefinitions.get().tree(ClientPlayerState.get().jobIndex());
        if (tree == null) {
            return "";
        }
        for (jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeNodeInfo node : tree.nodes()) {
            if (node.kindEnum() == kind) {
                return node.id();
            }
        }
        return "";
    }

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

    /**
     * 何も描かない画面にする（Phase 6 その4 の段取りから）。画面が空（null）になると、mc-runtime-test がその間に自動テストを
     * 始めて、サーバーが遅れる（run 163 で、依頼を出す前に「出した依頼」を撮ってしまった）。画面を閉じたら、同じ tick でこれにする。
     */
    /** 街の真ん中（建てる前は null）。 */
    @Nullable
    static BlockPos cityOrigin() {
        return cityOrigin;
    }

    static void hold(Minecraft minecraft) {
        minecraft.setScreen(new HoldScreen());
    }

    /** サーバーとしてコマンドを打つ（Phase 6 その4 の段取りから）。 */
    static void runCommand(Minecraft minecraft, String command) {
        command(minecraft, command);
    }

    private static void command(Minecraft minecraft, String command) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
}
