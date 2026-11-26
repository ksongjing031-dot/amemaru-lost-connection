package jp.amemaru.lostconnection.gacha;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GachaS2C;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 祈願（ガチャ。11月27日のご指示【補完】）。
 *
 * <ul>
 *   <li>祈願札：その階のボスを<b>2回目以降</b>に倒すと、参加した一人ずつに、その階の祈願札（{@code boss_tokens} 枚）。1回目は出ない</li>
 *   <li>引く：塔の入口の祈願所の巫女（ユノ）に話して、階ごとの祈願を選ぶ。1回＝祈願札1枚、10回＝10枚（★4以上が1つは出る）</li>
 *   <li>星：★5（その階のガチャ限のユニーク武器）・★4（その階のガチャ限のアクセサリーと結晶果）・★3（素材と薬）。確率は祈願ごと
 *       （{@code gacha/default.json}）。★5が出ないまま {@code pity} 回目は必ず★5（天井。祈願ごと・人ごとに数える）</li>
 *   <li>出た品は持ち物へ（いっぱいなら足もとへ）。武器はその場でユニークに決まり、本人専用</li>
 * </ul>
 */
public final class GachaService {
    /** 画面を開いてから、引けるまま待てる時間（30分）。 */
    private static final int OPEN_TICKS = 20 * 60 * 30;

    private record Open(int tick, boolean admin) {
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    /** 引いた1つ。 */
    public record Result(int stars, ItemStack stack) {
    }

    private GachaService() {
    }

    // ---------------------------------------------------------------- 開く

    /** 祈願の画面を開く（巫女との会話から。{@code admin} は管理者のコマンドで、巫女から離れていても引ける）。 */
    public static void open(ServerPlayer player, String banner, boolean admin) {
        OPEN.put(player.getUUID(), new Open(player.server.getTickCount(), admin));
        CompoundTag tag = screenTag(player);
        tag.putString("select", banner);
        ModNetwork.sendTo(player, new GachaS2C("screen", tag));
    }

    /** 管理者のコマンドで開く（巫女から離れていても引ける。まだ開いていない祈願も見える）。 */
    public static void openAdmin(ServerPlayer player, String banner) {
        OPEN.put(player.getUUID(), new Open(player.server.getTickCount(), true));
        CompoundTag tag = screenTag(player);
        tag.putString("select", banner);
        tag.putBoolean("admin", true);
        ModNetwork.sendTo(player, new GachaS2C("screen", tag));
    }

    /** 画面に要る分：祈願ごとの名前・星の確率・中身・祈願札の数・天井までの回数・その階のボスを倒した数。 */
    public static CompoundTag screenTag(ServerPlayer player) {
        GachaRules rules = GachaRules.current();
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        CompoundTag tag = new CompoundTag();
        ListTag banners = new ListTag();
        for (GachaRules.Banner banner : rules.banners()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", banner.id());
            one.putInt("floor", banner.floor());
            one.putString("name", banner.name());
            one.putString("text", banner.text());
            one.putString("token", banner.token().toString());
            one.putInt("color", banner.color());
            one.putDouble("five", banner.rateFive());
            one.putDouble("four", banner.rateFour());
            one.putInt("pity", banner.pity());
            for (int stars = 3; stars <= 5; stars++) {
                ListTag items = new ListTag();
                for (GachaRules.Entry entry : banner.pool(stars)) {
                    CompoundTag item = new CompoundTag();
                    item.putString("item", entry.item().toString());
                    item.putInt("min", entry.minCount());
                    item.putInt("max", entry.maxCount());
                    item.putDouble("chance", chance(banner, stars, entry));
                    items.add(item);
                }
                one.put("pool" + stars, items);
            }
            one.putInt("tokens", countTokens(player, banner.token()));
            if (data != null) {
                AdventureData.GachaProgress progress = data.getAdventure().gacha(banner.id());
                one.putInt("pulls", progress.pulls());
                one.putInt("since", progress.sinceFive());
                one.putInt("fives", progress.fives());
                one.putInt("kills", data.getStory().bossKills(banner.floor()));
            }
            banners.add(one);
        }
        tag.put("banners", banners);
        tag.putInt("ten", rules.tenCount());
        tag.putInt("guarantee", rules.tenGuarantee());
        tag.putInt("boss_tokens", rules.bossTokens());
        return tag;
    }

    /** その品が1回で出る確率（%）。 */
    static double chance(GachaRules.Banner banner, int stars, GachaRules.Entry entry) {
        double rate = switch (stars) {
            case 5 -> banner.rateFive();
            case 4 -> banner.rateFour();
            default -> 100.0 - banner.rateFive() - banner.rateFour();
        };
        int total = 0;
        for (GachaRules.Entry one : banner.pool(stars)) {
            total += one.weight();
        }
        return total <= 0 ? 0.0 : rate * entry.weight() / total;
    }

    // ---------------------------------------------------------------- 引く

    /** クライアントの「引く」（{@code GachaC2S}）。 */
    public static void onPull(ServerPlayer player, String bannerId, int count) {
        GachaRules rules = GachaRules.current();
        GachaRules.Banner banner = rules.banner(bannerId);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (banner == null || data == null || (count != 1 && count != rules.tenCount())) {
            return;
        }
        if (!canUse(player, rules)) {
            fail(player, "gacha.amemaru.too_far");
            return;
        }
        if (countTokens(player, banner.token()) < count) {
            fail(player, "gacha.amemaru.not_enough");
            return;
        }
        if (!consumeTokens(player, banner.token(), count)) {
            fail(player, "gacha.amemaru.not_enough");
            return;
        }
        AdventureData.GachaProgress progress = data.getAdventure().gacha(banner.id());
        List<Result> results = roll(progress, banner, rules, count, player.getRandom());
        data.getAdventure().gachaChanged();
        int best = 3;
        ListTag list = new ListTag();
        for (Result result : results) {
            ItemStack stack = result.stack();
            finish(stack, player.serverLevel(), result.stars());
            best = Math.max(best, result.stars());
            CompoundTag one = new CompoundTag();
            one.putInt("stars", result.stars());
            one.put("stack", stack.save(new CompoundTag()));
            list.add(one);
            give(player, stack.copy());
        }
        CompoundTag tag = screenTag(player);
        tag.putString("select", banner.id());
        tag.putBoolean("admin", isAdmin(player));
        tag.put("results", list);
        tag.putInt("best", best);
        ModNetwork.sendTo(player, new GachaS2C("result", tag));
        if (best >= 5) {
            AmemaruMod.LOGGER.info("[amemaru] {} drew a 5-star from gacha {}", player.getGameProfile().getName(), banner.id());
            for (Result result : results) {
                if (result.stars() >= 5) {
                    // ★5は、サーバーのみんなのチャットに知らせる（画面の真ん中には出さない）
                    player.server.getPlayerList().broadcastSystemMessage(Component.translatable("gacha.amemaru.five_announce",
                            player.getDisplayName(), banner.name(), result.stack().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE),
                            false);
                }
            }
        }
    }

    /**
     * 引いた結果を決める（祈願の記録を進める）。10回なら、最後の1つが★4より下だけなら上げる。★5が出ないまま天井の回数目は★5。
     */
    public static List<Result> roll(AdventureData.GachaProgress progress, GachaRules.Banner banner, GachaRules rules, int count,
                                    RandomSource random) {
        List<Result> results = new ArrayList<>();
        boolean high = false;
        for (int i = 0; i < count; i++) {
            int stars;
            if (progress.sinceFive() + 1 >= banner.pity()) {
                stars = 5;
            } else {
                double r = random.nextDouble() * 100.0;
                stars = r < banner.rateFive() ? 5 : r < banner.rateFive() + banner.rateFour() ? 4 : 3;
            }
            boolean last = i == count - 1;
            if (last && count >= rules.tenCount() && !high && stars < rules.tenGuarantee()) {
                stars = rules.tenGuarantee();
            }
            if (stars >= 4) {
                high = true;
            }
            progress.record(stars >= 5);
            results.add(new Result(stars, pick(banner, stars, random)));
        }
        return results;
    }

    private static ItemStack pick(GachaRules.Banner banner, int stars, RandomSource random) {
        List<GachaRules.Entry> pool = banner.pool(stars);
        int total = 0;
        for (GachaRules.Entry entry : pool) {
            total += entry.weight();
        }
        int r = random.nextInt(Math.max(1, total));
        for (GachaRules.Entry entry : pool) {
            r -= entry.weight();
            if (r < 0) {
                Item item = ForgeRegistries.ITEMS.getValue(entry.item());
                if (item == null || item == Items.AIR) {
                    break;
                }
                int amount = entry.minCount() + (entry.maxCount() > entry.minCount() ? random.nextInt(entry.maxCount() - entry.minCount() + 1) : 0);
                return new ItemStack(item, amount);
            }
        }
        return new ItemStack(Items.AIR);
    }

    /** 装備とアクセサリーは、その場で決める（★5の武器は定義のレアリティ＝ユニーク）。 */
    private static void finish(ItemStack stack, ServerLevel level, int stars) {
        if (stack.getItem() instanceof GearItem) {
            EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
            EquipmentDef def = snapshot.get(stack);
            if (def != null && !GearData.isRolled(stack)) {
                GearData.roll(stack, def, snapshot.rules(), level.random, def.rarity(), false, null);
            }
        } else if (stack.getItem() instanceof AccessoryItem) {
            AccessoryService.ensureRolled(stack, level.random);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        CompoundTag tag = screenTag(player);
        tag.putBoolean("admin", isAdmin(player));
        tag.putString("error", key);
        ModNetwork.sendTo(player, new GachaS2C("error", tag));
    }

    /** 画面を使えるか（開いてから30分のうちで、祈願所の巫女から {@code npc_range} ブロック以内。管理者が開いたときは距離を見ない）。 */
    static boolean canUse(ServerPlayer player, GachaRules rules) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || player.server.getTickCount() - open.tick() > OPEN_TICKS) {
            return false;
        }
        if (open.admin()) {
            return true;
        }
        double range = rules.npcRange();
        AABB box = player.getBoundingBox().inflate(range);
        for (NpcEntity npc : player.serverLevel().getEntitiesOfClass(NpcEntity.class, box)) {
            if (npc.def() != null && npc.def().hasRole("gacha") && npc.distanceTo(player) <= range) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdmin(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        return open != null && open.admin();
    }

    /** 自動テストと管理者用：巫女がいなくても引けるようにする。 */
    public static void allowAnywhere(ServerPlayer player) {
        OPEN.put(player.getUUID(), new Open(player.server.getTickCount(), true));
    }

    // ---------------------------------------------------------------- 祈願札

    public static int countTokens(ServerPlayer player, ResourceLocation token) {
        Item item = ForgeRegistries.ITEMS.getValue(token);
        if (item == null || item == Items.AIR) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consumeTokens(ServerPlayer player, ResourceLocation token, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(token);
        if (item == null || countTokens(player, token) < count) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int left = count;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        inventory.setChanged();
        return left == 0;
    }

    /**
     * ボスを倒したとき（{@code BossRoom}。倒した数を足す前に呼ぶ）：その階のボスをもう倒したことがあれば、その階の祈願札。
     *
     * @return 渡した祈願札の数（1回目は0）
     */
    public static int onBossDefeated(ServerPlayer player, PlayerData data, int floor) {
        GachaRules rules = GachaRules.current();
        GachaRules.Banner banner = rules.bannerOfFloor(floor);
        if (banner == null || rules.bossTokens() <= 0 || data.getStory().bossKills(floor) < 1) {
            return 0;
        }
        Item item = ForgeRegistries.ITEMS.getValue(banner.token());
        if (item == null || item == Items.AIR) {
            return 0;
        }
        give(player, new ItemStack(item, rules.bossTokens()));
        player.sendSystemMessage(Component.translatable("gacha.amemaru.tokens", new ItemStack(item).getHoverName(), rules.bossTokens())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                1.0F, 1.2F);
        return rules.bossTokens();
    }

    /** ログアウトしたら、開いた印を消す。 */
    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    @Nullable
    static GachaRules.Banner bannerOf(String id) {
        return GachaRules.current().banner(id);
    }
}
