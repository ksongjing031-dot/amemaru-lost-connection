package jp.amemaru.lostconnection.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * プレイヤーごとの物語（Phase 0-B 4.1「物語：会話の既読、個人クエストの進み、見つけた古代記録、NPCごとの親密度」のうち Phase 5 の分）。
 *
 * <ul>
 *   <li>本人だけの物語フラグ（目覚めた・名簿に名前を書いた など）</li>
 *   <li>オープニングの進み（0：まだ、1：目覚めた（演出を見た）、2：リリアに名前を伝えた＝終わり）</li>
 *   <li>見たストーリーイベント（大結晶の「記憶の再生」で見返せる）と、読んだ会話（既読のみスキップ）</li>
 *   <li>起動した石柱（階ごと。番号のビット）・行ったことのある階・倒した階層ボスの回数</li>
 *   <li>はじめの手引き（目覚めてから1階のボスまでの道しるべ）の今の段</li>
 * </ul>
 */
public final class PlayerStory {
    /** オープニング：まだ。 */
    public static final int OPENING_NONE = 0;
    /** オープニング：目覚めた（演出を見た）。 */
    public static final int OPENING_AWAKENED = 1;
    /** オープニング：リリアに名前を伝えた（終わり）。 */
    public static final int OPENING_DONE = 2;

    private final Runnable onChange;
    private final Set<ResourceLocation> flags = new LinkedHashSet<>();
    private int opening;
    private final Set<ResourceLocation> seenEvents = new LinkedHashSet<>();
    private final Set<ResourceLocation> readDialogues = new LinkedHashSet<>();
    private final Map<Integer, Integer> pillars = new TreeMap<>();
    private final Set<Integer> floorsVisited = new TreeSet<>();
    private final Map<Integer, Integer> bossKills = new TreeMap<>();
    private int guide;

    public PlayerStory(Runnable onChange) {
        this.onChange = onChange;
    }

    // ---------------------------------------------------------------- フラグ

    public boolean has(ResourceLocation flag) {
        return flags.contains(flag);
    }

    public boolean add(ResourceLocation flag) {
        boolean added = flags.add(flag);
        if (added) {
            onChange.run();
        }
        return added;
    }

    public boolean remove(ResourceLocation flag) {
        boolean removed = flags.remove(flag);
        if (removed) {
            onChange.run();
        }
        return removed;
    }

    public Set<ResourceLocation> flags() {
        return Collections.unmodifiableSet(flags);
    }

    // ---------------------------------------------------------------- オープニング

    public int opening() {
        return opening;
    }

    public void setOpening(int stage) {
        int value = Math.max(OPENING_NONE, Math.min(OPENING_DONE, stage));
        if (value != opening) {
            opening = value;
            onChange.run();
        }
    }

    // ---------------------------------------------------------------- 見たイベント・読んだ会話

    public boolean seen(ResourceLocation event) {
        return seenEvents.contains(event);
    }

    public void markSeen(ResourceLocation event) {
        if (seenEvents.add(event)) {
            onChange.run();
        }
    }

    public Set<ResourceLocation> seenEvents() {
        return Collections.unmodifiableSet(seenEvents);
    }

    public boolean read(ResourceLocation dialogue) {
        return readDialogues.contains(dialogue);
    }

    public void markRead(ResourceLocation dialogue) {
        if (readDialogues.add(dialogue)) {
            onChange.run();
        }
    }

    // ---------------------------------------------------------------- 石柱・階・ボス

    /** その階で起動した石柱（番号のビット）。 */
    public int pillars(int floor) {
        return pillars.getOrDefault(floor, 0);
    }

    /** 石柱を起動した印をつける（もう起動していれば false）。 */
    public boolean activatePillar(int floor, int index) {
        int before = pillars(floor);
        int after = before | (1 << index);
        if (after == before) {
            return false;
        }
        pillars.put(floor, after);
        onChange.run();
        return true;
    }

    public void resetPillars(int floor) {
        if (pillars.remove(floor) != null) {
            onChange.run();
        }
    }

    public Map<Integer, Integer> allPillars() {
        return Collections.unmodifiableMap(pillars);
    }

    public boolean visited(int floor) {
        return floorsVisited.contains(floor);
    }

    public boolean markVisited(int floor) {
        boolean added = floorsVisited.add(floor);
        if (added) {
            onChange.run();
        }
        return added;
    }

    public int bossKills(int floor) {
        return bossKills.getOrDefault(floor, 0);
    }

    public void addBossKill(int floor) {
        bossKills.merge(floor, 1, Integer::sum);
        onChange.run();
    }

    // ---------------------------------------------------------------- 手引き

    public int guide() {
        return guide;
    }

    public void setGuide(int step) {
        if (step != guide) {
            guide = Math.max(0, step);
            onChange.run();
        }
    }

    // ---------------------------------------------------------------- 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag flagList = new ListTag();
        for (ResourceLocation id : flags) {
            flagList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("flags", flagList);
        tag.putInt("opening", opening);
        tag.put("seen", ids(seenEvents));
        tag.put("read", ids(readDialogues));
        CompoundTag pillarTag = new CompoundTag();
        for (Map.Entry<Integer, Integer> entry : pillars.entrySet()) {
            pillarTag.putInt(Integer.toString(entry.getKey()), entry.getValue());
        }
        tag.put("pillars", pillarTag);
        ListTag visited = new ListTag();
        for (int floor : floorsVisited) {
            visited.add(IntTag.valueOf(floor));
        }
        tag.put("visited", visited);
        CompoundTag kills = new CompoundTag();
        for (Map.Entry<Integer, Integer> entry : bossKills.entrySet()) {
            kills.putInt(Integer.toString(entry.getKey()), entry.getValue());
        }
        tag.put("bossKills", kills);
        tag.putInt("guide", guide);
        return tag;
    }

    public void load(CompoundTag tag) {
        flags.clear();
        for (Tag t : tag.getList("flags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                flags.add(id);
            }
        }
        opening = Math.max(OPENING_NONE, Math.min(OPENING_DONE, tag.getInt("opening")));
        readIds(tag.getList("seen", Tag.TAG_STRING), seenEvents);
        readIds(tag.getList("read", Tag.TAG_STRING), readDialogues);
        pillars.clear();
        CompoundTag pillarTag = tag.getCompound("pillars");
        for (String key : pillarTag.getAllKeys()) {
            try {
                pillars.put(Integer.parseInt(key), pillarTag.getInt(key));
            } catch (NumberFormatException ignored) {
                // 読めない記録は飛ばす
            }
        }
        floorsVisited.clear();
        for (Tag t : tag.getList("visited", Tag.TAG_INT)) {
            floorsVisited.add(((IntTag) t).getAsInt());
        }
        bossKills.clear();
        CompoundTag kills = tag.getCompound("bossKills");
        for (String key : kills.getAllKeys()) {
            try {
                bossKills.put(Integer.parseInt(key), kills.getInt(key));
            } catch (NumberFormatException ignored) {
                // 読めない記録は飛ばす
            }
        }
        guide = Math.max(0, tag.getInt("guide"));
    }

    public void copyFrom(PlayerStory other) {
        load(other.save());
    }

    private static ListTag ids(Set<ResourceLocation> set) {
        ListTag list = new ListTag();
        for (ResourceLocation id : set) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static void readIds(ListTag list, Set<ResourceLocation> into) {
        into.clear();
        for (Tag t : list) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                into.add(id);
            }
        }
    }
}
