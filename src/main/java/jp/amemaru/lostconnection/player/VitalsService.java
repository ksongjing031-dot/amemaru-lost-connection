package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.PassiveService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * 独自のHPとMP（Phase 0-B 2章「HPの持ち方」、Phase 0-A 19章の方式B）。
 *
 * <ul>
 *   <li>計算は独自のHP（例：2,000）で行う。バニラの体力は「独自のHP ÷ 最大HP」の割合で後から合わせる。</li>
 *   <li>落下・溺れ・溶岩など、バニラの仕組みで体力が減ったときは、減った割合を独自のHPから引く。
 *       回復薬などで体力が増えたときも、同じ割合で独自のHPを増やす。</li>
 *   <li>自然回復はバニラではなく、ここで行う（ゲームルール naturalRegeneration は止める）。</li>
 * </ul>
 *
 * <p>Phase 1では、HPが0になるとバニラの死亡になる。Phase 4で「ダウン→蘇生待ち→教会で復活」に置き換える。</p>
 */
public final class VitalsService {
    private static final float EPSILON = 1.0E-3F;
    /** HPが残っている間にバニラの体力として置く最小の値（0にすると死亡扱いになるため）。 */
    private static final float MIN_ALIVE_HEALTH = 0.01F;

    private VitalsService() {
    }

    /** 毎 tick、サーバーで呼ぶ。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        if (!data.isStatsReady()) {
            LevelService.recompute(player, data);
        }
        if (player.isDeadOrDying()) {
            // 死亡画面の間は、HUDのHPも0にしておく
            data.setHp(0);
            return;
        }
        absorbVanillaChanges(player, data);
        if (player.tickCount % 20 == 0) {
            regenerate(player, data);
            if (ServerConfig.KEEP_FOOD_FULL.get()) {
                keepFoodFull(player);
            }
        }
        pushVanillaHealth(player, data);
    }

    /** バニラの仕組みで体力が変わっていたら、同じ割合を独自のHPに反映する。 */
    private static void absorbVanillaChanges(ServerPlayer player, PlayerData data) {
        float expected = data.getExpectedVanillaHealth();
        if (expected < 0) {
            return;
        }
        float actual = player.getHealth();
        if (Math.abs(actual - expected) <= EPSILON) {
            return;
        }
        float vanillaMax = player.getMaxHealth();
        if (vanillaMax <= 0) {
            return;
        }
        double delta = (actual - expected) / vanillaMax * data.getStats().maxHp();
        data.setHp(data.getHp() + delta);
        if (delta < 0) {
            data.markCombat(player.level().getGameTime());
        }
    }

    /** 1秒ごとの自然回復。 */
    private static void regenerate(ServerPlayer player, PlayerData data) {
        long now = player.level().getGameTime();
        boolean inCombat = now - data.getLastCombatTick() < ServerConfig.OUT_OF_COMBAT_SECONDS.get() * 20L;
        double hpRate = inCombat ? ServerConfig.HP_REGEN_IN_COMBAT.get() : ServerConfig.HP_REGEN_OUT_OF_COMBAT.get();
        double mpRate = inCombat ? ServerConfig.MP_REGEN_IN_COMBAT.get() : ServerConfig.MP_REGEN_OUT_OF_COMBAT.get();

        double maxHp = data.getStats().maxHp();
        double maxMp = data.getStats().maxMp();
        if (hpRate > 0 && data.getHp() > 0 && data.getHp() < maxHp) {
            data.setHp(data.getHp() + maxHp * hpRate / 100.0);
        }
        if (mpRate > 0 && data.getMp() < maxMp) {
            // パッシブ「マナの泉」で増える
            double multiplier = PassiveService.mpRegenMultiplier(data);
            data.setMp(data.getMp() + maxMp * mpRate / 100.0 * multiplier);
        }
    }

    private static void keepFoodFull(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < 20) {
            food.setFoodLevel(20);
        }
        if (food.getSaturationLevel() < 5.0F) {
            food.setSaturation(5.0F);
        }
    }

    /** 独自のHPの割合に合わせて、バニラの体力を決める。 */
    public static void pushVanillaHealth(ServerPlayer player, PlayerData data) {
        if (!data.isStatsReady() || player.isDeadOrDying()) {
            return;
        }
        float vanillaMax = player.getMaxHealth();
        double maxHp = data.getStats().maxHp();
        float target = (float) (data.getHp() / maxHp * vanillaMax);
        target = Math.max(MIN_ALIVE_HEALTH, Math.min(vanillaMax, target));
        if (Math.abs(player.getHealth() - target) > EPSILON / 10.0F) {
            player.setHealth(target);
        }
        data.setExpectedVanillaHealth(player.getHealth());
    }
}
