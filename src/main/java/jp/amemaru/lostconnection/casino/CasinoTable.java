package jp.amemaru.lostconnection.casino;

import jp.amemaru.lostconnection.casino.CasinoGames.Bet;
import jp.amemaru.lostconnection.casino.CasinoGames.Game;
import jp.amemaru.lostconnection.casino.CasinoGames.Runner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * カジノの卓（Phase 7 その3）。みんなで同じ1回の勝負を囲む（Phase 0-A 15章「4人以上の卓で遊ぶとCP」）。
 *
 * <p>流れ：だれかが卓の画面を開くと「賭ける時間」（{@code bet_seconds}）→ だれかが賭けていれば「回る・めくる・走る」
 * （{@code roll_seconds}。始めに結果を決めて、画面はそこへ向けて動く）→「結果」（{@code result_seconds}。配当を払う）→ 次の勝負。
 * だれも見ていなければ止まる。賭けたチップは賭けたときに減り、外したらそのまま、当たれば倍率の分が戻る。</p>
 */
public final class CasinoTable {
    public enum Phase {
        IDLE, BETTING, ROLLING, RESULT
    }

    final String id;
    final Game game;
    Phase phase = Phase.IDLE;
    int timer;
    int round;
    final Map<UUID, List<Bet>> bets = new LinkedHashMap<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    /** その回に賭けた人（自動テストの人は、サーバーの一覧にいないので、ここから払う）。 */
    final Map<UUID, net.minecraft.server.level.ServerPlayer> seats = new LinkedHashMap<>();
    /** 画面を開いている人（卓を囲んでいる人）。 */
    final Set<UUID> watchers = new LinkedHashSet<>();
    /** 結果を払い終えた人（ROLLING の途中でいなくなった人は、その時に払う）。 */
    final Set<UUID> settled = new LinkedHashSet<>();
    /** その回の結果（ルーレット：目・ハイ＆ロー：次のカード・魚レース：勝った走路）。決まる前は -1。 */
    int outcome = -1;
    /** ハイ＆ロー：今のカード。 */
    int card;
    /** 魚レース：走る魚・勝つ見込み・倍率・レースの歩み・出した人。 */
    final List<Runner> runners = new ArrayList<>();
    double[] multipliers = new double[0];
    float[][] frames = new float[0][];
    /** 結果の並び（新しい順。ルーレット：目・ハイ＆ロー：カード・魚レース：勝った魚の名前の番号）。 */
    final Deque<Integer> history = new ArrayDeque<>();
    /** その回の結果（人ごと：払い戻し）。 */
    final Map<UUID, Long> payouts = new LinkedHashMap<>();
    /** その回に賭けた人の数（4人以上の卓）。 */
    int bettorsThisRound;

    CasinoTable(String id, Game game) {
        this.id = id;
        this.game = game;
    }

    public String id() {
        return id;
    }

    public Game game() {
        return game;
    }

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int outcome() {
        return outcome;
    }

    public int card() {
        return card;
    }

    public List<Runner> runners() {
        return List.copyOf(runners);
    }

    public double multiplier(int lane) {
        return lane >= 0 && lane < multipliers.length ? multipliers[lane] : 0;
    }

    public Set<UUID> watchers() {
        return Set.copyOf(watchers);
    }

    public List<Bet> betsOf(UUID who) {
        return List.copyOf(bets.getOrDefault(who, List.of()));
    }

    public long stakeOf(UUID who) {
        long sum = 0;
        for (Bet b : bets.getOrDefault(who, List.of())) {
            sum += b.chips();
        }
        return sum;
    }

    public boolean anyBet() {
        return !bets.isEmpty();
    }

    /** その人の賭けの払い戻し（結果が決まっているとき）。 */
    long payoutOf(UUID who) {
        long back = 0;
        for (Bet b : bets.getOrDefault(who, List.of())) {
            if (wins(b)) {
                back += (long) Math.floor(b.chips() * b.multiplier());
            }
        }
        return back;
    }

    boolean wins(Bet b) {
        if (outcome < 0) {
            return false;
        }
        return switch (game) {
            case ROULETTE -> CasinoGames.rouletteWins(b.kind(), b.value(), outcome);
            case HIGHLOW -> CasinoGames.highLowWins(b.kind(), card, outcome);
            case FISHRACE -> "lane".equals(b.kind()) && b.value() == outcome;
        };
    }

    /** 次の勝負の賭ける時間を始める。 */
    void startBetting(RandomSource random, int ticks) {
        phase = Phase.BETTING;
        timer = ticks;
        round++;
        bets.clear();
        names.clear();
        seats.clear();
        settled.clear();
        payouts.clear();
        outcome = -1;
        frames = new float[0][];
        bettorsThisRound = 0;
        if (game == Game.HIGHLOW && card == 0) {
            card = CasinoGames.drawCard(random);
        }
    }

    void remember(int value) {
        history.addFirst(value);
        while (history.size() > 12) {
            history.removeLast();
        }
    }

    /** 画面へ送る卓の様子（viewer の賭けを mine に入れる）。 */
    CompoundTag stateTag(UUID viewer, String viewerName) {
        CompoundTag tag = new CompoundTag();
        tag.putString("table", id);
        tag.putString("game", game.id());
        tag.putString("phase", phase.name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("timer", timer);
        tag.putInt("round", round);
        tag.putInt("outcome", phase == Phase.BETTING ? -1 : outcome);
        tag.putInt("card", card);
        ListTag hist = new ListTag();
        for (int h : history) {
            hist.add(IntTag.valueOf(h));
        }
        tag.put("history", hist);
        ListTag players = new ListTag();
        for (UUID who : watchers) {
            CompoundTag one = new CompoundTag();
            one.putString("name", names.getOrDefault(who, CasinoService.nameOf(who)));
            one.putLong("stake", stakeOf(who));
            if (payouts.containsKey(who)) {
                one.putLong("back", payouts.get(who));
            }
            players.add(one);
        }
        // 見ていないが賭けた人（画面を閉じた人）も並べる
        for (UUID who : bets.keySet()) {
            if (!watchers.contains(who)) {
                CompoundTag one = new CompoundTag();
                one.putString("name", names.getOrDefault(who, "?"));
                one.putLong("stake", stakeOf(who));
                if (payouts.containsKey(who)) {
                    one.putLong("back", payouts.get(who));
                }
                players.add(one);
            }
        }
        tag.put("players", players);
        tag.putInt("bettors", bets.size());
        ListTag mine = new ListTag();
        for (Bet b : bets.getOrDefault(viewer, List.of())) {
            CompoundTag one = new CompoundTag();
            one.putString("kind", b.kind());
            one.putInt("value", b.value());
            one.putLong("chips", b.chips());
            one.putDouble("mult", b.multiplier());
            mine.add(one);
        }
        tag.put("mine", mine);
        if (payouts.containsKey(viewer)) {
            tag.putLong("myBack", payouts.get(viewer));
        }
        if (game == Game.HIGHLOW && card > 0) {
            double edge = CasinoRules.current().highLowEdge();
            tag.putDouble("multHigh", CasinoGames.highLowMultiplier("high", card, edge));
            tag.putDouble("multLow", CasinoGames.highLowMultiplier("low", card, edge));
        }
        if (game == Game.FISHRACE) {
            ListTag lanes = new ListTag();
            for (int i = 0; i < runners.size(); i++) {
                Runner r = runners.get(i);
                CompoundTag one = new CompoundTag();
                one.putString("fish", r.fish());
                one.putString("name", r.name());
                one.putString("owner", r.ownerName());
                one.putInt("stars", r.rarity());
                one.putDouble("mult", multiplier(i));
                lanes.add(one);
            }
            tag.put("lanes", lanes);
            if (phase != Phase.BETTING && frames.length > 0) {
                ListTag fr = new ListTag();
                for (float[] f : frames) {
                    ListTag row = new ListTag();
                    for (float p : f) {
                        row.add(FloatTag.valueOf(p));
                    }
                    fr.add(row);
                }
                tag.put("frames", fr);
            }
        }
        return tag;
    }
}
