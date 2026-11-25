package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 古代記録と記録帳（Phase 0-A 14章「古代記録と記録帳」。Phase 6 その3）。
 *
 * <ul>
 *   <li>階層で見つけた石板や文書を拾うと、記録帳に「未解読」で残る。エルネストに渡すと「解読済み」になり、本文と彼のひと言が読める</li>
 *   <li>分類：歴史／リンク／ロスト／NULL／人々／？？？。伏線の記録は「？？？」に入り、最後まで分類されない</li>
 *   <li>階層ごとの収集率を出し、集めた割合（25／60／100%）で称号（記録の拾い手→記録の読み手→歴史の語り部）</li>
 *   <li>記録帳は、3日目にエルネストが渡す（3階の記録を持ち帰ると登場。Phase 0-A 11章）。それまでに見つけた記録も、未解読で残っている</li>
 * </ul>
 */
public final class RecordService {
    private RecordService() {
    }

    /**
     * 集めた古代記録の割合（%。切り捨て）。塔の中の記録（1〜19階）だけを数える（Phase 7 その2【補完】：塔の外の記憶魚の記録は、
     * 釣りの称号「記憶を釣り上げた者」で数える）。
     */
    public static long percent(PlayerData data) {
        int total = 0;
        for (RecordDef def : RecordDefs.all().values()) {
            if (def.floor() >= 1) {
                total++;
            }
        }
        if (total <= 0) {
            return 0;
        }
        int found = 0;
        for (ResourceLocation id : data.getGuild().records().keySet()) {
            RecordDef def = RecordDefs.get(id);
            if (def != null && def.floor() >= 1) {
                found++;
            }
        }
        return found * 100L / total;
    }

    /** 記録を見つけた（未解読で残す）。初めてなら true。 */
    public static boolean find(ServerPlayer player, ResourceLocation id) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        RecordDef def = RecordDefs.get(id);
        if (data == null || def == null) {
            return false;
        }
        int day = ProgressionData.get(player.server).day();
        if (!data.getGuild().findRecord(id, day)) {
            return false;
        }
        CompoundTag toast = new CompoundTag();
        toast.putString("type", "record");
        toast.putInt("floor", def.floor());
        ModNetwork.sendTo(player, new GuildS2C("toast", toast));
        // 塔の外の記録（記憶魚。Phase 7 その2）は「塔の外」
        Object where = def.floor() == 0 ? Component.translatable("screen.amemaru.record_book.floor0")
                : Component.translatable("screen.amemaru.record_book.floor", def.floor());
        player.displayClientMessage(Component.translatable(data.getGuild().hasRecordBook() ? "message.amemaru.record.found"
                : "message.amemaru.record.found_nobook", where).withStyle(ChatFormatting.AQUA), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        QuestService.onRecord(player);
        TitleService.check(player);
        GuildService.syncSelf(player, data);
        // Phase 7 その1：古代記録の発見（物語）
        jp.amemaru.lostconnection.connection.ConnectionService.award(player,
                jp.amemaru.lostconnection.connection.ConnectionActions.RECORD_FOUND);
        return true;
    }

    /** エルネストが、未解読の記録をすべて解読する。解読した数を返す。 */
    public static int decipher(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (RecordDef def : RecordDefs.sorted()) {
            if (data.getGuild().decipherRecord(def.id())) {
                count++;
                player.displayClientMessage(Component.translatable("message.amemaru.record.deciphered", def.title())
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        if (count == 0) {
            player.displayClientMessage(Component.translatable("message.amemaru.record.nothing").withStyle(ChatFormatting.GRAY), false);
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
            if (!data.getGuild().hasRecordBook()) {
                giveBook(player);
            }
        }
        TitleService.check(player);
        GuildService.syncSelf(player, data);
        return count;
    }

    /** 記録帳を渡す（なくしたときも、もう一度もらえる）。 */
    public static void giveBook(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        boolean first = !data.getGuild().hasRecordBook();
        data.getGuild().setRecordBook(true);
        boolean has = player.getInventory().contains(new ItemStack(ModItems.RECORD_BOOK.get()));
        if (!has) {
            ItemStack book = new ItemStack(ModItems.RECORD_BOOK.get());
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            player.displayClientMessage(Component.translatable(first ? "message.amemaru.record.book" : "message.amemaru.record.book_again")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        GuildService.syncSelf(player, data);
    }

    /** 記録帳を開く。 */
    public static void openBook(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        if (!data.getGuild().hasRecordBook()) {
            player.displayClientMessage(Component.translatable("message.amemaru.record.no_book").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag tag = bookTag(player, data);
        tag.putString("screen", "record_book");
        ModNetwork.sendTo(player, new GuildS2C("screen", tag));
    }

    /** 記録帳の中身（見つけていない記録は、階とヒントだけ。解読していない記録は、本文を送らない）。 */
    public static CompoundTag bookTag(ServerPlayer player, PlayerData data) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        boolean hints = TitleService.hasEffect(player, "record_hints");
        int[] total = new int[21];
        int[] found = new int[21];
        for (RecordDef def : RecordDefs.sorted()) {
            int floor = Math.max(0, Math.min(20, def.floor()));
            total[floor]++;
            GuildData.RecordEntry entry = data.getGuild().record(def.id());
            CompoundTag one = new CompoundTag();
            one.putString("id", def.id().toString());
            one.putInt("floor", def.floor());
            if (entry == null) {
                one.putString("state", "missing");
                one.putString("hint", def.hint());
            } else {
                found[floor]++;
                one.putInt("day", entry.foundDay());
                if (entry.deciphered()) {
                    one.putString("state", "read");
                    one.putString("title", def.title());
                    one.putString("category", def.category().key());
                    ListTag pages = new ListTag();
                    for (String page : def.pages()) {
                        pages.add(StringTag.valueOf(GuildService.fill(player, page)));
                    }
                    one.put("pages", pages);
                    one.putString("comment", GuildService.fill(player, def.comment()));
                } else {
                    one.putString("state", "unread");
                    one.putString("hint", def.hint());
                }
                // 歴史の語り部：隠し称号のヒント（22章【提案】「記録帳に隠し称号のヒントが出る」）
                if (hints && def.titleHint() != null) {
                    TitleDef hinted = TitleDefs.get(def.titleHint());
                    if (hinted != null && !data.getGuild().hasTitle(hinted.id())) {
                        one.putString("titleHint", hinted.hint().isEmpty() ? hinted.description() : hinted.hint());
                    }
                }
            }
            entries.add(one);
        }
        tag.put("entries", entries);
        tag.putIntArray("total", total);
        tag.putIntArray("found", found);
        tag.putLong("percent", percent(data));
        return tag;
    }

    /** 見つけた記録の数（管理者の一覧など）。 */
    public static List<ResourceLocation> foundIds(PlayerData data) {
        return List.copyOf(data.getGuild().records().keySet());
    }
}
