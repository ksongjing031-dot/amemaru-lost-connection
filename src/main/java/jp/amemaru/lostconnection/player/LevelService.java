package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.core.config.ServerConfig;
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
     * 経験値を足す。足りればレベルを上げる。最大レベルでは経験値はたまらない
     * （Phase 3で、代わりに「結晶の欠片」がたまるようにする）。
     *
     * @return 上がったレベルの数
     */
    public static int addExp(ServerPlayer player, PlayerData data, long amount, ExpSource source) {
        if (amount <= 0) {
            return 0;
        }
        GrowthDef growth = GrowthRules.current();
        double scaled = source == ExpSource.GAMEPLAY ? amount * ServerConfig.EXP_MULTIPLIER.get() : amount;
        long gained = Math.max(0L, Math.round(scaled));

        int startLevel = data.getLevel();
        int level = startLevel;
        long exp = (long) data.getExp() + gained;
        while (level < growth.maxLevel()) {
            int need = growth.expToNext(level);
            if (exp < need) {
                break;
            }
            exp -= need;
            level++;
        }
        if (level >= growth.maxLevel()) {
            level = growth.maxLevel();
            exp = 0;
        }
        data.setExp((int) Math.min(exp, Integer.MAX_VALUE));
        if (level != startLevel) {
            data.setLevel(level);
            onLevelChanged(player, data, startLevel, level);
        }
        return level - startLevel;
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
        recompute(player, data);
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

    // ---------------------------------------------------------------- ポイント

    /** まだ振り分けていないステータスポイント。 */
    public static int availableStatPoints(PlayerData data, GrowthDef growth) {
        return Math.max(0, growth.totalStatPoints(data.getLevel()) - data.totalAllocated());
    }

    /** まだ使っていないスキルポイント（Phase 2でスキルの習得に使った分を引く）。 */
    public static int availableSkillPoints(PlayerData data, GrowthDef growth) {
        return growth.totalSkillPoints(data.getLevel());
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
     * 振り分けをすべて戻す（管理者用。Phase 6で「忘れ水」からも呼ぶ）。
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
        if (!data.vitalsInitialized()) {
            data.setHp(data.getStats().maxHp());
            data.setMp(data.getStats().maxMp());
        }
        VitalsService.pushVanillaHealth(player, data);
        // 残りポイントや次のレベルまでの経験値は定義から求めるため、毎回送り直す
        data.markDirty(PlayerData.SYNC_GROWTH);
    }
}
