package jp.amemaru.lostconnection.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 街（Phase 0-B 4.2「TownData：施設の状態、共同事業ごとの寄付額と寄付者」。銘板とスタッフロールに使う。Phase 7 その4）。
 * 共同事業ごとに、寄付の合計・寄付した人（名前と額）・完成した日（進行日とゲームの日）・施設が変わったか。
 */
public final class TownData extends SavedData {
    private static final String NAME = "amemaru_town";

    /** 寄付した人（銘板の一行）。 */
    public record Donor(UUID who, String name, long amount, int firstDay) {
    }

    /** 共同事業ひとつの状態。 */
    public static final class Project {
        long donated;
        final Map<UUID, Donor> donors = new LinkedHashMap<>();
        /** 完成した進行日（0 ＝ まだ）。 */
        int completedDay;
        /** 完成したときのゲームの日（ワールドの時刻 ÷ 24000）。次の朝に施設が変わる。 */
        long completedGameDay = -1;
        /** 施設が変わった（物語の印・育ったあとの形）。 */
        boolean built;
        int builtDay;

        public long donated() {
            return donated;
        }

        public boolean completed() {
            return completedDay > 0;
        }

        public int completedDay() {
            return completedDay;
        }

        public boolean built() {
            return built;
        }

        public int builtDay() {
            return builtDay;
        }

        /** 銘板の並び（額の多い順。同じなら先に寄付した順）。 */
        public List<Donor> plate() {
            List<Donor> list = new ArrayList<>(donors.values());
            list.sort(Comparator.comparingLong(Donor::amount).reversed());
            return list;
        }

        public long donatedBy(UUID who) {
            Donor d = donors.get(who);
            return d == null ? 0 : d.amount();
        }
    }

    private final Map<String, Project> projects = new LinkedHashMap<>();

    public static TownData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TownData::load, TownData::new, NAME);
    }

    public Project project(ResourceLocation id) {
        return projects.computeIfAbsent(id.toString(), k -> new Project());
    }

    void donate(ResourceLocation id, UUID who, String name, long amount, int day) {
        Project p = project(id);
        p.donated += amount;
        Donor before = p.donors.get(who);
        p.donors.put(who, new Donor(who, name, (before == null ? 0 : before.amount()) + amount, before == null ? day : before.firstDay()));
        setDirty();
    }

    void complete(ResourceLocation id, int day, long gameDay) {
        Project p = project(id);
        p.completedDay = Math.max(1, day);
        p.completedGameDay = gameDay;
        setDirty();
    }

    void build(ResourceLocation id, int day) {
        Project p = project(id);
        p.built = true;
        p.builtDay = day;
        setDirty();
    }

    void reset(ResourceLocation id) {
        projects.remove(id.toString());
        setDirty();
    }

    /** すべての共同事業の寄付した人（スタッフロール。同じ人は合計）。 */
    public List<Donor> allDonors() {
        Map<UUID, Donor> sum = new LinkedHashMap<>();
        for (Project p : projects.values()) {
            for (Donor d : p.donors.values()) {
                Donor s = sum.get(d.who());
                sum.put(d.who(), new Donor(d.who(), d.name(), (s == null ? 0 : s.amount()) + d.amount(),
                        s == null ? d.firstDay() : Math.min(s.firstDay(), d.firstDay())));
            }
        }
        List<Donor> list = new ArrayList<>(sum.values());
        list.sort(Comparator.comparingLong(Donor::amount).reversed());
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        projects.forEach((id, p) -> {
            CompoundTag one = new CompoundTag();
            one.putLong("donated", p.donated);
            one.putInt("completedDay", p.completedDay);
            one.putLong("completedGameDay", p.completedGameDay);
            one.putBoolean("built", p.built);
            one.putInt("builtDay", p.builtDay);
            ListTag donors = new ListTag();
            for (Donor d : p.donors.values()) {
                CompoundTag dt = new CompoundTag();
                dt.putUUID("who", d.who());
                dt.putString("name", d.name());
                dt.putLong("amount", d.amount());
                dt.putInt("day", d.firstDay());
                donors.add(dt);
            }
            one.put("donors", donors);
            all.put(id, one);
        });
        tag.put("projects", all);
        return tag;
    }

    static TownData load(CompoundTag tag) {
        TownData data = new TownData();
        CompoundTag all = tag.getCompound("projects");
        for (String id : all.getAllKeys()) {
            CompoundTag one = all.getCompound(id);
            Project p = new Project();
            p.donated = one.getLong("donated");
            p.completedDay = one.getInt("completedDay");
            p.completedGameDay = one.contains("completedGameDay") ? one.getLong("completedGameDay") : -1;
            p.built = one.getBoolean("built");
            p.builtDay = one.getInt("builtDay");
            for (Tag t : one.getList("donors", Tag.TAG_COMPOUND)) {
                CompoundTag dt = (CompoundTag) t;
                if (dt.hasUUID("who")) {
                    p.donors.put(dt.getUUID("who"), new Donor(dt.getUUID("who"), dt.getString("name"), dt.getLong("amount"), dt.getInt("day")));
                }
            }
            data.projects.put(id, p);
        }
        return data;
    }
}
