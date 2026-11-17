package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.job.SkillDescriber;
import jp.amemaru.lostconnection.job.def.Affects;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 職業とスキルの定義を、クライアントに配る（ログイン時と /reload の後）。
 *
 * <p>定義の本体はサーバーのデータパックにあるため、画面に要る分（名前・説明・アイコン・数値・説明文）だけを送る。
 * 職業とスキルには、サーバーで振った番号をつける（ほかのパケットはこの番号で送る）。</p>
 */
public final class SyncDefinitionsS2C {
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_LINES = 32;

    /** 流派。 */
    public record SchoolInfo(String id, String name, String description, int color) {
    }

    /** 職業の、画面に要る分。 */
    public record JobInfo(int index, ResourceLocation id, String name, String role, String description, int color,
                          ResourceLocation icon, String weaponText, boolean hasGauge, String gaugeName, String gaugeDescription,
                          int gaugeMax, int gaugeColor, List<SchoolInfo> schools, boolean hasUpper, String upperName,
                          String upperDescription, int upperMinLevel, int upperMinDay, int[] startingSkills,
                          List<ResourceLocation> kitItems, double[] statPercent) {
    }

    /** スキルの、画面に要る分。数値は段階ごとの配列（0番目が1段目）。 */
    public record SkillInfo(int index, ResourceLocation id, int jobIndex, int schoolIndex, int row, int column, boolean passive,
                            boolean ultimate, String name, String description, ResourceLocation icon, int maxRank, int[] rankCosts,
                            int upperRank, int minLevel, int[] requiredSkills, int[] requiredRanks, int link, int delivery,
                            boolean alliesOnly, int[] mpCost, int[] gaugeCost, float[] cooldown, float[] cast, float[] aimRange,
                            float[] radius, String animation, String castAnimation, String fx, List<List<String>> details,
                            float chargeTime, String chargeAnimation, float[] angle, int[] count, int[] countCharged) {
        /** 溜めるスキルか。 */
        public boolean charged() {
            return chargeTime > 0;
        }
    }

    private final int version;
    private final List<JobInfo> jobs;
    private final List<SkillInfo> skills;

    public SyncDefinitionsS2C(int version, List<JobInfo> jobs, List<SkillInfo> skills) {
        this.version = version;
        this.jobs = jobs;
        this.skills = skills;
    }

    /** サーバーで、今の一覧から作る。 */
    public static SyncDefinitionsS2C of(SkillRegistry.Snapshot snapshot) {
        List<JobInfo> jobs = new ArrayList<>();
        for (int i = 0; i < snapshot.jobs().size(); i++) {
            JobDef job = snapshot.jobs().get(i);
            List<SchoolInfo> schools = new ArrayList<>();
            for (JobDef.SchoolDef school : job.schools()) {
                schools.add(new SchoolInfo(school.id(), school.name(), school.description(), school.color()));
            }
            int[] starting = job.startingSkills().stream().mapToInt(snapshot::skillIndex).filter(index -> index >= 0).toArray();
            List<ResourceLocation> kit = new ArrayList<>();
            for (JobDef.KitEntry entry : job.starterKit()) {
                kit.add(entry.item());
            }
            double[] percent = new double[StatType.COUNT];
            for (StatType type : StatType.all()) {
                percent[type.ordinal()] = job.statPercent(type);
            }
            JobDef.GaugeDef gauge = job.gauge();
            JobDef.UpperDef upper = job.upper();
            jobs.add(new JobInfo(i, job.id(), job.name(), job.role(), job.description(), job.color(), job.icon(), job.weaponText(),
                    gauge != null, gauge == null ? "" : gauge.name(), gauge == null ? "" : gauge.description(),
                    gauge == null ? 0 : gauge.max(), gauge == null ? 0 : gauge.color(), schools,
                    upper != null, upper == null ? "" : upper.name(), upper == null ? "" : upper.description(),
                    upper == null ? 0 : upper.minLevel(), upper == null ? 0 : upper.minDay(), starting, kit, percent));
        }
        List<SkillInfo> skills = new ArrayList<>();
        for (int i = 0; i < snapshot.skills().size(); i++) {
            SkillDef skill = snapshot.skills().get(i);
            JobDef job = snapshot.job(skill.job());
            int max = skill.maxRank();
            int[] costs = new int[max];
            int[] mp = new int[max];
            int[] gauge = new int[max];
            float[] cooldown = new float[max];
            float[] cast = new float[max];
            float[] aim = new float[max];
            float[] radius = new float[max];
            float[] angle = new float[max];
            int[] count = new int[max];
            int[] countCharged = new int[max];
            List<List<String>> details = new ArrayList<>();
            for (int r = 1; r <= max; r++) {
                costs[r - 1] = skill.rankCost(r);
                mp[r - 1] = skill.mpCost(r);
                gauge[r - 1] = skill.gaugeCost(r);
                cooldown[r - 1] = (float) skill.cooldown(r);
                cast[r - 1] = (float) skill.cast(r);
                aim[r - 1] = (float) skill.delivery().aimRange(r);
                radius[r - 1] = (float) skill.delivery().radius(r);
                angle[r - 1] = (float) skill.delivery().angle(r);
                count[r - 1] = skill.delivery().count(r);
                countCharged[r - 1] = skill.delivery().countCharged(r);
                details.add(SkillDescriber.describe(skill, job, r));
            }
            int[] reqSkills = new int[skill.requirements().size()];
            int[] reqRanks = new int[reqSkills.length];
            for (int j = 0; j < reqSkills.length; j++) {
                reqSkills[j] = snapshot.skillIndex(skill.requirements().get(j).skill());
                reqRanks[j] = skill.requirements().get(j).rank();
            }
            int school = skill.isUltimate() || job == null ? -1 : job.schoolIndex(skill.school());
            skills.add(new SkillInfo(i, skill.id(), snapshot.jobIndex(skill.job()), school, skill.row(), skill.column(),
                    !skill.isActive(), skill.isUltimate(), skill.name(), skill.description(), skill.icon(), max, costs,
                    skill.upperRank(), skill.minLevel(), reqSkills, reqRanks, skill.link().ordinal(),
                    skill.delivery().type().ordinal(), skill.delivery().affects() == Affects.ALLIES, mp, gauge, cooldown, cast,
                    aim, radius, skill.animation(), skill.castAnimation(), skill.fx(), details, (float) skill.chargeTime(),
                    skill.chargeAnimation(), angle, count, countCharged));
        }
        return new SyncDefinitionsS2C(snapshot.version(), jobs, skills);
    }

    // ---------------------------------------------------------------- 書き出し・読み込み

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeVarInt(jobs.size());
        for (JobInfo job : jobs) {
            buf.writeVarInt(job.index());
            buf.writeResourceLocation(job.id());
            buf.writeUtf(job.name());
            buf.writeUtf(job.role());
            buf.writeUtf(job.description());
            buf.writeInt(job.color());
            buf.writeResourceLocation(job.icon());
            buf.writeUtf(job.weaponText());
            buf.writeBoolean(job.hasGauge());
            buf.writeUtf(job.gaugeName());
            buf.writeUtf(job.gaugeDescription());
            buf.writeVarInt(job.gaugeMax());
            buf.writeInt(job.gaugeColor());
            buf.writeVarInt(job.schools().size());
            for (SchoolInfo school : job.schools()) {
                buf.writeUtf(school.id());
                buf.writeUtf(school.name());
                buf.writeUtf(school.description());
                buf.writeInt(school.color());
            }
            buf.writeBoolean(job.hasUpper());
            buf.writeUtf(job.upperName());
            buf.writeUtf(job.upperDescription());
            buf.writeVarInt(job.upperMinLevel());
            buf.writeVarInt(job.upperMinDay());
            buf.writeVarIntArray(job.startingSkills());
            buf.writeVarInt(job.kitItems().size());
            for (ResourceLocation item : job.kitItems()) {
                buf.writeResourceLocation(item);
            }
            buf.writeVarInt(job.statPercent().length);
            for (double value : job.statPercent()) {
                buf.writeDouble(value);
            }
        }
        buf.writeVarInt(skills.size());
        for (SkillInfo skill : skills) {
            buf.writeVarInt(skill.index());
            buf.writeResourceLocation(skill.id());
            buf.writeVarInt(skill.jobIndex());
            buf.writeVarInt(skill.schoolIndex() + 1);
            buf.writeVarInt(skill.row());
            buf.writeVarInt(skill.column());
            buf.writeBoolean(skill.passive());
            buf.writeBoolean(skill.ultimate());
            buf.writeUtf(skill.name());
            buf.writeUtf(skill.description());
            buf.writeResourceLocation(skill.icon());
            buf.writeVarInt(skill.maxRank());
            buf.writeVarIntArray(skill.rankCosts());
            buf.writeVarInt(skill.upperRank());
            buf.writeVarInt(skill.minLevel());
            buf.writeVarInt(skill.requiredSkills().length);
            for (int j = 0; j < skill.requiredSkills().length; j++) {
                buf.writeVarInt(skill.requiredSkills()[j] + 1);
                buf.writeVarInt(skill.requiredRanks()[j]);
            }
            buf.writeVarInt(skill.link());
            buf.writeVarInt(skill.delivery());
            buf.writeBoolean(skill.alliesOnly());
            buf.writeVarIntArray(skill.mpCost());
            buf.writeVarIntArray(skill.gaugeCost());
            writeFloats(buf, skill.cooldown());
            writeFloats(buf, skill.cast());
            writeFloats(buf, skill.aimRange());
            writeFloats(buf, skill.radius());
            buf.writeUtf(skill.animation());
            buf.writeUtf(skill.castAnimation());
            buf.writeUtf(skill.fx());
            buf.writeVarInt(skill.details().size());
            for (List<String> lines : skill.details()) {
                buf.writeVarInt(lines.size());
                for (String line : lines) {
                    buf.writeUtf(line);
                }
            }
            buf.writeFloat(skill.chargeTime());
            buf.writeUtf(skill.chargeAnimation());
            writeFloats(buf, skill.angle());
            buf.writeVarIntArray(skill.count());
            buf.writeVarIntArray(skill.countCharged());
        }
    }

    public static SyncDefinitionsS2C decode(FriendlyByteBuf buf) {
        int version = buf.readVarInt();
        int jobCount = check(buf.readVarInt(), MAX_ENTRIES);
        List<JobInfo> jobs = new ArrayList<>(jobCount);
        for (int i = 0; i < jobCount; i++) {
            int index = buf.readVarInt();
            ResourceLocation id = buf.readResourceLocation();
            String name = buf.readUtf();
            String role = buf.readUtf();
            String description = buf.readUtf();
            int color = buf.readInt();
            ResourceLocation icon = buf.readResourceLocation();
            String weaponText = buf.readUtf();
            boolean hasGauge = buf.readBoolean();
            String gaugeName = buf.readUtf();
            String gaugeDescription = buf.readUtf();
            int gaugeMax = buf.readVarInt();
            int gaugeColor = buf.readInt();
            int schoolCount = check(buf.readVarInt(), 8);
            List<SchoolInfo> schools = new ArrayList<>(schoolCount);
            for (int s = 0; s < schoolCount; s++) {
                schools.add(new SchoolInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt()));
            }
            boolean hasUpper = buf.readBoolean();
            String upperName = buf.readUtf();
            String upperDescription = buf.readUtf();
            int upperMinLevel = buf.readVarInt();
            int upperMinDay = buf.readVarInt();
            int[] starting = buf.readVarIntArray(16);
            int kitCount = check(buf.readVarInt(), 32);
            List<ResourceLocation> kit = new ArrayList<>(kitCount);
            for (int k = 0; k < kitCount; k++) {
                kit.add(buf.readResourceLocation());
            }
            int statCount = check(buf.readVarInt(), 64);
            double[] percent = new double[statCount];
            for (int k = 0; k < statCount; k++) {
                percent[k] = buf.readDouble();
            }
            jobs.add(new JobInfo(index, id, name, role, description, color, icon, weaponText, hasGauge, gaugeName,
                    gaugeDescription, gaugeMax, gaugeColor, schools, hasUpper, upperName, upperDescription, upperMinLevel,
                    upperMinDay, starting, kit, percent));
        }
        int skillCount = check(buf.readVarInt(), MAX_ENTRIES);
        List<SkillInfo> skills = new ArrayList<>(skillCount);
        for (int i = 0; i < skillCount; i++) {
            int index = buf.readVarInt();
            ResourceLocation id = buf.readResourceLocation();
            int jobIndex = buf.readVarInt();
            int schoolIndex = buf.readVarInt() - 1;
            int row = buf.readVarInt();
            int column = buf.readVarInt();
            boolean passive = buf.readBoolean();
            boolean ultimate = buf.readBoolean();
            String name = buf.readUtf();
            String description = buf.readUtf();
            ResourceLocation icon = buf.readResourceLocation();
            int maxRank = check(buf.readVarInt(), 16);
            int[] costs = buf.readVarIntArray(16);
            int upperRank = buf.readVarInt();
            int minLevel = buf.readVarInt();
            int reqCount = check(buf.readVarInt(), 16);
            int[] reqSkills = new int[reqCount];
            int[] reqRanks = new int[reqCount];
            for (int j = 0; j < reqCount; j++) {
                reqSkills[j] = buf.readVarInt() - 1;
                reqRanks[j] = buf.readVarInt();
            }
            int link = buf.readVarInt();
            int delivery = buf.readVarInt();
            boolean alliesOnly = buf.readBoolean();
            int[] mp = buf.readVarIntArray(16);
            int[] gauge = buf.readVarIntArray(16);
            float[] cooldown = readFloats(buf);
            float[] cast = readFloats(buf);
            float[] aim = readFloats(buf);
            float[] radius = readFloats(buf);
            String animation = buf.readUtf();
            String castAnimation = buf.readUtf();
            String fx = buf.readUtf();
            int detailCount = check(buf.readVarInt(), 16);
            List<List<String>> details = new ArrayList<>(detailCount);
            for (int d = 0; d < detailCount; d++) {
                int lineCount = check(buf.readVarInt(), MAX_LINES);
                List<String> lines = new ArrayList<>(lineCount);
                for (int l = 0; l < lineCount; l++) {
                    lines.add(buf.readUtf());
                }
                details.add(lines);
            }
            float chargeTime = buf.readFloat();
            String chargeAnimation = buf.readUtf();
            float[] angle = readFloats(buf);
            int[] count = buf.readVarIntArray(16);
            int[] countCharged = buf.readVarIntArray(16);
            skills.add(new SkillInfo(index, id, jobIndex, schoolIndex, row, column, passive, ultimate, name, description, icon,
                    maxRank, costs, upperRank, minLevel, reqSkills, reqRanks, link, delivery, alliesOnly, mp, gauge, cooldown, cast,
                    aim, radius, animation, castAnimation, fx, details, chargeTime, chargeAnimation, angle, count, countCharged));
        }
        return new SyncDefinitionsS2C(version, jobs, skills);
    }

    private static void writeFloats(FriendlyByteBuf buf, float[] values) {
        buf.writeVarInt(values.length);
        for (float value : values) {
            buf.writeFloat(value);
        }
    }

    private static float[] readFloats(FriendlyByteBuf buf) {
        int count = check(buf.readVarInt(), 16);
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static int check(int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Bad length in SyncDefinitionsS2C: " + value);
        }
        return value;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleDefinitions(this));
    }

    public int version() {
        return version;
    }

    public List<JobInfo> jobs() {
        return jobs;
    }

    public List<SkillInfo> skills() {
        return skills;
    }
}
