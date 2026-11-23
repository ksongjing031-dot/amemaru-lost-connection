package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 会話の続き（Phase 0-B 5章 {@code DialogueNode}：話し手・文章・選択肢）。次の選択肢（か終わり）までの吹き出しをまとめて送る。
 * 物語の文章は、開いたときに必要な分だけ送る（クライアントのファイルには入れない。Phase 0-B 5章）。
 */
public record DialogueS2C(int session, ResourceLocation npc, String npcName, String npcTitle, List<Page> pages,
                          List<ChoiceView> choices, boolean ends, boolean read, boolean replay) {
    /** 吹き出し1つ。{@code portrait} は立ち絵のNPC（地の文・本人の台詞は null）。 */
    public record Page(String speakerKind, String speaker, @Nullable ResourceLocation portrait, String expression, String text) {
    }

    /** 選択肢（{@code index} は元の番号）。 */
    public record ChoiceView(int index, String text) {
    }

    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("air");

    /** 会話を閉じる知らせ。 */
    public static DialogueS2C close(int session) {
        return new DialogueS2C(session, NONE, "", "", List.of(), List.of(), true, false, false);
    }

    public boolean isClose() {
        return pages.isEmpty() && choices.isEmpty() && ends;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeResourceLocation(npc);
        buf.writeUtf(npcName, 64);
        buf.writeUtf(npcTitle, 64);
        buf.writeVarInt(pages.size());
        for (Page p : pages) {
            buf.writeUtf(p.speakerKind(), 16);
            buf.writeUtf(p.speaker(), 64);
            buf.writeBoolean(p.portrait() != null);
            if (p.portrait() != null) {
                buf.writeResourceLocation(p.portrait());
            }
            buf.writeUtf(p.expression(), 32);
            buf.writeUtf(p.text(), 256);
        }
        buf.writeVarInt(choices.size());
        for (ChoiceView c : choices) {
            buf.writeVarInt(c.index());
            buf.writeUtf(c.text(), 128);
        }
        buf.writeBoolean(ends);
        buf.writeBoolean(read);
        buf.writeBoolean(replay);
    }

    public static DialogueS2C decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        ResourceLocation npc = buf.readResourceLocation();
        String name = buf.readUtf(64);
        String title = buf.readUtf(64);
        int n = Math.min(64, buf.readVarInt());
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String kind = buf.readUtf(16);
            String speaker = buf.readUtf(64);
            ResourceLocation portrait = buf.readBoolean() ? buf.readResourceLocation() : null;
            pages.add(new Page(kind, speaker, portrait, buf.readUtf(32), buf.readUtf(256)));
        }
        int c = Math.min(8, buf.readVarInt());
        List<ChoiceView> choices = new ArrayList<>();
        for (int i = 0; i < c; i++) {
            choices.add(new ChoiceView(buf.readVarInt(), buf.readUtf(128)));
        }
        return new DialogueS2C(session, npc, name, title, List.copyOf(pages), List.copyOf(choices), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleDialogue(this));
    }
}
