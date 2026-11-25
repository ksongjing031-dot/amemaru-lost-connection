package jp.amemaru.lostconnection.town;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.fishing.FishDef;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.fishing.FishItem;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.ProjectS2C;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ペトラ・ポムの料理屋（Phase 7 その4。共同事業③「料理屋の開店：料理による一時強化」。Phase 0-A 7章「魚や素材から、戦闘用の料理
 * （一時的な強化）を作る。釣りが戦闘を支える導線」・8章「ペトラ・ポム：料理人。魚料理が得意。ポッカ爺から魚を仕入れる」）。
 *
 * <ul>
 *   <li>ペトラの会話から料理屋の画面を開き、材料（釣った魚と、商店街で買える野菜など）と代金で料理を作ってもらう</li>
 *   <li>料理を食べると、しばらく（20分）強くなる。強化は1つだけで、新しく食べると入れかわり、倒れると消える【補完】</li>
 *   <li>魚は「★がその数以上」の魚から、★の低い・安い魚を先に使う。記憶魚と湖の主は使わない【補完】</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class RestaurantService {
    /** 食べた料理の強化（プレイヤーの永続データ。倒れると消える）。 */
    private static final String FOOD = "AmemaruFood";
    private static final long OPEN_TICKS = 20L * 60 * 30;
    private static final double OPEN_RANGE = 12.0;

    private record Open(Vec3 at, long tick, boolean admin) {
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    private RestaurantService() {
    }

    /** 料理屋が開いているか（共同事業③の印）。 */
    public static boolean isOpen(MinecraftServer server) {
        return StoryState.get(server).has(RestaurantRules.current().flag());
    }

    // ================================================================ 画面

    public static void open(ServerPlayer player, boolean admin) {
        if (!admin && !isOpen(player.server)) {
            player.displayClientMessage(Component.translatable("restaurant.amemaru.closed").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        OPEN.put(player.getUUID(), new Open(player.position(), player.server.getTickCount(), admin));
        ModNetwork.sendTo(player, new ProjectS2C("restaurant", screenTag(player)));
    }

    static boolean canUse(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.server.getTickCount() - open.tick() > OPEN_TICKS) {
            return false;
        }
        return open.admin() || player.position().distanceTo(open.at()) <= OPEN_RANGE;
    }

    static CompoundTag screenTag(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElseThrow();
        RestaurantRules rules = RestaurantRules.current();
        CompoundTag tag = new CompoundTag();
        tag.putLong("knots", Wallet.balance(data));
        tag.putInt("minutes", rules.minutes());
        ListTag dishes = new ListTag();
        for (RestaurantRules.Dish dish : rules.dishes()) {
            CompoundTag one = new CompoundTag();
            one.putString("item", dish.item().toString());
            one.put("stack", dishStack(dish, rules.minutes()).save(new CompoundTag()));
            one.putString("effect", Component.Serializer.toJson(effectText(dish.stat().id(), dish.percent(), dish.amount(), rules.minutes())));
            one.putLong("fee", dish.fee());
            boolean all = Wallet.balance(data) >= dish.fee();
            ListTag ing = new ListTag();
            for (RestaurantRules.Ingredient in : dish.ingredients()) {
                CompoundTag it = new CompoundTag();
                int have = count(player, in);
                it.putInt("need", in.count());
                it.putInt("have", have);
                if (in.fishStars() > 0) {
                    it.putInt("stars", in.fishStars());
                } else {
                    Item item = ForgeRegistries.ITEMS.getValue(in.item());
                    it.put("stack", new ItemStack(item == null ? net.minecraft.world.item.Items.BARRIER : item).save(new CompoundTag()));
                }
                all &= have >= in.count();
                ing.add(it);
            }
            one.put("ingredients", ing);
            one.putBoolean("can", all);
            dishes.add(one);
        }
        tag.put("dishes", dishes);
        CompoundTag food = active(player);
        if (food != null) {
            tag.putString("food", Component.Serializer.toJson(Component.translatable("restaurant.amemaru.active",
                    itemName(food.getString("item")), effectText(food.getString("stat"), food.getBoolean("percent"), food.getDouble("amount"), 0),
                    Math.max(1, (food.getLong("until") - System.currentTimeMillis() + 59_999) / 60_000))));
        }
        return tag;
    }

    /** 画面の操作（{@link jp.amemaru.lostconnection.network.c2s.ProjectC2S} の cook）。 */
    public static void onAction(ServerPlayer player, CompoundTag args) {
        if (!canUse(player)) {
            player.displayClientMessage(Component.translatable("restaurant.amemaru.far").withStyle(ChatFormatting.RED), true);
            return;
        }
        RestaurantRules.Dish dish = RestaurantRules.current().dish(ResourceLocation.tryParse(args.getString("dish")));
        String error = dish == null ? "restaurant.amemaru.error.dish" : cook(player, dish);
        if (error != null) {
            player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
        }
        CompoundTag tag = screenTag(player);
        tag.putBoolean("refresh", true);
        ModNetwork.sendTo(player, new ProjectS2C("restaurant", tag));
    }

    // ================================================================ 料理を作る

    /** 料理を作ってもらう（材料と代金を出す）。うまくいかなければ、わけの翻訳キー。 */
    @Nullable
    public static String cook(ServerPlayer player, RestaurantRules.Dish dish) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return "restaurant.amemaru.error.dish";
        }
        for (RestaurantRules.Ingredient in : dish.ingredients()) {
            if (count(player, in) < in.count()) {
                return "restaurant.amemaru.error.ingredients";
            }
        }
        if (Wallet.balance(data) < dish.fee()) {
            return "restaurant.amemaru.error.knots";
        }
        if (dish.fee() > 0 && !Wallet.pay(player, data, dish.fee(), "restaurant")) {
            return "restaurant.amemaru.error.knots";
        }
        for (RestaurantRules.Ingredient in : dish.ingredients()) {
            take(player, in);
        }
        ItemStack stack = dishStack(dish, RestaurantRules.current().minutes());
        Component name = stack.getHoverName();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable("restaurant.amemaru.cooked", name).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMOKER_SMOKE, SoundSource.PLAYERS, 0.8F, 1.1F);
        return null;
    }

    /** 料理のアイテム（強化の中身を書く）。 */
    public static ItemStack dishStack(RestaurantRules.Dish dish, int minutes) {
        Item item = ForgeRegistries.ITEMS.getValue(dish.item());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTagElement(DishItem.TAG);
        tag.putString("stat", dish.stat().id());
        tag.putBoolean("percent", dish.percent());
        tag.putDouble("amount", dish.amount());
        tag.putInt("minutes", minutes);
        return stack;
    }

    /** その材料を、持ち物にいくつ持っているか（魚は★がその数以上の★1〜3の魚。記憶魚と湖の主は数えない）。 */
    static int count(ServerPlayer player, RestaurantRules.Ingredient in) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, in)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static boolean matches(ItemStack stack, RestaurantRules.Ingredient in) {
        if (stack.isEmpty()) {
            return false;
        }
        if (in.fishStars() > 0) {
            FishDef def = FishDefs.of(stack);
            return def != null && def.rarity() >= in.fishStars() && def.rarity() <= 3;
        }
        return in.item() != null && in.item().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** 材料を持ち物から出す（魚は、★の低い・安い順。大物は最後）。 */
    private static void take(ServerPlayer player, RestaurantRules.Ingredient in) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, in)) {
                items.add(stack);
            }
        }
        if (in.fishStars() > 0) {
            items.sort(Comparator.comparingInt((ItemStack s) -> FishItem.isBig(s) ? 1 : 0)
                    .thenComparingInt(s -> FishDefs.of(s).rarity())
                    .thenComparingLong(s -> FishDefs.of(s).price()));
        }
        int left = in.count();
        for (ItemStack stack : items) {
            int n = Math.min(left, stack.getCount());
            stack.shrink(n);
            left -= n;
            if (left <= 0) {
                break;
            }
        }
    }

    // ================================================================ 食べる・強化

    /** 料理を食べた：強化を入れかえる（強化は1つだけ）。 */
    public static void eat(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getTagElement(DishItem.TAG);
        RestaurantRules rules = RestaurantRules.current();
        RestaurantRules.Dish dish = rules.dish(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        String stat;
        boolean percent;
        double amount;
        int minutes;
        if (tag != null && tag.contains("stat")) {
            stat = tag.getString("stat");
            percent = tag.getBoolean("percent");
            amount = tag.getDouble("amount");
            minutes = tag.getInt("minutes") > 0 ? tag.getInt("minutes") : rules.minutes();
        } else if (dish != null) {
            stat = dish.stat().id();
            percent = dish.percent();
            amount = dish.amount();
            minutes = rules.minutes();
        } else {
            return;
        }
        CompoundTag food = new CompoundTag();
        food.putString("item", String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        food.putString("stat", stat);
        food.putBoolean("percent", percent);
        food.putDouble("amount", amount);
        food.putLong("until", System.currentTimeMillis() + minutes * 60_000L);
        player.getPersistentData().put(FOOD, food);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        player.sendSystemMessage(Component.translatable("restaurant.amemaru.ate", stack.getHoverName(),
                effectText(stat, percent, amount, minutes)).withStyle(ChatFormatting.GOLD));
    }

    /** 今効いている料理の強化（なければ null）。 */
    @Nullable
    public static CompoundTag active(ServerPlayer player) {
        CompoundTag food = player.getPersistentData().getCompound(FOOD);
        if (food.isEmpty() || food.getLong("until") <= System.currentTimeMillis()) {
            return null;
        }
        return food;
    }

    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        CompoundTag food = active(player);
        if (food == null) {
            return;
        }
        StatType.byId(food.getString("stat")).ifPresent(stat -> {
            if (food.getBoolean("percent")) {
                event.addPercent(stat, food.getDouble("amount"));
            } else {
                event.addFlat(stat, food.getDouble("amount"));
            }
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 13) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            CompoundTag food = player.getPersistentData().getCompound(FOOD);
            if (!food.isEmpty() && food.getLong("until") <= now) {
                player.getPersistentData().remove(FOOD);
                PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
                player.displayClientMessage(Component.translatable("restaurant.amemaru.worn_off", itemName(food.getString("item")))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }

    /** 自動テスト：料理の強化を消す。 */
    public static void clearForTest(ServerPlayer player) {
        player.getPersistentData().remove(FOOD);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
    }

    // ================================================================ 文

    /** 「攻撃 +5%（20分）」。minutes が 0 なら時間を書かない。 */
    public static MutableComponent effectText(String stat, boolean percent, double amount, int minutes) {
        String value = (amount == Math.floor(amount) ? String.valueOf((long) amount) : String.format(Locale.ROOT, "%.1f", amount))
                + (percent ? "%" : "");
        MutableComponent line = Component.translatable("restaurant.amemaru.effect", Component.translatable("stat.amemaru." + stat), value);
        return minutes > 0 ? Component.translatable("restaurant.amemaru.effect_time", line, minutes) : line;
    }

    private static Component itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
    }
}
