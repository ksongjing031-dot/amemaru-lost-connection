package jp.amemaru.lostconnection.job.def;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 職業とスキルの定義を組み合わせ、互いの参照を確かめた「今使える一覧」を作る（サーバー側）。
 *
 * <p>データパックを読み直すたびに作り直す。職業とスキルには、ログイン時にクライアントへ配る短い番号（並べた順の番号）を
 * 振る（Phase 0-B 5章「定義のIDは、ログイン時に配る短い番号で送る」）。参照が切れているスキルは一覧から外し、
 * 管理者に知らせる。</p>
 */
public final class SkillRegistry {
    /** 今わかっている専用ゲージの働き（誓気・集中・気配・気。働きは {@link jp.amemaru.lostconnection.job.GaugeService}）。 */
    public static final Set<String> KNOWN_GAUGES = Set.of("oath", "focus", "presence", "ki");

    private static final DefinitionLoader<JobDef> JOBS = new DefinitionLoader<>("jobs", JobDef::parse);
    private static final DefinitionLoader<SkillDef> SKILLS = new DefinitionLoader<>("skills", SkillDef::parse);

    private static Snapshot snapshot = Snapshot.EMPTY;
    private static int builtJobs = -1;
    private static int builtSkills = -1;
    private static int version;

    private SkillRegistry() {
    }

    public static DefinitionLoader<JobDef> jobLoader() {
        return JOBS;
    }

    public static DefinitionLoader<SkillDef> skillLoader() {
        return SKILLS;
    }

    /** 今の一覧。データパックを読み直していれば、作り直してから返す。 */
    public static synchronized Snapshot get() {
        if (JOBS.generation() != builtJobs || SKILLS.generation() != builtSkills) {
            builtJobs = JOBS.generation();
            builtSkills = SKILLS.generation();
            snapshot = build(JOBS.all(), SKILLS.all(), ++version, true);
        }
        return snapshot;
    }

    /**
     * 定義の組から一覧を作る。
     *
     * @param report 問題をローダーの問題の一覧に足すか（テストでは足さない）
     */
    public static Snapshot build(Map<ResourceLocation, JobDef> jobDefs, Map<ResourceLocation, SkillDef> skillDefs,
                                 int version, boolean report) {
        List<String> problems = new ArrayList<>();

        List<JobDef> jobs = new ArrayList<>(jobDefs.values());
        jobs.sort(Comparator.comparingInt(JobDef::order).thenComparing(job -> job.id().toString()));
        Map<ResourceLocation, JobDef> jobById = new HashMap<>();
        for (JobDef job : jobs) {
            jobById.put(job.id(), job);
            if (job.gauge() != null && !KNOWN_GAUGES.contains(job.gauge().type())) {
                problems.add("jobs/" + job.id() + "：専用ゲージの種類「" + job.gauge().type() + "」はまだありません（使えるもの：" + KNOWN_GAUGES + "）");
            }
            for (JobDef.KitEntry entry : job.starterKit()) {
                if (!ForgeRegistries.ITEMS.containsKey(entry.item())) {
                    problems.add("jobs/" + job.id() + "：見習い装備のアイテム「" + entry.item() + "」がありません");
                }
            }
        }

        // 1回目：職業・流派・状態・音を確かめる
        Map<ResourceLocation, SkillDef> candidates = new LinkedHashMap<>();
        for (SkillDef skill : skillDefs.values()) {
            String where = "skills/" + skill.id() + "：";
            JobDef job = jobById.get(skill.job());
            if (job == null) {
                problems.add(where + "職業「" + skill.job() + "」がありません");
                continue;
            }
            if (job.kind() != JobDef.Kind.COMBAT) {
                problems.add(where + "スキルを持てるのは戦闘職だけです（" + job.id() + " は生産職）");
                continue;
            }
            if (job.schoolIndex(skill.school()) < 0) {
                problems.add(where + "流派「" + skill.school() + "」が職業「" + job.id() + "」にありません");
                continue;
            }
            if (skill.isUltimate() && job.upper() == null) {
                problems.add(where + "奥義ですが、職業「" + job.id() + "」に上位職（upper）がありません");
                continue;
            }
            if (!checkEffects(skill.effects(), where, problems) || !checkEffects(skill.fullChargeEffects(), where, problems)) {
                continue;
            }
            checkSound(skill.sound(), where, problems);
            checkSound(skill.castSound(), where, problems);
            checkSound(skill.impactSound(), where, problems);
            candidates.put(skill.id(), skill);
        }

        // 2回目：前提のスキルを確かめる（外したスキルを前提にしていたスキルも外すため、変わらなくなるまで繰り返す）
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillDef skill : new ArrayList<>(candidates.values())) {
                for (SkillDef.Requirement requirement : skill.requirements()) {
                    SkillDef required = candidates.get(requirement.skill());
                    String problem = null;
                    if (required == null) {
                        problem = "前提のスキル「" + requirement.skill() + "」がありません";
                    } else if (!required.job().equals(skill.job())) {
                        problem = "前提のスキル「" + requirement.skill() + "」は別の職業のスキルです";
                    } else if (requirement.rank() > required.maxRank()) {
                        problem = "前提のスキル「" + requirement.skill() + "」の段階 " + requirement.rank() + " は、最大の段階より大きいです";
                    } else if (required.id().equals(skill.id())) {
                        problem = "自分自身を前提にしています";
                    }
                    if (problem != null) {
                        problems.add("skills/" + skill.id() + "：" + problem);
                        candidates.remove(skill.id());
                        changed = true;
                        break;
                    }
                }
            }
        }

        // 3回目：スキルツリーの同じ場所に2つ置いていないか（奥義は流派ごとに1つ。2つの流派の奥義から1つを選ぶ）
        Map<String, ResourceLocation> places = new HashMap<>();
        List<SkillDef> skills = new ArrayList<>();
        for (SkillDef skill : candidates.values()) {
            String place = skill.job() + "/" + (skill.isUltimate() ? "ultimate/" + skill.school()
                    : skill.school() + "/" + skill.row() + "/" + skill.column());
            ResourceLocation other = places.putIfAbsent(place, skill.id());
            if (other != null) {
                problems.add("skills/" + skill.id() + "：スキルツリーの同じ場所に「" + other + "」があります"
                        + (skill.isUltimate() ? "（奥義は流派ごとに1つです）" : "（流派・row・column を変えてください）"));
                continue;
            }
            skills.add(skill);
        }

        Map<ResourceLocation, Integer> jobOrder = new HashMap<>();
        for (int i = 0; i < jobs.size(); i++) {
            jobOrder.put(jobs.get(i).id(), i);
        }
        skills.sort(Comparator.<SkillDef>comparingInt(skill -> jobOrder.getOrDefault(skill.job(), Integer.MAX_VALUE))
                .thenComparingInt(skill -> skill.isUltimate() ? 1 : 0)
                .thenComparingInt(skill -> jobById.get(skill.job()).schoolIndex(skill.school()))
                .thenComparingInt(SkillDef::row)
                .thenComparingInt(SkillDef::column)
                .thenComparing(skill -> skill.id().toString()));

        Map<ResourceLocation, SkillDef> skillById = new HashMap<>();
        for (SkillDef skill : skills) {
            skillById.put(skill.id(), skill);
        }
        // 最初から使えるスキルを確かめる
        for (JobDef job : jobs) {
            for (ResourceLocation start : job.startingSkills()) {
                SkillDef skill = skillById.get(start);
                if (skill == null) {
                    problems.add("jobs/" + job.id() + "：最初から使えるスキル「" + start + "」がありません");
                } else if (!skill.job().equals(job.id())) {
                    problems.add("jobs/" + job.id() + "：最初から使えるスキル「" + start + "」は別の職業のスキルです");
                } else if (!skill.isActive() || skill.isUltimate() || !skill.requirements().isEmpty() || skill.minLevel() > 1) {
                    problems.add("jobs/" + job.id() + "：最初から使えるスキル「" + start + "」は、前提のない・レベル1から使えるアクティブスキルにしてください");
                }
            }
        }

        if (report) {
            for (String problem : problems) {
                SKILLS.addError(problem);
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Job/skill registry v{}: {} job(s), {} skill(s), {} problem(s)",
                version, jobs.size(), skills.size(), problems.size());
        // 生産職（Phase 3）は、戦闘職の一覧（番号・職業選択の画面）には入れず、別の一覧に置く
        List<JobDef> combat = new ArrayList<>();
        List<JobDef> production = new ArrayList<>();
        for (JobDef job : jobs) {
            (job.kind() == JobDef.Kind.COMBAT ? combat : production).add(job);
            if (job.kind() == JobDef.Kind.PRODUCTION && job.workshop().isEmpty()) {
                problems.add("jobs/" + job.id() + "：生産職には「workshop」（weapon・armor・accessory）が要ります");
            }
        }
        return new Snapshot(combat, production, skills, version, problems);
    }

    private static boolean checkEffects(List<EffectDef> effects, String where, List<String> problems) {
        for (EffectDef effect : effects) {
            if (effect.type() == EffectDef.Type.STATUS) {
                ResourceLocation id = effect.statusEffect();
                if (id == null || !ForgeRegistries.MOB_EFFECTS.containsKey(id)) {
                    problems.add(where + "状態「" + id + "」がありません");
                    return false;
                }
            }
            if (effect.bonusStatus() != null && !ForgeRegistries.MOB_EFFECTS.containsKey(effect.bonusStatus())) {
                problems.add(where + "状態「" + effect.bonusStatus() + "」がありません（bonus_status）");
                return false;
            }
            if (!checkEffects(effect.nested(), where, problems)) {
                return false;
            }
        }
        return true;
    }

    private static void checkSound(@Nullable ResourceLocation sound, String where, List<String> problems) {
        if (sound != null && !ForgeRegistries.SOUND_EVENTS.containsKey(sound)) {
            problems.add(where + "音「" + sound + "」がありません（音は鳴りません）");
        }
    }

    /** 組み合わせ済みの一覧。作った後は変わらない。 */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), 0, List.of());

        private final List<JobDef> jobs;
        private final List<JobDef> productionJobs;
        private final List<SkillDef> skills;
        private final Map<ResourceLocation, Integer> jobIndex = new HashMap<>();
        private final Map<ResourceLocation, Integer> skillIndex = new HashMap<>();
        private final Map<ResourceLocation, List<SkillDef>> byJob = new HashMap<>();
        private final int version;
        private final List<String> problems;

        Snapshot(List<JobDef> jobs, List<JobDef> productionJobs, List<SkillDef> skills, int version, List<String> problems) {
            this.jobs = List.copyOf(jobs);
            this.productionJobs = List.copyOf(productionJobs);
            this.skills = List.copyOf(skills);
            this.version = version;
            this.problems = List.copyOf(problems);
            for (int i = 0; i < this.jobs.size(); i++) {
                jobIndex.put(this.jobs.get(i).id(), i);
            }
            Set<ResourceLocation> seen = new HashSet<>();
            for (int i = 0; i < this.skills.size(); i++) {
                SkillDef skill = this.skills.get(i);
                skillIndex.put(skill.id(), i);
                if (seen.add(skill.job())) {
                    byJob.put(skill.job(), new ArrayList<>());
                }
                byJob.get(skill.job()).add(skill);
            }
            byJob.replaceAll((job, list) -> Collections.unmodifiableList(list));
        }

        /** 作るたびに増える番号（クライアントに配った一覧と、プレイヤーの番号がずれていないかの確かめに使う）。 */
        public int version() {
            return version;
        }

        /** 戦闘職（職業選択の順）。 */
        public List<JobDef> jobs() {
            return jobs;
        }

        /** 生産職（Phase 3。武器職人・防具職人・装飾師）。 */
        public List<JobDef> productionJobs() {
            return productionJobs;
        }

        /** 生産職を探す（なければ null）。 */
        @Nullable
        public JobDef productionJob(@Nullable ResourceLocation id) {
            if (id == null) {
                return null;
            }
            for (JobDef job : productionJobs) {
                if (job.id().equals(id)) {
                    return job;
                }
            }
            return null;
        }

        /** その工房（weapon・armor・accessory）の生産職（なければ null）。 */
        @Nullable
        public JobDef productionJobFor(String workshop) {
            for (JobDef job : productionJobs) {
                if (job.workshop().equals(workshop)) {
                    return job;
                }
            }
            return null;
        }

        public List<SkillDef> skills() {
            return skills;
        }

        /** 組み合わせで見つかった問題。 */
        public List<String> problems() {
            return problems;
        }

        @Nullable
        public JobDef job(@Nullable ResourceLocation id) {
            Integer index = id == null ? null : jobIndex.get(id);
            return index == null ? null : jobs.get(index);
        }

        @Nullable
        public SkillDef skill(@Nullable ResourceLocation id) {
            Integer index = id == null ? null : skillIndex.get(id);
            return index == null ? null : skills.get(index);
        }

        /** 職業の番号（ないときは -1）。 */
        public int jobIndex(@Nullable ResourceLocation id) {
            Integer index = id == null ? null : jobIndex.get(id);
            return index == null ? -1 : index;
        }

        /** スキルの番号（ないときは -1）。 */
        public int skillIndex(@Nullable ResourceLocation id) {
            Integer index = id == null ? null : skillIndex.get(id);
            return index == null ? -1 : index;
        }

        @Nullable
        public JobDef jobAt(int index) {
            return index >= 0 && index < jobs.size() ? jobs.get(index) : null;
        }

        @Nullable
        public SkillDef skillAt(int index) {
            return index >= 0 && index < skills.size() ? skills.get(index) : null;
        }

        /** その職業のスキル（スキルツリーの順）。 */
        public List<SkillDef> skillsOf(ResourceLocation job) {
            return byJob.getOrDefault(job, List.of());
        }
    }
}
