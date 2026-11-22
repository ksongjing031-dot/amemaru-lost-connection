package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.AfflictionService;
import jp.amemaru.lostconnection.combat.BasicShotService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.entity.LampBolt;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.HealService;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.StealthService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
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
 * Phase 6 その2（職業とスキル）の自動テスト：ヒーラーの回復・ほどき・呼び戻し、毒・標・縫い止め・戒め・煙、潜伏、
 * モンクの連撃（起・継・極）、ローグの気配、アーチャーの集中と弓の通常の射撃。
 *
 * <p>数値は同梱の {@code skills/**}・{@code jobs/**} と config の初期値が前提。偽のプレイヤーのダメージは「持ち主なし」になり、
 * 訓練人形は傷つかないため、体力を増やしたハスクで確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Phase6JobGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private Phase6JobGameTests() {
    }

    // ================================================================ ヒーラー

    /** 癒しの鈴：味方のHPが戻る。結び手当てのほどき：悪い状態（鈍足）が消える。 */
    @GameTest(template = EMPTY)
    public static void healerHealsAndCleanses(GameTestHelper helper) {
        FakePlayer healer = fake(helper, "healer");
        PlayerData data = setup(healer, "healer", "apprentice_bell_staff");
        FakePlayer ally = fake(helper, "heal_ally");
        PlayerData allyData = setup(ally, "knight", "apprentice_sword");
        placeInFront(ally, healer, 2.0);
        allyData.setHp(allyData.getStats().maxHp() * 0.3);
        double before = allyData.getHp();
        SkillDef bell = skill("healer/bell_heal");
        SkillTreeService.forceRank(healer, data, bell, 1);
        int slot = data.loadoutSlotOf(bell.id());
        check(helper, slot >= 0, "癒しの鈴がスキル枠に入っていません");
        SkillService.Result result = SkillService.tryUse(healer, data, slot, ally.getId(), null);
        check(helper, result.ok(), "癒しの鈴を使えません：" + result.failure());
        check(helper, allyData.getHp() > before + 1.0, "癒しの鈴で、味方のHPが戻っていません：" + before + " → " + allyData.getHp());
        check(helper, HealService.healMultiplier(healer, data) >= 1.0, "回復の倍率が1より小さいです");

        ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        int removed = HealService.cleanse(ally, 0, healer);
        check(helper, removed >= 1 && !ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "ほどきで、鈍足が消えていません：" + removed);
        helper.succeed();
    }

    /** 呼び戻し：倒れた仲間を、HPを決まった割合にして起こす。 */
    @GameTest(template = EMPTY)
    public static void healerRevivesDowned(GameTestHelper helper) {
        FakePlayer healer = fake(helper, "reviver");
        setup(healer, "healer", "apprentice_bell_staff");
        FakePlayer downed = fake(helper, "revived");
        PlayerData downedData = setup(downed, "knight", "apprentice_sword");
        placeInFront(downed, healer, 2.0);
        DownHandler.goDown(downed, downedData);
        check(helper, DownHandler.isDown(downed), "倒れていません");
        check(helper, HealService.revive(downed, 30.0, healer), "呼び戻しで起こせません");
        check(helper, !DownHandler.isDown(downed), "起こした後も倒れたままです");
        check(helper, downedData.getHp() > 0, "起こした後のHPが0です");
        helper.succeed();
    }

    // ================================================================ 状態

    /** 毒：重なり、毎秒ダメージが入り、弾けさせると重なりを使い切る。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void poisonStacksAndTicks(GameTestHelper helper) {
        FakePlayer player = fake(helper, "poison");
        setup(player, "rogue", "apprentice_daggers");
        Zombie target = toughZombieInFront(helper, player, 1.6);
        AfflictionService.addPoison(target, 2, 3, 2.0, 120, player);
        check(helper, AfflictionService.poisonStacks(target) == 2, "毒が2つ重なっていません：" + AfflictionService.poisonStacks(target));
        AfflictionService.addPoison(target, 5, 3, 2.0, 120, player);
        check(helper, AfflictionService.poisonStacks(target) == 3, "毒が上限の3で止まっていません：" + AfflictionService.poisonStacks(target));
        check(helper, target.hasEffect(ModEffects.POISON.get()), "毒の効果（アイコン）が付いていません");
        float before = target.getHealth();
        helper.runAfterDelay(45, () -> {
            check(helper, target.getHealth() < before, "毒のダメージが入っていません：" + before + " → " + target.getHealth());
            int consumed = AfflictionService.consumePoison(target);
            check(helper, consumed == 3, "弾けさせた毒の数が3ではありません：" + consumed);
            check(helper, AfflictionService.poisonStacks(target) == 0, "弾けさせた後も毒が残っています");
            target.discard();
            helper.succeed();
        });
    }

    /** 標・戒め・煙・縫い止め：値と時間が残り、強い方が残る。 */
    @GameTest(template = EMPTY)
    public static void marksAndHindrances(GameTestHelper helper) {
        FakePlayer player = fake(helper, "marks");
        setup(player, "archer", "apprentice_bow");
        Zombie target = toughZombieInFront(helper, player, 1.6);
        try {
            AfflictionService.mark(target, 15.0, 100, player);
            AfflictionService.mark(target, 10.0, 100, player);
            check(helper, Math.abs(AfflictionService.markBonus(target) - 15.0) < EPSILON, "標の値が強い方（15）になっていません："
                    + AfflictionService.markBonus(target));
            check(helper, target.hasEffect(ModEffects.HUNTER_MARK.get()), "標の効果が付いていません");
            AfflictionService.weaken(target, 20.0, 80, player);
            check(helper, Math.abs(AfflictionService.weakness(target) - 20.0) < EPSILON, "戒めの値が20ではありません");
            AfflictionService.blind(target, 30.0, 80, player);
            check(helper, Math.abs(AfflictionService.missChance(target) - 30.0) < EPSILON, "煙で外れる確率が30%ではありません");
            AfflictionService.root(target, 40, player);
            check(helper, AfflictionService.rooted(target), "縫い止めになっていません");
            check(helper, target.hasEffect(ModEffects.ROOTED.get()), "縫い止めの効果が付いていません");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** 潜伏：狙っていた魔物が見失い、解くと元に戻る。 */
    @GameTest(template = EMPTY)
    public static void stealthLosesTarget(GameTestHelper helper) {
        FakePlayer player = fake(helper, "stealth");
        PlayerData data = setup(player, "rogue", "apprentice_daggers");
        Zombie zombie = toughZombieInFront(helper, player, 2.0);
        try {
            zombie.setTarget(player);
            StealthService.hide(player, 60);
            check(helper, StealthService.hidden(player), "潜伏していません");
            check(helper, player.hasEffect(ModEffects.HIDDEN.get()), "潜伏の効果が付いていません");
            check(helper, zombie.getTarget() != player, "潜伏しても、魔物が狙い続けています");
            StealthService.reveal(player, data);
            check(helper, !StealthService.hidden(player), "解いた後も潜伏しています");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    // ================================================================ モンク・ローグ・アーチャー

    /** 連撃：【起】で1、違う【継】で2、同じ【継】を続けると切れて0。【極】で使い切って0に戻る。気もたまる。 */
    @GameTest(template = EMPTY)
    public static void monkComboChains(GameTestHelper helper) {
        FakePlayer player = fake(helper, "monk");
        PlayerData data = setup(player, "monk", "apprentice_gauntlets");
        JobDef monk = job("monk");
        check(helper, monk.combo() != null && monk.combo().max() == 5, "モンクの連撃の決まり（最大5）がありません");
        SkillDef jab = skill("monk/jab_combo");
        SkillDef spin = skill("monk/spin_kick");
        SkillDef hakkei = skill("monk/hakkei");
        check(helper, jab.combo() == SkillDef.ComboRole.OPENER && spin.combo() == SkillDef.ComboRole.LINK
                && hakkei.combo() == SkillDef.ComboRole.FINISHER, "連打・旋脚・発勁が、起・継・極になっていません");
        for (SkillDef skill : List.of(jab, spin, hakkei)) {
            SkillTreeService.forceRank(player, data, skill, 1);
        }
        Zombie target = toughZombieInFront(helper, player, 1.6);
        try {
            data.markCombat(helper.getLevel().getGameTime());
            use(helper, player, data, jab, target);
            check(helper, data.getCombo() == 1, "【起】で連撃が1になっていません：" + data.getCombo());
            check(helper, data.getGauge() > 0, "連撃で気がたまっていません");
            use(helper, player, data, spin, target);
            check(helper, data.getCombo() == 2, "違う【継】で連撃が2になっていません：" + data.getCombo());
            use(helper, player, data, spin, target);
            check(helper, data.getCombo() == 0, "同じ【継】を続けたのに、連撃が切れていません：" + data.getCombo());
            use(helper, player, data, jab, target);
            use(helper, player, data, spin, target);
            check(helper, data.getCombo() == 2, "やり直した連撃が2になっていません：" + data.getCombo());
            use(helper, player, data, hakkei, target);
            check(helper, data.getCombo() == 0, "【極】で連撃が0に戻っていません：" + data.getCombo());
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** 気配：スキルを当てるとたまり、最大は5。 */
    @GameTest(template = EMPTY)
    public static void roguePresenceGauge(GameTestHelper helper) {
        FakePlayer player = fake(helper, "presence");
        PlayerData data = setup(player, "rogue", "apprentice_daggers");
        check(helper, GaugeService.PRESENCE.equals(GaugeService.typeOf(data)), "ローグのゲージが気配ではありません");
        check(helper, GaugeService.max(data) == 5, "気配の最大が5ではありません：" + GaugeService.max(data));
        SkillDef flurry = skill("rogue/flurry");
        SkillTreeService.forceRank(player, data, flurry, 1);
        Zombie target = toughZombieInFront(helper, player, 1.6);
        try {
            data.markCombat(helper.getLevel().getGameTime());
            use(helper, player, data, flurry, target);
            check(helper, data.getGauge() >= 1.0 - EPSILON, "乱れ刃で気配がたまっていません：" + data.getGauge());
            for (int i = 0; i < 6; i++) {
                GaugeService.onHit(player, data, helper.getLevel().getGameTime(), true);
            }
            check(helper, data.getGauge() <= 5.0 + EPSILON, "気配が最大の5を超えました：" + data.getGauge());
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /** 集中：戦いの間、立ち止まっているとたまる。弓を持つと通常の射撃で矢が飛ぶ（間隔をあけて）。 */
    @GameTest(template = EMPTY)
    public static void archerFocusAndBasicShot(GameTestHelper helper) {
        FakePlayer player = fake(helper, "archer");
        PlayerData data = setup(player, "archer", "apprentice_bow");
        check(helper, GaugeService.FOCUS.equals(GaugeService.typeOf(data)), "アーチャーのゲージが集中ではありません");
        check(helper, PassiveService.holdsBow(player), "見習いの弓を、弓として持っていません");
        long now = helper.getLevel().getGameTime();
        data.markCombat(now);
        data.setGauge(0.0);
        player.setOnGround(true);
        GaugeService.tick(player, data, now);
        GaugeService.tick(player, data, now + 10);
        check(helper, data.getGauge() > 0, "立ち止まっていても集中がたまりません");

        ServerLevel level = helper.getLevel();
        int before = level.getEntitiesOfClass(LampBolt.class, player.getBoundingBox().inflate(6.0)).size();
        BasicShotService.tryShoot(player);
        List<LampBolt> bolts = level.getEntitiesOfClass(LampBolt.class, player.getBoundingBox().inflate(6.0));
        check(helper, bolts.size() == before + 1, "通常の射撃で矢が飛んでいません：" + before + " → " + bolts.size());
        check(helper, bolts.stream().anyMatch(bolt -> bolt.kind() == LampBolt.Kind.ARROW), "飛んだ弾が矢になっていません");
        check(helper, BasicShotService.nextShot(player.getUUID()) > now, "次に射てる時刻が決まっていません");
        BasicShotService.tryShoot(player);
        int after = level.getEntitiesOfClass(LampBolt.class, player.getBoundingBox().inflate(6.0)).size();
        check(helper, after == before + 1, "間隔をあけずに、もう1本射てました");
        for (LampBolt bolt : level.getEntitiesOfClass(LampBolt.class, player.getBoundingBox().inflate(8.0))) {
            bolt.discard();
        }
        helper.succeed();
    }

    // ================================================================ 共通

    private static void use(GameTestHelper helper, ServerPlayer player, PlayerData data, SkillDef skill, LivingEntity target) {
        int slot = data.loadoutSlotOf(skill.id());
        check(helper, slot >= 0, skill.id() + " がスキル枠に入っていません");
        data.clearCooldowns();
        data.setMp(data.getStats().maxMp());
        SkillService.Result result = SkillService.tryUse(player, data, slot, target.getId(), null);
        check(helper, result.ok(), skill.id() + " を使えません：" + result.failure());
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "am6_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-phase6:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** レベル15の、その職業（武器を右手に）。MPは満タン、HPも満タン。 */
    private static PlayerData setup(ServerPlayer player, String jobName, String weapon) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
        LevelService.setLevel(player, data, 15);
        JobService.setJobForced(player, data, job(jobName));
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(weapon));
        if (item == null) {
            throw new GameTestAssertException("武器がありません：" + weapon);
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        LevelService.recompute(player, data);
        data.clearCooldowns();
        data.setMp(data.getStats().maxMp());
        data.setHp(data.getStats().maxHp());
        data.setGauge(0.0);
        data.setCombo(0, 0L);
        return data;
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return new Vec3(look.x, 0.0, look.z).normalize();
    }

    private static void placeInFront(LivingEntity entity, ServerPlayer player, double distance) {
        Vec3 flat = flatLook(player);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.moveTo(player.getX() + flat.x * distance, player.getY(), player.getZ() + flat.z * distance, 180.0F, 0.0F);
    }

    /** 目の前に、体力を200にしたハスク（動かない）を置く。 */
    private static Zombie toughZombieInFront(GameTestHelper helper, ServerPlayer player, double distance) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 1, 1));
        placeInFront(zombie, player, distance);
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(200.0);
        }
        zombie.setHealth(200.0F);
        return zombie;
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(id(name));
        if (job == null) {
            throw new GameTestAssertException("職業が見つかりません：" + name);
        }
        return job;
    }

    private static SkillDef skill(String path) {
        SkillDef skill = SkillRegistry.get().skill(id(path));
        if (skill == null) {
            throw new GameTestAssertException("スキルが見つかりません：" + path);
        }
        return skill;
    }

    private static ResourceLocation id(String path) {
        return AmemaruMod.id(path);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
