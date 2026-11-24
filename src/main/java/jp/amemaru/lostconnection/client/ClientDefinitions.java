package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * サーバーから受け取った、職業とスキルの定義（クライアント専用）。番号はサーバーで振ったもの。
 */
public final class ClientDefinitions {
    private static final ClientDefinitions INSTANCE = new ClientDefinitions();

    private int version = -1;
    private List<SyncDefinitionsS2C.JobInfo> jobs = List.of();
    private List<SyncDefinitionsS2C.SkillInfo> skills = List.of();
    private List<SyncDefinitionsS2C.TreeInfo> trees = List.of();

    private ClientDefinitions() {
    }

    public static ClientDefinitions get() {
        return INSTANCE;
    }

    public void apply(SyncDefinitionsS2C message) {
        version = message.version();
        jobs = List.copyOf(message.jobs());
        skills = List.copyOf(message.skills());
        trees = List.copyOf(message.trees());
    }

    public void reset() {
        version = -1;
        jobs = List.of();
        skills = List.of();
        trees = List.of();
    }

    /** その職業のスキルツリー（11月24日。なければ null）。 */
    @Nullable
    public SyncDefinitionsS2C.TreeInfo tree(int jobIndex) {
        for (SyncDefinitionsS2C.TreeInfo tree : trees) {
            if (tree.jobIndex() == jobIndex) {
                return tree;
            }
        }
        return null;
    }

    public boolean received() {
        return version >= 0;
    }

    public int version() {
        return version;
    }

    public List<SyncDefinitionsS2C.JobInfo> jobs() {
        return jobs;
    }

    @Nullable
    public SyncDefinitionsS2C.JobInfo job(int index) {
        return index >= 0 && index < jobs.size() ? jobs.get(index) : null;
    }

    @Nullable
    public SyncDefinitionsS2C.SkillInfo skill(int index) {
        return index >= 0 && index < skills.size() ? skills.get(index) : null;
    }

    /** その職業のスキル（スキルツリーの順）。 */
    public List<SyncDefinitionsS2C.SkillInfo> skillsOf(int jobIndex) {
        List<SyncDefinitionsS2C.SkillInfo> list = new ArrayList<>();
        for (SyncDefinitionsS2C.SkillInfo skill : skills) {
            if (skill.jobIndex() == jobIndex) {
                list.add(skill);
            }
        }
        return list;
    }
}
