package jp.amemaru.lostconnection.tower.iceforest;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 7階「氷の森林」の合言葉の扉（11月25日。Phase 0-A 9章の7階「最奥の『合言葉の扉』は、パーティ全員が同じ合言葉をチャットに打つと
 * 開く」。古代記録「森林監視所の記録」：「侵食域では、隣にいる者が誰か分からなくなる。合言葉を決めよ」）。
 *
 * <p>扉の前（{@link FloorFeatures.Passphrase#area()}）にいる人がチャットに言葉を打つと、覚えておく。扉の前にいるパーティの
 * 全員が、{@code seconds} 秒のうちに同じ言葉を打つと、その人たちに印（{@link #flag}）がつき、封印（{@code passphrase}）を
 * 通れる。パーティでない人は、ひとりで言えばよい。言葉は何でもよい（前後の空白・全角半角・大文字小文字はそろえて比べる）【補完】。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PassphraseService {
    public static final int MAX_LENGTH = 32;
    private static final Map<UUID, Said> SAID = new HashMap<>();

    private record Said(int floor, String text, long time) {
    }

    private PassphraseService() {
    }

    /** 合言葉の扉を通れる印（人ごと）。 */
    public static ResourceLocation flag(int floor) {
        return AmemaruMod.id("floor" + floor + "/passphrase");
    }

    public static boolean passed(ServerPlayer player, int floor) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        return data != null && data.getStory().has(flag(floor));
    }

    /** 比べるための形（前後の空白を落とし、全角半角・大文字小文字をそろえ、空白を1つに）。 */
    public static String normalize(String raw) {
        String text = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
        return text.replaceAll("\\s+", " ");
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        say(event.getPlayer(), event.getRawText());
    }

    /** 言葉を言った（チャット。自動テストからも）。そろって扉を通れるようになったら true。 */
    public static boolean say(ServerPlayer player, String raw) {
        FloorDef def = FloorDefs.floorAt(player.level(), player.position());
        FloorFeatures.Passphrase door = def == null ? null : def.features().passphrase();
        if (door == null || !FloorFeatures.inside(door.area(), player.position(), def.originVec())) {
            return false;
        }
        String text = normalize(raw);
        if (text.isEmpty() || text.length() > MAX_LENGTH || text.startsWith("/")) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        SAID.put(player.getUUID(), new Said(def.number(), text, now));
        List<ServerPlayer> group = group(player, def, door);
        long window = door.seconds() * 20L;
        int same = 0;
        for (ServerPlayer member : group) {
            Said said = SAID.get(member.getUUID());
            if (said != null && said.floor() == def.number() && said.text().equals(text) && now - said.time() <= window) {
                same++;
            }
        }
        if (same < group.size()) {
            for (ServerPlayer member : group) {
                member.displayClientMessage(Component.translatable("message.amemaru.passphrase.waiting", same, group.size(), door.seconds())
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return false;
        }
        for (ServerPlayer member : group) {
            SAID.remove(member.getUUID());
            PlayerDataCapability.get(member).ifPresent(data -> {
                data.getStory().add(flag(def.number()));
                data.markDirty(PlayerData.SYNC_STORY);
            });
            member.sendSystemMessage(Component.translatable("message.amemaru.passphrase.opened", text).withStyle(ChatFormatting.GOLD));
            member.serverLevel().sendParticles(ParticleTypes.END_ROD, member.getX(), member.getY() + 1.0, member.getZ(), 16, 0.4, 0.6, 0.4, 0.04);
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 2.0F, 1.0F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.2F, 1.4F);
        AmemaruMod.LOGGER.info("[amemaru] Passphrase door on floor {} opened for {} player(s)", def.number(), group.size());
        return true;
    }

    /** 扉の前にいる、言った人のパーティの人（パーティでなければ、言った人だけ）。 */
    private static List<ServerPlayer> group(ServerPlayer player, FloorDef def, FloorFeatures.Passphrase door) {
        List<ServerPlayer> group = new ArrayList<>();
        for (ServerPlayer member : PartyService.onlineMembers(player)) {
            if (member.level() == player.level() && member.isAlive() && !member.isSpectator()
                    && FloorFeatures.inside(door.area(), member.position(), def.originVec())) {
                group.add(member);
            }
        }
        if (!group.contains(player)) {
            group.add(player);
        }
        return group;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SAID.remove(event.getEntity().getUUID());
    }
}
