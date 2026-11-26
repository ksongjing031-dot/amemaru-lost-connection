package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.Reflecta;
import jp.amemaru.lostconnection.enemy.iceforest.Mimic;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.item.KnotCordItem;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.npc.LiliaMemoryService;
import jp.amemaru.lostconnection.npc.NpcMemoryService;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.iceforest.IceForestBlocks;
import jp.amemaru.lostconnection.tower.iceforest.LightMazeService;
import jp.amemaru.lostconnection.tower.iceforest.PassphraseService;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 11月25日（7階「氷の森林」）の自動テスト。
 *
 * <ul>
 *   <li>7階の形：光の迷路3つ（光の泉・鏡・受け石・開く門）・合言葉の扉・名のある魔物2体・封印は「光＋名のある魔物＋合言葉」</li>
 *   <li>鏡の決まり：「／」と「＼」で光の向きが変わる</li>
 *   <li>ロスト状態「薄れ」：かかる・ロスト耐性で短くなる（50%まで）・結び直し（そばでしゃがんで3秒）・結び紐でほどける</li>
 *   <li>写し身：人の姿を写し、斬ると薄れ。鏡像獣リフレクタ：写し身を作って鏡の守り、すべて倒すと守りが割れる</li>
 *   <li>リリア：7階に人が来ると忘れ、「ただいま」（1人1日1回）で少しずつ思い出す</li>
 *   <li>ボスのレア「鏡氷の短剣」（二重に当たる）・名のある魔物のレアのお守り・7階のアクセサリー</li>
 * </ul>
 * <p>鏡を回して光を届ける道と、扉の前で合言葉を言う道は、7階を本当に建てて {@link FloorRealPlayGameTests} で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IceForestGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private IceForestGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void floor7Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(7);
        check(helper, def != null && def.bossRoom() != null, "7階の定義がありません");
        check(helper, def.chapter() == 2, "7階が第2章になっていません：" + def.chapter());
        for (int n = 1; n <= 6; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と7階が重なっています");
        }
        check(helper, AmemaruMod.id("reflecta").equals(def.bossRoom().boss()), "7階のボスがリフレクタではありません：" + def.bossRoom().boss());
        check(helper, def.bossRoom().next() != null, "7階のボスの部屋に、次の階への円がありません");
        FloorFeatures fx = def.features();
        check(helper, fx.requires(FloorFeatures.SEAL_LIGHTS) && fx.requires(FloorFeatures.SEAL_GUARDIANS)
                && fx.requires(FloorFeatures.SEAL_PASSPHRASE), "7階の封印の条件が「光の迷路＋名のある魔物＋合言葉」ではありません");
        check(helper, fx.lightMazes().size() == 3, "光の迷路が3つではありません：" + fx.lightMazes().size());
        for (FloorFeatures.LightMaze maze : fx.lightMazes()) {
            check(helper, maze.mirrors().size() >= 3, maze.name() + "の鏡が少なすぎます：" + maze.mirrors().size());
            check(helper, maze.door() != null, maze.name() + "に、光で開く門がありません");
            check(helper, fx.lightMaze(maze.id()) == maze, maze.name() + "を ID で引けません");
        }
        check(helper, fx.passphrase() != null && fx.passphrase().seconds() >= 10, "合言葉の扉がないか、言う時間が短すぎます");
        check(helper, TowerService.namedGuardians(def).size() == 2, "名のある魔物（白角・吹雪の大精）が2体ではありません");
        check(helper, fx.environment() != null && "aurora".equals(fx.environment().sky()) && "snow".equals(fx.environment().weather()),
                "7階の空がオーロラ・天気が雪ではありません");
        check(helper, fx.waypoints().size() == 4, "中継の灯が4つではありません：" + fx.waypoints().size());
        check(helper, def.extras().chests().size() >= 15, "7階の宝箱が少なすぎます：" + def.extras().chests().size());
        for (String zone : new String[]{"mirror_grove", "watch_station", "passphrase_door"}) {
            check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals(zone)), "物語の場所 " + zone + " がありません");
            check(helper, StoryEventManager.get(AmemaruMod.id("floor7/" + zone)) != null, "物語 floor7/" + zone + " がありません");
        }
        check(helper, StoryEventManager.get(AmemaruMod.id("floor7/enter")) != null && StoryEventManager.get(AmemaruMod.id("floor7/first_kill")) != null,
                "7階の入ったとき・初撃破の物語がありません");
        check(helper, RecordDefs.get(AmemaruMod.id("f07_watch_post")) != null && def.tablet() != null,
                "古代記録「森林監視所の記録」か、その書見台がありません");
        helper.succeed();
    }

    /** 鏡の決まり：「／」は東→北・北→東・西→南・南→西、「＼」は東→南・南→東・西→北・北→西。 */
    @GameTest(template = EMPTY)
    public static void mirrorsReflectLikeSlashes(GameTestHelper helper) {
        Direction[][] slash = {{Direction.EAST, Direction.NORTH}, {Direction.NORTH, Direction.EAST}, {Direction.WEST, Direction.SOUTH},
                {Direction.SOUTH, Direction.WEST}};
        Direction[][] back = {{Direction.EAST, Direction.SOUTH}, {Direction.SOUTH, Direction.EAST}, {Direction.WEST, Direction.NORTH},
                {Direction.NORTH, Direction.WEST}};
        for (Direction[] pair : slash) {
            check(helper, LightMazeService.reflect(pair[0], true) == pair[1], "「／」で " + pair[0] + " が " + pair[1] + " になりません");
        }
        for (Direction[] pair : back) {
            check(helper, LightMazeService.reflect(pair[0], false) == pair[1], "「＼」で " + pair[0] + " が " + pair[1] + " になりません");
        }
        check(helper, jp.amemaru.lostconnection.core.registry.ModBlocks.ICE_MIRROR.get().defaultBlockState().getValue(IceForestBlocks.SLASH),
                "氷の鏡のはじめの向きが「／」ではありません");
        check(helper, !jp.amemaru.lostconnection.core.registry.ModBlocks.LIGHT_TARGET.get().defaultBlockState().getValue(IceForestBlocks.LIT),
                "光の受け石が、はじめから灯っています");
        helper.succeed();
    }

    // ---------------------------------------------------------------- ロスト状態「薄れ」

    @GameTest(template = EMPTY, batch = "iceforest_lost", timeoutTicks = 100)
    public static void fadeRetieAndKnotCord(GameTestHelper helper) {
        FakePlayer a = fake(helper, "fade_a");
        FakePlayer b = fake(helper, "fade_b");
        ready(a);
        ready(b);
        a.removeAllEffects();
        b.removeAllEffects();
        check(helper, LostService.isLost(ModEffects.LOST_FADE.get()), "薄れがロスト状態になっていません");
        LostService.inflict(a, ModEffects.LOST_FADE.get(), 200);
        check(helper, a.hasEffect(ModEffects.LOST_FADE.get()) && a.getEffect(ModEffects.LOST_FADE.get()).getDuration() >= 190,
                "薄れが10秒かかりません");
        LostService.inflict(a, ModEffects.LOST_FADE.get(), 40);
        check(helper, a.getEffect(ModEffects.LOST_FADE.get()).getDuration() >= 150, "短い薄れで、長い薄れが短くなりました");

        // 結び直し：しゃがまないと進まない。そばで、しゃがみ続けて3秒
        b.setPos(a.getX() + 1.0, a.getY(), a.getZ());
        List<ServerPlayer> around = List.of(a, b);
        a.setShiftKeyDown(false);
        check(helper, !LostService.tickRetie(a, around) && LostService.retieTicks(a) == 0, "しゃがんでいないのに、結び直しが進みます");
        a.setShiftKeyDown(true);
        boolean done = false;
        for (int i = 0; i < LostService.RETIE_TICKS && !done; i++) {
            done = LostService.tickRetie(a, around);
            if (i == LostService.RETIE_TICKS / 2) {
                check(helper, a.hasEffect(ModEffects.LOST_FADE.get()), "結び直しの途中で、もう薄れがほどけました");
            }
        }
        a.setShiftKeyDown(false);
        check(helper, done && !a.hasEffect(ModEffects.LOST_FADE.get()), "そばでしゃがみ続けても、結び直しで薄れがほどけません");
        // 離れていると、相手にならない
        LostService.inflict(a, ModEffects.LOST_FADE.get(), 200);
        b.setPos(a.getX() + LostService.RETIE_RANGE + 2.0, a.getY(), a.getZ());
        a.setShiftKeyDown(true);
        check(helper, !LostService.tickRetie(a, around) && LostService.retieTicks(a) == 0, "離れた人と、結び直しが始まりました");
        a.setShiftKeyDown(false);

        // 結び紐：自分とまわりの仲間をほどく（1つ減る）。だれもロスト状態でなければ減らない
        b.setPos(a.getX() + 2.0, a.getY(), a.getZ());
        LostService.inflict(b, ModEffects.LOST_FADE.get(), 200);
        Item cord = ModItems.MANIFEST.get("knot_cord").get();
        check(helper, cord instanceof KnotCordItem, "結び紐が結び紐の品になっていません");
        ItemStack stack = new ItemStack(cord, 2);
        b.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // にせのプレイヤーは世界の人の一覧に入らないので、まわりの人を渡して使う（右クリックの use と同じ中身）
        KnotCordItem knot = (KnotCordItem) cord;
        InteractionResultHolder<ItemStack> used = knot.apply(b, b.getMainHandItem(), around);
        check(helper, used.getResult().consumesAction(), "結び紐を使えません：" + used.getResult());
        check(helper, !a.hasEffect(ModEffects.LOST_FADE.get()) && !b.hasEffect(ModEffects.LOST_FADE.get()), "結び紐で、二人の薄れがほどけません");
        check(helper, b.getMainHandItem().getCount() == 1, "結び紐が1つ減りません：" + b.getMainHandItem().getCount());
        InteractionResultHolder<ItemStack> idle = knot.apply(b, b.getMainHandItem(), around);
        check(helper, !idle.getResult().consumesAction() && b.getMainHandItem().getCount() == 1, "ほどくものがないのに、結び紐が減りました");
        b.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.removeAllEffects();
        b.removeAllEffects();
        helper.succeed();
    }

    /** ロスト耐性：吹雪の灯（ロスト耐性 +20%）を付けると、薄れが2割短くなる。ステータスと足して50%まで。 */
    @GameTest(template = EMPTY, batch = "iceforest_lost")
    public static void lostResistShortensFade(GameTestHelper helper) {
        FakePlayer player = fake(helper, "resist");
        PlayerData data = ready(player);
        player.removeAllEffects();
        check(helper, LostService.resist(player) < EPSILON, "何も付けていないのに、ロスト耐性があります：" + LostService.resist(player));
        AccessoryDef lamp = AccessoryDefs.get(ModItems.MANIFEST.get("blizzard_lamp_charm").get());
        check(helper, lamp != null && lamp.floor() == 7 && lamp.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.LOST_RESIST
                && Math.abs(e.percent() - 20.0) < EPSILON), "吹雪の灯の効き目が「ロスト耐性 +20%」ではありません");
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        check(helper, handler != null, "Curios の持ち物がありません");
        ICurioStacksHandler charm = handler.getCurios().get(AccessorySlot.CHARM.curiosId());
        ItemStack stack = new ItemStack(ModItems.MANIFEST.get("blizzard_lamp_charm").get());
        AccessoryRoller.roll(stack, lamp, AccessoryRules.current(), RandomSource.create(7L), false, null, 1);
        try {
            charm.getStacks().setStackInSlot(0, stack);
            LevelService.recompute(player, data);
            double resist = LostService.resist(player);
            check(helper, resist >= 20.0 - EPSILON && resist <= LostService.MAX_RESIST + EPSILON, "吹雪の灯で、ロスト耐性が20%以上になりません：" + resist);
            LostService.inflict(player, ModEffects.LOST_FADE.get(), 200);
            int length = player.getEffect(ModEffects.LOST_FADE.get()).getDuration();
            int expect = (int) Math.round(200 * (1.0 - resist / 100.0));
            check(helper, Math.abs(length - expect) <= 2, "ロスト耐性 " + resist + "% で、薄れが " + expect + " tick になりません：" + length);
        } finally {
            charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            LevelService.recompute(player, data);
            player.removeAllEffects();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 写し身と鏡像獣

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void mimicCopiesAndFades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "mimic");
        ready(player);
        player.removeAllEffects();
        Mimic mimic = ModEntities.MIMIC.get().create(level);
        check(helper, mimic != null, "写し身を作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        mimic.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(mimic);
        EnemyDef def = EnemyDefs.forType(ModEntities.MIMIC.get());
        check(helper, def != null && def.floor() == 7 && "写し身".equals(def.name()), "写し身の定義がありません");
        check(helper, !mimic.bossCopy(), "ふつうの写し身が、ボスの写し身になっています");
        mimic.copy(player.getUUID());
        check(helper, player.getUUID().equals(mimic.copied()), "写し身が、その人の姿を写しません");
        mimic.onStruck(player, 1.0);
        check(helper, player.hasEffect(ModEffects.LOST_FADE.get()), "写し身に斬られても、薄れになりません");
        mimic.discard();
        player.removeAllEffects();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void reflectaCopiesWardAndBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer a = fake(helper, "refl_a");
        FakePlayer b = fake(helper, "refl_b");
        ready(a);
        ready(b);
        a.removeAllEffects();
        b.removeAllEffects();
        Reflecta boss = ModEntities.REFLECTA.get().create(level);
        check(helper, boss != null, "リフレクタを作れません");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        EnemyDef def = EnemyDefs.forType(ModEntities.REFLECTA.get());
        check(helper, def != null && def.pity(), "リフレクタの定義がないか、天井がありません");
        check(helper, def.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id("mirror_ice_daggers")) && Math.abs(r.chance() - 5.0) < EPSILON),
                "リフレクタのレアに「鏡氷の短剣」5% がありません");
        check(helper, Math.abs(boss.damageTakenMultiplier(null) - 1.0) < EPSILON, "写し身がいないのに、鏡の守りがあります");
        int made = boss.spawnCopies(level, List.of(a, b));
        check(helper, made == 2 && boss.copies() == 2, "二人ぶんの写し身ができません：" + made);
        check(helper, a.hasEffect(ModEffects.LOST_FADE.get()) && b.hasEffect(ModEffects.LOST_FADE.get()), "写し身ができても、薄れになりません");
        double ward = def.skill("mirror", 0, 2, 60, 6).param("ward", 0.3);
        check(helper, Math.abs(boss.damageTakenMultiplier(null) - ward) < EPSILON, "写し身がいる間、鏡の守り（" + ward + "倍）になりません");
        List<Mimic> copies = new ArrayList<>();
        for (Entity e : level.getEntities(boss, boss.getBoundingBox().inflate(20.0), e -> e instanceof Mimic)) {
            copies.add((Mimic) e);
        }
        check(helper, copies.size() == 2 && copies.stream().allMatch(Mimic::bossCopy), "ボスの写し身（reflecta_copy）が2体いません：" + copies.size());
        check(helper, copies.stream().anyMatch(m -> a.getUUID().equals(m.copied())) && copies.stream().anyMatch(m -> b.getUUID().equals(m.copied())),
                "写し身が、戦う人ひとりずつを写していません");
        for (Mimic copy : copies) {
            copy.discard();
        }
        boss.testTickCopies(level);
        check(helper, boss.copies() == 0 && boss.wardBroken(), "写し身をすべて倒しても、鏡の守りが割れません");
        double broken = def.skill("mirror", 0, 2, 60, 6).param("broken", 1.3);
        check(helper, Math.abs(boss.damageTakenMultiplier(null) - broken) < EPSILON, "鏡の守りが割れている間、受けるダメージが " + broken + " 倍になりません");
        check(helper, "open".equals(boss.status()), "鏡の守りが割れても、攻めどきの知らせ（open）が出ません：" + boss.status());
        boss.resetFight();
        check(helper, Math.abs(boss.damageTakenMultiplier(null) - 1.0) < EPSILON, "戦いをやり直しても、守りの倍率が残っています");
        boss.discard();
        a.removeAllEffects();
        b.removeAllEffects();
        helper.succeed();
    }

    // ---------------------------------------------------------------- リリア

    @GameTest(template = EMPTY, batch = "iceforest_lilia")
    public static void liliaRemembersWithTadaima(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StoryState state = StoryState.get(server);
        ResourceLocation[] flags = {LiliaMemoryService.FORGOT, LiliaMemoryService.NAME, LiliaMemoryService.FACE, LiliaMemoryService.MORE,
                NpcMemoryService.QUEEN_DOWN};
        boolean[] had = new boolean[flags.length];
        for (int i = 0; i < flags.length; i++) {
            had[i] = state.has(flags[i]);
            state.remove(flags[i]);
        }
        LiliaMemoryService.Data data = LiliaMemoryService.Data.get(server);
        data.clear();
        try {
            FakePlayer[] players = {fake(helper, "lilia_a"), fake(helper, "lilia_b"), fake(helper, "lilia_c"), fake(helper, "lilia_d")};
            check(helper, !LiliaMemoryService.greet(players[0]), "リリアが忘れていないのに、「ただいま」を数えました");
            state.add(LiliaMemoryService.FORGOT);
            check(helper, LiliaMemoryService.forgets(server) && LiliaMemoryService.stage(server) == 0, "7階のあと、リリアが忘れていません");
            for (FakePlayer p : players) {
                data.see(p.getUUID().toString());
            }
            check(helper, LiliaMemoryService.greet(players[0]) && LiliaMemoryService.percent(server) == 25 && LiliaMemoryService.stage(server) == 1,
                    "4人のうち1人の「ただいま」で、名前を思い出しません（" + LiliaMemoryService.percent(server) + "%）");
            check(helper, !LiliaMemoryService.greet(players[0]) && LiliaMemoryService.percent(server) == 25, "同じ人が同じ日に2回数えられました");
            check(helper, LiliaMemoryService.greet(players[1]) && LiliaMemoryService.stage(server) == 2, "2人の「ただいま」で、顔を思い出しません");
            check(helper, LiliaMemoryService.greet(players[2]) && LiliaMemoryService.stage(server) == 3, "3人の「ただいま」で、さらに思い出しません");
            state.add(NpcMemoryService.QUEEN_DOWN);
            check(helper, !LiliaMemoryService.forgets(server) && !LiliaMemoryService.greet(players[3]), "10日目のボスのあとも、リリアが忘れています");
            int forgotDialogues = 0;
            for (String name : new String[]{"forgot", "forgot_name", "forgot_face", "forgot_more"}) {
                if (jp.amemaru.lostconnection.story.DialogueDefs.get(AmemaruMod.id("lilia/" + name)) != null) {
                    forgotDialogues++;
                }
            }
            check(helper, forgotDialogues == 4, "リリアの「忘れている間」の会話が4つありません：" + forgotDialogues);
        } finally {
            data.clear();
            for (int i = 0; i < flags.length; i++) {
                if (had[i]) {
                    state.add(flags[i]);
                } else {
                    state.remove(flags[i]);
                }
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 装備・アクセサリー・合言葉

    @GameTest(template = EMPTY)
    public static void floor7GearAndPassphraseWords(GameTestHelper helper) {
        EquipmentDef daggers = EquipmentDefs.server().get(AmemaruMod.id("mirror_ice_daggers"));
        check(helper, daggers != null && daggers.rare(), "鏡氷の短剣の定義がありません");
        check(helper, daggers.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.DOUBLE_HIT && e.chance() > 0
                && Math.abs(e.percent() - 100.0) < EPSILON), "鏡氷の短剣の効き目が「二重に当たる（100%）」ではありません");
        int floor7 = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            AccessoryDef def = AccessoryDefs.get(item);
            if (def != null && def.floor() == 7) {
                floor7++;
            }
        }
        // 11月27日：祈願（ガチャ）の★4、7階の祈願のガチャ限のアクセサリー（指輪とお守り）2つで9
        check(helper, floor7 == 9, "7階のアクセサリーが9つ（作る5つ・レアのお守り2つ・ガチャ限2つ）ではありません：" + floor7);
        for (String named : new String[]{"ice_stag_lord", "blizzard_great_spirit"}) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(named));
            check(helper, def != null && def.rare().stream().anyMatch(r -> r.item().getPath().endsWith("_charm")),
                    named + "（名のある魔物）が、レアのお守りを落としません");
        }
        List<WorkshopRecipe> recipes = helper.getLevel().getServer().getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get());
        for (String name : new String[]{"mirror_ice_battle_ring", "mirror_ice_wisdom_ring", "ice_antler_necklace", "icicle_bracelet",
                "passphrase_charm"}) {
            WorkshopRecipe recipe = recipes.stream().filter(r -> r.getId().getPath().endsWith(name)).findFirst().orElse(null);
            check(helper, recipe != null && recipe.ingredients().stream().anyMatch(c -> c.item().equals(AmemaruMod.id("pedestal_cloth"))),
                    name + " のレシピがないか、防具職人の台座布を使いません");
        }
        // 合言葉：全角・大文字・空白のちがいはそろえる。コマンドや長すぎる言葉は数えない
        check(helper, "abc def".equals(PassphraseService.normalize("  ＡＢＣ　 Def  ")), "合言葉のそろえ方がおかしい："
                + PassphraseService.normalize("  ＡＢＣ　 Def  "));
        check(helper, PassphraseService.flag(7).equals(AmemaruMod.id("floor7/passphrase")), "合言葉の印の名前が違います：" + PassphraseService.flag(7));
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static PlayerData ready(FakePlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        LevelService.setLevel(player, data, 30);
        JobService.setJobForced(player, data, SkillRegistry.get().job(AmemaruMod.id("knight")));
        LevelService.recompute(player, data);
        return data;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "ami_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-f7:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
