package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * スキルツリー：スキルの習得・段階の上げ下げ・スキル枠・やり直し（Phase 0-B 5章 {@code LearnSkill}・{@code SetLoadout}）。
 *
 * <p>スキルポイントの残りは保存せず、「レベルまでにもらえる合計 − 習得に使った合計」で毎回求める（定義の数値を変えても
 * ずれないようにするため）。職業を選んだときに無料で覚える1段目は、使った合計に含めない。</p>
 */
public final class SkillTreeService {
    /** 習得できない理由。 */
    public enum Blocker {
        NONE(""),
        NO_JOB("message.amemaru.skill.learn.no_job"),
        UNKNOWN("message.amemaru.skill.learn.unknown"),
        OTHER_JOB("message.amemaru.skill.learn.other_job"),
        MAX_RANK("message.amemaru.skill.learn.max_rank"),
        LEVEL("message.amemaru.skill.learn.level"),
        REQUIREMENT("message.amemaru.skill.learn.requirement"),
        UPPER("message.amemaru.skill.learn.upper"),
        /** 奥義は職業ごとに1つだけ。もう一方を覚えている（選び直すには、白紙の誓約書でやり直す）。 */
        ULTIMATE_CHOSEN("message.amemaru.skill.learn.ultimate_chosen"),
        POINTS("message.amemaru.skill.learn.points");

        private final String key;

        Blocker(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private SkillTreeService() {
    }

    // ---------------------------------------------------------------- ポイント

    /** 習得に使ったスキルポイントの合計（無料で覚えた1段目は含めない）。 */
    public static int spentPoints(PlayerData data) {
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        JobDef job = snapshot.job(data.getCombatJob());
        int spent = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : data.getLearnedSkills().entrySet()) {
            SkillDef skill = snapshot.skill(entry.getKey());
            if (skill == null) {
                continue;
            }
            spent += skill.totalCost(entry.getValue());
            if (job != null && job.startingSkills().contains(skill.id())) {
                spent -= skill.rankCost(1);
            }
        }
        return Math.max(0, spent);
    }

    /** まだ使っていないスキルポイント（負になることがある：レベルを下げたときなど）。 */
    public static int availablePoints(PlayerData data, GrowthDef growth) {
        return growth.totalSkillPoints(data.getLevel()) - spentPoints(data);
    }

    // ---------------------------------------------------------------- 習得

    /** 次の段階を覚えられるか。 */
    public static Blocker canLearn(PlayerData data, SkillDef skill) {
        if (!data.hasCombatJob()) {
            return Blocker.NO_JOB;
        }
        if (!skill.job().equals(data.getCombatJob())) {
            return Blocker.OTHER_JOB;
        }
        int current = data.getSkillRank(skill.id());
        int next = current + 1;
        if (next > skill.maxRank()) {
            return Blocker.MAX_RANK;
        }
        if (skill.isUltimate() && chosenUltimate(data, skill) != null) {
            return Blocker.ULTIMATE_CHOSEN;
        }
        if (data.getLevel() < skill.minLevel()) {
            return Blocker.LEVEL;
        }
        for (SkillDef.Requirement requirement : skill.requirements()) {
            if (data.getSkillRank(requirement.skill()) < requirement.rank()) {
                return Blocker.REQUIREMENT;
            }
        }
        if ((skill.isUltimate() || next >= skill.upperRank()) && !data.isPromoted()) {
            return Blocker.UPPER;
        }
        if (availablePoints(data, GrowthRules.current()) < skill.rankCost(next)) {
            return Blocker.POINTS;
        }
        return Blocker.NONE;
    }

    /**
     * 同じ職業の奥義のうち、もう覚えている「ほかの」奥義（奥義は2つから1つを選ぶ。Phase 0-A 19章）。なければ null。
     */
    @Nullable
    public static SkillDef chosenUltimate(PlayerData data, SkillDef ultimate) {
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        for (Map.Entry<ResourceLocation, Integer> entry : data.getLearnedSkills().entrySet()) {
            if (entry.getValue() <= 0 || entry.getKey().equals(ultimate.id())) {
                continue;
            }
            SkillDef other = snapshot.skill(entry.getKey());
            if (other != null && other.isUltimate() && other.job().equals(ultimate.job())) {
                return other;
            }
        }
        return null;
    }

    /**
     * 次の段階を覚える。覚えたアクティブスキルは、空いているスキル枠に入れる。
     *
     * @return 覚えられたか（覚えられなければ、本人に理由を出す）
     */
    public static boolean learn(ServerPlayer player, PlayerData data, ResourceLocation skillId) {
        SkillDef skill = SkillRegistry.get().skill(skillId);
        if (skill == null) {
            tell(player, Component.translatable(Blocker.UNKNOWN.key()));
            return false;
        }
        Blocker blocker = canLearn(data, skill);
        if (blocker != Blocker.NONE) {
            tell(player, blockerMessage(blocker, skill, data));
            return false;
        }
        int rank = data.getSkillRank(skill.id()) + 1;
        data.setSkillRank(skill.id(), rank);
        if (rank == 1 && skill.isActive() && data.loadoutSlotOf(skill.id()) < 0) {
            for (int slot = 0; slot < PlayerData.LOADOUT_SIZE; slot++) {
                if (data.getLoadout(slot) == null) {
                    data.setLoadout(slot, skill.id());
                    break;
                }
            }
        }
        if (!skill.isActive()) {
            // パッシブはステータスに効くことがある
            LevelService.recompute(player, data);
        }
        data.markDirty(PlayerData.SYNC_GROWTH | PlayerData.SYNC_SKILLS);
        player.displayClientMessage(Component.translatable("message.amemaru.skill.learned", skill.name(), rank)
                .withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("skill.learn"),
                SoundSource.PLAYERS, 0.8F, 1.0F + rank * 0.1F);
        return true;
    }

    /** 管理者用：段階をそのまま入れる（前提・ポイントは確かめない）。 */
    public static void forceRank(ServerPlayer player, PlayerData data, SkillDef skill, int rank) {
        int clamped = Math.max(0, Math.min(rank, skill.maxRank()));
        data.setSkillRank(skill.id(), clamped);
        if (clamped > 0 && skill.isActive() && data.loadoutSlotOf(skill.id()) < 0) {
            for (int slot = 0; slot < PlayerData.LOADOUT_SIZE; slot++) {
                if (data.getLoadout(slot) == null) {
                    data.setLoadout(slot, skill.id());
                    break;
                }
            }
        }
        LevelService.recompute(player, data);
        data.markDirty(PlayerData.SYNC_GROWTH | PlayerData.SYNC_SKILLS);
    }

    /** 理由を、画面に出す文にする。 */
    public static Component blockerMessage(Blocker blocker, SkillDef skill, PlayerData data) {
        return switch (blocker) {
            case LEVEL -> Component.translatable(blocker.key(), skill.minLevel());
            case POINTS -> Component.translatable(blocker.key(), skill.rankCost(data.getSkillRank(skill.id()) + 1));
            case REQUIREMENT -> Component.translatable(blocker.key(), requirementText(skill));
            case ULTIMATE_CHOSEN -> {
                SkillDef chosen = chosenUltimate(data, skill);
                yield Component.translatable(blocker.key(), chosen == null ? "" : chosen.name());
            }
            default -> Component.translatable(blocker.key());
        };
    }

    private static String requirementText(SkillDef skill) {
        StringBuilder text = new StringBuilder();
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        for (SkillDef.Requirement requirement : skill.requirements()) {
            SkillDef required = snapshot.skill(requirement.skill());
            if (text.length() > 0) {
                text.append("、");
            }
            text.append(required == null ? requirement.skill().toString() : required.name()).append(" ").append(requirement.rank()).append("段");
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- スキル枠

    /**
     * スキル枠にスキルを入れる（null で空にする）。習得済みのアクティブスキルだけ入れられる。
     */
    public static boolean setLoadout(ServerPlayer player, PlayerData data, int slot, @Nullable ResourceLocation skillId) {
        if (slot < 0 || slot >= PlayerData.LOADOUT_SIZE) {
            return false;
        }
        if (data.getCasting() != null && data.getCasting().slot() == slot) {
            SkillService.cancelCast(player, data, true);
        }
        if (skillId == null) {
            data.setLoadout(slot, null);
            return true;
        }
        SkillDef skill = SkillRegistry.get().skill(skillId);
        if (skill == null || !skill.isActive() || data.getSkillRank(skillId) <= 0 || !skill.job().equals(data.getCombatJob())) {
            tell(player, Component.translatable("message.amemaru.skill.loadout.not_learned"));
            return false;
        }
        data.setLoadout(slot, skillId);
        return true;
    }

    // ---------------------------------------------------------------- やり直し

    /**
     * スキルの習得をすべて戻す（白紙の誓約書・転職）。職業の「最初から使えるスキル」は1段目に戻し、スキル枠に入れ直す。
     */
    public static void resetSkills(ServerPlayer player, PlayerData data) {
        if (data.getCasting() != null) {
            SkillService.cancelCast(player, data, false);
        }
        data.clearSkills();
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        if (job != null) {
            int slot = 0;
            for (ResourceLocation start : job.startingSkills()) {
                SkillDef skill = SkillRegistry.get().skill(start);
                if (skill == null || !skill.job().equals(job.id())) {
                    continue;
                }
                data.setSkillRank(start, 1);
                if (slot < PlayerData.LOADOUT_SIZE) {
                    data.setLoadout(slot++, start);
                }
            }
        }
        LevelService.recompute(player, data);
        data.markDirty(PlayerData.SYNC_GROWTH | PlayerData.SYNC_SKILLS);
    }

    /**
     * レベルを下げたなどで、使ったポイントがもらえる合計を超えていたら、スキルをやり直す。
     *
     * @return やり直したか
     */
    public static boolean validatePoints(ServerPlayer player, PlayerData data) {
        if (availablePoints(data, GrowthRules.current()) >= 0) {
            return false;
        }
        resetSkills(player, data);
        tell(player, Component.translatable("message.amemaru.skill.reset_by_level"));
        return true;
    }

    /** 本人に、アクションバーで伝える。 */
    static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }
}
