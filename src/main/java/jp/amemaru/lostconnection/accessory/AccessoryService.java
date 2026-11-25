package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.loot.LootItemEntity;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AccessoryVisibilityS2C;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * アクセサリーの仕組み（Phase 0-A 21章）：付けているアクセサリーのステータス、留め具の枠の解放（11日目）、
 * 体に表示するかどうかの同期、完璧なアクセサリーの告知。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class AccessoryService {
    /** 留め具の枠を開ける印（Curios の枠の数の変更）。 */
    private static final UUID CLASP_UNLOCK = UUID.fromString("0b7e1c55-3d1f-4c8a-9e52-6c5a2d9b7f11");
    /** 完璧なアクセサリーで贈る称号（称号の仕組みはPhase 6。それまで預かる）。 */
    public static final String PERFECT_TITLE = "amemaru:perfect_crystal";

    private AccessoryService() {
    }

    // ---------------------------------------------------------------- 抽選

    /** まだ抽選していなければ抽選する（コマンドで出した品など）。 */
    public static void ensureRolled(ItemStack stack, RandomSource random) {
        if (AccessoryData.isRolled(stack)) {
            return;
        }
        AccessoryDef def = AccessoryDefs.get(stack);
        if (def != null) {
            AccessoryRoller.roll(stack, def, AccessoryRules.current(), random, false, null, 0);
        }
    }

    // ---------------------------------------------------------------- 付けているもの

    /** 付けているアクセサリーをすべて返す（枠の順。空の枠は入れない）。 */
    public static List<ItemStack> equipped(LivingEntity entity) {
        List<ItemStack> list = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(entity).resolve().orElse(null);
        if (handler == null) {
            return list;
        }
        for (AccessorySlot slot : AccessorySlot.all()) {
            ICurioStacksHandler stacks = handler.getCurios().get(slot.curiosId());
            if (stacks == null) {
                continue;
            }
            IDynamicStackHandler items = stacks.getStacks();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof AccessoryItem) {
                    list.add(stack);
                }
            }
        }
        return list;
    }

    /** その枠（何番目か）に付けているアクセサリー（なければ空）。 */
    public static ItemStack equippedIn(LivingEntity entity, AccessorySlot slot, int index) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(entity).resolve().orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ICurioStacksHandler stacks = handler.getCurios().get(slot.curiosId());
        if (stacks == null || index < 0 || index >= stacks.getSlots()) {
            return ItemStack.EMPTY;
        }
        return stacks.getStacks().getStackInSlot(index);
    }

    /** 付けているアクセサリーのステータスを足す（メインとサブ。%のステータスは%のまま足す。メインは強化の伸びを入れる）。 */
    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        for (ItemStack stack : equipped(player)) {
            // メインは強化1段ごとに +5%（Phase 6 その4。Phase 0-A 21章）
            AccessoryData.StatLine main = AccessoryData.main(stack);
            if (main != null) {
                event.addFlat(main.type(), AccessoryData.effectiveMain(stack, main));
            }
            for (AccessoryData.StatLine line : AccessoryData.subs(stack)) {
                event.addFlat(line.type(), line.value());
            }
        }
    }

    /** 付けかえたら、ステータスを計算し直す（Curios の知らせ）。 */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        onChanged(event.getEntity());
    }

    /** アクセサリーを付けかえたとき。 */
    public static void onChanged(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        }
    }

    // ---------------------------------------------------------------- 留め具の枠（11日目に解放）

    /** 進行日に合わせて、留め具の枠を開け閉めする（ログイン時と、進行日が変わったとき）。 */
    public static void updateClaspSlot(ServerPlayer player) {
        boolean open = ProgressionData.currentDay(player.server) >= ServerConfig.CLASP_UNLOCK_DAY.get();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            boolean has = hasModifier(handler, AccessorySlot.CLASP.curiosId(), CLASP_UNLOCK);
            if (open && !has) {
                handler.addTransientSlotModifier(AccessorySlot.CLASP.curiosId(), CLASP_UNLOCK, "amemaru:clasp_unlock", 1.0,
                        AttributeModifier.Operation.ADDITION);
            } else if (!open && has) {
                handler.removeSlotModifier(AccessorySlot.CLASP.curiosId(), CLASP_UNLOCK);
            }
        });
    }

    private static boolean hasModifier(ICuriosItemHandler handler, String slot, UUID uuid) {
        for (AttributeModifier modifier : handler.getModifiers().get(slot)) {
            if (modifier.getId().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** ログイン・リスポーン・ディメンションの移動のとき：留め具の枠を合わせ、表示のオン・オフを送る。 */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    private static void refresh(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            updateClaspSlot(serverPlayer);
            PlayerDataCapability.get(serverPlayer).ifPresent(data -> broadcastVisibility(serverPlayer, data));
        }
    }

    /** 今の進行日で、留め具の枠が開いているか。 */
    public static boolean claspOpen(ServerPlayer player) {
        return ProgressionData.currentDay(player.server) >= ServerConfig.CLASP_UNLOCK_DAY.get();
    }

    // ---------------------------------------------------------------- 表示のオン・オフ

    /** 体に表示するかを変える（自分の画面から。まわりの人にも知らせる）。 */
    public static void setVisible(ServerPlayer player, AccessorySlot slot, boolean visible) {
        if (!slot.shownOnBody()) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            data.getAdventure().setAccessoryVisible(slot.ordinal(), visible);
            broadcastVisibility(player, data);
        });
    }

    /** 表示のオン・オフを、本人とまわりの人に送る。 */
    public static void broadcastVisibility(ServerPlayer player, PlayerData data) {
        ModNetwork.sendToTrackingAndSelf(player, new AccessoryVisibilityS2C(player.getId(), data.getAdventure().hiddenAccessoryBits()));
    }

    /** 誰かが見え始めたら、その人のアクセサリーの表示のオン・オフを送る。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            PlayerDataCapability.get(target).ifPresent(data ->
                    ModNetwork.sendTo(viewer, new AccessoryVisibilityS2C(target.getId(), data.getAdventure().hiddenAccessoryBits())));
        }
    }

    // ---------------------------------------------------------------- 完璧なアクセサリー

    /**
     * 作った・手に入れたアクセサリーが完璧なら、本人の画面で特別な演出をし、サーバー全体に告知し、称号を預かる
     * （Phase 0-A 21章【確定】）。
     */
    public static void checkPerfect(ServerPlayer player, ItemStack stack) {
        if (!AccessoryData.isPerfect(stack, AccessoryRules.current())) {
            return;
        }
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.PERFECT_ACCESSORY, stack.copy()));
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.accessory.perfect_announce",
                player.getDisplayName(), stack.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        // Phase 6 その3：称号「完璧の結晶」と、限定称号「最初の完璧」（預かりの印も残す。前の版のデータとの互換のため）
        PlayerDataCapability.get(player).ifPresent(data -> data.getAdventure().addPendingTitle(PERFECT_TITLE));
        jp.amemaru.lostconnection.guild.GuildHooks.onPerfectAccessory(player);
    }

    /** 本人の手に入ったアクセサリー（作った・拾った）を確かめる。 */
    public static void onObtained(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer && stack.getItem() instanceof AccessoryItem) {
            checkPerfect(serverPlayer, stack);
        }
    }

    /**
     * 敵のドロップ（その人だけのドロップ）を拾ったとき。一度拾った物を落として拾い直しても、もう一度は知らせない
     * （ふつうのアイテムとして落ちるため）。
     */
    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getOriginalEntity() instanceof LootItemEntity) {
            onObtained(event.getEntity(), event.getStack());
        }
    }
}
