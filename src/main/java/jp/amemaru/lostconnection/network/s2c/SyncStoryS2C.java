package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.story.GuideService;
import jp.amemaru.lostconnection.story.OpeningService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 本人の物語のうち、画面に要る分（Phase 5）：オープニングの進みと「準備を待っている」か、起動した石柱（封印を通れるかを、
 * クライアントでも同じに決めるため）、はじめの手引きの今の段と行き先。
 */
public record SyncStoryS2C(int opening, boolean openingPending, Map<Integer, Integer> pillars, Map<Integer, Integer> pillarTotals,
                           int guideIndex, int guideTotal,
                           String guideText, @Nullable ResourceLocation guideDimension, @Nullable Vec3 guideTarget) {
    public static SyncStoryS2C of(ServerPlayer player, PlayerData data) {
        GuideService.View view = GuideService.view(player, data);
        Map<Integer, Integer> totals = new HashMap<>();
        Map<Integer, Integer> pillars = new HashMap<>(data.getStory().allPillars());
        for (jp.amemaru.lostconnection.tower.FloorDef def : jp.amemaru.lostconnection.tower.FloorDefs.all()) {
            jp.amemaru.lostconnection.tower.FloorFeatures features = def.features();
            if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_LOST_WOODS)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_GUARDIANS)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_FLOODGATES)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_MURALS)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_STATUES)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_LIGHTS)
                    || features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_PASSPHRASE)) {
                // Phase 8 その1：迷いの森の区画（みんなで同じ）を、石柱と同じ形で送る（封印の判定をクライアントでも同じにする）。
                // 11月24日：名のある魔物を倒した印（人ごと）も、その後ろのビットに足す
                int bits = 0;
                int total = 0;
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_LOST_WOODS)) {
                    bits = jp.amemaru.lostconnection.tower.forest.LostWoodsService.tieBits(player.server, def);
                    total = features.lostWoods().size();
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_FLOODGATES)) {
                    // Phase 8 その2：開いた水門（みんなで同じ）
                    for (jp.amemaru.lostconnection.tower.FloorFeatures.Floodgate gate : features.floodgates()) {
                        if (jp.amemaru.lostconnection.tower.FloorFeatures.DRAIN.equals(gate.id())) {
                            continue;
                        }
                        if (jp.amemaru.lostconnection.tower.waterway.FloodgateService.isOpen(player.server, def.number(), gate.id())) {
                            bits |= 1 << total;
                        }
                        total++;
                    }
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_MURALS)) {
                    // 11月24日（4階）：戻った壁画（みんなで同じ）
                    int[] murals = jp.amemaru.lostconnection.tower.temple.TempleService.count(player.server, def);
                    bits |= jp.amemaru.lostconnection.tower.temple.TempleService.muralBits(player.server, def) << total;
                    total += murals[1];
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_STATUES)) {
                    // 11月25日（6階）：溶けた記憶の氷像（みんなで同じ）
                    bits |= jp.amemaru.lostconnection.tower.village.VillageService.statueBits(player.server, def) << total;
                    total += features.memoryStatues().size();
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_LIGHTS)) {
                    // 11月25日（7階）：解けた光の迷路（みんなで同じ）
                    bits |= jp.amemaru.lostconnection.tower.iceforest.LightMazeService.mazeBits(player.server, def) << total;
                    total += features.lightMazes().size();
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_PASSPHRASE)) {
                    // 11月25日（7階）：合言葉がそろった印（人ごと）
                    if (data.getStory().has(jp.amemaru.lostconnection.tower.iceforest.PassphraseService.flag(def.number()))) {
                        bits |= 1 << total;
                    }
                    total++;
                }
                if (features.requires(jp.amemaru.lostconnection.tower.FloorFeatures.SEAL_GUARDIANS)) {
                    List<jp.amemaru.lostconnection.tower.FloorDef.Guardian> named = jp.amemaru.lostconnection.tower.TowerService.namedGuardians(def);
                    for (int k = 0; k < named.size(); k++) {
                        if (data.getStory().has(jp.amemaru.lostconnection.tower.TowerService.guardianFlag(def.number(), named.get(k).id()))) {
                            bits |= 1 << (total + k);
                        }
                    }
                    total += named.size();
                }
                totals.put(def.number(), total);
                pillars.put(def.number(), bits);
            } else {
                totals.put(def.number(), def.pillars().size());
            }
        }
        return new SyncStoryS2C(data.getStory().opening(), OpeningService.isPending(player), Map.copyOf(pillars),
                Map.copyOf(totals),
                view == null ? -1 : view.index(), view == null ? 0 : view.total(), view == null ? "" : view.text(),
                view == null || view.dimension() == null ? null : view.dimension().location(), view == null ? null : view.target());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(opening);
        buf.writeBoolean(openingPending);
        buf.writeVarInt(pillars.size());
        for (Map.Entry<Integer, Integer> entry : pillars.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeVarInt(pillarTotals.size());
        for (Map.Entry<Integer, Integer> entry : pillarTotals.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeVarInt(guideIndex + 1);
        buf.writeVarInt(guideTotal);
        buf.writeUtf(guideText, 256);
        buf.writeBoolean(guideDimension != null && guideTarget != null);
        if (guideDimension != null && guideTarget != null) {
            buf.writeResourceLocation(guideDimension);
            buf.writeDouble(guideTarget.x);
            buf.writeDouble(guideTarget.y);
            buf.writeDouble(guideTarget.z);
        }
    }

    public static SyncStoryS2C decode(FriendlyByteBuf buf) {
        int opening = buf.readVarInt();
        boolean pending = buf.readBoolean();
        int n = Math.min(64, buf.readVarInt());
        Map<Integer, Integer> pillars = new HashMap<>();
        for (int i = 0; i < n; i++) {
            pillars.put(buf.readVarInt(), buf.readVarInt());
        }
        int t = Math.min(64, buf.readVarInt());
        Map<Integer, Integer> totals = new HashMap<>();
        for (int i = 0; i < t; i++) {
            totals.put(buf.readVarInt(), buf.readVarInt());
        }
        int index = buf.readVarInt() - 1;
        int total = buf.readVarInt();
        String text = buf.readUtf(256);
        ResourceLocation dimension = null;
        Vec3 target = null;
        if (buf.readBoolean()) {
            dimension = buf.readResourceLocation();
            target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        return new SyncStoryS2C(opening, pending, Map.copyOf(pillars), Map.copyOf(totals), index, total, text, dimension, target);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStory(this));
    }
}
