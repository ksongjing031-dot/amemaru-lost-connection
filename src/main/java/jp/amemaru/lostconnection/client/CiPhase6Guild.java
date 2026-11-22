package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.GuildScreen;
import jp.amemaru.lostconnection.client.screen.RecordBookScreen;
import jp.amemaru.lostconnection.client.screen.TitleListScreen;
import jp.amemaru.lostconnection.guild.RecordService;
import jp.amemaru.lostconnection.guild.StallDonation;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.story.DialogueManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * 自動の確認の写真（Phase 6 その3：ギルド）の段取り：NPC（ノア・エルネスト）に話しかける・記録帳を開いて記録を読む・
 * 称号の一覧を開く・ギルドの窓口のタブを変える・露店の区画の寄付の画面を開く。
 */
final class CiPhase6Guild {
    private CiPhase6Guild() {
    }

    /** その種類の行なら true（first のときだけ動かす）。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p6g_talk", "p6g_book", "p6g_book_read", "p6g_titles", "p6g_tab", "p6g_donation").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "p6g_talk" -> CiPhase3.onServer(minecraft, player -> {
                ResourceLocation id = jp.amemaru.lostconnection.AmemaruMod.id(shot[3]);
                player.level().getEntitiesOfClass(NpcEntity.class, new AABB(player.blockPosition()).inflate(12.0), npc -> id.equals(npc.npcId()))
                        .stream().min(Comparator.comparingDouble(npc -> npc.distanceToSqr(player)))
                        .ifPresent(npc -> DialogueManager.talk(player, npc));
            });
            case "p6g_book" -> CiPhase3.onServer(minecraft, RecordService::openBook);
            case "p6g_book_read" -> {
                if (minecraft.screen instanceof RecordBookScreen book) {
                    book.ciRead(shot[3]);
                }
            }
            case "p6g_titles" -> minecraft.setScreen(new TitleListScreen(null));
            case "p6g_tab" -> {
                if (minecraft.screen instanceof GuildScreen guild) {
                    guild.ciTab(shot[3]);
                }
            }
            case "p6g_donation" -> CiPhase3.onServer(minecraft, StallDonation::open);
            default -> {
            }
        }
        return true;
    }
}
