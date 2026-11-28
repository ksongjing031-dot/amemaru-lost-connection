package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientUniqueSkills;
import jp.amemaru.lostconnection.equipment.UniqueSkillDef;
import jp.amemaru.lostconnection.equipment.UniqueSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ユニーク武器の固有スキルの一覧を配る（11月28日【補完】。ログインしたときと /reload の後）。
 *
 * <p>中身は職業のスキルと同じ形（{@link SyncDefinitionsS2C.SkillInfo}）に、武器と演出の色を足したもの。クライアントは
 * これで、固有スキルの狙い・クールダウン・説明（ツールチップ）・演出を職業のスキルと同じように扱う。</p>
 */
public record SyncUniqueSkillsS2C(List<Entry> entries) {
    private static final int MAX_ENTRIES = 256;

    /** 固有スキル1つ分。 */
    public record Entry(SyncDefinitionsS2C.SkillInfo skill, ResourceLocation item, int color) {
    }

    /** サーバーで、今の一覧から作る。 */
    public static SyncUniqueSkillsS2C current() {
        List<Entry> entries = new ArrayList<>();
        for (UniqueSkillDef def : UniqueSkills.all()) {
            int index = UniqueSkills.index(def);
            entries.add(new Entry(SyncDefinitionsS2C.skillInfo(def.skill(), index, -1, -1, null, new int[0], new int[0]), def.item(),
                    def.color()));
        }
        return new SyncUniqueSkillsS2C(List.copyOf(entries));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            SyncDefinitionsS2C.writeSkill(buf, entry.skill());
            buf.writeResourceLocation(entry.item());
            buf.writeInt(entry.color());
        }
    }

    public static SyncUniqueSkillsS2C decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many unique skills: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SyncDefinitionsS2C.SkillInfo skill = SyncDefinitionsS2C.readSkill(buf);
            entries.add(new Entry(skill, buf.readResourceLocation(), buf.readInt()));
        }
        return new SyncUniqueSkillsS2C(List.copyOf(entries));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientUniqueSkills.apply(this));
    }
}
