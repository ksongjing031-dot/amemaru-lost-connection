package jp.amemaru.lostconnection.story;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.DialogueS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会話を進める（サーバー。Phase 0-B 8章「会話は DialogueManager が StoryState のフラグを見て選びます」）。
 *
 * <ul>
 *   <li>NPCを右クリックすると、条件に合う会話を選んで始める</li>
 *   <li>選択肢のない場面は、次の選択肢（か終わり）までまとめて送る。クライアントが自分の速さで読み進めるので、
 *       通信が遅れても会話が止まらない。選んだ選択肢だけをサーバーに送る（{@code DialogueChoice}）</li>
 *   <li>選択肢は、その選択肢が今出ているか、NPCとの距離をサーバーで確かめてから行う（Phase 0-B 5章）</li>
 *   <li>最後まで読むと既読にする（2回目からは、クライアントで「スキップ」ができる。Phase 0-A 14章「既読のみスキップ」）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class DialogueManager {
    /** 会話の続き。{@code npcEntity} はNPCのエンティティ（オープニングの役者のように、いなければ null）。 */
    private record Session(int id, ResourceLocation dialogue, ResourceLocation npc, @Nullable UUID npcEntity, String node,
                           boolean replay) {
        Session at(String next) {
            return new Session(id, dialogue, npc, npcEntity, next, replay);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextId = 1;

    private DialogueManager() {
    }

    // ---------------------------------------------------------------- 始める

    /** NPCを右クリックしたとき。 */
    public static void talk(ServerPlayer player, NpcEntity npc) {
        ResourceLocation npcId = npc.npcId();
        if (npcId == null) {
            return;
        }
        if (StoryEventManager.isPlaying(player)) {
            return;
        }
        NpcDef def = NpcDefs.get(npcId);
        double range = def == null ? 5.0 : def.talkRange();
        if (player.distanceToSqr(npc) > (range + 1.5) * (range + 1.5)) {
            return;
        }
        DialogueDef dialogue = DialogueDefs.choose(player, npcId);
        if (dialogue == null) {
            // 会話がまだないNPC：名前だけの短いあいさつ
            player.displayClientMessage(Component.translatable("message.amemaru.npc.no_dialogue",
                    def == null ? npcId.toString() : def.name()), true);
            return;
        }
        npc.startTalking(player);
        start(player, dialogue, npc.getUUID(), false);
    }

    /** NPCのエンティティなしで会話を始める（オープニングの役者・大結晶の記憶の再生）。 */
    public static boolean open(ServerPlayer player, ResourceLocation npc, @Nullable ResourceLocation dialogueId, boolean replay) {
        DialogueDef dialogue = dialogueId != null ? DialogueDefs.get(dialogueId) : DialogueDefs.choose(player, npc);
        if (dialogue == null) {
            AmemaruMod.LOGGER.warn("[amemaru] No dialogue {} for {}", dialogueId, npc);
            return false;
        }
        start(player, dialogue, null, replay);
        return true;
    }

    private static void start(ServerPlayer player, DialogueDef dialogue, @Nullable UUID npcEntity, boolean replay) {
        Session session = new Session(nextId++, dialogue.id(), dialogue.npc(), npcEntity, dialogue.start(), replay);
        SESSIONS.put(player.getUUID(), session);
        sendFrom(player, session, dialogue);
    }

    // ---------------------------------------------------------------- 進める

    /**
     * 今の場面から、選択肢のある場面か終わりまでの吹き出しをまとめて送る。途中の場面の処理は、送るときに行う
     * （記憶の再生では行わない）。
     */
    private static void sendFrom(ServerPlayer player, Session session, DialogueDef dialogue) {
        List<DialogueS2C.Page> pages = new ArrayList<>();
        DialogueDef.Node node = dialogue.node(session.node());
        List<DialogueS2C.ChoiceView> choices = new ArrayList<>();
        int guard = 0;
        while (node != null && guard++ < 64) {
            if (!node.text().isEmpty()) {
                pages.add(page(player, dialogue, node));
            }
            if (!session.replay()) {
                for (StoryAction action : node.actions()) {
                    action.run(player, npcOf(player, session));
                }
            }
            if (node.hasChoices()) {
                for (int i = 0; i < node.choices().size(); i++) {
                    DialogueDef.Choice choice = node.choices().get(i);
                    if (choice.conditions().test(player)) {
                        choices.add(new DialogueS2C.ChoiceView(i, fill(player, choice.text())));
                    }
                }
                break;
            }
            if (node.next() == null) {
                node = null;
                break;
            }
            node = dialogue.node(node.next());
            if (node != null) {
                session = session.at(node.key());
            }
        }
        boolean ends = choices.isEmpty();
        SESSIONS.put(player.getUUID(), session);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        boolean read = data != null && data.getStory().read(dialogue.id());
        NpcDef def = NpcDefs.get(dialogue.npc());
        ModNetwork.sendTo(player, new DialogueS2C(session.id(), dialogue.npc(), def == null ? "" : def.name(),
                def == null ? "" : def.title(), List.copyOf(pages), List.copyOf(choices), ends, read, session.replay()));
    }

    private static DialogueS2C.Page page(ServerPlayer player, DialogueDef dialogue, DialogueDef.Node node) {
        String name;
        ResourceLocation portrait = null;
        switch (node.speakerKind()) {
            case "player" -> name = player.getGameProfile().getName();
            case "narration" -> name = "";
            case "core" -> name = "？？？";
            default -> {
                ResourceLocation speaker = node.speaker() != null ? node.speaker() : dialogue.npc();
                NpcDef def = NpcDefs.get(speaker);
                name = def == null ? speaker.getPath() : def.name();
                portrait = speaker;
            }
        }
        return new DialogueS2C.Page(node.speakerKind(), name, portrait, node.expression(), fill(player, node.text()));
    }

    /** {@code {player}} を名前に置き換える。 */
    public static String fill(ServerPlayer player, String text) {
        return text.replace("{player}", player.getGameProfile().getName());
    }

    /** 選択肢を選んだ・最後まで読んだ（{@code choice} が −1）・閉じた（−2）とき（{@code DialogueChoiceC2S}）。 */
    public static void onChoice(ServerPlayer player, int sessionId, int choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != sessionId) {
            return;
        }
        DialogueDef dialogue = DialogueDefs.get(session.dialogue());
        if (dialogue == null) {
            end(player, session, false);
            return;
        }
        if (choice == -2) {
            // 途中で閉じた：終わりの処理はしない
            end(player, session, false);
            OpeningService.onDialogueClosed(player, dialogue.id(), false);
            return;
        }
        DialogueDef.Node node = dialogue.node(session.node());
        if (choice == -1) {
            if (node == null || !node.hasChoices()) {
                end(player, session, true);
                OpeningService.onDialogueClosed(player, dialogue.id(), true);
            }
            return;
        }
        if (node == null || choice < 0 || choice >= node.choices().size()) {
            return;
        }
        // NPCから離れすぎていたら、選べない（会話を終える）
        NpcEntity npc = npcOf(player, session);
        if (session.npcEntity() != null) {
            NpcDef def = NpcDefs.get(session.npc());
            double range = (def == null ? 5.0 : def.talkRange()) + 4.0;
            if (npc == null || npc.distanceToSqr(player) > range * range) {
                end(player, session, false);
                ModNetwork.sendTo(player, DialogueS2C.close(session.id()));
                return;
            }
        }
        DialogueDef.Choice picked = node.choices().get(choice);
        if (!picked.conditions().test(player)) {
            return;
        }
        boolean close = false;
        if (!session.replay()) {
            for (StoryAction action : picked.actions()) {
                if (action.closes()) {
                    close = true;
                } else {
                    action.run(player, npc);
                }
            }
        }
        if (close || picked.next() == null) {
            end(player, session, true);
            ModNetwork.sendTo(player, DialogueS2C.close(session.id()));
            OpeningService.onDialogueClosed(player, dialogue.id(), true);
            return;
        }
        Session next = session.at(picked.next());
        sendFrom(player, next, dialogue);
    }

    /** 会話を終える。{@code finished} なら終わりの処理をして既読にする。 */
    private static void end(ServerPlayer player, Session session, boolean finished) {
        SESSIONS.remove(player.getUUID());
        NpcEntity npc = npcOf(player, session);
        if (npc != null) {
            npc.stopTalking(player);
        }
        if (finished && !session.replay()) {
            DialogueDef dialogue = DialogueDefs.get(session.dialogue());
            if (dialogue != null) {
                for (StoryAction action : dialogue.endActions()) {
                    action.run(player, npc);
                }
                PlayerDataCapability.get(player).ifPresent(data -> data.getStory().markRead(dialogue.id()));
            }
        }
    }

    @Nullable
    private static NpcEntity npcOf(ServerPlayer player, Session session) {
        if (session.npcEntity() == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(session.npcEntity());
        return entity instanceof NpcEntity npc ? npc : null;
    }

    /** 会話の途中か。 */
    public static boolean isTalking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
