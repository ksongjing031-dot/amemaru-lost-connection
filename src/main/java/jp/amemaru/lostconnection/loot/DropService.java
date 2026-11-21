package jp.amemaru.lostconnection.loot;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.party.PartyExp;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 敵が倒れたときのドロップと経験値（Phase 0-A 20章・24章）。
 *
 * <ul>
 *   <li>受け取るのは、その敵に攻撃を当てた人（と挑発した人）。倒れた場所から遠すぎる人（config の {@code lootRange}）は除く</li>
 *   <li>経験値：敵の経験値を、受け取る人の数で均等に分ける。Phase 4：受け取る人のパーティのメンバーで、32ブロック以内の人も
 *       受け取り、パーティごとに人数のボーナスをかける（{@link PartyExp}）</li>
 *   <li>ふつうのドロップ：一人ずつドロップ表を引く（config の {@code dropRateMultiplier} の回数）</li>
 *   <li>レアドロップ：一人ずつ、敵の定義の確率 × config の {@code rareDropMultiplier}（天井のある敵は、外れるたびに
 *       +10%）で抽選する。出たら、その人の画面にだけ光と音、その品の確率（定義の確率）が {@code rareAnnounceThreshold}%
 *       以下ならサーバー全体に告知</li>
 *   <li>ドロップは、その人だけが拾える（ほかの人には見えない）。図鑑に、倒した数と手に入れた品を記録する</li>
 * </ul>
 */
public final class DropService {
    private DropService() {
    }

    /** 敵が倒れたとき（{@link AmemaruMob#dropFromLootTable} から）。 */
    public static void onEnemyDeath(AmemaruMob mob, DamageSource source) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        EnemyDef def = mob.def();
        List<ServerPlayer> receivers = receivers(mob, source, level);
        if (def == null || receivers.isEmpty()) {
            return;
        }
        // Phase 4：近くのパーティのメンバーも受け取り、パーティの人数のボーナスをかける
        receivers = PartyExp.withPartyMembers(receivers, mob, level);
        Map<UUID, Integer> shares = PartyExp.shares(def.exp(), receivers);
        for (ServerPlayer player : receivers) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data == null) {
                continue;
            }
            data.getAdventure().recordKill(def.id());
            int share = shares.getOrDefault(player.getUUID(), 0);
            if (share > 0) {
                LevelService.addExp(player, data, share, LevelService.ExpSource.GAMEPLAY);
            }
            dropNormal(mob, def, source, level, player, data);
            dropRare(mob, def, level, player, data);
        }
    }

    /** ドロップと経験値を受け取る人。 */
    private static List<ServerPlayer> receivers(AmemaruMob mob, DamageSource source, ServerLevel level) {
        double range = ServerConfig.LOOT_RANGE.get();
        List<ServerPlayer> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : mob.contributions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.level() == level && !player.isSpectator() && player.distanceToSqr(mob) <= range * range) {
                list.add(player);
            }
        }
        if (list.isEmpty() && source.getEntity() instanceof ServerPlayer killer && killer.level() == level) {
            list.add(killer);
        }
        return list;
    }

    // ---------------------------------------------------------------- ふつうのドロップ

    private static void dropNormal(AmemaruMob mob, EnemyDef def, DamageSource source, ServerLevel level, ServerPlayer player,
                                   PlayerData data) {
        if (def.lootTable() == null) {
            return;
        }
        LootTable table = level.getServer().getLootData().getLootTable(def.lootTable());
        if (table == LootTable.EMPTY) {
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        double multiplier = ServerConfig.DROP_RATE_MULTIPLIER.get();
        int rolls = (int) Math.floor(multiplier);
        if (level.random.nextDouble() < multiplier - rolls) {
            rolls++;
        }
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.isEmpty()) {
                    continue;
                }
                finish(stack, level);
                record(data, def, stack);
                LootItemEntity.spawn(level, mob.getX(), mob.getY() + 0.4, mob.getZ(), stack, player.getUUID(), -1);
            }
        }
    }

    // ---------------------------------------------------------------- レアドロップ

    private static void dropRare(AmemaruMob mob, EnemyDef def, ServerLevel level, ServerPlayer player, PlayerData data) {
        if (def.rare().isEmpty()) {
            return;
        }
        int misses = def.pity() ? data.getAdventure().pity(def.id()) : 0;
        boolean any = false;
        for (EnemyDef.RareDrop rare : def.rare()) {
            double chance = rareChance(rare, rare.pity() ? misses : 0);
            if (level.random.nextDouble() * 100.0 >= chance) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rare.item());
            if (item == null || !rare.item().equals(ForgeRegistries.ITEMS.getKey(item))) {
                continue;
            }
            if (rare.pity()) {
                any = true;
            }
            ItemStack stack = new ItemStack(item, rare.count());
            Rarity rarity = rare.rarity() == null ? null : Rarity.byId(rare.rarity());
            finishRare(stack, level, rarity);
            record(data, def, stack);
            Rarity shown = rarityOf(stack);
            LootItemEntity.spawn(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack, player.getUUID(), shown.ordinal());
            // 告知と表示は、その品の確率（定義の確率。config の倍率と天井をかける前）で決める【補完】
            announce(player, mob, def, stack, rare.chance(), shown);
        }
        if (def.pity()) {
            data.getAdventure().setPity(def.id(), nextPity(misses, any));
        }
    }

    // ---------------------------------------------------------------- 計算（自動テストでも使う）

    /** 一人分の経験値（敵の経験値を、受け取る人の数で均等に分けて切り上げる）。 */
    public static int expShare(int exp, int receivers) {
        if (exp <= 0 || receivers <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) exp / receivers);
    }

    /**
     * レアドロップの実際の確率（%）：定義の確率 × config の {@code rareDropMultiplier} × 天井の補正（外れた回数 × config の
     * {@code pityStep}%。天井のない敵は {@code misses} が0）。100%を超えれば必ず出る。
     */
    public static double rareChance(EnemyDef.RareDrop rare, int misses) {
        double pityFactor = 1.0 + Math.max(0, misses) * ServerConfig.PITY_STEP.get() / 100.0;
        return rare.chance() * ServerConfig.RARE_DROP_MULTIPLIER.get() * pityFactor;
    }

    /** 天井の数え方：何か出れば0に戻り、何も出なければ1つ増える。 */
    public static int nextPity(int misses, boolean dropped) {
        return dropped ? 0 : Math.max(0, misses) + 1;
    }

    /** サーバー全体に告知する確率か（config の {@code rareAnnounceThreshold}%以下）。 */
    public static boolean announces(double chance) {
        return chance <= ServerConfig.RARE_ANNOUNCE_THRESHOLD.get();
    }

    /** レアドロップが出たとき：本人の画面に光と音、確率が低ければサーバー全体に告知。 */
    private static void announce(ServerPlayer player, AmemaruMob mob, EnemyDef def, ItemStack stack, double chance, Rarity rarity) {
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.RARE_DROP, stack.copy()));
        player.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.6F, 1.8F);
        if (announces(chance)) {
            String percent = String.format(Locale.ROOT, chance < 1.0 ? "%.2f" : "%.1f", chance);
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.rare_drop.announce",
                    player.getDisplayName(), Component.literal(def.name()), stack.getDisplayName(), percent)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        AmemaruMod.LOGGER.info("[amemaru] Rare drop: {} got {} from {} ({}%)", player.getGameProfile().getName(),
                ForgeRegistries.ITEMS.getKey(stack.getItem()), def.id(), chance);
    }

    // ---------------------------------------------------------------- 仕上げ

    /** ドロップ表で抽選していない装備・アクセサリーを抽選する。 */
    private static void finish(ItemStack stack, ServerLevel level) {
        if (stack.getItem() instanceof GearItem) {
            EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
            EquipmentDef def = snapshot.get(stack);
            if (def != null) {
                GearData.ensureRolled(stack, def, snapshot.rules(), level.random);
            }
        } else if (stack.getItem() instanceof AccessoryItem) {
            AccessoryService.ensureRolled(stack, level.random);
        }
    }

    /** レアドロップの仕上げ（レアリティを決めて抽選し、レアドロップの印をつける）。 */
    private static void finishRare(ItemStack stack, ServerLevel level, Rarity rarity) {
        if (stack.getItem() instanceof GearItem) {
            EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
            EquipmentDef def = snapshot.get(stack);
            if (def != null) {
                GearData.roll(stack, def, snapshot.rules(), level.random, rarity, false, null);
                GearData.markRareDrop(stack);
            }
        } else {
            finish(stack, level);
        }
    }

    /** 光の柱の色に使うレアリティ（装備・アクセサリーはその品の、ほかはレア）。 */
    private static Rarity rarityOf(ItemStack stack) {
        if (stack.getItem() instanceof GearItem) {
            EquipmentDef def = EquipmentDefs.server().get(stack);
            if (def != null) {
                return GearData.rarity(stack, def);
            }
        }
        if (stack.getItem() instanceof AccessoryItem) {
            return jp.amemaru.lostconnection.accessory.AccessoryData.rarity(stack, Rarity.UNCOMMON);
        }
        if (stack.getItem() instanceof jp.amemaru.lostconnection.item.MaterialItem material) {
            return material.amemaruRarity().ordinal() >= Rarity.RARE.ordinal() ? material.amemaruRarity() : Rarity.RARE;
        }
        return Rarity.RARE;
    }

    /** 図鑑に、手に入れた品を記録する。 */
    private static void record(PlayerData data, EnemyDef def, ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            data.getAdventure().recordDrop(def.id(), id);
        }
    }
}
