package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.enemy.boss.Valgrim;
import jp.amemaru.lostconnection.enemy.castle.ForgottenMaid;
import jp.amemaru.lostconnection.enemy.castle.FrostHound;
import jp.amemaru.lostconnection.enemy.castle.FrozenGuard;
import jp.amemaru.lostconnection.enemy.castle.IceCavalry;
import jp.amemaru.lostconnection.enemy.castle.ShelfWraith;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.story.DialogueDefs;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.castle.LineageService;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 11月28日（9階「氷結した王城」）の自動テスト。
 *
 * <ul>
 *   <li>9階の形：王家の系譜（肖像画9・名前の札11（まぎらわしい札2つ）・組3つと扉・手がかり2つ・女王の肖像と隠し扉・書簡3通）・
 *       名のある魔物2体・封印は「系譜＋名のある魔物」・近衛の間の場所（近衛の円・部下の場所4つ・増援の場所6つ）</li>
 *   <li>系譜：札が違うとはじかれる。組をそろえると扉が開く。女王の名の見分け。日が変わると戻る</li>
 *   <li>凍れる近衛兵：像の間は硬い。前からは盾で硬く、隊列でもっと硬い。後ろからは効く</li>
 *   <li>氷の騎兵：壁にぶつかるとよろめき、受けるダメージが増える。霜蹄の旗手は続けて突撃する</li>
 *   <li>書架霊：頁は守りを通り抜け、「薄れ」にする。霜の猟犬：噛んだ人に匂い。忘れられた侍女：城の敵を治す</li>
 *   <li>ヴァルグリム：盾の誓い（つながった部下は傷つかない・近衛長も硬い）・離すと鎖が切れて崩れる（攻めどき）・第3段階で誓いを解く</li>
 *   <li>ボスのレア「近衛の誓盾」（肩代わり）・9階のアクセサリー・ガルドの会話・物語</li>
 * </ul>
 * <p>札の台と肖像画を右クリックして解く道は、9階を本当に建てて {@link FloorRealPlayGameTests} で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CastleGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private CastleGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void floor9Layout(GameTestHelper helper) {
        FloorDef def = FloorDefs.byNumber(9);
        check(helper, def != null && def.bossRoom() != null, "9階の定義がありません");
        check(helper, def.chapter() == 2, "9階が第2章になっていません：" + def.chapter());
        check(helper, def.bossRoom().boss().equals(AmemaruMod.id("valgrim")), "9階のボスがヴァルグリムではありません");
        FloorFeatures fx = def.features();
        check(helper, fx.requires(FloorFeatures.SEAL_LINEAGE) && fx.requires(FloorFeatures.SEAL_GUARDIANS),
                "9階の封印が「系譜＋名のある魔物」ではありません");
        FloorFeatures.Lineage lineage = fx.lineage();
        check(helper, lineage != null, "9階に王家の系譜がありません");
        check(helper, lineage.portraits().size() == 9 && lineage.plates().size() == 11 && lineage.groups().size() == 3
                && lineage.clues().size() == 2, "王家の系譜の数が違います（肖像画9・札11・組3・手がかり2）");
        int decoys = 0;
        for (FloorFeatures.NamePlate plate : lineage.plates()) {
            Item item = LineageService.plateItem(plate.id());
            check(helper, item != null, "名前の札の持ち物がありません：" + plate.id());
            if (lineage.portrait(plate.id()) == null) {
                decoys++;
            }
        }
        check(helper, decoys == 2, "まぎらわしい札が2つではありません：" + decoys);
        for (FloorFeatures.Portrait portrait : lineage.portraits()) {
            check(helper, lineage.plate(portrait.id()) != null, "肖像画 " + portrait.id() + " の札がありません");
        }
        check(helper, lineage.groups().stream().filter(g -> g.door() != null).count() == 2, "組の扉（大階段の氷の門・回廊の扉）が2つではありません");
        for (FloorFeatures.LineageGroup group : lineage.groups()) {
            check(helper, !group.guards().isEmpty(), group.name() + "：札を間違えたときの近衛兵の場所がありません");
        }
        check(helper, lineage.queen() != null && lineage.queen().names().contains("イゼルナ") && lineage.queen().openSeconds() > 5,
                "女王の肖像（名前・隠し扉）の決まりがありません");
        check(helper, fx.relics().size() == 3 && fx.relics().stream().allMatch(r -> r.record() != null
                        && r.record().equals(AmemaruMod.id("f09_queen_letters"))),
                "女王の書簡3通（古代記録 f09_queen_letters）がありません");
        check(helper, RecordDefs.get(AmemaruMod.id("f09_queen_letters")) != null, "古代記録「女王の書簡（三通）」がありません");
        check(helper, def.extras().guardians().size() == 2, "9階の名のある魔物が2体ではありません：" + def.extras().guardians().size());
        check(helper, fx.bossMarks("guard_spots").size() == 4 && fx.bossMarks("reinforce").size() == 6,
                "近衛の間の、部下の場所4つ・増援の場所6つがありません");
        // 系譜の見分け
        check(helper, LineageService.isQueenName("イゼルナさま", lineage.queen().names())
                && LineageService.isQueenName("い ぜ る な", lineage.queen().names())
                && LineageService.isQueenName("Izeruna", lineage.queen().names())
                && !LineageService.isQueenName("エルヴィーラ", lineage.queen().names()), "女王の名の見分けがおかしい");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 雑魚

    /** 凍れる近衛兵：像の間は硬い。前からは盾（隊列ならもっと硬い）、後ろからはふつう。目を覚ますと、像ではなくなる。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void frozenGuardShieldAndFormation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "guard");
        EnemyDef def = EnemyDefs.forType(ModEntities.FROZEN_GUARD.get());
        check(helper, def != null && def.floor() == 9, "凍れる近衛兵の定義がありません");
        FrozenGuard guard = spawn(helper, ModEntities.FROZEN_GUARD.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        helper.runAfterDelay(2, () -> {
            check(helper, guard.dormant(), "凍れる近衛兵が、はじめは像になっていません");
            check(helper, Math.abs(guard.damageTakenMultiplier(null) - def.behavior("dormant_taken", 0.3)) < EPSILON, "像の近衛兵が硬くありません");
            guard.wakeNow();
            check(helper, !guard.dormant(), "目を覚ましません");
            // 前（南。向きは0度＝南）から
            player.moveTo(guard.getX(), guard.getY(), guard.getZ() + 3.0, 180.0F, 0.0F);
            check(helper, Math.abs(guard.damageTakenMultiplier(player) - def.behavior("shield_taken", 0.4)) < EPSILON,
                    "前からの攻撃を、塔の盾が受けません：" + guard.damageTakenMultiplier(player));
            // 後ろ（北）から
            player.moveTo(guard.getX(), guard.getY(), guard.getZ() - 3.0, 0.0F, 0.0F);
            check(helper, Math.abs(guard.damageTakenMultiplier(player) - 1.0) < EPSILON, "後ろからの攻撃が、ふつうに効きません");
            // 隊列（となりに、目を覚ました近衛兵）
            FrozenGuard mate = spawn(helper, ModEntities.FROZEN_GUARD.get().create(level), new Vec3(3.5, 1.0, 1.5), 0.0F);
            mate.wakeNow();
            check(helper, guard.inFormation(), "となりの近衛兵と、隊列になりません");
            player.moveTo(guard.getX(), guard.getY(), guard.getZ() + 3.0, 180.0F, 0.0F);
            check(helper, Math.abs(guard.damageTakenMultiplier(player) - def.behavior("formation_taken", 0.2)) < EPSILON,
                    "隊列の近衛兵の盾が、もっと硬くなりません：" + guard.damageTakenMultiplier(player));
            guard.discard();
            mate.discard();
            helper.succeed();
        });
    }

    /** 氷の騎兵：壁にぶつかるとよろめき、受けるダメージが増える。霜蹄の旗手は、続けて突撃する。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void iceCavalryStaggerAndChain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IceCavalry cavalry = spawn(helper, ModEntities.ICE_CAVALRY.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        IceCavalry bearer = spawn(helper, ModEntities.ICE_CAVALRY.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        bearer.setVariant(AmemaruMod.id("ice_cavalry_bearer"));
        EnemyDef def = EnemyDefs.forType(ModEntities.ICE_CAVALRY.get());
        EnemyDef bearerDef = EnemyDefs.get(AmemaruMod.id("ice_cavalry_bearer"));
        check(helper, def != null && bearerDef != null && bearerDef.behavior("chain", 1.0) >= 2.0, "氷の騎兵・霜蹄の旗手の定義がありません");
        helper.runAfterDelay(2, () -> {
            check(helper, cavalry.onChargeEnded(false) == 0 && !cavalry.staggered(), "壁にぶつからない突撃のあとで、よろめきました");
            int stagger = cavalry.onChargeEnded(true);
            check(helper, stagger >= 20 && cavalry.staggered(), "壁にぶつかっても、よろめきません");
            check(helper, Math.abs(cavalry.damageTakenMultiplier(null) - def.behavior("stagger_taken", 1.5)) < EPSILON,
                    "よろめいている間、受けるダメージが増えません");
            int normal = 140;
            check(helper, bearer.chargeCooldown(normal) < normal && bearer.chargeCooldown(normal) == normal,
                    "霜蹄の旗手が、続けて（2回）突撃しません");
            check(helper, cavalry.chargeCooldown(normal) == normal, "ふつうの氷の騎兵が、続けて突撃します");
            cavalry.discard();
            bearer.discard();
            helper.succeed();
        });
    }

    /** 書架霊の頁は、守りを通り抜ける魔法で、当たると「薄れ」。頁の嵐は、印の中の人に当たる。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void shelfWraithPagesPierceAndFade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "wraith");
        ShelfWraith wraith = spawn(helper, ModEntities.SHELF_WRAITH.get().create(level), new Vec3(1.5, 2.0, 1.5), 0.0F);
        EnemyDef def = EnemyDefs.forType(ModEntities.SHELF_WRAITH.get());
        check(helper, def != null && def.behavior("pierce", 0.0) > 0.0, "書架霊の頁に、守りを通り抜ける割合がありません");
        helper.runAfterDelay(2, () -> {
            check(helper, wraith.projectileStyle() == jp.amemaru.lostconnection.entity.CrystalNeedle.STYLE_PAGE, "書架霊の弾が頁ではありません");
            check(helper, Math.abs(wraith.projectilePierce() - def.behavior("pierce", 0.0)) < EPSILON, "頁の、守りを通り抜ける割合が違います");
            wraith.onStruck(player, 1.0);
            check(helper, player.hasEffect(ModEffects.LOST_FADE.get()), "頁に当たっても「薄れ」になりません");
            check(helper, EnemyCombat.pendingPierce(player) == 0.0, "攻撃のあとに、守りを通り抜ける値が残っています");
            Vec3 at = player.position();
            int hits = wraith.landStorm(level, at);
            check(helper, hits >= 0, "頁の嵐が落ちません");
            wraith.discard();
            helper.succeed();
        });
    }

    /** 霜の猟犬：噛んだ人に霜の匂い。まわりの猟犬はその人を狙い、強く噛む。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void frostHoundScentDrawsThePack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "hound");
        FrostHound.resetForTest();
        FrostHound a = spawn(helper, ModEntities.FROST_HOUND.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        FrostHound b = spawn(helper, ModEntities.FROST_HOUND.get().create(level), new Vec3(3.5, 1.0, 1.5), 0.0F);
        helper.runAfterDelay(2, () -> {
            check(helper, !FrostHound.scented(player), "噛まれる前から、霜の匂いがついています");
            a.onStruck(player, 1.0);
            check(helper, FrostHound.scented(player), "噛まれても、霜の匂いがつきません");
            check(helper, b.getTarget() == player, "まわりの猟犬が、匂いのついた人を狙いません");
            check(helper, b.scentMultiplier(player) > 1.0, "匂いのついた人を、強く噛みません");
            FrostHound.clearScent(player.getUUID());
            check(helper, !FrostHound.scented(player), "霜の匂いが消えません");
            a.discard();
            b.discard();
            helper.succeed();
        });
    }

    /** 忘れられた侍女：傷ついた城の敵を治す（ほかの階の敵は治さない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void forgottenMaidMendsCastleAllies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ForgottenMaid maid = spawn(helper, ModEntities.FORGOTTEN_MAID.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        FrozenGuard guard = spawn(helper, ModEntities.FROZEN_GUARD.get().create(level), new Vec3(3.5, 1.0, 1.5), 0.0F);
        helper.runAfterDelay(3, () -> {
            check(helper, ForgottenMaid.castleAlly(guard), "凍れる近衛兵が、侍女の治す相手になっていません");
            guard.setEnemyHp(guard.getEnemyMaxHp() * 0.5);
            double before = guard.getEnemyHp();
            check(helper, maid.mendOnce(level, guard) && guard.getEnemyHp() > before, "侍女が、傷ついた近衛兵を治しません");
            guard.setEnemyHp(guard.getEnemyMaxHp());
            check(helper, !maid.mendOnce(level, guard), "HPの減っていない相手を治しました");
            maid.discard();
            guard.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- ヴァルグリム

    /**
     * 盾の誓い：近衛長のそばの部下は鎖でつながって傷つかず、近衛長も硬い。部下を離すと鎖が切れて、その部下は傷つく。つながりがすべて
     * 消えると、近衛長は崩れる（受けるダメージが増える）。第3段階では誓いを解く（部下は砕ける）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void valgrimOathAndCrumble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Valgrim boss = spawn(helper, ModEntities.VALGRIM.get().create(level), new Vec3(1.5, 1.0, 1.5), 0.0F);
        EnemyDef def = EnemyDefs.forType(ModEntities.VALGRIM.get());
        check(helper, def != null && def.floor() == 9 && def.pity(), "ヴァルグリムの定義がないか、天井がありません");
        check(helper, def.rare().stream().anyMatch(r -> r.item().equals(AmemaruMod.id("royal_oath_shield")) && Math.abs(r.chance() - 4.0) < EPSILON),
                "ヴァルグリムのレアに「近衛の誓盾」4% がありません");
        check(helper, boss.skillNames().containsAll(List.of("thrust", "bash", "spears", "guard", "rally", "march")),
                "ヴァルグリムの技がそろっていません：" + boss.skillNames());
        EnemyDef.Skill oath = def.skill("oath", 0, 0, 0, 12);
        EnemyDef.Skill crumble = def.skill("crumble", 0, 0, 0, 0);
        EnemyDef.Skill march = def.skill("march", 3.4, 3.6, 10, 46);
        EnemyDef.Skill nova = def.skill("nova", 2.2, 3.4, 46, 15);
        check(helper, march.power() > nova.power(), "近衛の行進（しくじったときの一撃）が、大技より弱いです");
        FrozenGuard near = spawn(helper, ModEntities.FROZEN_GUARD.get().create(level), new Vec3(3.5, 1.0, 1.5), 0.0F);
        near.setVariant(Valgrim.GUARD_VARIANT);
        near.wakeNow();
        helper.runAfterDelay(2, () -> {
            boss.testAddGuard(near);
            boss.testTickOath(level);
            check(helper, boss.linkedCount() == 1 && near.oathLinked(), "そばの部下が、盾の誓いでつながりません");
            check(helper, Math.abs(near.damageTakenMultiplier(null)) < EPSILON, "つながった部下が傷つきます");
            check(helper, Math.abs(boss.damageTakenMultiplier(null) - oath.param("ward", 0.35)) < EPSILON,
                    "部下とつながっている間、近衛長が硬くなりません：" + boss.damageTakenMultiplier(null));
            // 部下を引き離す（鎖が切れる）
            near.moveTo(boss.getX() + oath.range() + 4.0, boss.getY(), boss.getZ(), 0.0F, 0.0F);
            boss.testTickOath(level);
            check(helper, !near.oathLinked() && boss.linkedCount() == 0, "部下を離しても、鎖が切れません");
            check(helper, near.damageTakenMultiplier(null) > 0.0, "鎖の切れた部下が、傷つきません");
            check(helper, boss.crumbled(), "つながりがすべて消えても、近衛長が崩れません");
            check(helper, Math.abs(boss.damageTakenMultiplier(null) - crumble.param("taken", 1.5)) < EPSILON,
                    "崩れている間、受けるダメージが増えません：" + boss.damageTakenMultiplier(null));
            check(helper, "open".equals(boss.status()), "攻めどきの知らせ（open）が出ません：" + boss.status());
            near.discard();
            boss.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- 装備・アクセサリー・ガルド・物語

    @GameTest(template = EMPTY)
    public static void floor9GearAndStory(GameTestHelper helper) {
        EquipmentDef shield = EquipmentDefs.server().get(AmemaruMod.id("royal_oath_shield"));
        check(helper, shield != null && shield.rare(), "近衛の誓盾の定義がありません");
        check(helper, shield.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.OATH_GUARD && e.threshold() > 0 && e.percent() > 0),
                "近衛の誓盾の効き目が「肩代わり」ではありません");
        check(helper, shield.jobs().contains(AmemaruMod.id("knight")), "近衛の誓盾を、ナイトが使えません");
        int floor9 = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            AccessoryDef def = AccessoryDefs.get(item);
            if (def != null && def.floor() == 9) {
                floor9++;
            }
        }
        check(helper, floor9 == 7, "9階のアクセサリーが7つ（作る5つ・レアのお守り2つ）ではありません：" + floor9);
        AccessoryDef tassel = AccessoryDefs.get(ModItems.MANIFEST.get("bearer_tassel").get());
        check(helper, tassel != null && tassel.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.MOVE_SPEED),
                "旗手の房飾りの効き目が「移動の速さ」ではありません");
        AccessoryDef bookmark = AccessoryDefs.get(ModItems.MANIFEST.get("binder_bookmark").get());
        check(helper, bookmark != null && bookmark.effects().stream().anyMatch(e -> e.type() == SpecialEffect.Type.LOST_RESIST),
                "綴じ糸の栞の効き目が「ロスト耐性」ではありません");
        String[][] named = {{"ice_cavalry_bearer", "bearer_tassel"}, {"shelf_wraith_binder", "binder_bookmark"}};
        for (String[] pair : named) {
            EnemyDef def = EnemyDefs.get(AmemaruMod.id(pair[0]));
            check(helper, def != null && def.rare().stream().anyMatch(r -> r.item().getPath().equals(pair[1])),
                    pair[0] + "（名のある魔物）が、レアのお守り " + pair[1] + " を落としません");
        }
        List<WorkshopRecipe> recipes = helper.getLevel().getServer().getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get());
        for (String name : new String[]{"guard_ice_battle_ring", "guard_ice_wisdom_ring", "portrait_necklace", "horseshoe_bracelet", "name_charm"}) {
            WorkshopRecipe recipe = recipes.stream().filter(r -> r.getId().getPath().endsWith(name)).findFirst().orElse(null);
            check(helper, recipe != null, name + " のレシピがありません");
        }
        for (String id : new String[]{"gard/forgot", "gard/forgot_again", "gard/captain"}) {
            check(helper, DialogueDefs.get(AmemaruMod.id(id)) != null, "ガルドの会話「" + id + "」がありません");
        }
        for (String id : new String[]{"enter", "hall", "library", "gallery", "study", "letter_1", "letter_2", "letter_3", "queen_name",
                "lineage_done", "first_kill"}) {
            check(helper, StoryEventManager.get(AmemaruMod.id("floor9/" + id)) != null, "9階の物語「" + id + "」がありません");
        }
        for (int k = 1; k <= 3; k++) {
            check(helper, ForgeRegistries.ITEMS.containsKey(AmemaruMod.id("queen_letter_" + k)), "女王の書簡（" + k + "通目）の持ち物がありません");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static <T extends net.minecraft.world.entity.Entity> T spawn(GameTestHelper helper, T entity, Vec3 local, float yaw) {
        check(helper, entity != null, "敵を作れません");
        Vec3 at = helper.absoluteVec(local);
        entity.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amc_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-f9:" + name).getBytes(StandardCharsets.UTF_8));
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
