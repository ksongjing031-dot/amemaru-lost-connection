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
 * <p>HPが0になると、バニラの死亡の代わりに「ダウン→蘇生待ち→教会で復活」になる（Phase 4。{@link DownHandler}）。</p>
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
        if (data.down().isDown()) {
            // ダウンの間（Phase 4）：HPは0のまま、回復しない。バニラの体力は死なない最小の値
            data.setHp(0);
            pushVanillaHealth(player, data);
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

    /**
     * ステータスを計算し直す前に、まだ独自のHPに写していないバニラの体力の変化を写す（{@code LevelService.recompute} から）。
     *
     * <p>11月27日のバグ「防具をつけていると、落下以外のダメージが通らない（ノックバックとダメージの数字だけ）」：敵の攻撃で
     * 防具の耐久が減ると、持ち物が変わった扱いになってステータスの計算し直しが走り、そこでバニラの体力を（まだ減っていない）
     * 独自のHPから置き直していたため、受けたダメージが消えていた。落下は防具の耐久を減らさないので通っていた。
     * 計算し直す前の最大HP（ダメージを決めたときと同じ値）で写す。</p>
     */
    public static void absorbPending(ServerPlayer player, PlayerData data) {
        if (player.isDeadOrDying() || data.down().isDown() || data.getStats() == null || data.getExpectedVanillaHealth() < 0) {
            return;
        }
        absorbVanillaChanges(player, data);
        data.setExpectedVanillaHealth(player.getHealth());
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

    /**
     * 独自のHPの割合に合わせて、バニラの体力を決める。
     *
     * <p>11月27日：置き直す前に、まだ写していないバニラの体力の変化（受けたばかりのダメージ）を写す。写さずに置き直すと、
     * そのダメージが消える（同じ tick に回復や計算し直しが入ったとき）。</p>
     */
    public static void pushVanillaHealth(ServerPlayer player, PlayerData data) {
        if (!data.isStatsReady() || player.isDeadOrDying()) {
            return;
        }
        absorbPending(player, data);
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
