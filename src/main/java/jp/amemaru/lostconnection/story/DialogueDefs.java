package jp.amemaru.lostconnection.story;

import jp.amemaru.lostconnection.core.data.DefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;

/** 会話の定義の置き場（{@code dialogues/}）。 */
public final class DialogueDefs {
    private static final DefinitionLoader<DialogueDef> LOADER = new DefinitionLoader<>("dialogues", DialogueDef::parse);

    private DialogueDefs() {
    }

    public static DefinitionLoader<DialogueDef> loader() {
        return LOADER;
    }

    @Nullable
    public static DialogueDef get(@Nullable ResourceLocation id) {
        return id == null ? null : LOADER.get(id).orElse(null);
    }

    public static Map<ResourceLocation, DialogueDef> all() {
        return LOADER.all();
    }

    /**
     * そのNPCの会話のうち、その人について条件に合い、{@code priority} がいちばん高いもの（同じなら、IDの順で後のもの）。
     * なければ null。
     */
    @Nullable
    public static DialogueDef choose(ServerPlayer player, ResourceLocation npc) {
        DialogueDef best = null;
        for (DialogueDef def : LOADER.all().values()) {
            if (!def.npc().equals(npc) || !def.conditions().test(player, npc)) {
                continue;
            }
            if (best == null || def.priority() >= best.priority()) {
                best = def;
            }
        }
        return best;
    }
}
