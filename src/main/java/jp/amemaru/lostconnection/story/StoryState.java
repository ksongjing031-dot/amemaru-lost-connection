package jp.amemaru.lostconnection.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * サーバー全体の物語（Phase 0-B 4.2 {@code StoryState}）：物語フラグ、起きたストーリーイベントとその日時。
 * ワールドの data フォルダの {@code amemaru_story.dat}。物語のフラグは、サーバーの記録だけを正本にする（Phase 0-B 4章）。
 *
 * <p>目覚める場所（オープニングで目覚める大結晶のそば）も、管理者が決めたときはここに持つ。</p>
 */
public final class StoryState extends SavedData {
    private static final String NAME = "amemaru_story";

    private final Set<ResourceLocation> flags = new LinkedHashSet<>();
    private final Map<ResourceLocation, Long> happened = new LinkedHashMap<>();
    @Nullable
    private Vec3 wakePos;
    private float wakeYaw;

    public static StoryState load(CompoundTag tag) {
        StoryState state = new StoryState();
        for (Tag t : tag.getList("flags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                state.flags.add(id);
            }
        }
        CompoundTag events = tag.getCompound("events");
        for (String key : events.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                state.happened.put(id, events.getLong(key));
            }
        }
        if (tag.contains("wake")) {
            CompoundTag wake = tag.getCompound("wake");
            state.wakePos = new Vec3(wake.getDouble("x"), wake.getDouble("y"), wake.getDouble("z"));
            state.wakeYaw = wake.getFloat("yaw");
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        ListTag list = new ListTag();
        for (ResourceLocation id : flags) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("flags", list);
        CompoundTag events = new CompoundTag();
        for (Map.Entry<ResourceLocation, Long> entry : happened.entrySet()) {
            events.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("events", events);
        if (wakePos != null) {
            CompoundTag wake = new CompoundTag();
            wake.putDouble("x", wakePos.x);
            wake.putDouble("y", wakePos.y);
            wake.putDouble("z", wakePos.z);
            wake.putFloat("yaw", wakeYaw);
            tag.put("wake", wake);
        }
        return tag;
    }

    public static StoryState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StoryState::load, StoryState::new, NAME);
    }

    // ---------------------------------------------------------------- フラグ

    public boolean has(ResourceLocation flag) {
        return flags.contains(flag);
    }

    /** 立てる（もう立っていれば false）。 */
    public boolean add(ResourceLocation flag) {
        boolean added = flags.add(flag);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean remove(ResourceLocation flag) {
        boolean removed = flags.remove(flag);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Set<ResourceLocation> flags() {
        return Collections.unmodifiableSet(flags);
    }

    // ---------------------------------------------------------------- 起きたイベント

    public boolean happened(ResourceLocation event) {
        return happened.containsKey(event);
    }

    public void markHappened(ResourceLocation event) {
        if (!happened.containsKey(event)) {
            happened.put(event, System.currentTimeMillis());
            setDirty();
        }
    }

    public boolean forget(ResourceLocation event) {
        boolean removed = happened.remove(event) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Map<ResourceLocation, Long> happenedEvents() {
        return Collections.unmodifiableMap(happened);
    }

    // ---------------------------------------------------------------- 目覚める場所

    @Nullable
    public Vec3 wakePos() {
        return wakePos;
    }

    public float wakeYaw() {
        return wakeYaw;
    }

    public void setWake(@Nullable Vec3 pos, float yaw) {
        wakePos = pos;
        wakeYaw = yaw;
        setDirty();
    }
}
