package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.UniqueSkillDef;
import jp.amemaru.lostconnection.equipment.UniqueSkillService;
import jp.amemaru.lostconnection.equipment.UniqueSkills;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.s2c.SyncUniqueSkillsS2C;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SkillCooldown;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ユニーク武器の固有スキルの自動テスト（11月28日のご指示「ユニーク武器や装備には特殊効果や特殊スキルをつけましょう」【補完】）。
 *
 * <p>演出（{@code client.UniqueSkillFx}）と、偽のプレイヤーでは数えられない「認証」（まわりのプレイヤーの数）は、
 * docs/ingame_check.md の手順で確かめる。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UniqueSkillGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;

    private UniqueSkillGameTests() {
    }

    /** ユニークの武器には、どれにも固有スキルが1つある。読み込みに問題がなく、数値は決まりの中、番号と同期も合う。 */
    @GameTest(template = EMPTY)
    public static void everyUniqueWeaponHasASkill(GameTestHelper helper) {
        check(helper, UniqueSkills.loader().errors().isEmpty(), "固有スキルの読み込みに問題があります：" + UniqueSkills.loader().errors());
        List<UniqueSkillDef> all = UniqueSkills.all();
        check(helper, all.size() == 8, "固有スキルが8つではありません：" + all.size());
        int weapons = 0;
        for (Map.Entry<ResourceLocation, EquipmentDef> entry : EquipmentDefs.server().equipment().entrySet()) {
            EquipmentDef def = entry.getValue();
            if (def.rarity() != Rarity.UNIQUE || def.slot() != GearSlot.WEAPON) {
                continue;
            }
            weapons++;
            check(helper, UniqueSkills.forItem(def.id()) != null, "ユニークの武器「" + def.id() + "」に固有スキルがありません");
        }
        check(helper, weapons == all.size(), "ユニークの武器（" + weapons + "）と固有スキル（" + all.size() + "）の数が合いません");
        Set<Integer> indices = new HashSet<>();
        for (UniqueSkillDef def : all) {
            String name = def.id().getPath();
            Item item = ForgeRegistries.ITEMS.getValue(def.item());
            check(helper, item != null && UniqueSkills.forStack(new ItemStack(item)) == def, name + "：武器から固有スキルを引けません");
            check(helper, def.skill().cooldown(1) >= 30 && def.skill().cooldown(1) <= 60,
                    name + "：クールダウンが30〜60秒ではありません：" + def.skill().cooldown(1));
            check(helper, def.skill().mpCost(1) > 0 && def.skill().cast(1) == 0 && !def.skill().isCharged(),
                    name + "：MPがないか、詠唱・溜めがあります");
            check(helper, def.skill().fx().startsWith("unique_"), name + "：演出の名前が unique_ で始まりません：" + def.skill().fx());
            check(helper, SkillRegistry.get().skill(def.skill().id()) == null, name + "：職業のスキルの一覧に入っています");
            int index = UniqueSkills.index(def);
            check(helper, index >= UniqueSkills.INDEX_BASE && UniqueSkills.byIndex(index) == def
                    && UniqueSkills.indexOfSkill(def.skill().id()) == index && indices.add(index), name + "：番号が合いません：" + index);
            check(helper, UniqueSkills.isUniqueSkillId(def.skill().id()), name + "：スキルIDが unique/ で始まりません");
        }

        // クライアントへの同期：書いて読み直しても同じ
        SyncUniqueSkillsS2C sent = SyncUniqueSkillsS2C.current();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        SyncUniqueSkillsS2C read = SyncUniqueSkillsS2C.decode(buf);
        check(helper, read.entries().size() == sent.entries().size(), "同期した固有スキルの数が合いません");
        for (int i = 0; i < sent.entries().size(); i++) {
            SyncUniqueSkillsS2C.Entry a = sent.entries().get(i);
            SyncUniqueSkillsS2C.Entry b = read.entries().get(i);
            check(helper, a.item().equals(b.item()) && a.color() == b.color() && a.skill().name().equals(b.skill().name())
                    && a.skill().index() == b.skill().index() && a.skill().fx().equals(b.skill().fx())
                    && a.skill().details().equals(b.skill().details()), "同期した固有スキルが変わりました：" + a.item());
            check(helper, !a.skill().details().isEmpty() && !a.skill().details().get(0).isEmpty(), a.item() + "：効き目の説明がありません");
        }
        helper.succeed();
    }

    /** 固有スキルを使うと、MPが減ってクールダウンが始まる。クールダウン中とMP不足は断られる。 */
    @GameTest(template = EMPTY)
    public static void uniqueSkillUsesMpAndCooldown(GameTestHelper helper) {
        FakePlayer player = fake(helper, "unique_use");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        ItemStack sword = stack("earth_horn_greatsword");
        equip(player, data, sword);
        UniqueSkillDef def = UniqueSkills.forStack(sword);
        check(helper, def != null, "大地角の大剣に固有スキルがありません");
        data.clearCooldowns();
        data.setMp(data.getStats().maxMp());
        double before = data.getMp();
        long now = helper.getLevel().getGameTime();
        SkillService.Result first = UniqueSkillService.tryUse(player, data, -1, null);
        check(helper, first.ok(), "固有スキルを使えません：" + first.failure());
        check(helper, Math.abs(data.getMp() - (before - def.skill().mpCost(1))) < EPSILON,
                "MPが " + def.skill().mpCost(1) + " 減っていません：" + before + " → " + data.getMp());
        int ticks = SkillService.cooldownTicks(data, def.skill(), 1, now);
        SkillCooldown cooldown = data.getCooldown(def.skill().id());
        check(helper, cooldown != null && cooldown.endTick() == now + ticks && ticks >= 600, "クールダウンが始まっていません：" + cooldown);
        check(helper, UniqueSkillService.cooldownRemaining(player, data) > 0, "手に持った武器のクールダウンが見えません");
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.COOLDOWN);

        data.clearCooldowns();
        data.setMp(def.skill().mpCost(1) - 1);
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.NO_MP);
        helper.succeed();
    }

    /** 固有スキルは、その武器を手に持ち、使える条件（職業・必要レベル）を満たし、壊れていないときだけ使える。 */
    @GameTest(template = EMPTY)
    public static void uniqueSkillNeedsItsWeapon(GameTestHelper helper) {
        FakePlayer player = fake(helper, "unique_rules");
        PlayerData data = data(helper, player);
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("knight"));
        data.clearCooldowns();

        equip(player, data, ItemStack.EMPTY);
        data.setMp(data.getStats().maxMp());
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.NO_WEAPON);

        equip(player, data, new ItemStack(ModItems.APPRENTICE_SWORD.get()));
        data.setMp(data.getStats().maxMp());
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.NO_WEAPON);

        // 職業が合わない（ウィザードの大剣）
        JobService.setJobForced(player, data, job("wizard"));
        equip(player, data, stack("earth_horn_greatsword"));
        data.setMp(data.getStats().maxMp());
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.NO_WEAPON);

        // 壊れた武器
        JobService.setJobForced(player, data, job("knight"));
        ItemStack broken = stack("earth_horn_greatsword");
        EquipmentDef def = EquipmentDefs.server().get(broken);
        check(helper, def != null, "大地角の大剣の定義がありません");
        GearData.setDurability(broken, def, 0);
        equip(player, data, broken);
        data.setMp(data.getStats().maxMp());
        expect(helper, UniqueSkillService.tryUse(player, data, -1, null), SkillService.Failure.NO_WEAPON);
        check(helper, data.getCooldown(UniqueSkills.forItem(def.id()).skill().id()) == null, "使えなかったのにクールダウンが始まりました");
        helper.succeed();
    }

    // ================================================================ 共通

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amu_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-unique:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    private static ItemStack stack(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(path));
        if (item == null) {
            throw new GameTestAssertException("品が見つかりません：" + path);
        }
        return new ItemStack(item);
    }

    /** 手に持つ物を決めて、ステータスを計算し直す（偽のプレイヤーは毎 tick の持ちかえの検知が動かないため）。 */
    private static void equip(ServerPlayer player, PlayerData data, ItemStack main) {
        player.setItemSlot(EquipmentSlot.MAINHAND, main);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        LevelService.recompute(player, data);
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(AmemaruMod.id(name));
        if (job == null) {
            throw new GameTestAssertException("職業が見つかりません：" + name);
        }
        return job;
    }

    private static void expect(GameTestHelper helper, SkillService.Result result, SkillService.Failure expected) {
        check(helper, result.failure() == expected, "固有スキルが " + expected + " で断られていません：" + result.failure());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
