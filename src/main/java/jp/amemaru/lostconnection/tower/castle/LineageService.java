package jp.amemaru.lostconnection.tower.castle;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 9階「氷結した王城」の王家の系譜（11月28日。Phase 0-A 9章の9階「城中に散らばった名前の札を集め、肖像画の空白に正しく戻す
 * （名前を思い出させるパズル）」）【補完】。
 *
 * <ul>
 *   <li>城じゅうの名前の札の台を右クリックすると、その札（{@code amemaru:name_plate_<名前>}）をもらう（持っていなければ。人ごと）</li>
 *   <li>札を持って肖像画の下の台座を右クリックすると、はめる。正しければ札が台にはまり、肖像画に顔と名前が戻る（札はなくなる）。
 *       違う札は、はじかれる（札は戻る）：凍えが2つ、その組の近衛兵が2体目を覚ます</li>
 *   <li>手がかり：大広間の建国の碑（建国の3人）と、書庫の系譜の書（その後の6人と、霜でにじんだ女王の名）。肖像画を右クリックすると、
 *       描かれている物（剣と盾・白い薔薇…）が分かる</li>
 *   <li>組の肖像画がそろうと、その組の扉が開く（建国の肖像：大階段の氷の門・歌と鷹の肖像：回廊の扉）。すべてそろうと封印の条件
 *       {@code lineage}。戻った肖像画はみんなで同じ。進行日が変わると、また空白に戻る（扉は閉じる。中に人がいる間は待つ）</li>
 *   <li>女王の肖像：札はない。最後の家族がそろったあと、肖像のそばで女王の名をチャットで言うと、横の隠し扉が少しの間だけ開く
 *       （女王の書斎）。書斎の内がわの留め金でも開く</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LineageService {
    /** 女王の肖像の組の名前（肖像画のブロックの group）。 */
    public static final String QUEEN = "queen";
    private static final String PORTRAIT = "portrait:";
    private static final String PLATE = "plate:";
    private static final String CLUE = "clue:";
    /** 札を間違えて目を覚ました近衛兵の印（数えすぎないように）。 */
    private static final String WOKEN_TAG = "amemaru_lineage_woken";

    private LineageService() {
    }

    // ================================================================ 保存（みんなの分。進行日が変わるまで）

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_lineage";
        private final Set<String> restored = new LinkedHashSet<>();
        private final Map<Integer, Long> queenOpen = new LinkedHashMap<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            for (Tag t : tag.getList("restored", Tag.TAG_STRING)) {
                data.restored.add(t.getAsString());
            }
            CompoundTag q = tag.getCompound("queen_open");
            for (String key : q.getAllKeys()) {
                try {
                    data.queenOpen.put(Integer.parseInt(key), q.getLong(key));
                } catch (NumberFormatException ignored) {
                    // 読めない印は捨てる
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            restored.forEach(k -> list.add(StringTag.valueOf(k)));
            tag.put("restored", list);
            CompoundTag q = new CompoundTag();
            queenOpen.forEach((floor, until) -> q.putLong(String.valueOf(floor), until));
            tag.put("queen_open", q);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean restored(int floor, String portrait) {
            return restored.contains(floor + ":" + portrait);
        }

        boolean restore(int floor, String portrait) {
            boolean added = restored.add(floor + ":" + portrait);
            if (added) {
                setDirty();
            }
            return added;
        }

        long queenOpenUntil(int floor) {
            return queenOpen.getOrDefault(floor, 0L);
        }

        void setQueenOpen(int floor, long until) {
            if (until <= 0L) {
                queenOpen.remove(floor);
            } else {
                queenOpen.put(floor, until);
            }
            setDirty();
        }

        public void clear() {
            if (!restored.isEmpty() || !queenOpen.isEmpty()) {
                restored.clear();
                queenOpen.clear();
                setDirty();
            }
        }
    }

    // ================================================================ 数える

    @Nullable
    private static FloorFeatures.Lineage lineage(@Nullable FloorDef def) {
        return def == null ? null : def.features().lineage();
    }

    /** 戻った肖像画の数と、ぜんぶの数。 */
    public static int[] count(MinecraftServer server, FloorDef def) {
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return new int[]{0, 0};
        }
        Data data = Data.get(server);
        int done = 0;
        for (FloorFeatures.Portrait portrait : lineage.portraits()) {
            if (data.restored(def.number(), portrait.id())) {
                done++;
            }
        }
        return new int[]{done, lineage.portraits().size()};
    }

    public static boolean allSolved(MinecraftServer server, FloorDef def) {
        int[] c = count(server, def);
        return c[1] > 0 && c[0] == c[1];
    }

    /** その組の肖像画がすべて戻ったか。 */
    public static boolean groupSolved(MinecraftServer server, FloorDef def, String group) {
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return false;
        }
        Data data = Data.get(server);
        List<FloorFeatures.Portrait> portraits = lineage.portraitsOf(group);
        if (portraits.isEmpty()) {
            return false;
        }
        for (FloorFeatures.Portrait portrait : portraits) {
            if (!data.restored(def.number(), portrait.id())) {
                return false;
            }
        }
        return true;
    }

    /** 戻った肖像画の印（肖像画 i が戻ったら i 番目のビット）。 */
    public static int portraitBits(MinecraftServer server, FloorDef def) {
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return 0;
        }
        Data data = Data.get(server);
        int bits = 0;
        for (int i = 0; i < lineage.portraits().size(); i++) {
            if (data.restored(def.number(), lineage.portraits().get(i).id())) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    // ================================================================ 名前の札

    /** その名前の札のアイテム（なければ null）。 */
    @Nullable
    public static Item plateItem(String plate) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id("name_plate_" + plate));
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /** そのアイテムが名前の札なら、札の名前（でなければ null）。 */
    @Nullable
    public static String plateOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals(AmemaruMod.MOD_ID) || !id.getPath().startsWith("name_plate_")) {
            return null;
        }
        return id.getPath().substring("name_plate_".length());
    }

    private static boolean hasPlate(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 名前の札の台を右クリックした：その札をもらう（持っていなければ）。 */
    public static void takePlate(ServerPlayer player, int floor, String group, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Lineage lineage = lineage(def);
        String id = group.startsWith(PLATE) ? group.substring(PLATE.length()) : group;
        FloorFeatures.NamePlate plate = lineage == null ? null : lineage.plate(id);
        Item item = plateItem(id);
        if (plate == null || item == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (hasPlate(player, item)) {
            player.displayClientMessage(Component.translatable("message.amemaru.lineage.plate_have", plate.name()).withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.3F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 14, 0.2, 0.2, 0.2, 0.02);
        player.displayClientMessage(Component.translatable("message.amemaru.lineage.plate_take", plate.name()).withStyle(ChatFormatting.AQUA),
                false);
    }

    // ================================================================ 肖像画

    /** 肖像画（の台座）を右クリックした。 */
    public static void usePortrait(ServerPlayer player, int floor, String group, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return;
        }
        if (QUEEN.equals(group)) {
            showQueen(player, def, lineage);
            return;
        }
        String id = group.startsWith(PORTRAIT) ? group.substring(PORTRAIT.length()) : group;
        FloorFeatures.Portrait portrait = lineage.portrait(id);
        if (portrait == null) {
            return;
        }
        MinecraftServer server = player.server;
        Data data = Data.get(server);
        if (data.restored(floor, id)) {
            ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT, portrait.name(),
                    Component.translatable("lineage.amemaru.portrait." + id + ".named").getString(), 120));
            return;
        }
        String plate = plateOf(player.getMainHandItem());
        if (plate == null) {
            ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT,
                    Component.translatable("lineage.amemaru.portrait.blank_title").getString(),
                    Component.translatable("lineage.amemaru.portrait." + id + ".blank").getString() + "\n"
                            + Component.translatable("lineage.amemaru.portrait.blank_hint").getString(), 140));
            return;
        }
        if (plate.equals(id)) {
            player.getMainHandItem().shrink(1);
            restore(server, def, portrait, player);
        } else {
            reject(player, def, lineage, portrait, plate, pos);
        }
    }

    /** 肖像画に名前が戻る（管理者・自動テストからも。by は null でよい）。 */
    public static void restore(MinecraftServer server, FloorDef def, FloorFeatures.Portrait portrait, @Nullable ServerPlayer by) {
        Data data = Data.get(server);
        if (!data.restore(def.number(), portrait.id())) {
            return;
        }
        FloorFeatures.Lineage lineage = lineage(def);
        ServerLevel tower = FloorDefs.towerLevel(server);
        int[] c = count(server, def);
        String groupName = groupName(lineage, portrait.group());
        List<FloorFeatures.Portrait> inGroup = lineage == null ? List.of() : lineage.portraitsOf(portrait.group());
        int groupDone = 0;
        for (FloorFeatures.Portrait p : inGroup) {
            groupDone += data.restored(def.number(), p.id()) ? 1 : 0;
        }
        if (tower != null) {
            BlockPos at = def.world(portrait.pos());
            setRestored(tower, at, true);
            tower.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 2.5, at.getZ() + 0.5, 50, 0.8, 1.4, 0.8, 0.04);
            tower.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.6F, 0.8F);
            tower.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            for (ServerPlayer p : playersOn(tower, def)) {
                p.sendSystemMessage(Component.translatable("message.amemaru.lineage.restored", portrait.name(), groupName, groupDone,
                        inGroup.size()).withStyle(ChatFormatting.GOLD));
            }
            if (by != null) {
                ModNetwork.sendTo(by, new StoryTextS2C(StoryTextS2C.Kind.SUBTITLE, "",
                        Component.translatable("message.amemaru.lineage.restored_line", portrait.name()).getString(), 80));
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Portrait {} on floor {} restored by {} ({}/{})", portrait.id(), def.number(),
                by == null ? "admin" : by.getGameProfile().getName(), c[0], c[1]);
        if (groupDone == inGroup.size()) {
            onGroupSolved(server, def, lineage, portrait.group());
        }
        resyncStory(server);
    }

    private static void onGroupSolved(MinecraftServer server, FloorDef def, @Nullable FloorFeatures.Lineage lineage, String group) {
        if (lineage == null) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        FloorFeatures.LineageGroup g = lineage.group(group);
        boolean all = allSolved(server, def);
        if (tower != null && g != null) {
            if (g.door() != null) {
                fill(tower, def, g.door(), Blocks.AIR.defaultBlockState());
            }
            for (ServerPlayer p : playersOn(tower, def)) {
                ModNetwork.sendTo(p, new AnnouncementS2C(AnnouncementS2C.Kind.STORY,
                        Component.translatable("message.amemaru.lineage.group_done." + group, g.name()).getString()));
                if (all) {
                    p.sendSystemMessage(Component.translatable("message.amemaru.lineage.all_done", lineage.portraits().size())
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                    jp.amemaru.lostconnection.story.StoryEventManager.play(p, AmemaruMod.id("floor9/lineage_done"), false);
                }
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] Lineage group {} on floor {} solved (all={})", group, def.number(), all);
    }

    /** 違う札：はじかれる。凍えが2つ。その組の近衛兵が目を覚ます（すでに起きている人数が少なければ）。 */
    private static void reject(ServerPlayer player, FloorDef def, FloorFeatures.Lineage lineage, FloorFeatures.Portrait portrait, String plate,
                               BlockPos pos) {
        ServerLevel level = player.serverLevel();
        FloorFeatures.NamePlate wrong = lineage.plate(plate);
        String name = wrong == null ? plate : wrong.name();
        level.playSound(null, pos, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1.4F, 0.6F);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.1);
        jp.amemaru.lostconnection.combat.PlayerColdService.chill(player, 2, 100);
        Vec3 away = player.position().subtract(Vec3.atCenterOf(pos)).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() > 1.0E-4) {
            away = away.normalize().scale(0.8);
            player.push(away.x, 0.3, away.z);
            player.hurtMarked = true;
        }
        player.displayClientMessage(Component.translatable("message.amemaru.lineage.wrong", name).withStyle(ChatFormatting.RED), false);
        FloorFeatures.LineageGroup group = lineage.group(portrait.group());
        if (group != null) {
            wakeGuards(level, def, group);
        }
    }

    /** 札を間違えた組の近衛兵を、目を覚まして出す（その組のまわりに、もう3体より多く起きていれば出さない）。 */
    private static void wakeGuards(ServerLevel level, FloorDef def, FloorFeatures.LineageGroup group) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(AmemaruMod.id("frozen_guard"));
        if (type == null || group.guards().isEmpty()) {
            return;
        }
        BlockPos first = def.world(group.guards().get(0));
        int awake = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(first).inflate(32.0),
                e -> e.isAlive() && e.getTags().contains(WOKEN_TAG)).size();
        if (awake >= 3) {
            return;
        }
        for (BlockPos local : group.guards()) {
            BlockPos at = def.world(local);
            Entity entity = type.create(level);
            if (!(entity instanceof jp.amemaru.lostconnection.enemy.AmemaruMob mob)) {
                continue;
            }
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            mob.addTag(WOKEN_TAG);
            mob.setHome(at);
            if (mob instanceof jp.amemaru.lostconnection.enemy.castle.FrozenGuard guard) {
                guard.wakeNow();
            }
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 30, 0.5, 1.0, 0.5, 0.05);
        }
        level.playSound(null, first, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    private static String groupName(@Nullable FloorFeatures.Lineage lineage, String group) {
        FloorFeatures.LineageGroup g = lineage == null ? null : lineage.group(group);
        return g == null ? group : g.name();
    }

    // ================================================================ 手がかり

    /** 建国の碑・系譜の書を読む。 */
    public static void showClue(ServerPlayer player, int floor, String group, BlockPos pos) {
        String id = group.startsWith(CLUE) ? group.substring(CLUE.length()) : group;
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT,
                Component.translatable("lineage.amemaru.clue." + id + ".title").getString(),
                Component.translatable("lineage.amemaru.clue." + id).getString(), 260));
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.5);
        // 同じ階のパーティの仲間にも、読んだことだけ知らせる（声をかけ合う手がかり）
        for (ServerPlayer mate : jp.amemaru.lostconnection.party.PartyService.onlineMembers(player)) {
            if (mate != player && mate.level() == player.level()) {
                mate.sendSystemMessage(Component.translatable("message.amemaru.lineage.clue_read", player.getDisplayName(),
                        Component.translatable("lineage.amemaru.clue." + id + ".title")).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ================================================================ 女王の肖像と隠し扉

    private static void showQueen(ServerPlayer player, FloorDef def, FloorFeatures.Lineage lineage) {
        boolean ready = lineage.groups().stream().allMatch(g -> groupSolved(player.server, def, g.id()));
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT,
                Component.translatable("lineage.amemaru.queen.title").getString(),
                Component.translatable(ready ? "lineage.amemaru.queen.ready" : "lineage.amemaru.queen.blank").getString(), 160));
    }

    /** 女王の名か（ひらがなはカタカナに、空白と「様」「さま」「陛下」をのぞいて比べる）。 */
    public static boolean isQueenName(String message, List<String> names) {
        String text = normalize(message);
        for (String name : names) {
            String n = normalize(name);
            if (!n.isEmpty() && text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c) || c == '・' || c == '、' || c == '。' || c == '!' || c == '?' || c == '〜') {
                continue;
            }
            if (c >= 'ぁ' && c <= 'ゖ') {
                c = (char) (c + ('ァ' - 'ぁ'));
            }
            out.append(c);
        }
        return out.toString().replace("サマ", "").replace("様", "").replace("陛下", "");
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || !player.level().dimension().equals(FloorDefs.TOWER)) {
            return;
        }
        FloorDef def = FloorDefs.floorAt(player.level(), player.position());
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null || lineage.queen() == null) {
            return;
        }
        FloorFeatures.QueenPortrait queen = lineage.queen();
        if (!FloorFeatures.inside(queen.area(), player.position(), def.originVec())) {
            return;
        }
        String message = event.getRawText();
        if (!isQueenName(message, queen.names())) {
            return;
        }
        MinecraftServer server = player.server;
        server.execute(() -> callQueen(player, def, lineage));
    }

    /** 女王の肖像の前で、女王の名を呼んだ。最後の家族がそろっていれば、隠し扉が開く。開いたら true。 */
    public static boolean callQueen(ServerPlayer player, FloorDef def, FloorFeatures.Lineage lineage) {
        FloorFeatures.QueenPortrait queen = lineage.queen();
        if (queen == null) {
            return false;
        }
        boolean ready = lineage.groups().stream().allMatch(g -> groupSolved(player.server, def, g.id()));
        ServerLevel level = player.serverLevel();
        BlockPos at = def.world(queen.pos());
        if (!ready) {
            player.displayClientMessage(Component.translatable("message.amemaru.lineage.queen_locked").withStyle(ChatFormatting.GRAY), false);
            level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
            return false;
        }
        openQueenDoor(player.server, def, player);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        ResourceLocation found = AmemaruMod.id("story/queen_door_found");
        if (data != null && !data.getStory().has(found)) {
            data.getStory().add(found);
            jp.amemaru.lostconnection.story.StoryEventManager.play(player, AmemaruMod.id("floor9/queen_name"), false);
        }
        return true;
    }

    /** 留め金（書斎の内がわ）から開ける。 */
    public static void useLatch(ServerPlayer player, int floor, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null || lineage.queen() == null) {
            return;
        }
        openQueenDoor(player.server, def, player);
        player.serverLevel().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    /** 隠し扉を {@code open_seconds} 秒開ける（管理者・自動テストからも）。 */
    public static void openQueenDoor(MinecraftServer server, FloorDef def, @Nullable ServerPlayer by) {
        FloorFeatures.Lineage lineage = lineage(def);
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (lineage == null || lineage.queen() == null || tower == null) {
            return;
        }
        FloorFeatures.QueenPortrait queen = lineage.queen();
        Data.get(server).setQueenOpen(def.number(), tower.getGameTime() + queen.openSeconds() * 20L);
        fill(tower, def, queen.door(), Blocks.AIR.defaultBlockState());
        BlockPos at = def.world(queen.pos());
        tower.playSound(null, at, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.5F);
        tower.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 0.6F);
        tower.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 60, 1.0, 1.5, 1.0, 0.05);
        if (by != null) {
            by.displayClientMessage(Component.translatable("message.amemaru.lineage.queen_open", queen.openSeconds())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        AmemaruMod.LOGGER.info("[amemaru] Queen's door on floor {} opened by {}", def.number(),
                by == null ? "admin" : by.getGameProfile().getName());
    }

    /** 隠し扉が開いているか。 */
    public static boolean queenDoorOpen(MinecraftServer server, FloorDef def) {
        ServerLevel tower = FloorDefs.towerLevel(server);
        return tower != null && Data.get(server).queenOpenUntil(def.number()) > tower.getGameTime();
    }

    // ================================================================ 管理者

    /** 組の肖像画をすべて戻す（{@code group} が "" なら、すべての組）。 */
    public static void solve(MinecraftServer server, FloorDef def, String group) {
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return;
        }
        for (FloorFeatures.Portrait portrait : lineage.portraits()) {
            if (group.isEmpty() || portrait.group().equals(group)) {
                restore(server, def, portrait, null);
            }
        }
    }

    /** 進行日が変わった（管理者のリセットからも）：肖像画はまた空白に、扉は閉じる（中に人がいれば、出るまで待つ）。 */
    public static void onDayChanged(MinecraftServer server) {
        Data.get(server).clear();
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower != null) {
            for (FloorDef def : FloorDefs.all()) {
                if (lineage(def) != null) {
                    reconcile(tower, def);
                }
            }
        }
        resyncStory(server);
    }

    // ================================================================ 見えているもの

    /** 肖像画・扉・隠し扉を、保存の状態に合わせる（読み込まれている所だけ）。 */
    public static void reconcile(ServerLevel level, FloorDef def) {
        FloorFeatures.Lineage lineage = lineage(def);
        if (lineage == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        Data data = Data.get(server);
        for (FloorFeatures.Portrait portrait : lineage.portraits()) {
            setRestored(level, def.world(portrait.pos()), data.restored(def.number(), portrait.id()));
        }
        for (FloorFeatures.LineageGroup group : lineage.groups()) {
            if (group.door() == null) {
                continue;
            }
            boolean open = groupSolved(server, def, group.id());
            if (!open && anyoneIn(level, def, group.door())) {
                continue;
            }
            fill(level, def, group.door(), open ? Blocks.AIR.defaultBlockState() : stateOf(group.door(), Blocks.BLUE_ICE));
        }
        FloorFeatures.QueenPortrait queen = lineage.queen();
        if (queen != null) {
            boolean open = data.queenOpenUntil(def.number()) > level.getGameTime();
            if (open || !anyoneIn(level, def, queen.door())) {
                fill(level, def, queen.door(), open ? Blocks.AIR.defaultBlockState() : stateOf(queen.door(), Blocks.CALCITE));
            }
            if (!open && data.queenOpenUntil(def.number()) > 0L && !anyoneIn(level, def, queen.door())) {
                data.setQueenOpen(def.number(), 0L);
                for (ServerPlayer p : playersOn(level, def)) {
                    if (p.blockPosition().distSqr(def.world(queen.pos())) < 24 * 24) {
                        p.displayClientMessage(Component.translatable("message.amemaru.lineage.queen_close").withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
        }
    }

    private static void setRestored(ServerLevel level, BlockPos pos, boolean restored) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CastleBlocks.Portrait && state.getValue(CastleBlocks.RESTORED) != restored) {
            level.setBlock(pos, state.setValue(CastleBlocks.RESTORED, restored), 3);
        }
    }

    private static BlockState stateOf(FloorFeatures.Door door, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(door.block());
        return (block == null || block == Blocks.AIR ? fallback : block).defaultBlockState();
    }

    private static boolean anyoneIn(ServerLevel level, FloorDef def, FloorFeatures.Door door) {
        BlockPos min = def.world(door.min());
        BlockPos max = def.world(door.max());
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(0.3);
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && box.intersects(p.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    /** 箱の中を、そのブロックにする（変わる所だけ）。 */
    private static void fill(ServerLevel level, FloorDef def, FloorFeatures.Door door, BlockState want) {
        BlockPos min = def.world(door.min());
        BlockPos max = def.world(door.max());
        boolean changed = false;
        for (BlockPos at : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState now = level.getBlockState(at);
            if (now.getBlock() == want.getBlock()) {
                continue;
            }
            level.setBlock(at.immutable(), want, 3);
            changed = true;
        }
        if (changed && level.isLoaded(min)) {
            Vec3 c = new Vec3((min.getX() + max.getX()) / 2.0 + 0.5, (min.getY() + max.getY()) / 2.0 + 0.5, (min.getZ() + max.getZ()) / 2.0 + 0.5);
            level.sendParticles(want.isAir() ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD, c.x, c.y, c.z, 50,
                    Math.max(0.6, (max.getX() - min.getX()) / 2.0), 1.0, Math.max(0.6, (max.getZ() - min.getZ()) / 2.0), 0.05);
            level.playSound(null, c.x, c.y, c.z, want.isAir() ? SoundEvents.GLASS_BREAK : SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                    1.4F, 0.7F);
        }
    }

    // ================================================================ 毎 tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 7) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        Data data = Data.get(server);
        for (FloorDef def : FloorDefs.all()) {
            FloorFeatures.Lineage lineage = lineage(def);
            if (lineage == null || playersOn(tower, def).isEmpty()) {
                continue;
            }
            long until = data.queenOpenUntil(def.number());
            boolean timeUp = until > 0L && until <= tower.getGameTime();
            if (timeUp || tick % 40 == 7) {
                reconcile(tower, def);
            }
        }
    }

    static List<ServerPlayer> playersOn(ServerLevel level, FloorDef def) {
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
