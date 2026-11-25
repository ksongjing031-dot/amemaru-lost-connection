package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.fishing.FishDef;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.TowerS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * NPCの記憶喪失（Phase 0-A 13章「NPCの記憶喪失」。6日目の分。11月25日）。
 *
 * <ul>
 *   <li>6階に初めて人が入ると、物語のフラグ {@link #LOST_IN_TOWN} が立つ（「ロストがコネリアにも及ぶ演出」。0-A の物語フラグ
 *       {@code lost_in_town}）。立った時、全員に知らせ、コネリアに白い雪が降る（クライアントの {@code town}）。10日目の
 *       {@link #QUEEN_DOWN}（10階の初撃破）で、みんなの記憶は戻る</li>
 *   <li>街の人（一部）は、プレイヤーを忘れる（6日目。0-A の表「あんた、見ない顔だね」）：門番バルド・カジノのジゼル・料理人ペトラ
 *       【補完】。忘れている間の会話は {@code conditions.remembered: false} の会話になる。教会の「記憶の相談所」（セレナ）の
 *       クエストで、一人ずつ思い出してもらう（本人のフラグ {@code amemaru:memory/<NPC>}）。施設の働き（門・カジノ・料理屋）は止めない</li>
 *   <li>忘れている間、そのNPCとの親密度は凍結される（下がらず、上がらない）。思い出したときに少し上乗せする（クエストのごほうび）</li>
 *   <li>ポッカ爺は魚の名前を忘れる（魚図鑑の名前が「？？？」）。魚を手に持って話しかけると、その魚を一匹ずつ思い出す（みんなの分）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class NpcMemoryService {
    public static final ResourceLocation LOST_IN_TOWN = AmemaruMod.id("ch2/lost_in_town");
    public static final ResourceLocation QUEEN_DOWN = AmemaruMod.id("ch2/queen_down");
    private static final ResourceLocation ANNOUNCED = AmemaruMod.id("ch2/lost_in_town_announced");
    /** 6日目に、プレイヤーを忘れる街の人（一部）。 */
    public static final Set<ResourceLocation> FORGET_DAY6 = Set.of(AmemaruMod.id("bard"), AmemaruMod.id("gisele"), AmemaruMod.id("petra"));
    public static final ResourceLocation POKKA = AmemaruMod.id("pokka");
    private static Boolean lastSent;

    private NpcMemoryService() {
    }

    /** 街にロストが及んでいる間か（6階に人が来てから、10階の初撃破まで）。 */
    public static boolean townLost(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        return state.has(LOST_IN_TOWN) && !state.has(QUEEN_DOWN);
    }

    /** そのNPCが、いまプレイヤーを忘れる時期か。 */
    public static boolean forgets(MinecraftServer server, ResourceLocation npc) {
        return FORGET_DAY6.contains(npc) && townLost(server);
    }

    /** 思い出してもらった印（本人のフラグ）。 */
    public static ResourceLocation memoryFlag(ResourceLocation npc) {
        return AmemaruMod.id("memory/" + npc.getPath());
    }

    /** そのNPCが、その人を覚えているか。 */
    public static boolean remembers(ServerPlayer player, ResourceLocation npc) {
        if (!forgets(player.server, npc)) {
            return true;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        return data != null && data.getStory().has(memoryFlag(npc));
    }

    // ================================================================ ポッカ爺と魚の名前

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_npc_memory";
        private final Set<String> fish = new LinkedHashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("fish", Tag.TAG_STRING)) {
                data.fish.add(t.getAsString());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            fish.forEach(k -> list.add(StringTag.valueOf(k)));
            tag.put("fish", list);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean remembered(ResourceLocation id) {
            return fish.contains(id.toString());
        }

        public boolean remember(ResourceLocation id) {
            boolean added = fish.add(id.toString());
            if (added) {
                setDirty();
            }
            return added;
        }
    }

    /** ポッカ爺が、その魚の名前を忘れているか（魚図鑑の名前が「？？？」になる）。 */
    public static boolean fishForgotten(MinecraftServer server, FishDef def) {
        return townLost(server) && !Data.get(server).remembered(def.id());
    }

    /** ポッカ爺に魚を見せた（手に持って話しかけた）：忘れていた名前を思い出す。思い出したら true。 */
    public static boolean showFish(ServerPlayer player, NpcEntity npc) {
        if (!POKKA.equals(npc.npcId()) || !townLost(player.server)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        FishDef def = FishDefs.of(held);
        if (def == null || !Data.get(player.server).remember(def.id())) {
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.amemaru.pokka.remembered", def.name()).withStyle(ChatFormatting.AQUA));
        player.serverLevel().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
        AmemaruMod.LOGGER.info("[amemaru] Pokka remembered {} (shown by {})", def.id(), player.getGameProfile().getName());
        return true;
    }

    // ================================================================ 街の雪と知らせ

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 17) {
            return;
        }
        MinecraftServer server = event.getServer();
        boolean lost = townLost(server);
        StoryState state = StoryState.get(server);
        if (lost && !state.has(ANNOUNCED)) {
            state.add(ANNOUNCED);
            ProgressionService.broadcast(server, Component.translatable("message.amemaru.town.lost"), AnnouncementS2C.Kind.STORY);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.translatable("message.amemaru.town.lost_detail").withStyle(ChatFormatting.GRAY));
            }
            AmemaruMod.LOGGER.info("[amemaru] The Lost reached Conelia (lost_in_town)");
        }
        if (lastSent == null || lastSent != lost) {
            lastSent = lost;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player, lost);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, townLost(player.server));
        }
    }

    private static void send(ServerPlayer player, boolean lost) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("lost", lost);
        ModNetwork.sendTo(player, new TowerS2C("town", tag));
    }

    /** 自動テスト用：次の tick に、街の様子を送り直す。 */
    public static void resendForTest() {
        lastSent = null;
    }
}
