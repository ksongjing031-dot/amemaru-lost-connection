package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.player.StatBlock;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * スキル1回分の情報。使った人のステータスは、使った瞬間の写しを持つ（地面に残る陣や、遅れて落ちる流星が、
 * 使った人がログアウトした後でも同じ強さで効くようにするため）。
 */
public final class SkillContext {
    public final ServerLevel level;
    public final UUID casterId;
    public final int casterEntityId;
    public final SkillDef skill;
    public final int skillIndex;
    public final int rank;
    public final long startTick;
    private final StatBlock stats = new StatBlock();
    private final RandomSource random;
    /** 強化の倍率（ゲージの消費・連携支援など）。ダメージ・MPの受け渡しにかかる。 */
    public double multiplier = 1.0;
    /** この1回で「連携支援」が効いたか。 */
    public boolean supported;

    public SkillContext(ServerPlayer caster, StatBlock casterStats, SkillDef skill, int skillIndex, int rank) {
        this.level = caster.serverLevel();
        this.casterId = caster.getUUID();
        this.casterEntityId = caster.getId();
        this.skill = skill;
        this.skillIndex = skillIndex;
        this.rank = rank;
        this.startTick = level.getGameTime();
        this.stats.copyFrom(casterStats);
        this.random = caster.getRandom();
    }

    /** 使った人（ログアウトした・別の場所にいる・死んでいるときは null）。 */
    @Nullable
    public ServerPlayer caster() {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(casterId);
        if (player == null || player.level() != level || !player.isAlive()) {
            return null;
        }
        return player;
    }

    /** ダメージの持ち主（倒したときの記録に使う）。 */
    @Nullable
    public Entity owner() {
        return caster();
    }

    /** 使った瞬間のステータス（「守る者の誇り」「魔力の共鳴」などの上がり幅も含む）。 */
    public double stat(StatType type) {
        return stats.get(type);
    }

    public RandomSource random() {
        return random;
    }
}
