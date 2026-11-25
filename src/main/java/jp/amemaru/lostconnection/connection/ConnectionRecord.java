package jp.amemaru.lostconnection.connection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * プレイヤーごとのコネクション量の記録（Phase 0-B 4.1「コネクション：今日の分野別CP（7分野）、累計CP、分野ごとの貢献」）。
 *
 * <ul>
 *   <li>今日（毎日4時に切り替わる日）の分野別CPと、行動ごと・相手ごとの回数（1日の上限・同じ相手の上限）</li>
 *   <li>「5体ごとに1」のような行動の、次の1回分までの数（日をまたいでも残す）</li>
 *   <li>分野別の累計（自分の貢献）</li>
 *   <li>初めて来た進行日（途中参加者かどうか。【補完】オープニングを見る前に初めて数えた人は、その日。前からいた人は1日目）</li>
 *   <li>今日、初めてパーティを組んだ人（はじめましてボーナス。Phase 0-A 15章：1日5人まで）</li>
 * </ul>
 */
public final class ConnectionRecord {
    private final Runnable onChange;
    private String day = "";
    private final int[] today = new int[ConnectionCategory.values().length];
    private final Map<String, Integer> actionsToday = new HashMap<>();
    private final Map<String, Integer> targetsToday = new HashMap<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final long[] lifetime = new long[ConnectionCategory.values().length];
    private final Set<String> capNoticed = new HashSet<>();
    private final Set<UUID> firstMeets = new java.util.LinkedHashSet<>();
    private int joinDay;

    public ConnectionRecord(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        onChange.run();
    }

    /** 日が変わっていたら、今日の分を空にする。変えたら true。 */
    public boolean rollover(String now) {
        if (now.equals(day)) {
            return false;
        }
        day = now;
        Arrays.fill(today, 0);
        actionsToday.clear();
        targetsToday.clear();
        capNoticed.clear();
        firstMeets.clear();
        changed();
        return true;
    }

    /** 今日、初めて組んだ人に足す（1日 limit 人まで）。足せたら true。 */
    public boolean addFirstMeet(UUID who, int limit) {
        if (firstMeets.contains(who) || firstMeets.size() >= limit) {
            return false;
        }
        firstMeets.add(who);
        changed();
        return true;
    }

    /** 今日、初めて組んだ人か（はじめましてボーナスの数に入る人）。 */
    public boolean isFirstMeet(UUID who) {
        return firstMeets.contains(who);
    }

    /** 今日、初めて組んだ人の数。 */
    public int firstMeetCount() {
        return firstMeets.size();
    }

    public String day() {
        return day;
    }

    public int today(ConnectionCategory category) {
        return today[category.ordinal()];
    }

    public int todayTotal() {
        int sum = 0;
        for (int v : today) {
            sum += v;
        }
        return sum;
    }

    public long lifetime(ConnectionCategory category) {
        return lifetime[category.ordinal()];
    }

    public long lifetimeTotal() {
        long sum = 0;
        for (long v : lifetime) {
            sum += v;
        }
        return sum;
    }

    public int actionsToday(ResourceLocation action) {
        return actionsToday.getOrDefault(action.toString(), 0);
    }

    public int targetToday(ResourceLocation action, UUID target) {
        return targetsToday.getOrDefault(action + "|" + target, 0);
    }

    int progress(ResourceLocation action) {
        return progress.getOrDefault(action.toString(), 0);
    }

    void setProgress(ResourceLocation action, int value) {
        if (value <= 0) {
            progress.remove(action.toString());
        } else {
            progress.put(action.toString(), value);
        }
        changed();
    }

    /** 行動を数え、CPを足す（上限はもう確かめてある）。 */
    void record(ResourceLocation action, ConnectionCategory category, int times, UUID target, int cp) {
        actionsToday.merge(action.toString(), times, Integer::sum);
        if (target != null) {
            targetsToday.merge(action + "|" + target, times, Integer::sum);
        }
        today[category.ordinal()] += Math.max(0, cp);
        lifetime[category.ordinal()] += Math.max(0, cp);
        changed();
    }

    /** CPなしで、行動の回数だけ数える（結び札：分野の上限でCPが0でも、贈った枚数は数える）。 */
    public void countOnly(ResourceLocation action, UUID target) {
        actionsToday.merge(action.toString(), 1, Integer::sum);
        if (target != null) {
            targetsToday.merge(action + "|" + target, 1, Integer::sum);
        }
        changed();
    }

    /** 今日、その分野の上限を知らせたか（知らせていなければ、知らせたことにして false）。 */
    boolean noticeCap(String key) {
        if (capNoticed.contains(key)) {
            return true;
        }
        capNoticed.add(key);
        return false;
    }

    public int joinDay() {
        return joinDay;
    }

    public void setJoinDay(int value) {
        joinDay = Math.max(0, value);
        changed();
    }

    // ---------------------------------------------------------------- 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("day", day);
        tag.put("today", new IntArrayTag(today.clone()));
        CompoundTag actions = new CompoundTag();
        actionsToday.forEach(actions::putInt);
        tag.put("actions", actions);
        CompoundTag targets = new CompoundTag();
        targetsToday.forEach(targets::putInt);
        tag.put("targets", targets);
        CompoundTag prog = new CompoundTag();
        progress.forEach(prog::putInt);
        tag.put("progress", prog);
        tag.put("lifetime", new LongArrayTag(lifetime.clone()));
        tag.putInt("joinDay", joinDay);
        net.minecraft.nbt.ListTag meets = new net.minecraft.nbt.ListTag();
        for (UUID uuid : firstMeets) {
            meets.add(net.minecraft.nbt.NbtUtils.createUUID(uuid));
        }
        tag.put("firstMeets", meets);
        return tag;
    }

    public void load(CompoundTag tag) {
        day = tag.getString("day");
        Arrays.fill(today, 0);
        int[] t = tag.getIntArray("today");
        System.arraycopy(t, 0, today, 0, Math.min(t.length, today.length));
        actionsToday.clear();
        CompoundTag actions = tag.getCompound("actions");
        for (String key : actions.getAllKeys()) {
            actionsToday.put(key, actions.getInt(key));
        }
        targetsToday.clear();
        CompoundTag targets = tag.getCompound("targets");
        for (String key : targets.getAllKeys()) {
            targetsToday.put(key, targets.getInt(key));
        }
        progress.clear();
        CompoundTag prog = tag.getCompound("progress");
        for (String key : prog.getAllKeys()) {
            progress.put(key, prog.getInt(key));
        }
        Arrays.fill(lifetime, 0L);
        long[] l = tag.getLongArray("lifetime");
        System.arraycopy(l, 0, lifetime, 0, Math.min(l.length, lifetime.length));
        joinDay = tag.getInt("joinDay");
        capNoticed.clear();
        firstMeets.clear();
        for (net.minecraft.nbt.Tag meet : tag.getList("firstMeets", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            firstMeets.add(net.minecraft.nbt.NbtUtils.loadUUID(meet));
        }
    }

    public void copyFrom(ConnectionRecord other) {
        load(other.save());
    }
}
