package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.DialogueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * NPCとの親密度（Phase 0-A 14章「NPCとの親密度」。Phase 6 その3）。
 *
 * <p>段階：1 顔見知り（基本の会話）→ 2 知り合い（個人的な依頼①）→ 3 信頼（過去の話・個人的な依頼②・小さな贈り物）→
 * 4 親友（特別な会話・ゆかりの小物）→ 5 かけがえのない人（称号・エンディング後の特別な一言）。
 * 上げ方は、1日1回の会話・個人的な依頼・好きな物の贈り物・物語イベントへの参加。記憶喪失中は凍結される（13章。Phase 8）。</p>
 *
 * <p>点と段階の境目【補完】：会うと段階1。30点で2、80点で3、150点で4、250点で5。1日の会話で+10、好きな物+25・気に入る物+12・
 * ほかの品+4（贈り物は1人に1日1回）、個人的な依頼は定義の値（+40など）、物語イベントへの参加+15。20日のうち、毎日話すだけで段階4、
 * 贈り物と依頼を合わせると10〜15日目ごろに段階5に届く。</p>
 */
public final class BondService {
    public static final int MAX_STAGE = 5;
    /** 段階2〜5になる点。 */
    public static final int[] STAGE_POINTS = {0, 0, 30, 80, 150, 250};
    public static final int POINTS_CAP = 300;
    public static final int DAILY_TALK = 10;
    public static final int GIFT_LOVED = 25;
    public static final int GIFT_LIKED = 12;
    public static final int GIFT_OTHER = 4;
    public static final int STORY_EVENT = 15;
    public static final int GIFTS_PER_DAY = 1;

    private BondService() {
    }

    // ================================================================ 段階

    public static int stage(PlayerData data, ResourceLocation npc) {
        GuildData.Bond bond = data.getGuild().bond(npc);
        if (bond == null || !bond.met()) {
            return 0;
        }
        return stageOf(bond.points());
    }

    public static int stageOf(int points) {
        int stage = 1;
        for (int s = 2; s <= MAX_STAGE; s++) {
            if (points >= STAGE_POINTS[s]) {
                stage = s;
            }
        }
        return stage;
    }

    public static int points(PlayerData data, ResourceLocation npc) {
        GuildData.Bond bond = data.getGuild().bond(npc);
        return bond == null ? 0 : bond.points();
    }

    /** 次の段階までの点（いちばん上なら 0）。 */
    public static int toNext(int points) {
        int stage = stageOf(points);
        return stage >= MAX_STAGE ? 0 : STAGE_POINTS[stage + 1] - points;
    }

    public static Component stageName(int stage) {
        return Component.translatable("bond.amemaru.stage." + Math.max(0, Math.min(MAX_STAGE, stage)));
    }

    /** 親密度のあるNPCか（主要NPC）。 */
    public static boolean hasBond(ResourceLocation npc) {
        NpcDef def = NpcDefs.get(npc);
        return def != null && def.bond() != null;
    }

    /** 記憶喪失中で、親密度が凍結されているか（Phase 0-A 13章。11月25日：6日目から、街の人の一部がプレイヤーを忘れる）。 */
    public static boolean frozen(ServerPlayer player, ResourceLocation npc) {
        return !jp.amemaru.lostconnection.npc.NpcMemoryService.remembers(player, npc);
    }

    // ================================================================ 上げる

    /** 話しかけた：会った（段階1）・今日はじめてなら+10。 */
    public static void onTalk(ServerPlayer player, ResourceLocation npc) {
        if (!hasBond(npc)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        int before = stage(data, npc);
        data.getGuild().meet(npc);
        int day = ProgressionData.get(player.server).day();
        if (!frozen(player, npc) && data.getGuild().talkToday(npc, day)) {
            data.getGuild().addBondPoints(npc, DAILY_TALK, POINTS_CAP);
        }
        afterChange(player, data, npc, before);
    }

    /** 物語・クエストで親密度を足す（{@code reason} は記録のため）。 */
    public static void add(ServerPlayer player, ResourceLocation npc, int amount, String reason) {
        if (!hasBond(npc) || amount == 0 || frozen(player, npc)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        int before = stage(data, npc);
        data.getGuild().meet(npc);
        data.getGuild().addBondPoints(npc, amount, POINTS_CAP);
        afterChange(player, data, npc, before);
    }

    /** 管理者：点を決める。 */
    public static void set(ServerPlayer player, ResourceLocation npc, int points) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        int before = stage(data, npc);
        data.getGuild().setBondPoints(npc, Math.max(0, Math.min(POINTS_CAP, points)));
        afterChange(player, data, npc, before);
    }

    /**
     * しゃがんで品を持って話しかけた：贈り物。贈り物として受け取ったら true（会話は始めない）。
     * 親密度のないNPCなら false（ふつうに話す）。
     */
    public static boolean gift(ServerPlayer player, NpcEntity entity) {
        ResourceLocation npc = entity.npcId();
        NpcDef def = npc == null ? null : NpcDefs.get(npc);
        if (def == null || def.bond() == null) {
            return false;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        if (frozen(player, npc)) {
            say(player, def, def.bond().reaction("frozen"), "message.amemaru.bond.frozen");
            return true;
        }
        int day = ProgressionData.get(player.server).day();
        if (!data.getGuild().giftToday(npc, day, GIFTS_PER_DAY)) {
            say(player, def, def.bond().reaction("full"), "message.amemaru.bond.gift_full");
            return true;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String kind;
        int amount;
        if (itemId != null && def.bond().loved().contains(itemId)) {
            kind = "loved";
            amount = GIFT_LOVED;
        } else if (itemId != null && def.bond().liked().contains(itemId)) {
            kind = "liked";
            amount = GIFT_LIKED;
        } else {
            kind = "normal";
            amount = GIFT_OTHER;
        }
        Component itemName = stack.getHoverName();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        int before = stage(data, npc);
        data.getGuild().meet(npc);
        data.getGuild().addBondPoints(npc, amount, POINTS_CAP);
        player.displayClientMessage(Component.translatable("message.amemaru.bond.gift", itemName, def.name())
                .withStyle(ChatFormatting.GRAY), false);
        say(player, def, def.bond().reaction(kind), "message.amemaru.bond.gift_" + kind);
        if (player.level() instanceof ServerLevel level) {
            int hearts = "loved".equals(kind) ? 6 : "liked".equals(kind) ? 3 : 1;
            level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), hearts,
                    0.35, 0.2, 0.35, 0.0);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F,
                    "loved".equals(kind) ? 1.4F : 1.1F);
        }
        afterChange(player, data, npc, before);
        return true;
    }

    /** NPCの一言（チャットに「名前：「…」」で出す）。台詞がなければ、決まった文。 */
    private static void say(ServerPlayer player, NpcDef def, String line, String fallbackKey) {
        Component text = line.isEmpty() ? Component.translatable(fallbackKey, def.name())
                : Component.translatable("message.amemaru.bond.says", def.name(), DialogueManager.fill(player, line));
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.WHITE), false);
    }

    /** 段階が上がったら、演出と段階のごほうび（3：小さな贈り物、4：ゆかりの小物、5：称号）。 */
    private static void afterChange(ServerPlayer player, PlayerData data, ResourceLocation npc, int before) {
        int after = stage(data, npc);
        NpcDef def = NpcDefs.get(npc);
        if (after > before && def != null) {
            for (int s = before + 1; s <= after; s++) {
                CompoundTag toast = new CompoundTag();
                toast.putString("type", "bond");
                toast.putString("npc", def.name());
                toast.putInt("stage", s);
                ModNetwork.sendTo(player, new GuildS2C("toast", toast));
                if (s >= 2) {
                    player.displayClientMessage(Component.translatable("message.amemaru.bond.stage_up", def.name(), stageName(s))
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                }
                reward(player, data, def, s);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        TitleService.check(player);
        GuildService.syncSelf(player, data);
    }

    private static void reward(ServerPlayer player, PlayerData data, NpcDef def, int stage) {
        NpcDef.Bond bond = def.bond();
        if (bond == null) {
            return;
        }
        GuildData.Bond entry = data.getGuild().bond(def.id());
        if (entry != null && entry.rewarded(stage)) {
            return;
        }
        ResourceLocation itemId = stage == 3 ? bond.gift() : stage == 4 ? bond.keepsake() : null;
        int count = stage == 3 ? bond.giftCount() : 1;
        if (itemId != null) {
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                ItemStack give = new ItemStack(item, count);
                Component name = give.getHoverName();
                if (!player.getInventory().add(give)) {
                    player.drop(give, false);
                }
                player.displayClientMessage(Component.translatable(stage == 3 ? "message.amemaru.bond.small_gift"
                        : "message.amemaru.bond.keepsake", def.name(), name).withStyle(ChatFormatting.GOLD), false);
            }
        }
        data.getGuild().markBondReward(def.id(), stage);
    }

    /** 物語イベントに加わった人の親密度を上げる（そのイベントに出たNPCについて）。 */
    public static void onStoryEvent(ServerPlayer player, @Nullable ResourceLocation npc) {
        if (npc != null) {
            add(player, npc, STORY_EVENT, "story");
        }
    }
}
