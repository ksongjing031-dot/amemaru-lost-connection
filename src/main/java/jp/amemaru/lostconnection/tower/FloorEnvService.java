package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.TowerS2C;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 今いる階の霧をクライアントへ送る（Phase 8 その1。{@link FloorFeatures.Environment}）。1秒ごとに、いる階が変わったかを見る。
 * あわせて、2階の結び目の灯は階の外へ持ち出せない（塔の外へ出たら消す）【補完】。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class FloorEnvService {
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private FloorEnvService() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int floor = 0;
            if (player.level().dimension().equals(FloorDefs.TOWER)) {
                FloorDef def = FloorDefs.floorAt(player.level(), player.position());
                floor = def == null ? 0 : def.number();
                if (def != null) {
                    checkStoryZones(server, player, def);
                    // Phase 8 その2：3階の水を、開いた水門の状態に合わせる（サーバーが起きて初めて人が来たとき）
                    jp.amemaru.lostconnection.tower.waterway.FloodgateService.sync(server, def);
                }
            } else {
                dropKnotLanterns(player);
            }
            Integer last = SENT.get(player.getUUID());
            if (last != null && last == floor) {
                continue;
            }
            SENT.put(player.getUUID(), floor);
            ModNetwork.sendTo(player, new TowerS2C("env", envTag(floor)));
        }
    }

    /** 物語の場所に入った人（とパーティ）に、その場所のイベントを流す（Phase 8 その1。2階の「霧の奥のノア」）。 */
    private static void checkStoryZones(MinecraftServer server, ServerPlayer player, FloorDef def) {
        if (player.isSpectator() || def.features().storyZones().isEmpty()) {
            return;
        }
        for (FloorFeatures.StoryZone zone : def.features().storyZones()) {
            if (!FloorFeatures.inside(zone.box(), player.position(), def.originVec())
                    || !jp.amemaru.lostconnection.story.StoryEventManager.zonePending(server, def.number(), zone.id())) {
                continue;
            }
            java.util.List<ServerPlayer> party = new java.util.ArrayList<>();
            for (ServerPlayer mate : jp.amemaru.lostconnection.party.PartyService.onlineMembers(player)) {
                if (mate.level() == player.level() && mate.distanceTo(player) < 64) {
                    party.add(mate);
                }
            }
            if (!party.contains(player)) {
                party.add(player);
            }
            jp.amemaru.lostconnection.story.StoryEventManager.triggerZone(server, def.number(), zone.id(), party);
        }
    }

    /** その階の霧（霧の決まりがなければ空。クライアントは霧を戻す）。 */
    public static CompoundTag envTag(int floor) {
        CompoundTag tag = new CompoundTag();
        FloorDef def = floor <= 0 ? null : FloorDefs.byNumber(floor);
        FloorFeatures.Environment env = def == null ? null : def.features().environment();
        if (env != null) {
            tag.putFloat("fogStart", env.fogStart());
            tag.putFloat("fogEnd", env.fogEnd());
            if (env.fogColor() >= 0) {
                tag.putInt("fogColor", env.fogColor());
            }
            // 11月25日（6階）：オーロラの空・雪
            if (!env.sky().isEmpty()) {
                tag.putString("sky", env.sky());
            }
            if (!env.weather().isEmpty()) {
                tag.putString("weather", env.weather());
            }
        }
        return tag;
    }

    private static void dropKnotLanterns(ServerPlayer player) {
        Item lantern = ModItems.MANIFEST.get("knot_lantern").get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(lantern)) {
                stack.setCount(0);
            }
        }
        if (player.getOffhandItem().is(lantern)) {
            player.getOffhandItem().setCount(0);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        SENT.remove(event.getEntity().getUUID());   // 入り直したら、もう一度送る
    }
}
