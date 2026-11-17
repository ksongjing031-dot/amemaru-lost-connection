package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 本人の「冒険の記録」を送る（Phase 3）：サーバーの進行日と章、結晶果、生産職と熟練度、図鑑、アクセサリーの表示。
 *
 * <p>どれも小さいので、変わったら全体を送る。図鑑のIDは文字列で送る（敵とドロップの種類は数十なので軽い）。</p>
 */
public final class SyncAdventureS2C {
    private static final int MAX_ENTRIES = 1024;

    /** 図鑑の1つの敵。 */
    public record Bestiary(ResourceLocation enemy, int kills, List<ResourceLocation> drops) {
    }

    private final int day;
    private final int chapter;
    private final int[] fruits;
    @Nullable
    private final ResourceLocation productionJob;
    private final Map<ResourceLocation, Integer> mastery;
    private final int crafted;
    private final List<Bestiary> bestiary;
    private final int hiddenAccessories;
    private final long overflowExp;

    private SyncAdventureS2C(int day, int chapter, int[] fruits, @Nullable ResourceLocation productionJob,
                             Map<ResourceLocation, Integer> mastery, int crafted, List<Bestiary> bestiary,
                             int hiddenAccessories, long overflowExp) {
        this.day = day;
        this.chapter = chapter;
        this.fruits = fruits;
        this.productionJob = productionJob;
        this.mastery = mastery;
        this.crafted = crafted;
        this.bestiary = bestiary;
        this.hiddenAccessories = hiddenAccessories;
        this.overflowExp = overflowExp;
    }

    /** サーバーで、今のデータから作る。 */
    public static SyncAdventureS2C of(ServerPlayer player, PlayerData data) {
        AdventureData adventure = data.getAdventure();
        ProgressionData progression = ProgressionData.get(player.server);
        int[] fruits = new int[StatType.COUNT];
        for (StatType type : StatType.all()) {
            fruits[type.ordinal()] = adventure.fruits(type);
        }
        List<Bestiary> bestiary = new ArrayList<>();
        for (Map.Entry<ResourceLocation, AdventureData.BestiaryEntry> entry : adventure.allBestiary().entrySet()) {
            bestiary.add(new Bestiary(entry.getKey(), entry.getValue().kills(), new ArrayList<>(entry.getValue().drops())));
        }
        return new SyncAdventureS2C(progression.day(), progression.chapter(), fruits, adventure.productionJob(),
                new LinkedHashMap<>(adventure.allMasteryExp()), adventure.crafted(), bestiary,
                adventure.hiddenAccessoryBits(), adventure.overflowExp());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(day);
        buf.writeVarInt(chapter);
        buf.writeVarInt(fruits.length);
        for (int value : fruits) {
            buf.writeVarInt(value);
        }
        buf.writeBoolean(productionJob != null);
        if (productionJob != null) {
            buf.writeResourceLocation(productionJob);
        }
        buf.writeVarInt(mastery.size());
        for (Map.Entry<ResourceLocation, Integer> entry : mastery.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeVarInt(crafted);
        buf.writeVarInt(bestiary.size());
        for (Bestiary entry : bestiary) {
            buf.writeResourceLocation(entry.enemy());
            buf.writeVarInt(entry.kills());
            buf.writeVarInt(entry.drops().size());
            for (ResourceLocation drop : entry.drops()) {
                buf.writeResourceLocation(drop);
            }
        }
        buf.writeVarInt(hiddenAccessories);
        buf.writeVarLong(overflowExp);
    }

    public static SyncAdventureS2C decode(FriendlyByteBuf buf) {
        int day = buf.readVarInt();
        int chapter = buf.readVarInt();
        int fruitCount = Math.min(buf.readVarInt(), 64);
        int[] fruits = new int[StatType.COUNT];
        for (int i = 0; i < fruitCount; i++) {
            int value = buf.readVarInt();
            if (i < fruits.length) {
                fruits[i] = value;
            }
        }
        ResourceLocation job = buf.readBoolean() ? buf.readResourceLocation() : null;
        int masteryCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Map<ResourceLocation, Integer> mastery = new LinkedHashMap<>();
        for (int i = 0; i < masteryCount; i++) {
            mastery.put(buf.readResourceLocation(), buf.readVarInt());
        }
        int crafted = buf.readVarInt();
        int bestiaryCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Bestiary> bestiary = new ArrayList<>();
        for (int i = 0; i < bestiaryCount; i++) {
            ResourceLocation enemy = buf.readResourceLocation();
            int kills = buf.readVarInt();
            int dropCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<ResourceLocation> drops = new ArrayList<>();
            for (int j = 0; j < dropCount; j++) {
                drops.add(buf.readResourceLocation());
            }
            bestiary.add(new Bestiary(enemy, kills, drops));
        }
        int hidden = buf.readVarInt();
        long overflow = buf.readVarLong();
        return new SyncAdventureS2C(day, chapter, fruits, job, mastery, crafted, bestiary, hidden, overflow);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAdventure(this));
    }

    public int day() {
        return day;
    }

    public int chapter() {
        return chapter;
    }

    public int[] fruits() {
        return fruits;
    }

    @Nullable
    public ResourceLocation productionJob() {
        return productionJob;
    }

    public Map<ResourceLocation, Integer> mastery() {
        return mastery;
    }

    public int crafted() {
        return crafted;
    }

    public List<Bestiary> bestiary() {
        return bestiary;
    }

    public int hiddenAccessories() {
        return hiddenAccessories;
    }

    public long overflowExp() {
        return overflowExp;
    }
}
