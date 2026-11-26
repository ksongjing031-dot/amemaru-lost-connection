package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.casino.CasinoRecord;
import jp.amemaru.lostconnection.casino.CasinoRules;
import jp.amemaru.lostconnection.casino.parlor.DailySettings;
import jp.amemaru.lostconnection.casino.parlor.MachineRecord;
import jp.amemaru.lostconnection.casino.parlor.PachinkoEngine;
import jp.amemaru.lostconnection.casino.parlor.PachinkoSpec;
import jp.amemaru.lostconnection.casino.parlor.ParlorModels;
import jp.amemaru.lostconnection.casino.parlor.ParlorRules;
import jp.amemaru.lostconnection.casino.parlor.ParlorService;
import jp.amemaru.lostconnection.casino.parlor.ReelControl;
import jp.amemaru.lostconnection.casino.parlor.SlotEngine;
import jp.amemaru.lostconnection.casino.parlor.SlotSpec;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * カジノの地下遊技場（11月27日のご指示「ルーレットとハイ＆ロー以外を廃止してパチンコとスロットを追加する」「本物と同じように演出や
 * 日にちごとに代わるあたり台と外れ台」「本物ように設定を作る」「専用のＧＵＩから遊べる」「少し多めに用意する」「地下なども用意する」）。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParlorGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final List<String> MODELS = List.of("p_tower", "p_forest", "p_lake", "s_knot", "s_star", "s_moon");

    private ParlorGameTests() {
    }

    /** 6機種（パチンコ3・スロット3）が読める。スロットは3つのリールが同じ長さ、パチンコは大当たりの表と釘の幅がある。 */
    @GameTest(template = EMPTY)
    public static void machineModelsLoad(GameTestHelper helper) {
        check(helper, ParlorModels.loader().errors().isEmpty(), "台の機種が読めません：" + ParlorModels.loader().errors());
        int slots = 0;
        int pachinko = 0;
        for (String id : MODELS) {
            ParlorModels.Model model = ParlorModels.get(id);
            check(helper, model != null, "機種がありません：" + id);
            if (model.isSlot()) {
                slots++;
                SlotSpec spec = model.slot();
                check(helper, spec != null && spec.bet() > 0, id + "：スロットの決まりがありません");
                for (int r = 0; r < 3; r++) {
                    check(helper, spec.reel(r).length() == spec.reelLength(), id + "：リールの長さがそろっていません");
                }
                if (spec.type() == SlotSpec.Type.A) {
                    check(helper, spec.bonus("big") != null && spec.bonus("reg") != null, id + "：BIG・REG がありません");
                } else {
                    check(helper, spec.at() != null && spec.at().ceiling() > 0 && spec.at().initial() > 0, id + "：AT の決まりがありません");
                }
            } else {
                pachinko++;
                PachinkoSpec spec = model.pachinko();
                check(helper, spec != null && !spec.jackpots().isEmpty(), id + "：大当たりの表がありません");
                double[] normal = spec.nails(false);
                double[] event = spec.nails(true);
                check(helper, normal[0] < normal[1] && event[0] < event[1] && event[1] >= normal[1], id + "：釘の幅がおかしい");
                for (int setting = 1; setting <= 6; setting++) {
                    check(helper, spec.normal(setting) > spec.high(), id + "：通常の確率が確変・ST より甘くなっています");
                }
            }
        }
        check(helper, slots == 3 && pachinko == 3, "パチンコ3・スロット3ではありません：" + pachinko + "・" + slots);
        helper.succeed();
    }

    /** 台の並び：72台（地下48・2階の新台の間24）。台番号は4と9を使わず、重ならない。どの台も機種があり、台・受け皿・島の掲示板から引ける。 */
    @GameTest(template = EMPTY)
    public static void parlorLayoutIsComplete(GameTestHelper helper) {
        ParlorRules rules = CasinoRules.current().parlor();
        check(helper, rules.machines().size() == 72, "台が72台ではありません：" + rules.machines().size());
        check(helper, rules.ballsPerChip() == 25 && rules.medalsPerChip() == 5, "玉・メダルの貸し出しの数がちがいます");
        int base = 0;
        int fresh = 0;
        Set<Integer> numbers = new HashSet<>();
        for (ParlorRules.Machine m : rules.machines()) {
            String no = String.valueOf(m.no());
            check(helper, !no.contains("4") && !no.contains("9"), "4・9のつく台番号があります：" + no);
            check(helper, numbers.add(m.no()), "台番号が重なっています：" + no);
            check(helper, ParlorModels.get(m.model()) != null, no + "番台の機種がありません：" + m.model());
            ParlorRules.Island island = rules.island(m.island());
            check(helper, island != null && island.model().equals(m.model()) && island.newFloor() == m.newFloor(), no + "番台の島が合いません");
            check(helper, m.equals(rules.machineAt(m.pos())) && m.equals(rules.machineAt(m.pos().below())), no + "番台を台・受け皿から引けません");
            check(helper, m.equals(rules.machine(m.no())), no + "番台を台番号から引けません");
            if (m.newFloor()) {
                fresh++;
            } else {
                base++;
            }
        }
        check(helper, base == 48 && fresh == 24, "地下48台・新台の間24台ではありません：" + base + "・" + fresh);
        for (ParlorRules.Island island : rules.islands()) {
            check(helper, !rules.machinesOf(island.id()).isEmpty(), island.id() + "：島に台がありません");
            check(helper, !island.boards().isEmpty(), island.id() + "：島のデータの掲示板がありません");
            for (BlockPos board : island.boards()) {
                check(helper, island.equals(rules.boardAt(board)), island.id() + "：掲示板から島を引けません");
            }
        }
        helper.succeed();
    }

    /**
     * リール制御：引いていない役はそろわない（はずれ・小役・押し順のちがう押し順ベル・持ち越したボーナス）。
     * 持ち越したボーナスは、7をねらって押せばそろう（目押し）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void reelControlNeverShowsUndrawnRoles(GameTestHelper helper) {
        SplittableRandom rnd = new SplittableRandom(27);
        for (String id : List.of("s_knot", "s_star", "s_moon")) {
            SlotSpec spec = ParlorModels.get(id).slot();
            int n = spec.reelLength();
            List<String> cases = new ArrayList<>();
            cases.add("");
            for (SlotSpec.Role role : spec.roles()) {
                cases.add(role.id());
            }
            for (String c : cases) {
                SlotSpec.Role role = spec.role(c);
                for (int s = 0; s < 1500; s++) {
                    SlotEngine.State st = new SlotEngine.State();
                    SlotEngine.Game g = new SlotEngine.Game();
                    if (role != null && role.isBonus()) {
                        st.carried = role.bonus();
                    } else {
                        g.role = c;
                    }
                    if (role != null && role.kind() == SlotSpec.RoleKind.ORDER) {
                        g.order = rnd.nextInt(SlotEngine.ORDERS.length);
                    }
                    st.game = g;
                    int[] order = SlotEngine.ORDERS[rnd.nextInt(SlotEngine.ORDERS.length)];
                    for (int k = 0; k < 3; k++) {
                        SlotEngine.stop(spec, st, order[k], rnd.nextInt(n));
                    }
                    int done = ReelControl.completed(spec, g.pos);
                    int want = role == null ? -1 : spec.groupOf(role);
                    boolean orderOk = role == null || role.kind() != SlotSpec.RoleKind.ORDER
                            || Arrays.equals(order, SlotEngine.ORDERS[g.order]);
                    int allowed = want >= 0 && orderOk ? 1 << want : 0;
                    check(helper, (done & ~allowed) == 0, id + "：" + (c.isEmpty() ? "はずれ" : c) + " で、引いていない役がそろいました "
                            + Arrays.toString(g.pos) + " 押し順 " + Arrays.toString(order));
                }
            }
            for (SlotSpec.Role role : spec.roles()) {
                if (!role.isBonus()) {
                    continue;
                }
                for (int s = 0; s < 200; s++) {
                    SlotEngine.State st = new SlotEngine.State();
                    st.carried = role.bonus();
                    st.game = new SlotEngine.Game();
                    for (int r = 0; r < 3; r++) {
                        SlotEngine.stop(spec, st, r, aimPress(spec, st, r, role.pattern().charAt(r), rnd));
                    }
                    SlotEngine.Result result = SlotEngine.finish(spec, st);
                    check(helper, role.bonus().equals(result.bonusStarted()), id + "：" + role.bonus() + " をねらって押してもそろいません");
                }
            }
        }
        helper.succeed();
    }

    /** Aタイプ（ノットランプ）：持ち越した BIG を7でそろえると始まり、ボーナス中の掛け枚数で遊び、払い出しが上限に来ると終わる。 */
    @GameTest(template = EMPTY)
    public static void aTypeBigRunsToItsCap(GameTestHelper helper) {
        SlotSpec spec = ParlorModels.get("s_knot").slot();
        SplittableRandom rnd = new SplittableRandom(7);
        SlotEngine.State st = new SlotEngine.State();
        st.carried = "big";
        st.lamp = true;
        SlotEngine.lever(spec, st, rnd);
        st.game.role = "";
        SlotSpec.Role bigRole = null;
        for (SlotSpec.Role role : spec.roles()) {
            if (role.isBonus() && "big".equals(role.bonus())) {
                bigRole = role;
            }
        }
        check(helper, bigRole != null, "BIG の役がありません");
        for (int r = 0; r < 3; r++) {
            SlotEngine.stop(spec, st, r, aimPress(spec, st, r, bigRole.pattern().charAt(r), rnd));
        }
        SlotEngine.Result first = SlotEngine.finish(spec, st);
        check(helper, "big".equals(first.bonusStarted()) && "big".equals(st.bonus) && st.carried.isEmpty() && !st.lamp,
                "7をそろえても BIG が始まりません：" + first.aligned());
        SlotSpec.Bonus big = spec.bonus("big");
        int games = 0;
        String ended = "";
        while (ended.isEmpty() && games < 500) {
            check(helper, SlotEngine.betFor(spec, st) == big.bet(), "ボーナス中の掛け枚数がちがいます");
            SlotEngine.lever(spec, st, rnd);
            for (int r = 0; r < 3; r++) {
                SlotEngine.stop(spec, st, r, rnd.nextInt(spec.reelLength()));
            }
            SlotEngine.Result result = SlotEngine.finish(spec, st);
            check(helper, result.bonusGame(), "ボーナス中のゲームになっていません");
            ended = result.bonusEnded();
            games++;
        }
        check(helper, "big".equals(ended) && st.bonus.isEmpty(), "BIG が終わりません");
        check(helper, st.bonusPaid >= big.cap(), "BIG の払い出しが上限に届いていません：" + st.bonusPaid);
        helper.succeed();
    }

    /** AT（星詠みの塔）：天井のゲーム数で AT に入る。AT の間はナビどおりに押すと押し順ベルがそろい、ゲーム数が尽きると終わる。 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void atMachineReachesCeilingAndRush(GameTestHelper helper) {
        SlotSpec spec = ParlorModels.get("s_star").slot();
        SplittableRandom rnd = new SplittableRandom(11);
        SlotEngine.State st = new SlotEngine.State();
        st.sinceAt = spec.at().ceiling() - 1;
        SlotEngine.Start start = SlotEngine.lever(spec, st, rnd);
        check(helper, start.events().contains("ceiling") && start.events().contains("at_start"), "天井で AT に入りません：" + start.events());
        for (int r = 0; r < 3; r++) {
            SlotEngine.stop(spec, st, r, rnd.nextInt(spec.reelLength()));
        }
        SlotEngine.finish(spec, st);
        check(helper, st.atGames == spec.at().initial() && st.sinceAt == 0, "AT のゲーム数がちがいます：" + st.atGames);
        int games = 0;
        int navBells = 0;
        int bellsAligned = 0;
        boolean ended = false;
        while (!ended && games < 5000) {
            SlotEngine.Start s = SlotEngine.lever(spec, st, rnd);
            int nav = s.navOrder();
            int[] order = nav >= 0 ? SlotEngine.ORDERS[nav] : SlotEngine.ORDERS[0];
            String role = st.game.role;
            for (int k = 0; k < 3; k++) {
                SlotEngine.stop(spec, st, order[k], rnd.nextInt(spec.reelLength()));
            }
            SlotEngine.Result result = SlotEngine.finish(spec, st);
            if (nav >= 0) {
                navBells++;
                if (role.equals(result.aligned())) {
                    bellsAligned++;
                }
            }
            ended = result.events().stream().anyMatch(e -> e.startsWith("at_end:"));
            games++;
        }
        check(helper, ended && st.atGames == 0, "AT が終わりません");
        check(helper, games >= spec.at().initial(), "AT が早く終わりすぎます：" + games);
        check(helper, navBells > 0 && bellsAligned * 100 >= navBells * 95, "ナビどおりに押しても押し順ベルがそろいません：" + bellsAligned + "/" + navBells);
        helper.succeed();
    }

    /** パチンコ（輝きの塔）：当たりの保留を回すと大当たり。右打ちで10ラウンドを消化して確変に入る。払い出しはラウンド×カウント×賞球。 */
    @GameTest(template = EMPTY)
    public static void pachinkoJackpotRunsItsRounds(GameTestHelper helper) {
        PachinkoSpec spec = ParlorModels.get("p_tower").pachinko();
        SplittableRandom rnd = new SplittableRandom(3);
        PachinkoEngine.State st = new PachinkoEngine.State();
        st.heso.add(new PachinkoEngine.Hold(true, "k10", 4, false));
        for (int t = 0; t < 5000 && st.round == null; t++) {
            PachinkoEngine.tick(spec, st, rnd);
        }
        check(helper, st.round != null && "k10".equals(st.round.jackpot) && st.chain == 1, "当たりの保留で大当たりが始まりません");
        String ended = "";
        int paid = 0;
        long net = 0;
        for (int t = 0; t < 40000 && ended.isEmpty(); t++) {
            if (t % PachinkoEngine.SHOT_TICKS == 0) {
                PachinkoEngine.Shot shot = PachinkoEngine.shoot(spec, st, 70, true, rnd);
                net += shot.back() - shot.used();
            }
            PachinkoEngine.Tick tick = PachinkoEngine.tick(spec, st, rnd);
            if (!tick.jackpotEnd().isEmpty()) {
                ended = tick.jackpotEnd();
                paid = tick.jackpotPaid();
            }
        }
        PachinkoSpec.Jackpot jackpot = spec.jackpot("k10");
        check(helper, "k10".equals(ended), "大当たりが終わりません");
        check(helper, paid == jackpot.rounds() * spec.count() * spec.ballPay(), "大当たりの払い出しがちがいます：" + paid);
        check(helper, net > 0, "右打ちしても玉が増えません：" + net);
        check(helper, st.round == null && st.mode == PachinkoEngine.Mode.KAKUHEN, "確変に入りません：" + st.mode);
        helper.succeed();
    }

    /** 大当たりの最中に台を離れたら、残りのラウンドを右打ちしたものとして消化する（打った玉を引いた分を返す。2度は払わない）。 */
    @GameTest(template = EMPTY)
    public static void pachinkoSettlesALeftJackpot(GameTestHelper helper) {
        PachinkoSpec spec = ParlorModels.get("p_tower").pachinko();
        SplittableRandom rnd = new SplittableRandom(5);
        PachinkoEngine.State st = new PachinkoEngine.State();
        st.heso.add(new PachinkoEngine.Hold(true, "k4", 4, false));
        for (int t = 0; t < 5000 && st.round == null; t++) {
            PachinkoEngine.tick(spec, st, rnd);
        }
        check(helper, st.round != null, "大当たりが始まりません");
        PachinkoSpec.Jackpot jackpot = spec.jackpot("k4");
        int back = PachinkoEngine.settleRound(spec, st);
        check(helper, back == jackpot.rounds() * spec.count() * (spec.ballPay() - 1), "消化した玉の数がちがいます：" + back);
        check(helper, PachinkoEngine.settleRound(spec, st) == 0, "残りのラウンドを2度払いました");
        String ended = "";
        for (int t = 0; t < 200 && ended.isEmpty(); t++) {
            ended = PachinkoEngine.tick(spec, st, rnd).jackpotEnd();
        }
        check(helper, "k4".equals(ended) && st.mode == PachinkoEngine.Mode.KAKUHEN, "消化したあと確変に入りません");
        helper.succeed();
    }

    /** 釘とヘソ：R = 250q ÷（1 − 戻り×q）が釘の回転数と合い、打てばその見込みでヘソに入る。ハンドルは 55〜80 がいちばん良い。 */
    @GameTest(template = EMPTY)
    public static void nailsMatchSpinsPerThousandYen(GameTestHelper helper) {
        for (String id : List.of("p_tower", "p_forest", "p_lake")) {
            PachinkoSpec spec = ParlorModels.get(id).pachinko();
            for (double nail : new double[]{14, 18, 22}) {
                double q = PachinkoEngine.hesoChance(spec, nail);
                double r = 250 * q / (1 - spec.hesoPay() * q);
                check(helper, Math.abs(r - nail) < 1.0E-6, id + "：釘 " + nail + " の回転数が合いません：" + r);
            }
        }
        check(helper, PachinkoEngine.stroke(70) == 1.0 && PachinkoEngine.stroke(55) == 1.0 && PachinkoEngine.stroke(80) == 1.0
                && PachinkoEngine.stroke(30) < 1.0 && PachinkoEngine.stroke(95) < 1.0 && PachinkoEngine.stroke(5) == 0.0,
                "ハンドルの強さの効き方がちがいます");
        PachinkoSpec spec = ParlorModels.get("p_tower").pachinko();
        PachinkoEngine.State st = new PachinkoEngine.State();
        st.nail = 18.0;
        SplittableRandom rnd = new SplittableRandom(18);
        int shots = 40000;
        int in = 0;
        for (int i = 0; i < shots; i++) {
            if (PachinkoEngine.shoot(spec, st, 70, false, rnd).heso()) {
                in++;
            }
            st.heso.clear();
        }
        double q = PachinkoEngine.hesoChance(spec, 18.0);
        check(helper, Math.abs(in / (double) shots - q) < q * 0.1, "ヘソに入る割合が見込みとちがいます：" + in + "/" + shots + "（見込み " + q + "）");
        helper.succeed();
    }

    /** 日ごとの設定：同じ日・同じ台なら何度見ても同じ（1〜6）。7のつく日はイベントの日で、高い設定と広い釘が多く、当たりの末尾の台は設定が上がる。 */
    @GameTest(template = EMPTY)
    public static void dailySettingsFavourLuckyKnotDays(GameTestHelper helper) {
        check(helper, DailySettings.isEvent("2026-11-27") && DailySettings.isEvent("2026-12-07") && DailySettings.isEvent("2026-12-17")
                && !DailySettings.isEvent("2026-11-26") && !DailySettings.isEvent("2026-11-30"), "7のつく日の見分けがちがいます");
        check(helper, DailySettings.luckyDigit(1L, "2026-11-26") == -1, "イベントの日でないのに当たりの末尾があります");
        List<ParlorRules.Machine> machines = CasinoRules.current().parlor().machines();
        PachinkoSpec tower = ParlorModels.get("p_tower").pachinko();
        double normal = 0;
        double event = 0;
        double normalNail = 0;
        double eventNail = 0;
        int n = 0;
        for (long salt = 1; salt <= 150; salt++) {
            int lucky = DailySettings.luckyDigit(salt, "2026-11-27");
            check(helper, lucky >= 0 && lucky <= 9, "当たりの末尾が 0〜9 ではありません：" + lucky);
            for (ParlorRules.Machine m : machines) {
                int a = DailySettings.setting(salt, "2026-11-26", m.no());
                int b = DailySettings.setting(salt, "2026-11-27", m.no());
                check(helper, a == DailySettings.setting(salt, "2026-11-26", m.no()), "同じ日なのに設定が変わります");
                check(helper, a >= 1 && a <= 6 && b >= 1 && b <= 6, "設定が 1〜6 ではありません");
                if (m.no() % 10 == lucky) {
                    check(helper, b >= 3, "当たりの末尾の台の設定が上がっていません");
                }
                normal += a;
                event += b;
                normalNail += DailySettings.nail(salt, "2026-11-26", m.no(), tower);
                eventNail += DailySettings.nail(salt, "2026-11-27", m.no(), tower);
                n++;
            }
        }
        check(helper, event / n > normal / n + 0.8, "イベントの日の設定が高くなっていません：" + event / n + " / " + normal / n);
        check(helper, eventNail / n > normalNail / n, "イベントの日の釘が広くなっていません");
        int same = 0;
        for (ParlorRules.Machine m : machines) {
            if (DailySettings.setting(9L, "2026-11-25", m.no()) == DailySettings.setting(9L, "2026-11-26", m.no())) {
                same++;
            }
        }
        check(helper, same < machines.size(), "日が変わっても、どの台も設定が変わりません");
        helper.succeed();
    }

    /** 台の中の様子とデータカウンターは保存して読みなおせる。日が変わると、今日の分は前の日のまとめに送る。 */
    @GameTest(template = EMPTY)
    public static void machineStateSurvivesSaving(GameTestHelper helper) {
        SlotEngine.State slot = new SlotEngine.State();
        slot.setting = 5;
        slot.carried = "reg";
        slot.lamp = true;
        slot.atGames = 12;
        slot.sinceAt = 345;
        slot.replay = true;
        SlotEngine.State slot2 = new SlotEngine.State();
        slot2.load(slot.save());
        check(helper, slot2.setting == 5 && "reg".equals(slot2.carried) && slot2.lamp && slot2.atGames == 12 && slot2.sinceAt == 345
                && slot2.replay, "スロットの様子を読みなおせません");
        PachinkoEngine.State p = new PachinkoEngine.State();
        p.setting = 4;
        p.nail = 21.5;
        p.mode = PachinkoEngine.Mode.ST;
        p.modeSpins = 77;
        p.lt = true;
        p.chain = 6;
        p.heso.add(new PachinkoEngine.Hold(false, "", 1, false));
        p.denchu.add(new PachinkoEngine.Hold(true, "s10", 5, true));
        PachinkoEngine.State p2 = new PachinkoEngine.State();
        p2.load(p.save());
        check(helper, p2.setting == 4 && Math.abs(p2.nail - 21.5) < 1.0E-9 && p2.mode == PachinkoEngine.Mode.ST && p2.modeSpins == 77
                && p2.lt && p2.chain == 6 && p2.heso.size() == 1 && p2.denchu.size() == 1 && p2.denchu.get(0).hit()
                && "s10".equals(p2.denchu.get(0).jackpot()), "パチンコの様子を読みなおせません");
        MachineRecord record = new MachineRecord();
        record.roll("2026-11-26", true);
        for (int i = 0; i < 30; i++) {
            record.slotGame(3, i % 3 == 0 ? 8 : 0, false);
        }
        record.slotHit("big");
        MachineRecord record2 = new MachineRecord();
        record2.load(record.save());
        check(helper, record2.games == 30 && record2.big == 1 && record2.diff == record.diff && record2.hitList.size() == 1
                && "2026-11-26".equals(record2.day), "データカウンターを読みなおせません");
        check(helper, record2.roll("2026-11-27", true) && record2.games == 0 && record2.history.size() == 1
                && record2.history.get(0).games() == 30, "日が変わっても、前の日のまとめに送られません");
        helper.succeed();
    }

    /** 交換所：玉は25個・メダルは5枚でチップ1枚（等価）。チップにならない端数は手もとに残る。 */
    @GameTest(template = EMPTY)
    public static void exchangeIsEqualAndKeepsRemainders(GameTestHelper helper) {
        FakePlayer player = fake(helper, "parlor_cash");
        CasinoRecord rec = data(helper, player).getCasino();
        rec.setChips(0);
        rec.setBalls(1010);
        rec.setMedals(23);
        ParlorService.cashout(player, "balls");
        check(helper, rec.chips() == 40 && rec.balls() == 10 && rec.medals() == 23, "玉をチップに戻せません：" + rec.chips());
        ParlorService.cashout(player, "all");
        check(helper, rec.chips() == 44 && rec.balls() == 10 && rec.medals() == 3, "メダルをチップに戻せません：" + rec.chips());
        ParlorService.cashout(player, "all");
        check(helper, rec.chips() == 44 && rec.balls() == 10 && rec.medals() == 3, "端数がチップになりました");
        helper.succeed();
    }

    /** 台に座る：座っている人の台に、ほかの人は座れない（データだけ見られる）。チップで玉を借りて戻せる。離れると空く。 */
    @GameTest(template = EMPTY)
    public static void seatsBelongToTheOccupant(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ParlorRules rules = CasinoRules.current().parlor();
        int no = machineOf(rules, "p_forest", 2);
        check(helper, no > 0 && ParlorService.reset(server, no), "結び目の森の台がありません");
        FakePlayer a = fake(helper, "pl_seat_a");
        FakePlayer b = fake(helper, "pl_seat_b");
        ParlorService.open(a, no, true);
        ParlorService.Live live = ParlorService.live(server, no);
        check(helper, live != null && a.getUUID().equals(live.occupant), "台に座れません");
        ParlorService.open(b, no, true);
        check(helper, a.getUUID().equals(live.occupant), "ほかの人の台に座れてしまいます");
        CasinoRecord rec = data(helper, a).getCasino();
        rec.setChips(100);
        rec.setBalls(0);
        int lend = rules.lend().get(0);
        CompoundTag args = new CompoundTag();
        args.putInt("no", no);
        args.putInt("chips", lend);
        ParlorService.onAction(a, "lend", args);
        check(helper, rec.chips() == 100 - lend && rec.balls() == (long) lend * rules.ballsPerChip(), "チップで玉を借りられません");
        ParlorService.onAction(b, "lend", args);
        check(helper, rec.balls() == (long) lend * rules.ballsPerChip(), "ほかの人が玉を借りられてしまいます");
        ParlorService.onAction(a, "cashout", args);
        check(helper, rec.chips() == 100 && rec.balls() == 0, "玉をチップに戻せません");
        ParlorService.onAction(a, "leave", args);
        check(helper, live.occupant == null, "台を離れても空きません");
        ParlorService.open(b, no, true);
        check(helper, b.getUUID().equals(live.occupant), "空いた台に座れません");
        ParlorService.onAction(b, "leave", args);
        helper.succeed();
    }

    /** スロットの台：レバーで掛け枚数を引き、3つ止めるとゲームが終わって払い出し、データカウンターに数える。 */
    @GameTest(template = EMPTY)
    public static void slotGamePlaysThroughTheService(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ParlorRules rules = CasinoRules.current().parlor();
        int no = machineOf(rules, "s_knot", 4);
        check(helper, no > 0 && ParlorService.reset(server, no), "ノットランプの台がありません");
        FakePlayer player = fake(helper, "parlor_slot");
        ParlorService.open(player, no, true);
        ParlorService.Live live = ParlorService.live(server, no);
        check(helper, live != null && player.getUUID().equals(live.occupant), "台に座れません");
        CasinoRecord rec = data(helper, player).getCasino();
        rec.setMedals(30);
        int gamesBefore = live.record.games;
        int bet = SlotEngine.betFor(live.model.slot(), live.slot);
        CompoundTag args = new CompoundTag();
        args.putInt("no", no);
        ParlorService.onAction(player, "lever", args);
        check(helper, live.slot.game != null && rec.medals() == 30 - bet, "レバーで掛けられません：" + rec.medals());
        long afterBet = rec.medals();
        for (int r = 0; r < 3; r++) {
            CompoundTag stop = args.copy();
            stop.putInt("reel", r);
            stop.putInt("pos", 5 + r * 3);
            ParlorService.onAction(player, "stop", stop);
        }
        check(helper, live.slot.game == null && live.record.games == gamesBefore + 1, "3つ止めてもゲームが終わりません");
        check(helper, rec.medals() >= afterBet, "払い出しでメダルが減りました");
        ParlorService.onAction(player, "leave", args);
        check(helper, live.occupant == null, "台を離れても空きません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    /** 目押し：その図柄が中段に止まる押し所（図柄の2コマ手前）のうち、本当に中段に止まる所を選ぶ。 */
    private static int aimPress(SlotSpec spec, SlotEngine.State st, int reel, char want, SplittableRandom rnd) {
        String strip = spec.reel(reel);
        List<Integer> good = new ArrayList<>();
        for (int i = 0; i < strip.length(); i++) {
            if (strip.charAt(i) != want) {
                continue;
            }
            int press = ReelControl.mod(i + 2, strip.length());
            SlotEngine.State copy = new SlotEngine.State();
            copy.carried = st.carried;
            copy.bonus = st.bonus;
            SlotEngine.Game g = new SlotEngine.Game();
            g.role = st.game.role;
            g.inBonus = st.game.inBonus;
            g.order = st.game.order;
            System.arraycopy(st.game.pos, 0, g.pos, 0, 3);
            System.arraycopy(st.game.stopOrder, 0, g.stopOrder, 0, 3);
            g.stops = st.game.stops;
            copy.game = g;
            int at = SlotEngine.stop(spec, copy, reel, press);
            if (ReelControl.symbolAt(spec, reel, at, 1) == want) {
                good.add(press);
            }
        }
        return good.isEmpty() ? rnd.nextInt(strip.length()) : good.get(rnd.nextInt(good.size()));
    }

    /** その機種の index 番目の台の番号（地下の台。なければ 0）。 */
    private static int machineOf(ParlorRules rules, String model, int index) {
        List<ParlorRules.Machine> list = new ArrayList<>();
        for (ParlorRules.Machine m : rules.machines()) {
            if (m.model().equals(model) && !m.newFloor()) {
                list.add(m);
            }
        }
        return index < list.size() ? list.get(index).no() : 0;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> {
            helper.fail("プレイヤーデータが付いていません");
            return new IllegalStateException("no player data");
        });
    }
}
