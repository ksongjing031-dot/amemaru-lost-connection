package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.BasicShotService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.AttackLockService;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillDescriber;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * 遠距離職の攻撃のあとの硬直（11月26日のご指示「遠距離職は攻撃の際にスキルなどは硬直やほぼほぼ動けないスロウ効果などを
 * つけましょう。動きながら打てるのが強すぎる」）の自動テスト。
 *
 * <ul>
 *   <li>ウィザードの攻撃の魔法を使うと、0.4秒（灯火弾）ほぼ動けない（足の速さが1割）。そのあとは戻る。説明文にも出る</li>
 *   <li>詠唱の間もほぼ止まり、放ったあとは魔法の重さの分だけ止まる</li>
 *   <li>アーチャーの通常の射撃は、0.9秒だけ足が60%遅い（印は出さない）</li>
 *   <li>移動の技・強化・回復の鈴・近接職のスキルには、硬直がつかない。アーチャーは放つ瞬間だけ（0.25〜0.6秒）</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackLockGameTests {
    private static final String EMPTY = "empty3x3x3";

    private AttackLockGameTests() {
    }

    /**
     * ウィザードが灯火弾を放つと、0.4秒ほぼ動けない（足が1割・印が出る）。硬直が消えると戻る。説明文にも「硬直」がある。
     * （テストの仮のプレイヤーは時間で状態が減らないので、長さを確かめてから外す）
     */
    @GameTest(template = EMPTY)
    public static void wizardSpellLocksLegs(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lock_wizard");
        PlayerData data = setUp(player, "wizard", "amemaru:apprentice_staff");
        SkillDef bolt = skill("wizard/lamp_bolt");
        int slot = learn(helper, player, data, bolt);
        player.removeEffect(ModEffects.ATTACK_LOCK.get());
        double free = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, result.ok(), "灯火弾を使えません：" + result.failure());
        MobEffectInstance lock = player.getEffect(ModEffects.ATTACK_LOCK.get());
        check(helper, lock != null, "灯火弾のあとに、硬直がつきません");
        check(helper, lock.getAmplifier() == 2 && lock.showIcon(), "硬直がほぼ止まる段階（3）ではないか、印が出ません：" + lock);
        check(helper, lock.getDuration() >= 7 && lock.getDuration() <= 8, "灯火弾の硬直が0.4秒ではありません：" + lock.getDuration() + "tick");
        double locked = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        check(helper, locked <= free * 0.11 + 1.0E-6, "硬直の間の足の速さが1割になりません：" + free + " → " + locked);
        List<String> lines = SkillDescriber.describe(bolt, SkillRegistry.get().job(AmemaruMod.id("wizard")), 1);
        check(helper, lines.stream().anyMatch(line -> line.contains("硬直") && line.contains("0.4秒")),
                "灯火弾の説明文に、硬直がありません：" + lines);
        player.removeEffect(ModEffects.ATTACK_LOCK.get());
        double after = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        check(helper, Math.abs(after - free) < 1.0E-6, "硬直が消えても、足の速さが戻りません：" + free + " → " + after);
        helper.succeed();
    }

    /** 詠唱の間もほぼ止まり、放ったあとは彗星（クールダウン12秒）の重さの分（0.9秒）止まる。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void wizardChantLocksLegs(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lock_chant");
        PlayerData data = setUp(player, "wizard", "amemaru:apprentice_staff");
        SkillDef comet = skill("wizard/comet");
        int slot = learn(helper, player, data, comet);
        player.removeEffect(ModEffects.ATTACK_LOCK.get());
        SkillService.Result result = SkillService.tryUse(player, data, slot, -1, null);
        check(helper, result.ok() && data.getCasting() != null, "彗星の詠唱が始まりません：" + result.failure());
        MobEffectInstance during = player.getEffect(ModEffects.ATTACK_LOCK.get());
        check(helper, during != null && during.getAmplifier() == 2, "詠唱の間に、ほぼ止まる硬直がつきません：" + during);
        int castTicks = SkillService.castTicks(data, comet, 1, helper.getLevel().getGameTime());
        int afterTicks = AttackLockService.skillTicks(AttackLockService.rule(comet), comet, 1);
        check(helper, afterTicks == 18, "彗星のあとの硬直が0.9秒ではありません：" + afterTicks + "tick");
        helper.runAfterDelay(castTicks + 2, () -> {
            // テストの仮のプレイヤーは毎 tick の処理が動かないので、ここで1回動かして詠唱を終わらせる
            SkillService.tick(player, data);
            check(helper, data.getCasting() == null, "詠唱が終わりません");
            MobEffectInstance after = player.getEffect(ModEffects.ATTACK_LOCK.get());
            check(helper, after != null && after.getAmplifier() == 2 && after.getDuration() > 10,
                    "放ったあとに、彗星の重さの硬直がつきません：" + after);
            helper.succeed();
        });
    }

    /** アーチャーの通常の射撃は、射るたびに0.9秒だけ足が60%遅い（何度も出たり消えたりするので、印は出さない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void archerBasicShotSlows(GameTestHelper helper) {
        FakePlayer player = fake(helper, "lock_archer");
        setUp(player, "archer", "amemaru:apprentice_bow");
        player.removeEffect(ModEffects.ATTACK_LOCK.get());
        double free = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        BasicShotService.tryShoot(player);
        MobEffectInstance lock = player.getEffect(ModEffects.ATTACK_LOCK.get());
        check(helper, lock != null, "通常の射撃で、足が遅くなりません");
        check(helper, lock.getAmplifier() == 1 && !lock.showIcon(), "通常の射撃の遅さが60%（印なし）ではありません：" + lock);
        check(helper, lock.getDuration() >= 17 && lock.getDuration() <= 18, "通常の射撃の遅さが0.9秒ではありません：" + lock.getDuration());
        double slowed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        check(helper, Math.abs(slowed - free * 0.4) < free * 0.01, "射っている間の足の速さが4割になりません：" + free + " → " + slowed);
        helper.succeed();
    }

    /**
     * 硬直がつくスキルと、つかないスキル：移動の技（瞬転・後跳び）・強化（魔力集中）・回復の鈴・近接職のスキルにはつかない。
     * アーチャーは放つ瞬間だけ（速射 0.3秒・天穿ち 0.6秒まで）、ウィザードは重い魔法ほど長い（星落とし 1.2秒まで）。
     */
    @GameTest(template = EMPTY)
    public static void lockOnlyOnRangedAttacks(GameTestHelper helper) {
        expectNone(helper, "wizard/blink");
        expectNone(helper, "wizard/mana_focus");
        expectNone(helper, "wizard/star_shield");
        expectNone(helper, "archer/back_leap");
        expectNone(helper, "archer/wind_cloak");
        expectNone(helper, "healer/bell_heal");
        expectNone(helper, "healer/heavens_bell");
        expectNone(helper, "knight/charge_slash");
        expectNone(helper, "rogue/flurry");
        expectTicks(helper, "wizard/lamp_bolt", 8, 8);
        expectTicks(helper, "wizard/starfall", 24, 24);
        expectTicks(helper, "wizard/blizzard", 18, 24);
        expectTicks(helper, "archer/quick_draw", 5, 6);
        expectTicks(helper, "archer/sky_piercer", 12, 12);
        expectTicks(helper, "archer/marking_arrow", 5, 12);
        expectTicks(helper, "healer/purifying_chime", 8, 24);
        expectTicks(helper, "healer/bell_strike", 8, 24);
        JobDef.AttackLock archer = SkillRegistry.get().job(AmemaruMod.id("archer")).attackLock();
        check(helper, archer != null && archer.chargeLevel() == 3 && archer.basicLevel() == 2,
                "アーチャーの引き絞りと通常の射撃の決まりがありません：" + archer);
        for (String melee : new String[]{"knight", "rogue", "monk"}) {
            check(helper, SkillRegistry.get().job(AmemaruMod.id(melee)).attackLock() == null, melee + " に硬直の決まりがあります");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    /** スキルを1段目で覚えさせ、入った枠を返す。 */
    private static int learn(GameTestHelper helper, FakePlayer player, PlayerData data, SkillDef skill) {
        SkillTreeService.forceRank(player, data, skill, 1);
        int slot = data.loadoutSlotOf(skill.id());
        check(helper, slot >= 0, skill.id() + " がスキルの枠に入りません");
        data.setMp(data.getStats().maxMp());
        return slot;
    }

    private static void expectNone(GameTestHelper helper, String id) {
        SkillDef skill = skill(id);
        int ticks = AttackLockService.skillTicks(AttackLockService.rule(skill), skill, 1);
        check(helper, ticks == 0, id + " に硬直がつきます（" + ticks + "tick）");
    }

    private static void expectTicks(GameTestHelper helper, String id, int min, int max) {
        SkillDef skill = skill(id);
        int ticks = AttackLockService.skillTicks(AttackLockService.rule(skill), skill, 1);
        check(helper, ticks >= min && ticks <= max, id + " の硬直が " + min + "〜" + max + "tick ではありません：" + ticks);
    }

    private static SkillDef skill(String path) {
        SkillDef skill = SkillRegistry.get().skill(AmemaruMod.id(path));
        if (skill == null) {
            throw new GameTestAssertException("スキルがありません：" + path);
        }
        return skill;
    }

    private static PlayerData setUp(FakePlayer player, String jobName, String weapon) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータがありません"));
        JobDef job = SkillRegistry.get().job(AmemaruMod.id(jobName));
        if (job == null) {
            throw new GameTestAssertException("職業がありません：" + jobName);
        }
        JobService.setJobForced(player, data, job);
        LevelService.setLevel(player, data, 30);
        Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(weapon));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        LevelService.recompute(player, data);
        data.setMp(data.getStats().maxMp());
        return data;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "am26_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-1126:" + name).getBytes(StandardCharsets.UTF_8));
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
