package jp.amemaru.lostconnection.casino.parlor;

import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.casino.CasinoRecord;
import jp.amemaru.lostconnection.casino.CasinoRules;
import jp.amemaru.lostconnection.casino.CasinoService;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ParlorS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * カジノの地下遊技場（パチンコとスロット。11月27日のご指示「ルーレットとハイ＆ロー以外を廃止してパチンコとスロットを追加する。本物と
 * 同じように演出や日にちごとに代わるあたり台と外れ台を用意する。本物ように設定を作る。専用のＧＵＩから遊べる。少し多めに用意する。
 * 場所が足りない場合は地下なども用意する」【補完】）。
 *
 * <ul>
 *   <li>台（台のブロック・その下の受け皿）を右クリックすると、その台の画面が開く。1台に1人。台を離れても、しばらく（ボーナス・確変・
 *       AT の最中は長く）その人の台のまま。ほかの人が座っている台は、データだけ見られる</li>
 *   <li>玉とメダルはチップで借りる（1枚で25個・5枚）。持ち玉・持ちメダルは台を離れても残り、台の画面か交換所でチップに戻す（等価）</li>
 *   <li>毎朝（4時）台ごとの設定と釘を決めなおす（{@link DailySettings}）。データカウンターはだれでも見られる</li>
 *   <li>大当たり・ボーナス・AT の最中は、台の上の呼び出しランプが光る（ブロックの {@code lit}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ParlorService {
    /** 台の画面へ、パチンコの様子を送る間（tick）。 */
    private static final int SYNC_TICKS = 4;
    /** スロットのゲームの途中で、止めボタンを押さないまま台を離れたら、この tick で自動で止める。 */
    private static final int SLOT_IDLE_TICKS = 20 * 60;
    /** 大当たりの最中に台の画面を閉じたら、残りのラウンドを消化して払う。 */
    private static final int SAVE_TICKS = 20 * 30;

    /** 台ひとつの、動いているあいだの様子。 */
    public static final class Live {
        public final ParlorRules.Machine machine;
        public final ParlorModels.Model model;
        public SlotEngine.State slot;
        public PachinkoEngine.State pachinko;
        public final MachineRecord record = new MachineRecord();
        public UUID occupant;
        String occupantName = "";
        long keepUntil;
        boolean watching;
        boolean shooting;
        int strength = 70;
        boolean right;
        long lastLever = -100000;
        long gameTick;
        boolean lit;
        int syncClock;
        long shots;
        long hesoIn;
        long denchuIn;
        long attackerIn;
        int forcedHits;
        boolean forceReach;
        /** 台の上のランプ（ブロックの lit）を、台の中の様子に合わせ終えたか（読みこんだあと・チャンクがなかったときは、あとで合わせる） */
        boolean lampSynced;

        Live(ParlorRules.Machine machine, ParlorModels.Model model) {
            this.machine = machine;
            this.model = model;
            if (model.isSlot()) {
                slot = new SlotEngine.State();
            } else {
                pachinko = new PachinkoEngine.State();
            }
        }

        public boolean inFeature() {
            return slot != null ? slot.inFeature() : pachinko.inFeature();
        }

        JsonObject saveState() {
            return slot != null ? slot.save() : pachinko.save();
        }

        void loadState(JsonObject o) {
            if (slot != null) {
                slot.load(o);
            } else {
                pachinko.load(o);
            }
        }
    }

    private static final Map<Integer, Live> LIVE = new LinkedHashMap<>();
    private static final Map<UUID, Integer> SEAT = new HashMap<>();
    private static int loadedGeneration = -1;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final RandomGenerator RND = RANDOM::nextLong;

    private ParlorService() {
    }

    // ================================================================ 台を読みこむ・日が変わる

    /** 台の一覧（データパックの台の並びと機種から作る。読みなおすと、台番号ごとに中の状態を引きつぐ）。 */
    public static Map<Integer, Live> live(MinecraftServer server) {
        int gen = CasinoRules.loader().generation() * 1000 + ParlorModels.loader().generation();
        if (gen != loadedGeneration) {
            saveAll(server);
            LIVE.clear();
            ParlorData data = ParlorData.get(server);
            for (ParlorRules.Machine m : CasinoRules.current().parlor().machines()) {
                ParlorModels.Model model = ParlorModels.get(m.model());
                if (model == null) {
                    continue;
                }
                Live l = new Live(m, model);
                JsonObject st = data.state(m.no());
                if (st != null) {
                    try {
                        l.loadState(st);
                    } catch (RuntimeException e) {
                        AmemaruMod.LOGGER.warn("[amemaru][parlor] machine {} state unreadable: {}", m.no(), e.getMessage());
                    }
                }
                JsonObject rec = data.record(m.no());
                if (rec != null) {
                    try {
                        l.record.load(rec);
                    } catch (RuntimeException e) {
                        AmemaruMod.LOGGER.warn("[amemaru][parlor] machine {} record unreadable: {}", m.no(), e.getMessage());
                    }
                }
                LIVE.put(m.no(), l);
            }
            loadedGeneration = gen;
            rollDay(server, true);
        }
        return LIVE;
    }

    @Nullable
    public static Live live(MinecraftServer server, int no) {
        return live(server).get(no);
    }

    /** 日が変わっていたら、台ごとの設定と釘を決めなおし、データカウンターを前の日へ送る。force なら日が同じでも設定を当てなおす。 */
    static void rollDay(MinecraftServer server, boolean force) {
        ParlorData data = ParlorData.get(server);
        String today = ConnectionService.today();
        boolean changed = !today.equals(data.day);
        if (!changed && !force) {
            return;
        }
        if (changed) {
            data.forcedSettings.clear();
            data.forcedNails.clear();
        }
        for (Live l : LIVE.values()) {
            int no = l.machine.no();
            l.record.roll(today, l.slot != null);
            int setting = data.forcedSettings.getOrDefault(no, DailySettings.setting(data.salt, today, no));
            if (l.slot != null) {
                if (changed && l.slot.setting != setting) {
                    // 設定を変えた台は、中の状態を消す（本物の「設定変更」と同じ。天井までの数えも 0 から）
                    SlotEngine.State fresh = new SlotEngine.State();
                    fresh.setting = setting;
                    l.slot = fresh;
                } else {
                    l.slot.setting = setting;
                }
            } else {
                PachinkoSpec spec = l.model.pachinko();
                double nail = data.forcedNails.containsKey(no) ? data.forcedNails.get(no) / 10.0
                        : DailySettings.nail(data.salt, today, no, spec);
                if (spec.settings() && changed && l.pachinko.setting != setting && l.pachinko.round == null && l.occupant == null) {
                    PachinkoEngine.State fresh = new PachinkoEngine.State();
                    l.pachinko = fresh;
                }
                l.pachinko.setting = spec.settings() ? setting : 1;
                l.pachinko.nail = nail;
                data.nails.put(no, (int) Math.round(nail * 10));
            }
            data.settings.put(no, setting);
            data.putState(no, l.saveState(), l.record.save());
        }
        data.day = today;
        data.setDirty();
    }

    /** 台の中の状態とデータを保存する。 */
    public static void saveAll(MinecraftServer server) {
        if (LIVE.isEmpty()) {
            return;
        }
        ParlorData data = ParlorData.get(server);
        for (Live l : LIVE.values()) {
            data.putState(l.machine.no(), l.saveState(), l.record.save());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Live l : LIVE.values()) {
            settleRound(event.getServer(), l);
            if (l.slot != null && l.slot.game != null) {
                // 途中のゲームは、止めボタンを押さなかったものとして終える（掛けたメダルを失わせない）
                finishSlot(event.getServer(), l, SlotEngine.autoFinish(l.model.slot(), l.slot, RND), null);
            }
        }
        saveAll(event.getServer());
        LIVE.clear();
        SEAT.clear();
        loadedGeneration = -1;
    }

    // ================================================================ 右クリック

    @Nullable
    static BlockPos relative(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        BlockPos o = CityCommands.builtOrigin(level).orElse(null);
        return o == null ? null : pos.subtract(o);
    }

    /** 台のブロック（受け皿も）を右クリックしたとき。 */
    public static InteractionResult onUseMachine(ServerPlayer player, BlockPos pos) {
        if (player instanceof FakePlayer || !ServerConfig.CASINO_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        BlockPos rel = relative(player.serverLevel(), pos);
        ParlorRules.Machine m = rel == null ? null : CasinoRules.current().parlor().machineAt(rel);
        if (m == null) {
            player.displayClientMessage(Component.translatable("parlor.amemaru.not_machine").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        open(player, m.no(), false);
        return InteractionResult.SUCCESS;
    }

    /** 島の掲示板を右クリックしたとき：その島のデータ。 */
    public static InteractionResult onUseBoard(ServerPlayer player, BlockPos pos) {
        if (player instanceof FakePlayer || !ServerConfig.CASINO_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        BlockPos rel = relative(player.serverLevel(), pos);
        ParlorRules.Island island = rel == null ? null : CasinoRules.current().parlor().boardAt(rel);
        if (island == null) {
            island = CasinoRules.current().parlor().islands().isEmpty() ? null : CasinoRules.current().parlor().islands().get(0);
        }
        if (island != null) {
            sendData(player, island.id());
        }
        return InteractionResult.SUCCESS;
    }

    /** 交換所の窓口（ふつうのブロック）を右クリックしたとき：カジノの画面の「両替」（玉とメダルの交換もここ）。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !ServerConfig.CASINO_ENABLED.get()) {
            return;
        }
        BlockPos rel = relative(player.serverLevel(), event.getPos());
        if (rel == null || !CasinoRules.current().parlor().inCounter(rel)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            CasinoService.open(player, "cashier", false);
        }
    }

    // ================================================================ 台に座る・離れる

    /** その台が遊べるか（開場の日・2階の新台の間は共同事業⑥のあと）。遊べなければ知らせの鍵。 */
    @Nullable
    static String closedReason(MinecraftServer server, Live l, boolean admin) {
        if (admin) {
            return null;
        }
        if (!CasinoService.isOpen(server)) {
            return "casino.amemaru.closed";
        }
        if (l.machine.newFloor() && !StoryState.get(server).has(CasinoRules.current().parlor().newFlag())) {
            return "parlor.amemaru.new_locked";
        }
        return null;
    }

    /** 台の画面を開く（座る）。ほかの人の台なら、データだけを見せる。 */
    public static void open(ServerPlayer player, int no, boolean admin) {
        MinecraftServer server = player.server;
        Live l = live(server, no);
        if (l == null) {
            player.displayClientMessage(Component.translatable("parlor.amemaru.not_machine").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        String reason = closedReason(server, l, admin);
        if (reason != null) {
            player.displayClientMessage(Component.translatable(reason, CasinoRules.current().openDay()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        long now = server.getTickCount();
        if (l.occupant != null && !l.occupant.equals(player.getUUID()) && (l.watching || now < l.keepUntil)) {
            long left = l.watching ? -1 : Math.max(1, (l.keepUntil - now) / 20);
            player.displayClientMessage(left < 0 ? Component.translatable("parlor.amemaru.taken", l.occupantName, no)
                    .withStyle(ChatFormatting.YELLOW) : Component.translatable("parlor.amemaru.kept", l.occupantName, no, left)
                    .withStyle(ChatFormatting.YELLOW), true);
            sendData(player, l.machine.island());
            return;
        }
        // ほかの台に座っていたら、その台を離れる
        Integer prev = SEAT.get(player.getUUID());
        if (prev != null && prev != no) {
            Live old = LIVE.get(prev);
            if (old != null) {
                release(server, old, true);
            }
        }
        if (l.occupant != null && !l.occupant.equals(player.getUUID())) {
            release(server, l, false);
        }
        l.occupant = player.getUUID();
        l.occupantName = player.getGameProfile().getName();
        l.watching = true;
        l.keepUntil = 0;
        l.shooting = false;
        SEAT.put(player.getUUID(), no);
        CompoundTag tag = new CompoundTag();
        tag.putInt("no", no);
        tag.putString("kind", l.model.isSlot() ? "slot" : "pachinko");
        tag.putString("modelId", l.model.id());
        tag.putString("model", l.model.json());
        ParlorRules.Island island = CasinoRules.current().parlor().island(l.machine.island());
        tag.putString("island", island == null ? "" : island.name());
        tag.putString("islandId", l.machine.island());
        tag.putBoolean("admin", admin);
        tag.put("rules", rulesTag());
        tag.put("state", stateTag(player, l));
        tag.put("record", recordTag(l));
        ModNetwork.sendTo(player, new ParlorS2C("open", tag));
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
    }

    /** 台を離れる。settle なら、大当たりの残りのラウンドを消化して払う。 */
    static void release(MinecraftServer server, Live l, boolean settle) {
        if (settle) {
            settleRound(server, l);
        }
        if (l.slot != null && l.slot.game != null) {
            finishSlot(server, l, SlotEngine.autoFinish(l.model.slot(), l.slot, RND), null);
        }
        if (l.occupant != null) {
            SEAT.remove(l.occupant);
        }
        l.occupant = null;
        l.occupantName = "";
        l.watching = false;
        l.shooting = false;
        l.keepUntil = 0;
    }

    /** 画面を閉じた：しばらくその人の台にしておく。 */
    static void close(ServerPlayer player, Live l) {
        if (!player.getUUID().equals(l.occupant)) {
            return;
        }
        l.watching = false;
        l.shooting = false;
        ParlorRules rules = CasinoRules.current().parlor();
        int keep = l.inFeature() ? rules.keepFeatureSeconds() : rules.keepSeconds();
        l.keepUntil = player.server.getTickCount() + keep * 20L;
        if (l.slot != null && l.slot.game != null) {
            finishSlot(player.server, l, SlotEngine.autoFinish(l.model.slot(), l.slot, RND), player);
        }
    }

    /** 大当たりの最中なら、残りのラウンドを消化して、座っている人の持ち玉に足す。 */
    static void settleRound(MinecraftServer server, Live l) {
        if (l.pachinko == null || l.pachinko.round == null) {
            return;
        }
        int pay = PachinkoEngine.settleRound(l.model.pachinko(), l.pachinko);
        if (pay <= 0) {
            return;
        }
        l.record.pachinkoPaid(pay);
        ServerPlayer p = l.occupant == null ? null : server.getPlayerList().getPlayer(l.occupant);
        if (p != null) {
            PlayerDataCapability.get(p).ifPresent(d -> d.getCasino().addBalls(pay));
            p.sendSystemMessage(Component.translatable("parlor.amemaru.settled", l.machine.no(), pay).withStyle(ChatFormatting.GOLD));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer no = SEAT.get(player.getUUID());
        Live l = no == null ? null : LIVE.get(no);
        if (l != null) {
            settleRound(player.server, l);
            close(player, l);
        }
    }

    // ================================================================ 操作（画面から）

    /** 画面からの操作。できるかどうかはここで確かめる。 */
    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        MinecraftServer server = player.server;
        if ("data".equals(action)) {
            sendData(player, args.getString("island"));
            return;
        }
        if ("cashout".equals(action) && args.getInt("no") == 0) {
            cashout(player, args.getString("what"));
            return;
        }
        Live l = live(server, args.getInt("no"));
        if (l == null || !player.getUUID().equals(l.occupant)) {
            if (!"close".equals(action)) {
                ModNetwork.sendTo(player, new ParlorS2C("close", new CompoundTag()));
            }
            return;
        }
        switch (action) {
            case "close" -> close(player, l);
            case "leave" -> {
                release(server, l, true);
                ModNetwork.sendTo(player, new ParlorS2C("close", new CompoundTag()));
            }
            case "lend" -> lend(player, l, args.getInt("chips"));
            case "cashout" -> {
                cashout(player, l.slot != null ? "medals" : "balls");
                sendState(player, l);
            }
            case "handle" -> {
                if (l.pachinko != null) {
                    l.watching = true;
                    l.shooting = args.getBoolean("on");
                    l.strength = Math.max(0, Math.min(100, args.getInt("strength")));
                    l.right = args.getBoolean("right");
                }
            }
            case "lever" -> lever(player, l);
            case "stop" -> stop(player, l, args.getInt("reel"), args.getInt("pos"));
            default -> {
            }
        }
    }

    /** チップで玉・メダルを借りる。 */
    static void lend(ServerPlayer player, Live l, int chips) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        ParlorRules rules = CasinoRules.current().parlor();
        if (data == null || chips <= 0 || !rules.lend().contains(chips)) {
            return;
        }
        CasinoRecord rec = data.getCasino();
        if (rec.chips() < chips) {
            player.displayClientMessage(Component.translatable("casino.amemaru.error.no_chips").withStyle(ChatFormatting.RED), true);
            return;
        }
        rec.addChips(-chips);
        ParlorData pd = ParlorData.get(player.server);
        pd.lentChips += chips;
        pd.setDirty();
        if (l.slot != null) {
            rec.addMedals((long) chips * rules.medalsPerChip());
        } else {
            rec.addBalls((long) chips * rules.ballsPerChip());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 1.6F);
        sendState(player, l);
    }

    /** 持ち玉・持ちメダルをチップに戻す（等価。チップにならない端数は残る）。what：balls・medals・all。 */
    public static void cashout(ServerPlayer player, String what) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ParlorRules rules = CasinoRules.current().parlor();
        CasinoRecord rec = data.getCasino();
        long chips = 0;
        if (!"medals".equals(what)) {
            long n = rec.balls() / rules.ballsPerChip();
            rec.addBalls(-n * rules.ballsPerChip());
            chips += n;
        }
        if (!"balls".equals(what)) {
            long n = rec.medals() / rules.medalsPerChip();
            rec.addMedals(-n * rules.medalsPerChip());
            chips += n;
        }
        if (chips <= 0) {
            player.displayClientMessage(Component.translatable("parlor.amemaru.nothing_to_cash").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        rec.addChips(chips);
        ParlorData pd = ParlorData.get(player.server);
        pd.returnedChips += chips;
        pd.setDirty();
        player.displayClientMessage(Component.translatable("parlor.amemaru.cashed", chips).withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    // ================================================================ スロット

    static void lever(ServerPlayer player, Live l) {
        if (l.slot == null) {
            return;
        }
        MinecraftServer server = player.server;
        SlotSpec spec = l.model.slot();
        long now = server.getTickCount();
        if (l.slot.game != null) {
            return;
        }
        if (now - l.lastLever < Math.max(1, spec.waitMs() / 50)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        int bet = SlotEngine.betFor(spec, l.slot);
        if (data.getCasino().medals() < bet) {
            CompoundTag t = new CompoundTag();
            t.putString("error", "parlor.amemaru.no_medals");
            send(player, l, "slot", t);
            return;
        }
        boolean forced = l.forcedHits > 0 && l.slot.game == null;
        if (forced) {
            forceSlot(l);
        }
        data.getCasino().addMedals(-bet);
        l.lastLever = now;
        l.gameTick = now;
        boolean bonusGame = !l.slot.bonus.isEmpty();
        SlotEngine.Start start = SlotEngine.lever(spec, l.slot, RND);
        if (forced && l.slot.game != null && spec.type() == SlotSpec.Type.A) {
            // 決めておいたボーナス：このゲームは小役を引かない（7をねらえばそろう）
            l.slot.game.role = "";
        }
        for (String e : start.events()) {
            if ("cz_start".equals(e)) {
                l.record.slotHit("cz");
            }
        }
        CompoundTag t = new CompoundTag();
        t.putString("type", "start");
        t.putInt("bet", start.bet());
        t.putBoolean("free", start.free());
        t.putBoolean("lampNow", start.lampNow());
        t.putString("effect", start.effect());
        t.putInt("nav", start.navOrder());
        t.putBoolean("bonusGame", bonusGame);
        t.putBoolean("lamp", l.slot.lamp);
        t.put("events", strings(start.events()));
        t.putLong("medals", data.getCasino().medals());
        send(player, l, "slot", t);
    }

    static void stop(ServerPlayer player, Live l, int reel, int press) {
        if (l.slot == null || l.slot.game == null || reel < 0 || reel > 2) {
            return;
        }
        SlotSpec spec = l.model.slot();
        int at = SlotEngine.stop(spec, l.slot, reel, ReelControl.mod(press, spec.reelLength()));
        CompoundTag t = new CompoundTag();
        t.putString("type", "stop");
        t.putInt("reel", reel);
        t.putInt("at", at);
        send(player, l, "slot", t);
        SlotEngine.Game g = l.slot.game;
        if (g.pos[0] >= 0 && g.pos[1] >= 0 && g.pos[2] >= 0) {
            finishSlot(player.server, l, SlotEngine.finish(spec, l.slot), player);
        }
    }

    /** 1ゲームの終わり：払い出し・データ・ランプ・知らせ。 */
    static void finishSlot(MinecraftServer server, Live l, SlotEngine.Result r, @Nullable ServerPlayer player) {
        ServerPlayer p = player != null ? player : l.occupant == null ? null : server.getPlayerList().getPlayer(l.occupant);
        // 掛けた枚数は、レバーの時に引いてある（リプレイは 0）
        if (p != null && r.payout() > 0) {
            PlayerDataCapability.get(p).ifPresent(d -> d.getCasino().addMedals(r.payout()));
        }
        l.record.slotGame(r.bet(), r.payout(), r.bonusGame());
        if (!r.bonusStarted().isEmpty()) {
            l.record.slotHit(r.bonusStarted());
        }
        for (String e : r.events()) {
            if ("at_start".equals(e)) {
                l.record.slotHit("at");
            }
            if (e.startsWith("at_end:") && p != null) {
                int got = Integer.parseInt(e.substring(7));
                if (got >= 1000) {
                    announce(server, Component.translatable("parlor.amemaru.announce.at", p.getGameProfile().getName(), l.machine.no(),
                            l.model.name(), got));
                }
            }
        }
        updateLamp(server, l);
        if (p != null) {
            CompoundTag t = new CompoundTag();
            t.putString("type", "result");
            t.putInt("payout", r.payout());
            t.putBoolean("replay", r.replay());
            t.putString("aligned", r.aligned());
            t.putBoolean("lampNow", r.lampNow());
            t.putString("bonusStarted", r.bonusStarted());
            t.putString("bonusEnded", r.bonusEnded());
            t.putInt("bonusPaid", r.bonusPaid());
            t.put("events", strings(r.events()));
            t.put("state", stateTag(p, l));
            t.put("record", recordTag(l));
            send(p, l, "slot", t);
        }
    }

    private static SlotSpec spec(Live l) {
        return l.model.slot();
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || CasinoRules.current().parlor().machines().isEmpty()) {
            return;
        }
        Map<Integer, Live> all = live(server);
        long now = server.getTickCount();
        if (now % 200 == 0) {
            rollDay(server, false);
        }
        if (now % 100 == 50) {
            // ランプを台の中の様子に合わせる（サーバーを起こしたあと・ランプの所が読みこまれていなかったとき）
            for (Live l : all.values()) {
                if (!l.lampSynced) {
                    updateLamp(server, l);
                }
            }
        }
        for (Live l : all.values()) {
            if (l.occupant == null) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(l.occupant);
            if (l.watching && (p == null || !near(p, l))) {
                if (p != null) {
                    close(p, l);
                    ModNetwork.sendTo(p, new ParlorS2C("close", new CompoundTag()));
                    p.displayClientMessage(Component.translatable("parlor.amemaru.left_seat").withStyle(ChatFormatting.GRAY), true);
                } else {
                    l.watching = false;
                    l.shooting = false;
                    l.keepUntil = now + CasinoRules.current().parlor().keepFeatureSeconds() * 20L;
                }
            }
            if (!l.watching && now >= l.keepUntil) {
                release(server, l, true);
                continue;
            }
            if (l.pachinko != null) {
                tickPachinko(server, l, p, now);
            } else if (l.slot.game != null && !l.watching && now - l.gameTick > SLOT_IDLE_TICKS) {
                finishSlot(server, l, SlotEngine.autoFinish(l.model.slot(), l.slot, RND), p);
            }
        }
        if (now % SAVE_TICKS == 0) {
            saveAll(server);
        }
    }

    static boolean near(ServerPlayer p, Live l) {
        BlockPos o = CityCommands.builtOrigin(p.serverLevel()).orElse(null);
        if (o == null || p.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos at = o.offset(l.machine.pos());
        return p.position().distanceTo(Vec3.atCenterOf(at)) <= CasinoRules.current().parlor().reach();
    }

    static void tickPachinko(MinecraftServer server, Live l, @Nullable ServerPlayer p, long now) {
        PachinkoSpec spec = l.model.pachinko();
        PachinkoEngine.State st = l.pachinko;
        CasinoRecord rec = p == null ? null : PlayerDataCapability.get(p).map(PlayerData::getCasino).orElse(null);
        // 大当たりの最中に画面を閉じたら、残りを消化
        if (!l.watching && st.round != null && st.round.phase != PachinkoEngine.Phase.ENDING) {
            settleRound(server, l);
        }
        boolean held = false;
        if (l.watching && l.shooting && rec != null) {
            st.shotClock++;
            if (st.shotClock >= PachinkoEngine.SHOT_TICKS) {
                st.shotClock = 0;
                if (rec.balls() > 0) {
                    if (l.forcedHits > 0 && st.round == null) {
                        forcePachinko(l);
                    }
                    int beforeHolds = st.heso.size() + st.denchu.size();
                    PachinkoEngine.Shot shot = PachinkoEngine.shoot(spec, st, l.strength, l.right, RND);
                    rec.addBalls(shot.back() - shot.used());
                    l.shots += shot.used();
                    if (shot.heso()) {
                        l.hesoIn++;
                    }
                    if (shot.denchu()) {
                        l.denchuIn++;
                    }
                    if (shot.attacker()) {
                        l.attackerIn++;
                        l.record.pachinkoPaid(shot.back());
                    }
                    held = st.heso.size() + st.denchu.size() > beforeHolds;
                } else {
                    l.shooting = false;
                    if (p != null) {
                        p.displayClientMessage(Component.translatable("parlor.amemaru.no_balls").withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            }
        }
        PachinkoEngine.Mode before = st.mode;
        PachinkoEngine.Tick t = PachinkoEngine.tick(spec, st, RND);
        if (t.started() != null && l.forceReach && st.spinHold != null && st.spinHold.hit()) {
            // 決めておいた当たり：ノーマルリーチで見せる
            l.forceReach = false;
            st.spin = PachinkoEngine.script(spec, st, st.spinHold, st.heso.size() + st.denchu.size(), RND, "normal");
            t = new PachinkoEngine.Tick(st.spin, null, false, false, false, "", 0, null, 0);
        }
        if (t.started() != null) {
            l.record.spin();
            if (p != null && l.watching) {
                CompoundTag tag = new CompoundTag();
                tag.putString("script", t.started().toJson().toString());
                send(p, l, "spin", tag);
            }
        }
        if (t.jackpotStart()) {
            PachinkoEngine.Round r = st.round;
            l.record.pachinkoHit(r.jackpot, st.chain);
            updateLamp(server, l);
            if (p != null && st.chain >= 5 && st.chain % 5 == 0) {
                announce(server, Component.translatable("parlor.amemaru.announce.chain", p.getGameProfile().getName(), l.machine.no(),
                        l.model.name(), st.chain));
            }
        }
        if (!t.jackpotEnd().isEmpty() || t.modeChanged() != null || st.mode != before) {
            updateLamp(server, l);
        }
        if (p != null && l.watching) {
            // データカウンター（回転数・大当たり・当たりの履歴と出玉）が変わった：画面の「本日のデータ」も送りなおす
            boolean recordChanged = t.started() != null || t.jackpotStart() || t.roundEnd() || !t.jackpotEnd().isEmpty();
            boolean event = t.jackpotStart() || t.roundStart() || t.roundEnd() || !t.jackpotEnd().isEmpty() || t.modeChanged() != null
                    || held || recordChanged;
            if (event || ++l.syncClock >= SYNC_TICKS) {
                l.syncClock = 0;
                CompoundTag tag = stateTag(p, l);
                if (recordChanged) {
                    tag.put("record", recordTag(l));
                }
                if (!t.jackpotEnd().isEmpty()) {
                    tag.putString("jackpotEnd", t.jackpotEnd());
                    tag.putInt("jackpotPaid", t.jackpotPaid());
                }
                if (t.jackpotStart()) {
                    tag.putBoolean("jackpotStart", true);
                }
                send(p, l, "state", tag);
            }
        }
    }

    /** 台の上の呼び出しランプ（ブロックの lit）：大当たり・確変・ST・ボーナス・AT の最中に光る。 */
    static void updateLamp(MinecraftServer server, Live l) {
        boolean on = l.slot != null ? !l.slot.bonus.isEmpty() || l.slot.atGames > 0
                : l.pachinko.round != null || l.pachinko.mode == PachinkoEngine.Mode.KAKUHEN || l.pachinko.mode == PachinkoEngine.Mode.ST;
        if (on == l.lit && l.lampSynced) {
            return;
        }
        l.lit = on;
        ServerLevel level = server.overworld();
        BlockPos o = CityCommands.builtOrigin(level).orElse(null);
        if (o == null) {
            // 街が建っていない（テストの世界など）
            l.lampSynced = true;
            return;
        }
        BlockPos at = o.offset(l.machine.pos());
        if (!level.isLoaded(at)) {
            // チャンクが読みこまれていない：あとで（毎 tick の見回りで）合わせる
            l.lampSynced = false;
            return;
        }
        BlockState bs = level.getBlockState(at);
        if (bs.hasProperty(BlockStateProperties.LIT) && bs.getValue(BlockStateProperties.LIT) != on) {
            level.setBlock(at, bs.setValue(BlockStateProperties.LIT, on), 3);
        }
        l.lampSynced = true;
    }

    static void announce(MinecraftServer server, Component text) {
        server.getPlayerList().broadcastSystemMessage(text.copy().withStyle(ChatFormatting.GOLD), false);
    }

    // ================================================================ 画面へ送る中身

    static void send(ServerPlayer p, Live l, String kind, CompoundTag tag) {
        tag.putInt("no", l.machine.no());
        ModNetwork.sendTo(p, new ParlorS2C(kind, tag));
    }

    static void sendState(ServerPlayer p, Live l) {
        send(p, l, "state", stateTag(p, l));
    }

    static CompoundTag rulesTag() {
        ParlorRules rules = CasinoRules.current().parlor();
        CompoundTag t = new CompoundTag();
        t.putInt("balls", rules.ballsPerChip());
        t.putInt("medals", rules.medalsPerChip());
        t.putIntArray("lend", rules.lend().stream().mapToInt(Integer::intValue).toArray());
        String today = ConnectionService.today();
        t.putBoolean("event", DailySettings.isEvent(today));
        t.putString("day", today);
        return t;
    }

    /** 台の様子（持ち玉・持ちメダル・チップと、台の中の見える所。設定と釘とボーナスの種類は送らない）。 */
    static CompoundTag stateTag(ServerPlayer p, Live l) {
        CompoundTag t = new CompoundTag();
        PlayerDataCapability.get(p).ifPresent(d -> {
            t.putLong("balls", d.getCasino().balls());
            t.putLong("medals", d.getCasino().medals());
            t.putLong("chips", d.getCasino().chips());
        });
        if (l.slot != null) {
            SlotEngine.State s = l.slot;
            t.putBoolean("lamp", s.lamp);
            t.putString("bonus", s.bonus);
            t.putInt("bonusPaid", s.bonusPaid);
            SlotSpec.Bonus b = s.bonus.isEmpty() ? null : spec(l).bonus(s.bonus);
            t.putInt("bonusCap", b == null ? 0 : b.cap());
            t.putInt("atGames", s.atGames);
            t.putInt("czGames", s.czGames);
            t.putInt("atGot", s.atGot);
            t.putBoolean("replay", s.replay);
            t.putInt("bet", SlotEngine.betFor(spec(l), s));
            if (s.game != null) {
                t.putIntArray("pos", s.game.pos.clone());
            }
            if (spec(l).at() != null) {
                t.putInt("ceiling", spec(l).at().ceiling());
            }
        } else {
            PachinkoEngine.State s = l.pachinko;
            t.putString("mode", s.mode.name());
            t.putInt("modeSpins", s.modeSpins);
            t.putBoolean("lt", s.lt);
            t.putInt("chain", s.chain);
            t.putInt("chainBalls", s.chainBalls);
            t.put("heso", colors(s.heso));
            t.put("denchu", colors(s.denchu));
            t.putBoolean("spinning", s.spin != null);
            t.putInt("spinTick", s.spinTick);
            t.putBoolean("shooting", l.shooting);
            t.putInt("strength", l.strength);
            t.putBoolean("right", l.right);
            t.putLong("shots", l.shots);
            t.putLong("hesoIn", l.hesoIn);
            t.putLong("denchuIn", l.denchuIn);
            t.putLong("attackerIn", l.attackerIn);
            if (s.round != null) {
                CompoundTag r = new CompoundTag();
                PachinkoSpec.Jackpot j = l.model.pachinko().jackpot(s.round.jackpot);
                r.putString("jackpot", j.id());
                r.putString("name", j.name());
                r.putBoolean("kakuhen", j.kakuhen());
                r.putBoolean("promoted", s.round.promoted);
                r.putInt("rounds", s.round.rounds);
                r.putInt("round", s.round.round);
                r.putInt("count", s.round.count);
                r.putInt("paid", s.round.paid);
                r.putString("phase", s.round.phase.name());
                r.putInt("timer", s.round.timer);
                t.put("round", r);
            }
            if (s.spin != null) {
                t.putString("script", s.spin.toJson().toString());
            }
        }
        return t;
    }

    private static IntArrayTag colors(List<PachinkoEngine.Hold> holds) {
        int[] c = new int[holds.size()];
        for (int i = 0; i < c.length; i++) {
            c[i] = holds.get(i).color();
        }
        return new IntArrayTag(c);
    }

    private static ListTag strings(List<String> list) {
        ListTag t = new ListTag();
        for (String s : list) {
            t.add(net.minecraft.nbt.StringTag.valueOf(s));
        }
        return t;
    }

    /** データカウンター（だれでも見られる）。 */
    static CompoundTag recordTag(Live l) {
        CompoundTag t = new CompoundTag();
        t.putString("json", l.record.save().toString());
        t.putInt("no", l.machine.no());
        t.putString("model", l.model.id());
        t.putString("name", l.model.name());
        t.putString("kind", l.model.isSlot() ? "slot" : "pachinko");
        t.putBoolean("busy", l.occupant != null);
        if (l.slot != null) {
            t.putInt("sinceAt", l.slot.sinceAt);
            t.putBoolean("at", l.slot.atGames > 0);
        } else {
            t.putString("mode", l.pachinko.mode.name());
        }
        return t;
    }

    /** 島のデータの画面（島の台をすべて）。 */
    public static void sendData(ServerPlayer player, String islandId) {
        MinecraftServer server = player.server;
        ParlorRules rules = CasinoRules.current().parlor();
        ParlorRules.Island island = rules.island(islandId);
        if (island == null && !rules.islands().isEmpty()) {
            island = rules.islands().get(0);
        }
        if (island == null) {
            return;
        }
        Map<Integer, Live> all = live(server);
        CompoundTag tag = new CompoundTag();
        tag.putString("island", island.id());
        tag.putString("name", island.name());
        ListTag islands = new ListTag();
        for (ParlorRules.Island i : rules.islands()) {
            if (i.newFloor() && !StoryState.get(server).has(rules.newFlag())) {
                continue;
            }
            CompoundTag one = new CompoundTag();
            one.putString("id", i.id());
            one.putString("name", i.name());
            ParlorModels.Model model = ParlorModels.get(i.model());
            one.putString("kind", model != null && model.isSlot() ? "slot" : "pachinko");
            islands.add(one);
        }
        tag.put("islands", islands);
        ListTag machines = new ListTag();
        for (ParlorRules.Machine m : rules.machinesOf(island.id())) {
            Live l = all.get(m.no());
            if (l != null) {
                machines.add(recordTag(l));
            }
        }
        tag.put("machines", machines);
        tag.put("rules", rulesTag());
        ModNetwork.sendTo(player, new ParlorS2C("data", tag));
    }

    // ================================================================ 管理者・自動テスト

    /**
     * 次の当たりを決めておく（管理者のコマンド・CIの写真）。スロットは次のレバーでボーナスの印（ランプが光る）・AT（天井）。
     * パチンコはすぐに当たりの保留を先頭に入れ、ノーマルリーチで見せる（大当たりの最中なら、そのあと）。
     */
    public static boolean force(MinecraftServer server, int no, String what) {
        Live l = live(server, no);
        if (l == null) {
            return false;
        }
        forcedKind.put(no, what);
        if (l.pachinko != null && l.pachinko.round == null) {
            // 回っているはずれの図柄は止めて、決めた当たりをすぐ回す
            if (l.pachinko.spin != null && !l.pachinko.spin.hit()) {
                l.pachinko.spin = null;
                l.pachinko.spinHold = null;
            }
            forcePachinko(l);
            l.forceReach = true;
        } else {
            l.forcedHits = 1;
        }
        return true;
    }

    private static final Map<Integer, String> forcedKind = new HashMap<>();

    static void forceSlot(Live l) {
        String what = forcedKind.getOrDefault(l.machine.no(), "big");
        l.forcedHits = 0;
        SlotSpec spec = spec(l);
        if (spec.type() == SlotSpec.Type.AT) {
            l.slot.atGames = 0;
            l.slot.czGames = 0;
            l.slot.sinceAt = spec.at().ceiling();   // 次のレバーで天井
        } else {
            l.slot.carried = "reg".equals(what) ? "reg" : "big";
            l.slot.lamp = true;
        }
    }

    static void forcePachinko(Live l) {
        l.forcedHits = 0;
        PachinkoSpec spec = l.model.pachinko();
        String what = forcedKind.getOrDefault(l.machine.no(), "");
        PachinkoSpec.Jackpot j = spec.jackpots().get(0);
        for (PachinkoSpec.Jackpot c : spec.jackpots()) {
            if (c.id().equals(what) || what.isEmpty() && c.heso() > 0 && c.kakuhen()) {
                j = c;
                break;
            }
        }
        List<PachinkoEngine.Hold> list = l.pachinko.support() ? l.pachinko.denchu : l.pachinko.heso;
        if (list.size() >= PachinkoEngine.MAX_HOLDS) {
            list.remove(list.size() - 1);
        }
        list.add(0, new PachinkoEngine.Hold(true, j.id(), 4, l.pachinko.support()));
    }

    /** 今日の設定と釘を決める（その日だけ。管理者のコマンド）。 */
    public static boolean setSetting(MinecraftServer server, int no, int setting, double nail) {
        Live l = live(server, no);
        if (l == null) {
            return false;
        }
        ParlorData data = ParlorData.get(server);
        if (setting > 0) {
            data.forcedSettings.put(no, setting);
        }
        if (nail > 0) {
            data.forcedNails.put(no, (int) Math.round(nail * 10));
        }
        data.setDirty();
        rollDay(server, true);
        return true;
    }

    /** 台を初めの状態に戻す（管理者のコマンド）。 */
    public static boolean reset(MinecraftServer server, int no) {
        Live l = live(server, no);
        if (l == null) {
            return false;
        }
        release(server, l, true);
        if (l.slot != null) {
            int s = l.slot.setting;
            l.slot = new SlotEngine.State();
            l.slot.setting = s;
        } else {
            int s = l.pachinko.setting;
            double n = l.pachinko.nail;
            l.pachinko = new PachinkoEngine.State();
            l.pachinko.setting = s;
            l.pachinko.nail = n;
        }
        updateLamp(server, l);
        saveAll(server);
        return true;
    }

    /** 管理者に見せる台の中（設定・釘・状態）。 */
    public static List<String> describe(MinecraftServer server, int no) {
        List<String> out = new ArrayList<>();
        Live l = live(server, no);
        if (l == null) {
            return out;
        }
        out.add(no + "番台 " + l.model.name() + "（" + l.model.specName() + "） 島 " + l.machine.island()
                + (l.occupant == null ? "" : " 座っている人 " + l.occupantName));
        if (l.slot != null) {
            out.add("設定 " + l.slot.setting + "  持ち越し " + (l.slot.carried.isEmpty() ? "なし" : l.slot.carried)
                    + "  ボーナス " + (l.slot.bonus.isEmpty() ? "なし" : l.slot.bonus) + "  AT 残り " + l.slot.atGames
                    + "  CZ 残り " + l.slot.czGames + "  天井まで " + (spec(l).at() == null ? "-" : spec(l).at().ceiling() - l.slot.sinceAt));
        } else {
            out.add("設定 " + l.pachinko.setting + "  釘 " + l.pachinko.nail + " 回/1000円  状態 " + l.pachinko.mode + " 残り "
                    + l.pachinko.modeSpins + "  保留 " + l.pachinko.heso.size() + "+" + l.pachinko.denchu.size()
                    + (l.pachinko.round == null ? "" : "  大当たり " + l.pachinko.round.jackpot));
        }
        MachineRecord r = l.record;
        out.add(l.slot != null ? "今日 " + r.games + "G  BIG " + r.big + "  REG " + r.reg + "  AT " + r.at + "  差枚 " + r.diff
                : "今日 " + r.spins + "回転  大当たり " + r.hits + "（初当たり " + r.firstHits + "）  最高連チャン " + r.maxChain);
        return out;
    }

    /** 今日の当たりの末尾と、設定の分かれ方（管理者のコマンド）。 */
    public static String today(MinecraftServer server) {
        ParlorData data = ParlorData.get(server);
        String day = ConnectionService.today();
        int[] count = new int[7];
        for (Live l : live(server).values()) {
            int s = l.slot != null ? l.slot.setting : l.pachinko.setting;
            count[Math.max(1, Math.min(6, s))]++;
        }
        return day + (DailySettings.isEvent(day) ? "（7のつく日・当たりの末尾 " + DailySettings.luckyDigit(data.salt, day) + "）" : "")
                + "  設定1:" + count[1] + " 2:" + count[2] + " 3:" + count[3] + " 4:" + count[4] + " 5:" + count[5] + " 6:" + count[6];
    }
}
