package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * レベル・経験値・ステータスポイントの振り分け。すべてサーバーで行う。
 */
public final class LevelService {
    /** 経験値の出どころ。倍率をかけるかどうかが変わる。 */
    public enum ExpSource {
        /** 敵・クエストなど、遊んで得た経験値（configの倍率をかける）。 */
        GAMEPLAY,
        /** 管理者のコマンドで与えた経験値（倍率をかけない）。 */
        ADMIN
    }

    private LevelService() {
    }

    // ---------------------------------------------------------------- 経験値とレベル

    /**
     * 経験値を足す。足りればレベルを上げる。
     *
     * <ul>
     *   <li>遊んで得た経験値には、config の倍率と、追いつき補正（サーバーのレベルの中央値より5以上低い人は+50%、
     *       10以上低い人は+100%。Phase 0-A 19章）をかける</li>
     *   <li>レベルは今の章の上限（第1章Lv15・第2章Lv28・第3章Lv40・第4章Lv50）で止まる。上限に届いている間は、
     *       経験値の代わりに交換用の「結晶の欠片」がたまる（{@code growth} の {@code overflow}。無駄にならない）</li>
     * </ul>
     *
     * @return 上がったレベルの数
     */
    public static int addExp(ServerPlayer player, PlayerData data, long amount, ExpSource source) {
        if (amount <= 0) {
            return 0;
        }
        GrowthDef growth = GrowthRules.current();
        double scaled = amount;
        if (source == ExpSource.GAMEPLAY) {
            scaled *= ServerConfig.EXP_MULTIPLIER.get();
            scaled *= 1.0 + catchUpBonus(player, data, growth) / 100.0;
        }
        long gained = Math.max(0L, Math.round(scaled));
        int cap = Math.min(growth.maxLevel(), ProgressionData.currentLevelCap(player.server));

        int startLevel = data.getLevel();
        int level = startLevel;
        long exp = (long) data.getExp() + gained;
        long overflow = 0L;
        if (level >= cap) {
            // もう上限：すべて結晶の欠片へ
            overflow = gained;
            exp = data.getExp();
        } else {
            while (level < cap) {
                int need = growth.expToNext(level);
                if (exp < need) {
                    break;
                }
                exp -= need;
                level++;
            }
            if (level >= cap) {
                overflow = exp;
                exp = 0;
            }
        }
        data.setExp((int) Math.min(exp, Integer.MAX_VALUE));
        if (level != startLevel) {
            data.setLevel(level);
            onLevelChanged(player, data, startLevel, level);
        }
        if (overflow > 0) {
            addOverflow(player, data, growth, overflow);
        }
        return level - startLevel;
    }

    /** 追いつき補正（%）。サーバーのレベルの中央値より低いほど増える。 */
    public static double catchUpBonus(ServerPlayer player, PlayerData data, GrowthDef growth) {
        int median = ProgressionData.get(player.server).medianLevel();
        return growth.catchUpBonus(median - data.getLevel());
    }

    /** 上限に届いている間の経験値をためて、決まった量ごとに結晶の欠片を渡す（持ち物がいっぱいなら足元に落とす）。 */
    private static void addOverflow(ServerPlayer player, PlayerData data, GrowthDef growth, long amount) {
        int per = growth.expPerShard();
        if (per <= 0 || growth.overflowItem() == null) {
            return;
        }
        long total = data.getAdventure().overflowExp() + amount;
        int shards = (int) Math.min(64 * 36, total / per);
        data.getAdventure().setOverflowExp(total - (long) shards * per);
        if (shards <= 0) {
            return;
        }
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(growth.overflowItem());
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        int left = shards;
        while (left > 0) {
            int count = Math.min(left, item.getMaxStackSize());
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            left -= count;
        }
        player.displayClientMessage(Component.translatable("message.amemaru.level_cap.shards", shards,
                new net.minecraft.world.item.ItemStack(item).getHoverName()).withStyle(ChatFormatting.AQUA), true);
    }

    /** レベルを指定する（管理者用）。経験値は0に戻す。下げたときに振り分けが多すぎれば、振り分けを戻す。 */
    public static void setLevel(ServerPlayer player, PlayerData data, int level) {
        GrowthDef growth = GrowthRules.current();
        int target = Math.max(1, Math.min(level, growth.maxLevel()));
        int oldLevel = data.getLevel();
        data.setLevel(target);
        data.setExp(0);
        if (data.totalAllocated() > growth.totalStatPoints(target)) {
            data.clearAllocated();
        }
        onLevelChanged(player, data, oldLevel, target);
    }

    private static void onLevelChanged(ServerPlayer player, PlayerData data, int oldLevel, int newLevel) {
        GrowthDef growth = GrowthRules.current();
        recordLevel(player, data);
        recompute(player, data);
        if (newLevel < oldLevel) {
            // レベルを下げて、使ったスキルポイントがもらえる合計を超えたら、スキルをやり直す
            SkillTreeService.validatePoints(player, data);
        }
        if (newLevel > oldLevel) {
            if (ServerConfig.RESTORE_ON_LEVEL_UP.get()) {
                data.setHp(data.getStats().maxHp());
                data.setMp(data.getStats().maxMp());
                VitalsService.pushVanillaHealth(player, data);
            }
            int statGain = growth.totalStatPoints(newLevel) - growth.totalStatPoints(oldLevel);
            int skillGain = growth.totalSkillPoints(newLevel) - growth.totalSkillPoints(oldLevel);
            // 最後の引数は、ステータス画面を開くキー（割り当てを変えていれば、変えた後のキーが出る）
            player.displayClientMessage(Component.translatable("message.amemaru.level_up", newLevel, statGain, skillGain,
                            Component.keybind("key.amemaru.status"))
                    .withStyle(ChatFormatting.GOLD), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        data.markDirty(PlayerData.SYNC_GROWTH);
    }

    /** サーバーのレベルの記録（追いつき補正の中央値）を更新する。テスト用の偽のプレイヤーは数えない。 */
    public static void recordLevel(ServerPlayer player, PlayerData data) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        ProgressionData.get(player.server).recordLevel(player.getUUID(), data.getLevel());
    }

    // ---------------------------------------------------------------- ポイント

    /** まだ振り分けていないステータスポイント。 */
    public static int availableStatPoints(PlayerData data, GrowthDef growth) {
        return Math.max(0, growth.totalStatPoints(data.getLevel()) - data.totalAllocated());
    }

    /** まだ使っていないスキルポイント（レベルまでにもらえる合計から、スキルの習得に使った分を引く）。 */
    public static int availableSkillPoints(PlayerData data, GrowthDef growth) {
        return Math.max(0, SkillTreeService.availablePoints(data, growth));
    }

    /**
     * ステータスポイントを振り分ける。
     *
     * @return 実際に振り分けた数（ポイントが足りなければ減る。振り分けられない種類なら0）
     */
    public static int allocate(ServerPlayer player, PlayerData data, StatType type, int amount) {
        if (!type.isAllocatable() || amount <= 0) {
            return 0;
        }
        int used = Math.min(amount, availableStatPoints(data, GrowthRules.current()));
        if (used <= 0) {
            return 0;
        }
        data.setAllocated(type, data.getAllocated(type) + used);
        recompute(player, data);
        return used;
    }

    /**
     * 振り分けをすべて戻す（管理者用と「忘れ水」）。
     *
     * @return 戻したポイントの数
     */
    public static int resetAllocation(ServerPlayer player, PlayerData data) {
        int refunded = data.totalAllocated();
        data.clearAllocated();
        recompute(player, data);
        return refunded;
    }

    // ---------------------------------------------------------------- 計算し直し

    /**
     * ステータスを計算し直す。初めてのログインならHPとMPを最大にする。
     * 定義を読み直して最大レベルが下がったときは、レベルを収める。
     */
    public static void recompute(ServerPlayer player, PlayerData data) {
        GrowthDef growth = GrowthRules.current();
        if (data.getLevel() > growth.maxLevel()) {
            data.setLevel(growth.maxLevel());
            data.setExp(0);
        }
        data.applyStats(StatCalculator.compute(player, data, growth));
        // 素早さ：移動と攻撃の速さ（回避は受けたときに抽選する）
        SpeedService.apply(player, data);
        if (!data.vitalsInitialized()) {
            data.setHp(data.getStats().maxHp());
            data.setMp(data.getStats().maxMp());
        }
        VitalsService.pushVanillaHealth(player, data);
        // 残りポイントや次のレベルまでの経験値は定義から求めるため、毎回送り直す
        data.markDirty(PlayerData.SYNC_GROWTH);
    }
}
