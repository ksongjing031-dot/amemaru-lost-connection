package jp.amemaru.lostconnection.tower.village;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 6階「凍結した村」のギミック「記憶の解凍」（11月25日。Phase 0-A 9章の6階：「熱の結晶を運んで氷像を溶かすと、その日の村の短い
 * 場面が見える。運ぶ人は足が遅くなるので、ほかの人が護衛する」）。
 *
 * <ul>
 *   <li>熱の炉（宿屋の暖炉・パン屋のかまど・鍛冶屋の炉）を右クリックすると、熱の結晶を1つもらえる（1人1つまで）。
 *       熱の結晶は {@link #CRYSTAL_SECONDS} 秒で冷えて消える【補完】</li>
 *   <li>熱の結晶や記憶の結晶を持っている人は、足が {@link #CARRY_SLOW} だけ遅くなり、走れない。まわりの敵は、運ぶ人を狙いやすい
 *       （敵視が少しずつたまる）。ほかの人が護衛する【補完】</li>
 *   <li>記憶の氷像を、熱の結晶を持って右クリックすると、そばにいる間 {@link #THAW_TICKS} tick で溶ける。溶けると、そばの人に
 *       その日の村の短い場面（ストーリーイベント）が流れる。みんなの分で、進行日が変わるまで（朝にはまた凍っている）</li>
 *   <li>記憶の氷像がすべて溶けると、封印の条件 {@link FloorFeatures#SEAL_STATUES} がそろう</li>
 *   <li>結晶は、村の階の外へは持ち出せない（砕ける）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class VillageService {
    /** 熱の結晶をもらった時刻（品のタグ）。 */
    public static final String MADE_TAG = "amemaru_made";
    /** 熱の結晶が冷えて消えるまで（秒）。 */
    public static final int CRYSTAL_SECONDS = 240;
    /** 氷像が溶けるまで（tick）。 */
    public static final int THAW_TICKS = 100;
    /** 溶かしている間、氷像のそばにいる距離。 */
    public static final double THAW_RANGE = 4.5;
    /** 結晶を運ぶ人の足の遅さ（移動の速さの割合）。 */
    public static final double CARRY_SLOW = -0.35;
    /** 運ぶ人に、まわりの敵の敵視がたまる（1秒ごと）。 */
    public static final double CARRY_THREAT = 80.0;
    private static final UUID SLOW_ID = UUID.fromString("5d3c9a1e-6b2f-4f7a-9c41-2e8b7d6a0f63");

    private static final Map<UUID, Thaw> THAWING = new HashMap<>();
    private static final Map<UUID, Long> NOTICE = new HashMap<>();

    /** 溶かしている人（世界のプレイヤーの一覧から探さず、その人を持つ：自動テストの仮の人でも同じ道で溶ける）。 */
    private record Thaw(ServerPlayer player, int floor, String memory, BlockPos pos, long started) {
    }

    private VillageService() {
    }

    // ================================================================ 保存（みんなの分。進行日が変わるまで）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_village";
        private final Set<String> thawed = new LinkedHashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("thawed", Tag.TAG_STRING)) {
                data.thawed.add(t.getAsString());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            thawed.forEach(k -> list.add(StringTag.valueOf(k)));
            tag.put("thawed", list);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean thawed(int floor, String memory) {
            return thawed.contains(floor + ":" + memory);
        }

        public boolean thaw(int floor, String memory) {
            boolean added = thawed.add(floor + ":" + memory);
            if (added) {
                setDirty();
            }
            return added;
        }

        public void clear() {
            if (!thawed.isEmpty()) {
                thawed.clear();
                setDirty();
            }
        }
    }

    public static boolean thawed(MinecraftServer server, int floor, String memory) {
        return Data.get(server).thawed(floor, memory);
    }

    /** 溶けた記憶の氷像の数と、ぜんぶの数。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int done = 0;
        for (FloorFeatures.MemoryStatue statue : def.features().memoryStatues()) {
            if (data.thawed(def.number(), statue.id())) {
                done++;
            }
        }
        return new int[]{done, def.features().memoryStatues().size()};
    }

    /** 記憶の氷像がすべて溶けたか（封印の条件 statues）。 */
    public static boolean allThawed(MinecraftServer server, FloorDef def) {
        int[] c = count(server, def);
        return c[1] > 0 && c[0] == c[1];
    }

    /** 溶けた記憶の氷像の印（封印の判定をクライアントでも同じにするため。氷像 i が溶けたら i 番目のビット）。 */
    public static int statueBits(MinecraftServer server, FloorDef def) {
        Data data = Data.get(server);
        int bits = 0;
        List<FloorFeatures.MemoryStatue> statues = def.features().memoryStatues();
        for (int i = 0; i < statues.size(); i++) {
            if (data.thawed(def.number(), statues.get(i).id())) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    // ================================================================ 結晶

    public static Item heatItem() {
        return ModItems.MANIFEST.get("heat_crystal").get();
    }

    public static Item memoryItem() {
        return ModItems.MANIFEST.get("memory_crystal").get();
    }

    /** 熱の結晶を1つ作る（もらった時刻つき）。 */
    public static ItemStack heatCrystal(long now) {
        ItemStack stack = new ItemStack(heatItem());
        stack.getOrCreateTag().putLong(MADE_TAG, now);
        return stack;
    }

    /** その人が持っているか（memory：記憶の結晶。そうでなければ熱の結晶）。 */
    public static boolean holds(Player player, boolean memory) {
        Item item = memory ? memoryItem() : heatItem();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return player.getOffhandItem().is(item);
    }

    /** 持っている結晶をなくす（数を返す）。 */
    public static int removeCrystals(Player player, boolean memory, int max) {
        Item item = memory ? memoryItem() : heatItem();
        int removed = 0;
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.add(player.getOffhandItem());
        for (ItemStack stack : all) {
            while (removed < max && stack.is(item) && !stack.isEmpty()) {
                stack.shrink(1);
                removed++;
            }
        }
        return removed;
    }

    /** 結晶を運んでいるか（どちらか）。 */
    public static boolean carrying(Player player) {
        return holds(player, false) || holds(player, true);
    }

    // ================================================================ 熱の炉

    /** 熱の炉を右クリックした：熱の結晶を1つもらう（もう持っていれば、もらえない）。 */
    public static void takeHeat(ServerPlayer player, int floor, String group, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null || def.features().hearths().isEmpty()) {
            return;
        }
        if (holds(player, false)) {
            player.displayClientMessage(Component.translatable("message.amemaru.village.heat_already").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        ItemStack stack = heatCrystal(level.getGameTime());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        String name = hearthName(def, group);
        player.displayClientMessage(Component.translatable("message.amemaru.village.heat_taken", name, CRYSTAL_SECONDS)
                .withStyle(ChatFormatting.GOLD), false);
        AmemaruMod.LOGGER.info("[amemaru] {} took a heat crystal from {} on floor {}", player.getGameProfile().getName(), group, floor);
    }

    private static String hearthName(FloorDef def, String group) {
        String id = group.startsWith("hearth:") ? group.substring("hearth:".length()) : group;
        for (FloorFeatures.Hearth hearth : def.features().hearths()) {
            if (hearth.id().equals(id)) {
                return hearth.name();
            }
        }
        return id;
    }

    // ================================================================ 記憶の氷像

    /** 氷像を右クリックした。記憶の氷像に熱の結晶を当てると、溶かし始める。 */
    public static void touchStatue(ServerPlayer player, BlockPos pos, VillageStatueBlockEntity statue) {
        long now = player.serverLevel().getGameTime();
        if (!statue.isMemory()) {
            if (now >= NOTICE.getOrDefault(player.getUUID(), 0L)) {
                player.displayClientMessage(Component.translatable("message.amemaru.village.statue_blank").withStyle(ChatFormatting.GRAY), true);
                NOTICE.put(player.getUUID(), now + 40);
            }
            return;
        }
        FloorDef def = FloorDefs.floorAt(player.level(), Vec3.atCenterOf(pos));
        FloorFeatures.MemoryStatue memory = def == null ? null : def.features().memoryStatue(statue.memory());
        if (memory == null || thawed(player.server, def.number(), memory.id())) {
            return;
        }
        if (!holds(player, false)) {
            player.displayClientMessage(Component.translatable("message.amemaru.village.need_heat", memory.name()).withStyle(ChatFormatting.YELLOW),
                    true);
            return;
        }
        Thaw current = THAWING.get(player.getUUID());
        if (current != null && current.pos().equals(pos)) {
            return;
        }
        THAWING.put(player.getUUID(), new Thaw(player, def.number(), memory.id(), pos.immutable(), now));
        player.serverLevel().playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.amemaru.village.thaw_start", memory.name()).withStyle(ChatFormatting.AQUA), true);
    }

    /** 溶かしている途中の人か（自動テスト用）。 */
    public static boolean isThawing(ServerPlayer player) {
        return THAWING.containsKey(player.getUUID());
    }

    /** 記憶の氷像が溶けた（管理者・自動テストからも）。そばの人に、その日の村の短い場面を流す。 */
    public static void thaw(MinecraftServer server, FloorDef def, FloorFeatures.MemoryStatue memory, @Nullable ServerPlayer by) {
        if (!Data.get(server).thaw(def.number(), memory.id())) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        int[] c = count(server, def);
        if (tower != null) {
            BlockPos at = def.world(memory.pos());
            VillageBlocks.Statue.setThawed(tower, at, true);
            tower.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2F, 0.7F);
            tower.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.9F);
            tower.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.6F);
            tower.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 60, 0.6, 0.9, 0.6, 0.06);
            tower.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 40, 0.8, 0.1, 0.8, 0.1);
            tower.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.04);
            for (ServerPlayer p : playersOn(tower, def)) {
                p.sendSystemMessage(Component.translatable("message.amemaru.village.thawed", memory.name(), c[0], c[1])
                        .withStyle(ChatFormatting.AQUA));
                if (p.position().distanceToSqr(Vec3.atCenterOf(at)) < 40 * 40) {
                    StoryEventManager.play(p, memory.event(), false);
                }
                if (c[0] == c[1]) {
                    ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                            Component.translatable("message.amemaru.village.all_thawed", def.name()).getString()));
                }
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Memory statue {} on floor {} thawed by {} ({}/{})", memory.id(), def.number(),
                by == null ? "admin" : by.getGameProfile().getName(), c[0], c[1]);
        resyncStory(server);
    }

    /** 見えているもの（氷像の溶けた・凍った）を、保存の状態に合わせる（読み込まれている所だけ）。 */
    public static void reconcile(ServerLevel level, FloorDef def) {
        Data data = Data.get(level.getServer());
        for (FloorFeatures.MemoryStatue memory : def.features().memoryStatues()) {
            BlockPos at = def.world(memory.pos());
            if (level.isLoaded(at)) {
                VillageBlocks.Statue.setThawed(level, at, data.thawed(def.number(), memory.id()));
            }
        }
    }

    /** 進行日が変わった：記憶の氷像はまた凍る。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        THAWING.clear();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().memoryStatues().isEmpty()) {
                    reconcile(tower, def);
                }
            }
        }
        resyncStory(server);
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        long now = tower.getGameTime();
        if (!THAWING.isEmpty()) {
            tickThaw(server, tower, now);
        }
        if (server.getTickCount() % 10 == 3) {
            carriers(server, tower, now, server.getTickCount() % 20 == 3);
        }
        if (server.getTickCount() % 40 == 11) {
            for (FloorDef def : FloorDefs.all()) {
                if (!def.features().memoryStatues().isEmpty() && !playersOn(tower, def).isEmpty()) {
                    reconcile(tower, def);
                }
            }
        }
    }

    private static void tickThaw(MinecraftServer server, ServerLevel tower, long now) {
        Iterator<Map.Entry<UUID, Thaw>> it = THAWING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Thaw> entry = it.next();
            Thaw thaw = entry.getValue();
            ServerPlayer player = thaw.player();
            FloorDef def = FloorDefs.byNumber(thaw.floor());
            FloorFeatures.MemoryStatue memory = def == null ? null : def.features().memoryStatue(thaw.memory());
            Vec3 at = Vec3.atBottomCenterOf(thaw.pos());
            if (memory == null || player.isRemoved() || !player.isAlive() || player.level() != tower || thawed(server, thaw.floor(), thaw.memory())) {
                it.remove();
                continue;
            }
            if (player.position().distanceTo(at) > THAW_RANGE || !holds(player, false)) {
                player.displayClientMessage(Component.translatable("message.amemaru.village.thaw_stop", memory.name()).withStyle(ChatFormatting.RED),
                        true);
                it.remove();
                continue;
            }
            long t = now - thaw.started();
            if (t % 4 == 0) {
                tower.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.4 + (t % 20) * 0.08, at.z, 3, 0.35, 0.2, 0.35, 0.01);
                tower.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 1.6, at.z, 4, 0.3, 0.4, 0.3, 0.0);
                tower.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.1, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
            }
            if (t % 20 == 0) {
                tower.playSound(null, thaw.pos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
                player.displayClientMessage(Component.translatable("message.amemaru.village.thawing", memory.name(),
                        Math.min(100, (int) (t * 100 / THAW_TICKS))).withStyle(ChatFormatting.AQUA), true);
            }
            if (t >= THAW_TICKS) {
                it.remove();
                removeCrystals(player, false, 1);
                thaw(server, def, memory, player);
            }
        }
    }

    /** 結晶を運ぶ人：足を遅くし、走れなくし、冷えた結晶を消し、まわりの敵の敵視をためる。階の外へ出たら結晶は砕ける。 */
    private static void carriers(MinecraftServer server, ServerLevel tower, long now, boolean second) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            carry(player, tower, now, second);
        }
    }

    /** 1人分（10 tick ごと。second は1秒ごとの回で、まわりの敵の敵視をためる）。自動テストからも呼ぶ。 */
    public static void carry(ServerPlayer player, ServerLevel tower, long now, boolean second) {
        boolean heat = holds(player, false);
        boolean memory = holds(player, true);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!heat && !memory) {
            if (speed != null && speed.getModifier(SLOW_ID) != null) {
                speed.removeModifier(SLOW_ID);
            }
            return;
        }
        FloorDef here = player.level() == tower ? FloorDefs.floorAt(tower, player.position()) : null;
        boolean village = here != null && (!here.features().hearths().isEmpty() || !here.features().memoryStatues().isEmpty());
        if (!village) {
            removeCrystals(player, false, 64);
            removeCrystals(player, true, 64);
            if (speed != null && speed.getModifier(SLOW_ID) != null) {
                speed.removeModifier(SLOW_ID);
            }
            player.displayClientMessage(Component.translatable("message.amemaru.village.crystal_shattered").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (heat) {
            expire(player, now);
            heat = holds(player, false);
        }
        if (speed != null && speed.getModifier(SLOW_ID) == null && (heat || memory)) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, "amemaru:carry_crystal", CARRY_SLOW,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        tower.sendParticles(memory ? ParticleTypes.END_ROD : ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 2,
                0.25, 0.3, 0.25, 0.01);
        if (second) {
            for (AmemaruMob mob : tower.getEntitiesOfClass(AmemaruMob.class, player.getBoundingBox().inflate(16.0))) {
                if (mob instanceof AmemaruBoss || !mob.isAlive()) {
                    continue;
                }
                mob.addThreat(player, CARRY_THREAT);
            }
        }
    }

    /** 運ぶ人の足が遅くなっているか（自動テスト用）。 */
    public static boolean slowed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(SLOW_ID) != null;
    }

    /** 冷えた熱の結晶を消す。 */
    private static void expire(ServerPlayer player, long now) {
        Item item = heatItem();
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.add(player.getOffhandItem());
        for (ItemStack stack : all) {
            if (!stack.is(item)) {
                continue;
            }
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains(MADE_TAG)) {
                tag.putLong(MADE_TAG, now);
            } else if (now - tag.getLong(MADE_TAG) > CRYSTAL_SECONDS * 20L) {
                stack.setCount(0);
                player.displayClientMessage(Component.translatable("message.amemaru.village.heat_cooled").withStyle(ChatFormatting.GRAY), false);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                        0.6F, 1.2F);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        THAWING.remove(event.getEntity().getUUID());
        NOTICE.remove(event.getEntity().getUUID());
    }

    // ================================================================ 共通

    private static List<ServerPlayer> playersOn(ServerLevel level, FloorDef def) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            FloorDef here = FloorDefs.floorAt(level, p.position());
            if (here != null && here.number() == def.number()) {
                list.add(p);
            }
        }
        return list;
    }

    private static void resyncStory(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.markDirty(PlayerData.SYNC_STORY);
            }
        }
    }
}
