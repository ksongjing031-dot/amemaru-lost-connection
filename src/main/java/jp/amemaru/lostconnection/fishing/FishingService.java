package jp.amemaru.lostconnection.fishing;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.connection.ConnectionActions;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.RecordService;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.FishingS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 釣り（Phase 7 その2。Phase 0-A 15章「娯楽の設計（釣り）」・7章「釣り堀：釣りのミニゲーム、魚図鑑、魚の売却」）。
 *
 * <ul>
 *   <li>バニラの釣り竿で投げ、浮きが沈んだときに合わせる（引く）と魚がかかる（バニラの釣りの品は出さない）。かかった魚は、釣り場
 *       （釣り堀・湖畔の釣り場・そのほかの水）と今の章で決まる。そのあと「糸の張り」のミニゲーム（クライアントの {@code ClientFishing}）
 *       に勝つと釣り上げる。負けると「糸が切れた」「逃げられた」</li>
 *   <li>釣り上げた魚は図鑑に残り（いちばん大きいの・釣った数）、初めての魚はCP（魚図鑑に新しく登録）。ほかの人の近くで釣るとCP。
 *       記憶魚は記録帳に記憶が入る。湖の主は、最初に釣った人を覚える（限定称号「最初の大物」）</li>
 *   <li>ポッカ爺の釣り具屋の画面：図鑑・売る（大物は高い）・留め糸と交換（★1〜3のふつうの魚を点に）・竿を借りる・大会</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class FishingService {
    /** ミニゲームの結果を受け付ける、いちばん短い時間（tick。これより早い「勝った」は数えない）。 */
    private static final int MIN_RESULT_TICKS = 20;
    /** ミニゲームの「寄せ」のはじめの量（クライアントの {@code ClientFishing} と同じ）。 */
    private static final double START_PROGRESS = 0.25;
    /** 釣り具屋の画面を開いてから操作できる時間と、離れてよい距離。 */
    private static final long OPEN_TICKS = 20L * 60 * 30;
    private static final double OPEN_RANGE = 10.0;

    /**
     * かかっている魚（ミニゲームの最中）。
     *
     * @param fastestMillis いちばん早く勝てる時間（ずっと帯の中にいても「寄せ」がいっぱいになるまでの時間の9割）。これより早い
     *                      「勝った」は、作り変えたクライアントの知らせとみて数えない（Phase 0-A 13章の不正対策【補完】）
     */
    private record Session(int id, FishDef def, double size, String spot, long startTick, int limitTicks, long startMillis,
                           long fastestMillis) {
    }

    /** 開いている釣り具屋の画面（開いた所と時刻）。 */
    private record Open(Vec3 at, long tick, boolean admin) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVE = new HashMap<>();
    private static final Map<UUID, Open> OPEN = new HashMap<>();
    private static int nextId = 1;

    private FishingService() {
    }

    // ================================================================ 釣り場と魚

    /** その場所の釣り場（{@code pond}・{@code lake}・{@code river}）。湖は、湖畔の釣り場ができるまでは「そのほかの水」。 */
    public static String spotAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD) {
            Optional<BlockPos> origin = CityCommands.builtOrigin(level);
            if (origin.isPresent()) {
                BlockPos o = origin.get();
                int x = pos.getX() - o.getX();
                int y = pos.getY() - o.getY();
                int z = pos.getZ() - o.getZ();
                FishingRules rules = FishingRules.current();
                FishingRules.Box pond = rules.spots().get("pond");
                if (pond != null && pond.contains(x, y, z)) {
                    return "pond";
                }
                FishingRules.Box lake = rules.spots().get("lake");
                if (lake != null && lake.contains(x, y, z)) {
                    return lakeOpen(level.getServer()) ? "lake" : "river";
                }
            }
        }
        return "river";
    }

    /** 湖畔の釣り場ができているか（共同事業①。物語の印 {@code lake_flag}）。 */
    public static boolean lakeOpen(MinecraftServer server) {
        return StoryState.get(server).has(FishingRules.current().lakeFlag());
    }

    /** かかる魚を決める（釣り場と今の章）。何もいなければ null。 */
    @Nullable
    public static FishDef roll(RandomSource random, String spot, int chapter) {
        FishingRules rules = FishingRules.current();
        List<FishDef> defs = FishDefs.ordered();
        if (!"river".equals(spot)) {
            if ("lake".equals(spot) && random.nextDouble() * 100.0 < rules.lordChance()) {
                for (FishDef def : defs) {
                    if (def.legendary() && def.spots().contains(spot) && def.chapter() <= chapter) {
                        return def;
                    }
                }
            }
            if (random.nextDouble() * 100.0 < rules.memoryChance()) {
                List<FishDef> memories = new ArrayList<>();
                for (FishDef def : defs) {
                    if (def.isMemory() && def.chapter() <= chapter && def.spots().contains(spot)) {
                        memories.add(def);
                    }
                }
                if (!memories.isEmpty()) {
                    return memories.get(random.nextInt(memories.size()));
                }
            }
        }
        // ふつうの魚（★1〜3）：★ごとの出やすさを、その★の魚の数で分ける（★の割合は変わらない）
        Map<Integer, Integer> perRarity = new HashMap<>();
        List<FishDef> pool = new ArrayList<>();
        for (FishDef def : defs) {
            if (def.rarity() <= 3 && !def.isMemory() && !def.legendary() && def.chapter() <= chapter && def.spots().contains(spot)) {
                pool.add(def);
                perRarity.merge(def.rarity(), 1, Integer::sum);
            }
        }
        double total = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            FishDef def = pool.get(i);
            double w = rules.rarityWeight(def.rarity()) / perRarity.get(def.rarity());
            if (def.chapter() == chapter) {
                w *= rules.currentChapterWeight();
            }
            weights[i] = Math.max(0.0, w);
            total += weights[i];
        }
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            pick -= weights[i];
            if (pick < 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** 大きさを決める（小さいのが多い。size_skew）。 */
    public static double rollSize(RandomSource random, FishDef def) {
        double r = Math.pow(random.nextDouble(), FishingRules.current().sizeSkew());
        return Math.round((def.sizeMin() + (def.sizeMax() - def.sizeMin()) * r) * 10.0) / 10.0;
    }

    @Nullable
    public static Item itemOf(FishDef def) {
        Item item = ForgeRegistries.ITEMS.getValue(def.itemId());
        return item == null || item == Items.AIR ? null : item;
    }

    /** 魚1匹の買い取りの値（大物は big_price_factor 倍。魚でなければ 0）。 */
    public static long price(ItemStack stack) {
        FishDef def = FishDefs.of(stack);
        if (def == null) {
            return 0;
        }
        double price = def.price() * (FishItem.isBig(stack) ? FishingRules.current().bigPriceFactor() : 1.0);
        return Math.max(1L, Math.round(price));
    }

    /** 留め糸と交換できる魚か（★1〜3のふつうの魚。大物・記憶魚・湖の主は交換しない）。 */
    public static boolean tradable(ItemStack stack) {
        FishDef def = FishDefs.of(stack);
        return def != null && def.rarity() <= 3 && !FishItem.isBig(stack);
    }

    /** まとめて売るときに売る魚か（記憶魚と湖の主は、まちがえて売らないように除く）。 */
    public static boolean bulkSellable(ItemStack stack) {
        FishDef def = FishDefs.of(stack);
        return def != null && def.rarity() <= 3;
    }

    /** 図鑑の埋まり具合（%。切り捨て）。 */
    public static long percent(PlayerData data) {
        int total = FishDefs.all().size();
        if (total <= 0) {
            return 0;
        }
        int found = 0;
        for (FishDef def : FishDefs.all().values()) {
            if (data.getFishing().has(def.id())) {
                found++;
            }
        }
        return found * 100L / total;
    }

    // ================================================================ かかる・ミニゲーム

    /** 合わせた（浮きが沈んでいる間に引いた）。バニラの品の代わりに、魚をかけてミニゲームを始める。 */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!ServerConfig.FISHING_ENABLED.get() || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        FishingHook hook = event.getHookEntity();
        event.setCanceled(true);
        if (SESSIONS.containsKey(player.getUUID()) || !(hook.level() instanceof ServerLevel level)) {
            return;
        }
        String spot = spotAt(level, hook.blockPosition());
        FishDef def = roll(level.random, spot, ProgressionData.currentChapter(player.server));
        if (def == null) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.nothing").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        startSession(player, def, rollSize(level.random, def), spot);
    }

    /** ミニゲームを始める（管理者のコマンドからも）。 */
    public static void startSession(ServerPlayer player, FishDef def, double size, String spot) {
        FishingRules rules = FishingRules.current();
        FishingRules.Minigame mg = rules.minigame();
        double d = def.difficulty();
        int ticks = mg.baseTicks() + (int) Math.round(mg.extraTicks() * d);
        int limit = mg.limitSeconds() * 20;
        int id = nextId++;
        long now = player.server.getTickCount();
        long fastest = Math.round((1.0 - START_PROGRESS) * ticks * 50 * 0.9);
        SESSIONS.put(player.getUUID(), new Session(id, def, size, spot, now, limit, System.currentTimeMillis(), fastest));
        LAST_ACTIVE.put(player.getUUID(), now);
        // 称号「湖を知る者」の効果：ゲージ（よい所）が広い（Phase 0-A 22章の効果の提案）
        double bonus = TitleService.effectAmount(player, "fishing_gauge") / 100.0;
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("stars", def.rarity());
        tag.putDouble("difficulty", d);
        tag.putDouble("center", mg.zoneCenter());
        tag.putDouble("half", Math.max(0.03, mg.zoneHalf() * (1.0 - mg.zoneShrink() * d) * (1.0 + bonus)));
        tag.putDouble("reel", mg.reelRate());
        tag.putDouble("slack", mg.slackRate());
        tag.putDouble("pull", mg.pull() * (0.5 + d));
        tag.putDouble("burst", mg.burst() * (0.3 + d));
        tag.putDouble("decay", mg.decay());
        tag.putInt("ticks", ticks);
        tag.putInt("escape", mg.escapeTicks());
        tag.putInt("limit", limit);
        tag.putLong("seed", player.getRandom().nextLong());
        // 「かかった！」は画面の真ん中の下の糸の張りの札に出す（画面の下の一行は札と重なるので使わない）
        ModNetwork.sendTo(player, new FishingS2C("start", tag));
        player.level().playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    /** 今ミニゲームの最中か。 */
    public static boolean inSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** ミニゲームの結果（クライアントから）。 */
    static void onResult(ServerPlayer player, CompoundTag args) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != args.getInt("id")) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        long elapsed = player.server.getTickCount() - session.startTick();
        long millis = System.currentTimeMillis() - session.startMillis();
        boolean success = args.getBoolean("success") && elapsed >= MIN_RESULT_TICKS && elapsed <= session.limitTicks() + 200
                && millis >= session.fastestMillis();
        LAST_ACTIVE.put(player.getUUID(), (long) player.server.getTickCount());
        if (!success) {
            // 「糸が切れた」「逃げられた」は、糸の張りの札に出る（ClientFishing）
            boolean snapped = "snap".equals(args.getString("reason"));
            player.level().playSound(null, player.blockPosition(), snapped ? SoundEvents.ITEM_BREAK : SoundEvents.FISH_SWIM,
                    SoundSource.PLAYERS, 0.6F, snapped ? 1.4F : 1.0F);
            return;
        }
        catchFish(player, session.def(), session.size(), session.spot(), true);
    }

    // ================================================================ 釣り上げた

    /**
     * 魚を釣り上げた（ミニゲームに勝った・管理者のコマンド）。アイテムを渡し、図鑑に書き、記録・称号・CP・大会に知らせる。
     *
     * @param minigame ミニゲームに勝ったか（CPは、ミニゲームに勝ったときだけ数える。Phase 0-A 13章の不正対策）
     */
    public static void catchFish(ServerPlayer player, FishDef def, double size, String spot, boolean minigame) {
        MinecraftServer server = player.server;
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        Item item = itemOf(def);
        if (data == null || item == null) {
            return;
        }
        FishingRules rules = FishingRules.current();
        int day = ProgressionData.currentDay(server);
        String name = player.getGameProfile().getName();
        boolean big = def.legendary() || def.sizeRatio(size) >= rules.bigRatio();
        ItemStack stack = new ItemStack(item);
        if (big) {
            FishItem.markBig(stack, size, name, day);
        }
        Component fishName = stack.getHoverName();
        give(player, stack);
        boolean first = data.getFishing().record(def.id(), size, day);
        String sizeText = String.format(Locale.ROOT, "%.1f", size);
        player.sendSystemMessage(Component.translatable(big ? "fishing.amemaru.caught_big" : "fishing.amemaru.caught", fishName, sizeText)
                .withStyle(big ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.8F, 1.2F);
        level.playSound(null, player.blockPosition(), big ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.7F, big ? 1.3F : 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.3, 0.4, 0.1);
        if (first) {
            player.sendSystemMessage(Component.translatable("fishing.amemaru.dex_new", fishName, data.getFishing().species(),
                    FishDefs.all().size()).withStyle(ChatFormatting.GREEN));
            if (minigame) {
                ConnectionService.award(player, ConnectionActions.FISH_DEX);
            }
        }
        // 記憶魚：古代の人々の短い記憶が、記録帳に入る
        if (def.memory() != null) {
            RecordService.find(player, def.memory());
            if (allMemories(data)) {
                TitleService.onEvent(player, "memory_fish_complete");
            }
        }
        if (def.legendary()) {
            lord(player, size);
        }
        if (minigame && nearOthers(player)) {
            ConnectionService.award(player, ConnectionActions.FISH_NEAR_OTHERS);
        }
        FishingTournament.onCatch(player, def, size, spot);
        TitleService.check(player);
        LAST_ACTIVE.put(player.getUUID(), (long) server.getTickCount());
        AmemaruMod.LOGGER.info("[amemaru] {} caught {} ({} cm) at {}", name, def.id(), sizeText, spot);
    }

    /** 記憶魚をすべて釣ったか。 */
    static boolean allMemories(PlayerData data) {
        boolean any = false;
        for (FishDef def : FishDefs.all().values()) {
            if (def.isMemory()) {
                any = true;
                if (!data.getFishing().has(def.id())) {
                    return false;
                }
            }
        }
        return any;
    }

    /** 湖の主を釣った。サーバーで初めてなら、その人を覚え、限定称号と告知（ポッカの五十年竿は Phase 8 のユニーク装備）。 */
    private static void lord(ServerPlayer player, double size) {
        MinecraftServer server = player.server;
        FishingData fd = FishingData.get(server);
        String name = player.getGameProfile().getName();
        String sizeText = String.format(Locale.ROOT, "%.1f", size);
        int day = ProgressionData.currentDay(server);
        if (fd.lord() == null) {
            fd.setLord(new FishingData.LordCatch(player.getUUID(), name, day, System.currentTimeMillis(), size));
            TitleService.onFirst(server, "first_lake_lord", List.of(player));
            server.getPlayerList().broadcastSystemMessage(Component.translatable("fishing.amemaru.lord_first", name, sizeText)
                    .withStyle(ChatFormatting.GOLD), false);
            GuildState.get(server).chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.lake_lord", List.of(name, sizeText));
        } else {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("fishing.amemaru.lord_again", name, sizeText)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** 管理者：湖の主を最初に釣った人の記録を消す（限定称号と冒険日誌はそのまま）。 */
    public static void resetLord(MinecraftServer server) {
        FishingData.get(server).setLord(null);
    }

    /** ほかの人が近くで釣っているか（16ブロック以内で、竿を投げている・ミニゲームの最中・30秒以内に釣った）。 */
    static boolean nearOthers(ServerPlayer player) {
        FishingRules rules = FishingRules.current();
        double r = rules.nearRadius();
        long window = rules.nearSeconds() * 20L;
        long now = player.server.getTickCount();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || other.isSpectator() || other.distanceToSqr(player) > r * r) {
                continue;
            }
            Long last = LAST_ACTIVE.get(other.getUUID());
            if (other.fishing != null || SESSIONS.containsKey(other.getUUID()) || (last != null && now - last <= window)) {
                return true;
            }
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ================================================================ ポッカ爺の釣り具屋

    /** 釣り具屋の画面を開く（ポッカ爺の会話・管理者のコマンド）。 */
    public static void openShop(ServerPlayer player, String tab, boolean admin) {
        OPEN.put(player.getUUID(), new Open(player.position(), player.server.getTickCount(), admin));
        sendShop(player, tab, false);
    }

    /** 画面の中身を送る（refresh：開いている画面を書きかえる）。 */
    private static void sendShop(ServerPlayer player, String tab, boolean refresh) {
        CompoundTag tag = shopTag(player);
        tag.putString("tab", tab);
        if (refresh) {
            tag.putBoolean("refresh", true);
        }
        ModNetwork.sendTo(player, new FishingS2C("screen", tag));
    }

    /** 画面の中身（図鑑・持ち物の魚と値段・点・大会）。 */
    public static CompoundTag shopTag(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow();
        FishingRecord rec = data.getFishing();
        FishingRules rules = FishingRules.current();
        CompoundTag tag = new CompoundTag();
        ListTag dex = new ListTag();
        for (FishDef def : FishDefs.ordered()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", def.id().toString());
            one.putString("item", def.itemId().toString());
            one.putString("name", def.name());
            one.putInt("chapter", def.chapter());
            one.putInt("stars", def.rarity());
            ListTag spots = new ListTag();
            for (String s : def.spots()) {
                spots.add(StringTag.valueOf(s));
            }
            one.put("spots", spots);
            one.putDouble("min", def.sizeMin());
            one.putDouble("max", def.sizeMax());
            one.putLong("price", def.price());
            one.putBoolean("memory", def.isMemory());
            one.putBoolean("lord", def.legendary());
            FishingRecord.Entry entry = rec.entry(def.id());
            if (entry != null) {
                one.putBoolean("known", true);
                one.putInt("count", entry.count());
                one.putDouble("best", entry.best());
                one.putInt("first", entry.firstDay());
                one.putString("comment", def.comment());
            }
            dex.add(one);
        }
        tag.put("dex", dex);
        tag.putInt("species", rec.species());
        tag.putInt("total", rec.total());
        ListTag inv = new ListTag();
        List<ItemStack> items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            FishDef def = FishDefs.of(stack);
            if (def == null) {
                continue;
            }
            CompoundTag one = new CompoundTag();
            one.putInt("slot", slot);
            one.put("stack", stack.save(new CompoundTag()));
            one.putLong("price", price(stack));
            one.putInt("points", tradable(stack) ? def.points() : 0);
            one.putBoolean("tradable", tradable(stack));
            one.putBoolean("bulk", bulkSellable(stack));
            inv.add(one);
        }
        tag.put("inv", inv);
        tag.putInt("points", rec.points());
        tag.putInt("per", rules.pointsPerThread());
        tag.putLong("rent", rules.rodRentKnots());
        tag.putBoolean("hasRod", hasRod(player));
        tag.putInt("chapter", ProgressionData.currentChapter(player.server));
        tag.putBoolean("lake", lakeOpen(player.server));
        FishingData.LordCatch lord = FishingData.get(player.server).lord();
        if (lord != null) {
            tag.putString("lordBy", lord.name());
        }
        tag.put("tournament", FishingTournament.stateTag(player.server, player));
        return tag;
    }

    /** 画面の操作（{@link jp.amemaru.lostconnection.network.c2s.FishingC2S}）。 */
    public static void onAction(ServerPlayer player, String action, CompoundTag args) {
        if ("result".equals(action)) {
            onResult(player, args);
            return;
        }
        if ("open".equals(action)) {
            // 画面の中でタブを変えた（もう開いているときだけ。開いた所と時刻はそのまま）
            if (canUseShop(player)) {
                sendShop(player, args.getString("tab"), true);
            }
            return;
        }
        if (!canUseShop(player)) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.shop_far").withStyle(ChatFormatting.RED), true);
            return;
        }
        switch (action) {
            case "sell" -> sell(player, args.getInt("slot"));
            case "sell_all" -> sellAll(player);
            case "trade" -> trade(player, args.getInt("slot"));
            case "trade_all" -> tradeAll(player);
            case "rent_rod" -> rentRod(player);
            default -> {
                return;
            }
        }
        sendShop(player, args.getString("tab"), true);
    }

    /** 釣り具屋の画面を使えるか（開いてから30分のうちで、開いた所から10ブロック以内。管理者が開いたときは距離を見ない）。 */
    static boolean canUseShop(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.server.getTickCount() - open.tick() > OPEN_TICKS) {
            return false;
        }
        return open.admin() || player.position().distanceTo(open.at()) <= OPEN_RANGE;
    }

    /** 持ち物の1か所の魚を売る。売った額。 */
    public static long sell(ServerPlayer player, int slot) {
        List<ItemStack> items = player.getInventory().items;
        if (slot < 0 || slot >= items.size()) {
            return 0;
        }
        ItemStack stack = items.get(slot);
        if (FishDefs.of(stack) == null) {
            return 0;
        }
        long total = price(stack) * stack.getCount();
        int count = stack.getCount();
        Component name = stack.getHoverName();
        items.set(slot, ItemStack.EMPTY);
        payout(player, total);
        player.sendSystemMessage(Component.translatable("fishing.amemaru.sold", name, count, total).withStyle(ChatFormatting.GREEN));
        return total;
    }

    /** ★1〜3の魚をまとめて売る（記憶魚と湖の主は売らない）。売った額。 */
    public static long sellAll(ServerPlayer player) {
        List<ItemStack> items = player.getInventory().items;
        long total = 0;
        int count = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (bulkSellable(stack)) {
                total += price(stack) * stack.getCount();
                count += stack.getCount();
                items.set(slot, ItemStack.EMPTY);
            }
        }
        if (count == 0) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.none_to_sell").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        payout(player, total);
        player.sendSystemMessage(Component.translatable("fishing.amemaru.sold_all", count, total).withStyle(ChatFormatting.GREEN));
        return total;
    }

    private static void payout(ServerPlayer player, long total) {
        PlayerDataCapability.get(player).ifPresent(data -> Wallet.give(player, data, total, "fish sale"));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** 持ち物の1か所の魚を、点に換える（留め糸）。換えた点。 */
    public static int trade(ServerPlayer player, int slot) {
        List<ItemStack> items = player.getInventory().items;
        if (slot < 0 || slot >= items.size() || !tradable(items.get(slot))) {
            return 0;
        }
        ItemStack stack = items.get(slot);
        int points = FishDefs.of(stack).points() * stack.getCount();
        items.set(slot, ItemStack.EMPTY);
        addPoints(player, points);
        return points;
    }

    /** ★1〜3のふつうの魚を、まとめて点に換える。換えた点。 */
    public static int tradeAll(ServerPlayer player) {
        List<ItemStack> items = player.getInventory().items;
        int points = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (tradable(stack)) {
                points += FishDefs.of(stack).points() * stack.getCount();
                items.set(slot, ItemStack.EMPTY);
            }
        }
        if (points == 0) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.none_to_trade").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        addPoints(player, points);
        return points;
    }

    /** 点を足し、決まった点ごとに留め糸を渡す（端数は残す）。 */
    private static void addPoints(ServerPlayer player, int points) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || points <= 0) {
            return;
        }
        int per = FishingRules.current().pointsPerThread();
        int sum = data.getFishing().points() + points;
        int threads = sum / per;
        data.getFishing().setPoints(sum % per);
        if (threads > 0) {
            Item thread = BuiltInRegistries.ITEM.get(AmemaruMod.id("binding_thread"));
            int left = threads;
            while (left > 0) {
                int n = Math.min(left, thread.getMaxStackSize());
                give(player, new ItemStack(thread, n));
                left -= n;
            }
        }
        player.sendSystemMessage(Component.translatable("fishing.amemaru.traded", points, threads, data.getFishing().points(), per)
                .withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    static boolean hasRod(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof FishingRodItem) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof FishingRodItem;
    }

    /** 竿を借りる（持っていないときだけ。Phase 0-B の釣り具屋の看板「貸し竿あり」【補完】）。 */
    public static boolean rentRod(ServerPlayer player) {
        if (hasRod(player)) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.rod_have").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        long cost = FishingRules.current().rodRentKnots();
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || (cost > 0 && !Wallet.pay(player, data, cost, "rental rod"))) {
            player.displayClientMessage(Component.translatable("fishing.amemaru.rod_money", cost).withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.setHoverName(Component.translatable("item.amemaru.rental_rod").withStyle(style -> style.withItalic(false)));
        give(player, rod);
        player.sendSystemMessage(Component.translatable("fishing.amemaru.rod_rented").withStyle(ChatFormatting.GREEN));
        return true;
    }

    // ================================================================ イベント

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 20 == 0) {
            // ミニゲームの結果が来ないまま、時間を過ぎた（画面を閉じた・切断した）
            SESSIONS.entrySet().removeIf(e -> now - e.getValue().startTick() > e.getValue().limitTicks() + 200);
            FishingTournament.tick(server);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SESSIONS.remove(id);
        OPEN.remove(id);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FishingTournament.onLoggedIn(player);
        }
    }

    /** 自動テスト：かかっている魚を消す。 */
    public static void clearSessionForTest(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** 自動テスト：ミニゲームの結果を、すぐ送ったことにする（始めた時刻を前にずらす）。 */
    public static boolean finishSessionForTest(ServerPlayer player, boolean success) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(s.id(), s.def(), s.size(), s.spot(), s.startTick() - MIN_RESULT_TICKS - 1, s.limitTicks(),
                s.startMillis() - s.fastestMillis() - 1, s.fastestMillis()));
        CompoundTag args = new CompoundTag();
        args.putInt("id", s.id());
        args.putBoolean("success", success);
        onResult(player, args);
        return true;
    }
}
