package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.boss.Cantare;
import jp.amemaru.lostconnection.enemy.temple.CrystalBeetle;
import jp.amemaru.lostconnection.enemy.temple.PrayerEcho;
import jp.amemaru.lostconnection.enemy.temple.SilentStatue;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDef;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.TowerService;
import jp.amemaru.lostconnection.tower.temple.TempleService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 11月24日（4階「崩壊した神殿」）の自動テスト。
 *
 * <ul>
 *   <li>4階の形：3階より広い。壁画3つと「一枚の絵」（12枚）・音の順番（鈴の石と4つの床）・結晶騎士団の遺品・中継の灯5つ・
 *       名のある魔物3体・白い壁画と野営の跡（物語の場所）・神殿の碑文（古代記録と同じ名前）・ボスの部屋と4つの音の床</li>
 *   <li>壁画：3つとも戻し、名のある魔物を3体とも倒すと封印が開く。それまでの知らせは「壁画」。日が変わると崩れる。保存して読み直しても同じ</li>
 *   <li>敵の守り：眠っている石像は硬い。結晶甲虫は魔法に弱い。祈り手の歌は仲間を治す。ボスは歌に守られている</li>
 *   <li>遺品：冑と認識票をもらい、ガルドに見せるクエストが始まる。2度はもらえない</li>
 *   <li>祈り手の鈴（4階のレア）：ヒーラーの鈴杖・回復に小さな防御強化</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempleGameTests {
    private static final String EMPTY = "empty3x3x3";

    private TempleGameTests() {
    }

    // ---------------------------------------------------------------- 4階の形

    @GameTest(template = EMPTY)
    public static void floor4Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(4);
        check(helper, def != null && def.bossRoom() != null, "4階の定義がありません");
        FloorDef three = FloorDefs.byNumber(3);
        check(helper, three != null && def.sizeX() > three.sizeX() && def.sizeZ() > three.sizeZ(),
                "4階が3階より広くありません（" + def.sizeX() + "×" + def.sizeZ() + "）");
        for (int n = 1; n <= 3; n++) {
            FloorDef other = FloorDefs.byNumber(n);
            check(helper, other != null && !def.contains(other.originVec()) && !other.contains(def.originVec()), n + "階と4階が重なっています");
        }
        FloorDef.BossRoomDef boss = def.bossRoom();
        check(helper, AmemaruMod.id("cantare").equals(boss.boss()), "4階のボスがカンターレではありません：" + boss.boss());
        check(helper, boss.scaling().hpMinPlayers() >= 2 && boss.scaling().enrageSeconds() > 0, "4階のボスが、ひとりでも2人ぶんのHPになっていません");
        check(helper, boss.next() != null, "4階のボスの部屋に、次の階への円がありません");
        FloorFeatures fx = def.features();
        check(helper, fx.requires(FloorFeatures.SEAL_MURALS) && fx.requires(FloorFeatures.SEAL_GUARDIANS),
                "4階の封印の条件が、壁画と名のある魔物になっていません：" + fx.sealRequires());
        check(helper, fx.murals().size() == 4, "壁画が3つと一枚の絵になっていません：" + fx.murals().size());
        int pieces = 0;
        for (FloorFeatures.Mural mural : fx.murals()) {
            if (mural.display()) {
                check(helper, mural.panels().size() == 12, "一枚の絵が12枚ではありません：" + mural.panels().size());
                continue;
            }
            pieces += mural.panels().size();
            check(helper, mural.text() != null && !mural.text().isEmpty(), mural.id() + " の壁画に、絵の言葉がありません");
            for (FloorFeatures.Panel panel : mural.panels()) {
                check(helper, def.contains(Vec3.atBottomCenterOf(def.world(panel.pos()))), mural.id() + " のパネルが階の外です");
            }
        }
        check(helper, pieces == 12, "3つの壁画のパネルが合わせて12枚ではありません：" + pieces);
        check(helper, fx.mural("first") != null && fx.mural("first").door() != null, "第一時代の壁画で、祈りの間への扉が開きません");
        check(helper, fx.songs().size() == 1 && fx.songs().get(0).plates().size() == 4 && fx.songs().get(0).length() == 3,
                "祈りの間の音の順番（鈴の石と4つの床・3音）がありません");
        FloorFeatures.Relic relic = fx.relic("knights");
        check(helper, relic != null && relic.items().size() == 2 && AmemaruMod.id("knight_relics").equals(relic.quest()),
                "結晶騎士団の遺品（冑と認識票・ガルドに見せるクエスト）がありません");
        check(helper, fx.bossMarks("sound_plates").size() == 4, "ボスの部屋の音の床が4つではありません");
        Vec3 center = boss.arenaCenter();
        for (BlockPos plate : fx.bossMarks("sound_plates")) {
            // 部屋のまん中は階の中の位置（音の床も）
            double dx = plate.getX() + 0.5 - center.x;
            double dz = plate.getZ() + 0.5 - center.z;
            check(helper, Math.sqrt(dx * dx + dz * dz) < boss.arenaRadius(), "ボスの音の床が部屋の外です");
        }
        check(helper, fx.waypoints().size() == 5 && fx.travelStone() != null, "中継の灯が5つ・道しるべの石がありません");
        check(helper, fx.environment() != null, "4階の霧がありません");
        check(helper, fx.storyZones().stream().anyMatch(z -> z.id().equals("white_murals"))
                && fx.storyZones().stream().anyMatch(z -> z.id().equals("knight_camp")), "白い壁画・野営の跡の物語の場所がありません");
        check(helper, TowerService.namedGuardians(def).size() == 3, "名のある魔物が3体ではありません：" + TowerService.namedGuardians(def).size());
        check(helper, def.tablet() != null && "神殿の碑文".equals(def.tablet().title()), "神殿の碑文がありません");
        boolean record = false;
        for (RecordDef r : RecordDefs.sorted()) {
            record |= r.floor() == 4 && r.title().equals(def.tablet().title());
        }
        check(helper, record, "神殿の碑文と同じ名前の古代記録がありません");
        check(helper, def.extras().chests().size() >= 20, "4階の宝箱が少なすぎます：" + def.extras().chests().size());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 壁画と封印

    @GameTest(template = EMPTY)
    public static void muralsOpenTheSeal(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FloorDef def = FloorDefs.byNumber(4);
        check(helper, def != null, "4階の定義がありません");
        TempleService.onDayChanged(server);
        FakePlayer a = fake(helper, "mural_a");
        PlayerData data = PlayerDataCapability.get(a).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().add(TowerService.guardianFlag(4, guardian.id()));
        }
        check(helper, !TempleService.allSolved(server, def), "日が変わったのに、壁画が戻ったままです");
        net.minecraft.network.chat.Component locked = TowerService.sealLockedMessage(a, def);
        check(helper, !TowerService.pillarsComplete(a, 4) && locked != null
                && locked.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("message.amemaru.seal.murals"), "壁画を戻していないのに封印が開くか、知らせが違います");
        int solved = 0;
        for (FloorFeatures.Mural mural : def.features().murals()) {
            if (mural.display()) {
                continue;
            }
            TempleService.solve(server, def, mural, null);
            solved++;
            int[] count = TempleService.count(server, def);
            check(helper, count[0] == solved && count[1] == 3, "戻った壁画の数が違います：" + count[0] + "/" + count[1]);
        }
        check(helper, TempleService.allSolved(server, def) && TowerService.pillarsComplete(a, 4),
                "壁画を3つとも戻し、名のある魔物を3体とも倒したのに、封印が開きません");
        TempleService.Data copy = TempleService.Data.load(TempleService.Data.get(server).save(new CompoundTag()));
        check(helper, copy.solved(4, "first") && copy.solved(4, "second"), "保存して読み直すと、戻した壁画が崩れます");
        TempleService.onDayChanged(server);
        check(helper, !TempleService.solved(server, 4, "first") && !TowerService.pillarsComplete(a, 4), "日が変わっても、壁画が崩れません");
        for (FloorDef.Guardian guardian : TowerService.namedGuardians(def)) {
            data.getStory().remove(TowerService.guardianFlag(4, guardian.id()));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 敵の守り

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void templeEnemyDefenses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        // 眠っている石像は硬い（目覚めると少しだけ）
        SilentStatue statue = ModEntities.SILENT_STATUE.get().create(level);
        check(helper, statue != null, "沈黙の石像を作れません");
        check(helper, statue.dormant() && Math.abs(statue.damageTakenMultiplier(null) - 0.3) < 1.0E-6
                        && Boolean.TRUE.equals(statue.boneToggles().get("dust")) && Boolean.FALSE.equals(statue.boneToggles().get("glow")),
                "眠っている石像の守りが 0.3 ではないか、ほこりが見えていません");
        // 結晶甲虫：魔法には弱く、それ以外には硬い
        CrystalBeetle beetle = ModEntities.CRYSTAL_BEETLE.get().create(level);
        check(helper, beetle != null, "結晶甲虫を作れません");
        check(helper, Math.abs(beetle.damageTakenMultiplier(null, true) - 1.6) < 1.0E-6
                && Math.abs(beetle.damageTakenMultiplier(null, false) - 0.35) < 1.0E-6, "結晶甲虫の守りが、魔法 1.6・それ以外 0.35 ではありません");
        // ボス：歌に守られている間は 1/3。光の輪が見える
        Cantare boss = ModEntities.CANTARE.get().create(level);
        check(helper, boss != null, "カンターレを作れません");
        check(helper, !boss.open() && Math.abs(boss.damageTakenMultiplier(null) - 0.34) < 1.0E-6
                && Boolean.TRUE.equals(boss.boneToggles().get("halo")), "歌に守られたカンターレの守りが 0.34 ではないか、光の輪がありません");
        check(helper, !boss.forceSkill("song"), "音の床のない所で、選律の歌が始まりました");
        // 祈りの歌：傷ついた仲間（甲虫）を治す
        beetle.moveTo(at.x + 1.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(beetle);
        PrayerEcho echo = ModEntities.PRAYER_ECHO.get().create(level);
        check(helper, echo != null, "祈り手の残響を作れません");
        echo.moveTo(at.x - 1.0, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(echo);
        helper.runAfterDelay(2, () -> {
            double max = beetle.getEnemyMaxHp();
            beetle.setEnemyHp(max * 0.5);
            int healed = echo.testHymn();
            check(helper, healed >= 1 && beetle.getEnemyHp() > max * 0.5, "祈りの歌で、傷ついた甲虫が治りません");
            beetle.discard();
            echo.discard();
            statue.discard();
            boss.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 遺品

    @GameTest(template = EMPTY)
    public static void relicGivesTheKnightsKeepsakes(GameTestHelper helper) {
        FakePlayer player = fake(helper, "relic");
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        ResourceLocation flag = AmemaruMod.id("story/knight_relics_found");
        data.getStory().remove(flag);
        data.getGuild().dropQuest(AmemaruMod.id("knight_relics"));
        Item helm = ForgeRegistries.ITEMS.getValue(AmemaruMod.id("knight_helm_relic"));
        Item tag = ForgeRegistries.ITEMS.getValue(AmemaruMod.id("harold_tag"));
        check(helper, helm != null && tag != null, "冑か認識票の品がありません");
        player.getInventory().clearContent();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        TempleService.takeRelic(player, 4, "knights", pos);
        check(helper, player.getInventory().countItem(helm) == 1 && player.getInventory().countItem(tag) == 1,
                "遺品から冑と認識票をもらえません");
        check(helper, data.getStory().has(flag), "遺品を見つけたしるし（物語のフラグ）が立ちません");
        check(helper, data.getGuild().quests().containsKey(AmemaruMod.id("knight_relics")), "ガルドに見せるクエストが始まりません");
        TempleService.takeRelic(player, 4, "knights", pos);
        check(helper, player.getInventory().countItem(helm) == 1, "遺品を2度もらえました");
        data.getGuild().dropQuest(AmemaruMod.id("knight_relics"));
        data.getStory().remove(flag);
        player.getInventory().clearContent();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 祈り手の鈴

    @GameTest(template = EMPTY)
    public static void prayerBellIsTheFourthFloorRare(GameTestHelper helper) {
        EquipmentDef bell = EquipmentDefs.server().get(AmemaruMod.id("prayer_bell_staff"));
        check(helper, bell != null && bell.rare(), "祈り手の鈴（4階のレア）の定義がありません");
        check(helper, bell.jobs().contains(AmemaruMod.id("healer")), "祈り手の鈴がヒーラーの武器ではありません");
        check(helper, bell.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.HEAL_WARD && e.percent() > 0),
                "祈り手の鈴に「回復に小さな防御強化」がありません");
        check(helper, ForgeRegistries.ITEMS.getValue(AmemaruMod.id("prayer_bell_staff")) != null, "祈り手の鈴の品がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}
