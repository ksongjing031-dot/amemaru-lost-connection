package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.StatusVisuals;
import jp.amemaru.lostconnection.client.screen.JobSelectScreen;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.network.s2c.AccessoryVisibilityS2C;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.network.s2c.SyncAdventureS2C;
import jp.amemaru.lostconnection.network.s2c.SyncContentS2C;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import net.minecraft.client.Minecraft;

/**
 * サーバーから届いたパケットの、クライアント側の処理。
 *
 * <p>このクラスはクライアントにだけ読み込まれる（パケットの側からは {@code DistExecutor} 経由で呼ぶ）。</p>
 */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleSync(SyncPlayerDataS2C message) {
        ClientPlayerState.get().apply(message);
    }

    public static void handleDefinitions(SyncDefinitionsS2C message) {
        ClientDefinitions.get().apply(message);
    }

    public static void handleSkillFx(SkillFxS2C message) {
        SkillFx.handle(message);
    }

    public static void handleCombatText(CombatTextS2C message) {
        CombatTextRenderer.handle(message);
        SkillFx.onCombatText(message);
    }

    public static void handleStatusFx(StatusFxS2C message) {
        StatusVisuals.handle(message);
    }

    public static void handleSkillResult(SkillResultS2C message) {
        SkillInput.handleResult(message);
    }

    public static void handleOpenJobScreen(OpenJobScreenS2C message) {
        Minecraft.getInstance().setScreen(new JobSelectScreen(message));
    }

    // ---- Phase 3

    public static void handleAdventure(SyncAdventureS2C message) {
        ClientAdventure.apply(message);
    }

    public static void handleContent(SyncContentS2C message) {
        java.util.Map<net.minecraft.resources.ResourceLocation, EquipmentDef> map = new java.util.LinkedHashMap<>();
        for (EquipmentDef def : message.equipment()) {
            map.put(def.id(), def);
        }
        EquipmentDefs.setClient(new EquipmentDefs.Snapshot(java.util.Map.copyOf(map), message.rules()));
        EnemyDefs.setClientInfo(message.enemies());
        ProductionRules.setClient(message.production());
        ClientContent.setProductionJobs(message.productionJobs());
    }

    public static void handleAccessoryVisibility(AccessoryVisibilityS2C message) {
        ClientAdventure.setHidden(message.entityId(), message.hiddenBits());
    }

    public static void handleSpecialMoment(SpecialMomentS2C message) {
        SpecialMomentFx.show(message.kind(), message.stack());
    }

    // ---------------------------------------------------------------- Phase 4：ダウン・パーティ・所持金

    public static void handleDownState(jp.amemaru.lostconnection.network.s2c.DownStateS2C message) {
        ClientDown.apply(message);
    }

    public static void handlePartyState(jp.amemaru.lostconnection.network.s2c.PartyStateS2C message) {
        ClientParty.apply(message);
    }

    public static void handleWallet(jp.amemaru.lostconnection.network.s2c.SyncWalletS2C message) {
        ClientPlayerState.get().setKnots(message.knots());
    }

    // ---------------------------------------------------------------- Phase 5：進行・物語・NPC・塔

    public static void handleWorldState(jp.amemaru.lostconnection.network.s2c.WorldStateS2C message) {
        ClientStory.apply(message);
    }

    public static void handleAnnouncement(jp.amemaru.lostconnection.network.s2c.AnnouncementS2C message) {
        ClientStory.announce(message);
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.7F));
    }

    public static void handleStory(jp.amemaru.lostconnection.network.s2c.SyncStoryS2C message) {
        ClientStory.apply(message);
    }

    public static void handleStoryText(jp.amemaru.lostconnection.network.s2c.StoryTextS2C message) {
        ClientStory.show(message);
        if (message.kind() == jp.amemaru.lostconnection.network.s2c.StoryTextS2C.Kind.ANCIENT) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.8F));
        }
    }

    public static void handleScene(jp.amemaru.lostconnection.network.s2c.SceneS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        // 場面は、開いている画面（メニューなど）を閉じて見せる。会話の画面と、CIの何も描かない画面は閉じない
        if (minecraft.screen != null && !(minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen)
                && !CiScreenshots.isHoldScreen(minecraft.screen)) {
            minecraft.setScreen(null);
        }
        ScenePlayer.start(message);
    }

    public static void handleDialogue(jp.amemaru.lostconnection.network.s2c.DialogueS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message.isClose()) {
            if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen screen
                    && screen.session() == message.session()) {
                screen.closeFromServer();
            }
            return;
        }
        if (minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen screen
                && screen.session() == message.session()) {
            screen.accept(message);
        } else {
            minecraft.setScreen(new jp.amemaru.lostconnection.client.screen.DialogueScreen(message));
        }
    }

    public static void handleReplayList(jp.amemaru.lostconnection.network.s2c.ReplayListS2C message) {
        Minecraft.getInstance().setScreen(new jp.amemaru.lostconnection.client.screen.ReplayScreen(message));
    }

    public static void handleFloorSelect(jp.amemaru.lostconnection.network.s2c.FloorSelectS2C message) {
        Minecraft.getInstance().setScreen(new jp.amemaru.lostconnection.client.screen.FloorSelectScreen(message));
    }

    public static void handleNpcs(jp.amemaru.lostconnection.network.s2c.SyncNpcsS2C message) {
        jp.amemaru.lostconnection.npc.NpcDefs.setClient(message.npcs());
    }

    // ---------------------------------------------------------------- Phase 6：経済

    public static void handleStorageContents(jp.amemaru.lostconnection.network.s2c.StorageContentsS2C message) {
        ClientStorage.set(message.containerId(), message.items(), message.received(), message.nextPrice(), message.maxPages());
    }
}
