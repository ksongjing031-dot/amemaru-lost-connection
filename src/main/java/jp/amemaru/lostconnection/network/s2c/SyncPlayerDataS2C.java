package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 本人のプレイヤーデータを送る（Phase 0-B 5章 {@code SyncPlayerData}）。
 *
 * <p>「全体、または変わったまとまりだけ」を送る。まとまりは {@link PlayerData#SYNC_GROWTH}・
 * {@link PlayerData#SYNC_STATS}・{@link PlayerData#SYNC_VITALS} の組み合わせ。</p>
 */
public final class SyncPlayerDataS2C {
    private static final int MAX_ARRAY = 64;

    private final int sections;
    // 成長
    private final int level;
    private final int maxLevel;
    private final int exp;
    private final int expToNext;
    private final int statPoints;
    private final int skillPoints;
    private final int[] allocated;
    // ステータス
    private final double[] stats;
    // HPとMP
    private final double hp;
    private final double mp;

    private SyncPlayerDataS2C(int sections, int level, int maxLevel, int exp, int expToNext, int statPoints,
                              int skillPoints, int[] allocated, double[] stats, double hp, double mp) {
        this.sections = sections;
        this.level = level;
        this.maxLevel = maxLevel;
        this.exp = exp;
        this.expToNext = expToNext;
        this.statPoints = statPoints;
        this.skillPoints = skillPoints;
        this.allocated = allocated;
        this.stats = stats;
        this.hp = hp;
        this.mp = mp;
    }

    /** サーバーで、今のデータから作る。 */
    public static SyncPlayerDataS2C of(PlayerData data, GrowthDef growth, int sections) {
        int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
        for (StatType type : StatType.allocatable()) {
            allocated[type.allocIndex()] = data.getAllocated(type);
        }
        return new SyncPlayerDataS2C(
                sections,
                data.getLevel(),
                growth.maxLevel(),
                data.getExp(),
                growth.expToNext(data.getLevel()),
                LevelService.availableStatPoints(data, growth),
                LevelService.availableSkillPoints(data, growth),
                allocated,
                data.getStats().toArray(),
                data.getHp(),
                data.getMp());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sections);
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            buf.writeVarInt(level);
            buf.writeVarInt(maxLevel);
            buf.writeVarInt(exp);
            buf.writeVarInt(expToNext);
            buf.writeVarInt(statPoints);
            buf.writeVarInt(skillPoints);
            buf.writeVarInt(allocated.length);
            for (int value : allocated) {
                buf.writeVarInt(value);
            }
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            buf.writeVarInt(stats.length);
            for (double value : stats) {
                buf.writeDouble(value);
            }
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            buf.writeDouble(hp);
            buf.writeDouble(mp);
        }
    }

    public static SyncPlayerDataS2C decode(FriendlyByteBuf buf) {
        int sections = buf.readVarInt();
        int level = 0;
        int maxLevel = 0;
        int exp = 0;
        int expToNext = 0;
        int statPoints = 0;
        int skillPoints = 0;
        int[] allocated = new int[0];
        double[] stats = new double[0];
        double hp = 0;
        double mp = 0;
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            level = buf.readVarInt();
            maxLevel = buf.readVarInt();
            exp = buf.readVarInt();
            expToNext = buf.readVarInt();
            statPoints = buf.readVarInt();
            skillPoints = buf.readVarInt();
            allocated = new int[checkLength(buf.readVarInt())];
            for (int i = 0; i < allocated.length; i++) {
                allocated[i] = buf.readVarInt();
            }
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            stats = new double[checkLength(buf.readVarInt())];
            for (int i = 0; i < stats.length; i++) {
                stats[i] = buf.readDouble();
            }
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            hp = buf.readDouble();
            mp = buf.readDouble();
        }
        return new SyncPlayerDataS2C(sections, level, maxLevel, exp, expToNext, statPoints, skillPoints,
                allocated, stats, hp, mp);
    }

    private static int checkLength(int length) {
        if (length < 0 || length > MAX_ARRAY) {
            throw new IllegalArgumentException("Bad array length in SyncPlayerDataS2C: " + length);
        }
        return length;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(this));
    }

    public int sections() {
        return sections;
    }

    public int level() {
        return level;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int exp() {
        return exp;
    }

    public int expToNext() {
        return expToNext;
    }

    public int statPoints() {
        return statPoints;
    }

    public int skillPoints() {
        return skillPoints;
    }

    public int[] allocated() {
        return allocated;
    }

    public double[] stats() {
        return stats;
    }

    public double hp() {
        return hp;
    }

    public double mp() {
        return mp;
    }
}
