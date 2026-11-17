package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.loot.LootItemEntity;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * CIの写真（Phase 3）の準備（{@link CiScreenshots} から呼ぶ。遊ぶときは動かない）。
 *
 * <p>同じゲームの中のサーバー（シングルプレイ）で、装備を着せる・ドロップを置くなどを直接行う。
 * 品はコマンドと同じ方法で抽選し、見た目を確かめやすい数値（品質・強化）に決める。</p>
 */
final class CiPhase3 {
    /** 写真に写す装備・アクセサリーの抽選に使う乱数（毎回同じ見た目にする）。 */
    private static final long SEED = 20261118L;

    private CiPhase3() {
    }

    /** サーバー側のプレイヤーで行う（次の tick に動く）。 */
    static void onServer(Minecraft minecraft, Consumer<ServerPlayer> action) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        UUID id = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                try {
                    action.accept(player);
                } catch (RuntimeException e) {
                    AmemaruMod.LOGGER.error("[amemaru] CI: phase 3 setup failed", e);
                }
            }
        });
    }

    // ---------------------------------------------------------------- 品を作る

    /** レアリティ・品質（割合）・強化を決めた装備。 */
    static ItemStack gear(String path, Rarity rarity, double ratio, int enhance) {
        Item item = item(path);
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        ItemStack stack = new ItemStack(item);
        EquipmentDef def = snapshot.get(stack);
        if (def == null) {
            return stack;
        }
        GearData.roll(stack, def, snapshot.rules(), RandomSource.create(SEED), rarity, false, null);
        GearData.setAllRatios(stack, def, ratio);
        GearData.setEnhance(stack, enhance);
        return stack;
    }

    /** サブの数と割合を決めたアクセサリー。 */
    static ItemStack accessory(String path, int subs, double ratio) {
        ItemStack stack = new ItemStack(item(path));
        AccessoryDef def = AccessoryDefs.get(stack);
        if (def != null) {
            AccessoryRoller.roll(stack, def, AccessoryRules.current(), RandomSource.create(SEED + subs), false, null, subs);
            AccessoryRoller.forceRatio(stack, AccessoryRules.current(), ratio);
        }
        return stack;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(path));
        return item == null ? net.minecraft.world.item.Items.BARRIER : item;
    }

    // ---------------------------------------------------------------- 準備

    /** 図鑑の見本：4種類の敵を倒した数と、手に入れた品。 */
    static void fillBestiary(ServerPlayer player) {
        PlayerDataCapability.get(player).ifPresent(data -> {
            AdventureData adventure = data.getAdventure();
            record(adventure, "tsunomofu", 12, "tsunomofu_fluff", "moss_iron", "soft_horn");
            record(adventure, "ishikure_soldier", 7, "ancient_stone", "moss_iron", "sentinel_blade");
            record(adventure, "crystal_bee", 9, "bee_stinger", "crystal_shard");
            record(adventure, "kakekiba_wolf", 5, "chipped_fang", "echo_fragment", "crystal_fruit_crimson");
        });
    }

    private static void record(AdventureData adventure, String enemy, int kills, String... drops) {
        ResourceLocation id = AmemaruMod.id(enemy);
        for (int i = 0; i < kills; i++) {
            adventure.recordKill(id);
        }
        for (String drop : drops) {
            adventure.recordDrop(id, AmemaruMod.id(drop));
        }
    }

    /** レアドロップの見本：目の前に見張りの石刃（レア）と蜂晶の杖（レジェンダリー）を置き、光の柱と知らせを出す。 */
    static void rareDrop(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        ItemStack blade = gear("sentinel_blade", Rarity.RARE, 0.93, 0);
        GearData.markRareDrop(blade);
        ItemStack staff = gear("bee_crystal_staff", Rarity.LEGENDARY, 0.97, 0);
        GearData.markRareDrop(staff);
        Vec3 first = player.position().add(look.scale(3.2)).add(side.scale(-0.9));
        Vec3 second = player.position().add(look.scale(4.4)).add(side.scale(1.4));
        spawnStill(level, first, blade, player.getUUID(), Rarity.RARE);
        spawnStill(level, second, staff, player.getUUID(), Rarity.LEGENDARY);
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.RARE_DROP, staff.copy()));
    }

    private static void spawnStill(ServerLevel level, Vec3 at, ItemStack stack, UUID owner, Rarity rarity) {
        LootItemEntity entity = LootItemEntity.spawn(level, at.x, at.y + 0.1, at.z, stack, owner, rarity.ordinal());
        entity.setDeltaMovement(Vec3.ZERO);
    }

    /** 重鎧の見本：見張りの石刃（エピック+7）・古代石の重鎧（+4）・首飾り・腕輪・お守り。指輪（完璧）は持ち物に。 */
    static void equipPlate(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.MAINHAND, gear("sentinel_blade", Rarity.EPIC, 0.95, 7));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, gear("ancient_stone_helm", Rarity.UNCOMMON, 0.9, 4));
        player.setItemSlot(EquipmentSlot.CHEST, gear("ancient_stone_cuirass", Rarity.UNCOMMON, 0.9, 4));
        player.setItemSlot(EquipmentSlot.LEGS, gear("ancient_stone_greaves", Rarity.UNCOMMON, 0.9, 4));
        player.setItemSlot(EquipmentSlot.FEET, gear("ancient_stone_sabatons", Rarity.UNCOMMON, 0.9, 4));
        setCurio(player, AccessorySlot.NECKLACE, accessory("grassland_necklace", 3, 0.9));
        setCurio(player, AccessorySlot.BRACELET, accessory("ishikure_bracelet", 2, 0.85));
        setCurio(player, AccessorySlot.CHARM, accessory("waystone_charm", 2, 0.8));
        setCurio(player, AccessorySlot.RING, accessory("moss_iron_wisdom_ring", 2, 0.75));
        player.getInventory().add(accessory("moss_iron_battle_ring", 4, 1.0));
    }

    /** 法衣の見本：蜂晶の杖（レジェンダリー+10。動く・光る）と古代石の法衣。 */
    static void equipRobe(ServerPlayer player) {
        ItemStack blade = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, gear("bee_crystal_staff", Rarity.LEGENDARY, 0.97, 10));
        if (!blade.isEmpty()) {
            player.getInventory().add(blade);
        }
        player.setItemSlot(EquipmentSlot.HEAD, gear("ancient_stone_hood", Rarity.RARE, 0.9, 7));
        player.setItemSlot(EquipmentSlot.CHEST, gear("ancient_stone_robe", Rarity.RARE, 0.9, 7));
        player.setItemSlot(EquipmentSlot.LEGS, gear("ancient_stone_legwear", Rarity.RARE, 0.9, 7));
        player.setItemSlot(EquipmentSlot.FEET, gear("ancient_stone_boots", Rarity.RARE, 0.9, 7));
    }

    /** 工房の見本：武器職人（熟練度3）と、素材・強化する剣。 */
    static void prepareWorkshop(ServerPlayer player) {
        player.getInventory().add(gear("ancient_stone_sword", Rarity.UNCOMMON, 0.82, 3));
        player.getInventory().add(new ItemStack(item("moss_iron"), 12));
        player.getInventory().add(new ItemStack(item("crystal_shard"), 6));
        player.getInventory().add(new ItemStack(item("ancient_stone"), 8));
    }

    /** 後片づけ：着ている物・付けている物・持ち物を空にする（後の建物の写真と自動テストに残さない）。 */
    static void cleanup(ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        for (AccessorySlot slot : AccessorySlot.values()) {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getStacksHandler(slot.curiosId()).ifPresent(stacks -> {
                for (int i = 0; i < stacks.getSlots(); i++) {
                    stacks.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                }
            }));
        }
        player.getInventory().clearContent();
    }

    private static void setCurio(ServerPlayer player, AccessorySlot slot, ItemStack stack) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getStacksHandler(slot.curiosId()).ifPresent(stacks -> {
            if (stacks.getSlots() > 0) {
                stacks.getStacks().setStackInSlot(0, stack);
            }
        }));
    }

    // ---------------------------------------------------------------- クライアント

    /** 予兆（地面の帯・円・構え）を出している敵がいるか。 */
    static boolean anyTelegraph(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof AmemaruMob mob && mob.telegraph() != AmemaruMob.TELEGRAPH_NONE
                    && mob.telegraph() != AmemaruMob.TELEGRAPH_ALERT) {
                return true;
            }
        }
        return false;
    }

    /** 目の前（向いている方向に2ブロック）の工房の台を、右クリックと同じように使う。 */
    static void useWorkbench(Minecraft minecraft, BlockPos pos) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    /** 工房の画面で、持ち物の装備（強化の見本の剣）を台に置く（シフトを押してクリックと同じ）。 */
    static void placeOnWorkbench(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null
                || !(minecraft.player.containerMenu instanceof WorkshopMenu menu)) {
            return;
        }
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (slot.index > 0 && stack.getItem() instanceof GearItem && GearData.enhance(stack) == 3) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, minecraft.player);
                return;
            }
        }
    }

    /** 説明文の見本の品：手に持っている装備と、持ち物の完璧な指輪。 */
    static List<ItemStack> tooltipSamples(Minecraft minecraft) {
        List<ItemStack> list = new ArrayList<>();
        if (minecraft.player == null) {
            return list;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.isEmpty()) {
            list.add(held.copy());
        }
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.getItem() instanceof AccessoryItem) {
                list.add(stack.copy());
                break;
            }
        }
        return list;
    }

    /** 品の説明文を横に並べて描く画面（CIの写真用）。後ろのゲームの画面とHUDはそのまま描かれる。 */
    static final class TooltipScreen extends Screen {
        private final List<ItemStack> stacks;

        TooltipScreen(List<ItemStack> stacks) {
            super(Component.empty());
            this.stacks = stacks;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = 4;
            for (ItemStack stack : stacks) {
                graphics.renderTooltip(font, stack, x, 22);
                int width = 0;
                for (Component line : Screen.getTooltipFromItem(minecraft, stack)) {
                    width = Math.max(width, font.width(line));
                }
                x += width + 22;
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
