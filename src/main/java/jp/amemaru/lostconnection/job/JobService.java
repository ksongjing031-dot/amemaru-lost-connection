package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.block.OathCrystalBlock;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerSync;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 職業の選択・転職・上位職（Phase 0-A 19章「上位職と転職のルール」）。
 *
 * <ul>
 *   <li>戦闘職の変更：お試し期間（初期値：3日目まで）は誓約の結晶で無料で何度でも変えられる。その後は「転職の証」を使う
 *       （ノットの費用はPhase 6で足す）。レベルはそのままで、スキルポイントはすべて戻る。</li>
 *   <li>上位職：11日目から、Lv30以上でギルドの昇格クエストを終えると昇格する（クエストはPhase 6。それまでは管理者のコマンドで昇格する）。
 *       一度昇格した人は、転職しても昇格クエストなしで昇格できる。</li>
 *   <li>職業を初めて選ぶと、その職業の見習い装備をもらう。最初の職業選択では、忘れ水と白紙の誓約書を1つずつもらう。</li>
 * </ul>
 */
public final class JobService {
    /** 画面を開いてから選ぶまでの猶予（5分）。 */
    private static final int SCREEN_TICKS = 20 * 60 * 5;

    /** 転職の判定の結果。 */
    public record ChangeRule(boolean free, int tokensNeeded, int tokensHeld, int day, int freeUntilDay) {
        public boolean allowed() {
            return free || tokensHeld >= tokensNeeded;
        }
    }

    private JobService() {
    }

    // ---------------------------------------------------------------- 画面

    /** 誓約の結晶を右クリックしたとき：職業選択の画面を開く。 */
    public static void openJobScreen(ServerPlayer player, BlockPos pos) {
        jp.amemaru.lostconnection.player.PlayerDataCapability.get(player).ifPresent(data -> {
            long now = player.level().getGameTime();
            data.setOathCrystal(pos, player.level().dimension(), now + SCREEN_TICKS);
            ChangeRule rule = changeRule(player, data);
            ModNetwork.sendTo(player, new OpenJobScreenS2C(
                    SkillRegistry.get().jobIndex(data.getCombatJob()),
                    rule.free(),
                    rule.tokensNeeded(),
                    rule.tokensHeld(),
                    rule.day(),
                    rule.freeUntilDay(),
                    data.isPromoted(),
                    data.hasPromotedOnce()));
        });
    }

    /** 画面を開いた誓約の結晶の近くにいるか（いなければ本人に伝えて null）。 */
    private static BlockPos nearCrystal(ServerPlayer player, PlayerData data) {
        long now = player.level().getGameTime();
        BlockPos pos = data.getOathCrystalPos(now);
        double reach = ServerConfig.OATH_CRYSTAL_REACH.get();
        // 誓約の結晶か、職業の登録の窓口のNPC（ギルドの受付のリリア。Phase 5）から開いた画面か
        if (pos == null || data.getOathCrystalLevel() != player.level().dimension()
                || pos.distToCenterSqr(player.position()) > reach * reach
                || !(player.level().getBlockState(pos).getBlock() instanceof OathCrystalBlock
                || (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && jp.amemaru.lostconnection.npc.NpcService.isRegistrarAt(serverLevel, pos)))) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.too_far"));
            return null;
        }
        return pos;
    }

    /** 画面で職業を選んだとき（{@code JobActionC2S}）。誓約の結晶の近くにいるかを確かめる。 */
    public static void selectFromScreen(ServerPlayer player, PlayerData data, int jobIndex) {
        BlockPos pos = nearCrystal(player, data);
        if (pos == null) {
            return;
        }
        JobDef job = SkillRegistry.get().jobAt(jobIndex);
        if (job == null || job.kind() != JobDef.Kind.COMBAT) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.unknown"));
            return;
        }
        if (changeJob(player, data, job)) {
            data.setOathCrystal(null, null, 0);
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    40, 0.4, 0.6, 0.4, 0.05);
            // はじめの手引き：職業を選んだ（Phase 5）
            jp.amemaru.lostconnection.story.GuideService.refresh(player, data);
        }
    }

    /**
     * 画面で「上位職に昇格」を押したとき。一度昇格したことがある人だけ、昇格試験なしで昇格できる（Phase 0-A 19章）。
     * 初めての昇格は、ギルドの昇格クエスト（Phase 6）か、管理者のコマンドで行う。
     */
    public static void promoteFromScreen(ServerPlayer player, PlayerData data) {
        if (nearCrystal(player, data) == null) {
            return;
        }
        if (!data.hasPromotedOnce()) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.need_trial"));
            return;
        }
        promote(player, data, false);
    }

    // ---------------------------------------------------------------- 選択と転職

    /** 今、戦闘職を変えるときの決まり。 */
    public static ChangeRule changeRule(ServerPlayer player, PlayerData data) {
        int day = ProgressionData.currentDay(player.server);
        int freeUntil = ServerConfig.JOB_CHANGE_FREE_UNTIL_DAY.get();
        boolean free = !data.hasCombatJob() || day <= freeUntil;
        int needed = free ? 0 : ServerConfig.JOB_CHANGE_TOKENS.get();
        int held = countTokens(player);
        return new ChangeRule(free, needed, held, day, freeUntil);
    }

    /**
     * 戦闘職を選ぶ・変える（プレイヤーの操作から）。費用が足りなければ変えない。
     *
     * @return 変えたか
     */
    public static boolean changeJob(ServerPlayer player, PlayerData data, JobDef job) {
        if (job.id().equals(data.getCombatJob())) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.same"));
            return false;
        }
        ChangeRule rule = changeRule(player, data);
        if (!rule.allowed()) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.need_token", rule.tokensNeeded()));
            return false;
        }
        if (!rule.free() && rule.tokensNeeded() > 0) {
            removeTokens(player, rule.tokensNeeded());
        }
        boolean first = !data.hasCombatJob();
        apply(player, data, job);
        if (!first) {
            data.countJobChange();
        }
        AmemaruMod.LOGGER.info("[amemaru] {} chose job {} (day {}, free={}, tokens={})",
                player.getGameProfile().getName(), job.id(), rule.day(), rule.free(), rule.tokensNeeded());
        return true;
    }

    /** 管理者用：費用や場所を確かめずに職業を入れる。 */
    public static void setJobForced(ServerPlayer player, PlayerData data, JobDef job) {
        apply(player, data, job);
    }

    /** 管理者用：職業を外す（スキルもすべて消える）。 */
    public static void clearJob(ServerPlayer player, PlayerData data) {
        SkillService.cancelCast(player, data, false);
        data.setCombatJob(null);
        data.setPromoted(false);
        data.clearSkills();
        data.clearCooldowns();
        data.setGauge(0);
        LevelService.recompute(player, data);
        PlayerSync.syncAll(player, data);
    }

    /** 職業を入れ、スキルを最初の形にし、見習い装備などを渡す。 */
    private static void apply(ServerPlayer player, PlayerData data, JobDef job) {
        SkillService.cancelCast(player, data, false);
        data.setCombatJob(job.id());
        data.setPromoted(false);
        data.clearCooldowns();
        data.setGauge(0);
        SkillTreeService.resetSkills(player, data);

        if (!data.hasReceivedKit(job.id())) {
            for (JobDef.KitEntry entry : job.starterKit()) {
                Item item = ForgeRegistries.ITEMS.getValue(entry.item());
                if (item != null) {
                    give(player, GearData.bind(new ItemStack(item, entry.count())));
                }
            }
            data.markKitReceived(job.id());
        }
        if (!data.hasFreeResetItems()) {
            give(player, GearData.bind(new ItemStack(ModItems.FORGET_WATER.get())));
            give(player, GearData.bind(new ItemStack(ModItems.BLANK_OATH.get())));
            data.markFreeResetItemsGiven();
        }

        LevelService.recompute(player, data);
        data.setHp(data.getStats().maxHp());
        data.setMp(data.getStats().maxMp());
        PlayerSync.syncAll(player, data);

        player.displayClientMessage(Component.translatable("message.amemaru.job.chosen", job.name())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("message.amemaru.job.chosen_hint",
                Component.keybind("key.amemaru.skill_tree")).withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("job.oath"),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ---------------------------------------------------------------- 上位職

    /**
     * 上位職に昇格する。{@code force} が false なら、進行日・レベルの条件を確かめる（昇格クエストはPhase 6）。
     *
     * @return 昇格したか
     */
    public static boolean promote(ServerPlayer player, PlayerData data, boolean force) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        if (job == null || job.upper() == null) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.no_upper"));
            return false;
        }
        if (data.isPromoted()) {
            SkillTreeService.tell(player, Component.translatable("message.amemaru.job.already_promoted"));
            return false;
        }
        if (!force) {
            int day = ProgressionData.currentDay(player.server);
            if (day < job.upper().minDay()) {
                SkillTreeService.tell(player, Component.translatable("message.amemaru.job.upper_day", job.upper().minDay()));
                return false;
            }
            if (data.getLevel() < job.upper().minLevel()) {
                SkillTreeService.tell(player, Component.translatable("message.amemaru.job.upper_level", job.upper().minLevel()));
                return false;
            }
        }
        data.setPromoted(true);
        LevelService.recompute(player, data);
        PlayerSync.syncAll(player, data);
        player.displayClientMessage(Component.translatable("message.amemaru.job.promoted", job.upper().name())
                .withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("job.promote"),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** 管理者用：上位職を外す（上位職でしか覚えられない段階は戻す）。 */
    public static void demote(ServerPlayer player, PlayerData data) {
        data.setPromoted(false);
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        for (var entry : new java.util.ArrayList<>(data.getLearnedSkills().entrySet())) {
            var skill = snapshot.skill(entry.getKey());
            if (skill == null) {
                continue;
            }
            int limit = skill.isUltimate() ? 0 : Math.min(entry.getValue(), skill.upperRank() - 1);
            if (limit < entry.getValue()) {
                data.setSkillRank(skill.id(), limit);
            }
        }
        LevelService.recompute(player, data);
        PlayerSync.syncAll(player, data);
    }

    // ---------------------------------------------------------------- 道具

    /** 見習い装備をもう一度渡す（管理者用。なくしたときなど）。 */
    public static void giveStarterKit(ServerPlayer player, JobDef job) {
        for (JobDef.KitEntry entry : job.starterKit()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.item());
            if (item != null) {
                give(player, GearData.bind(new ItemStack(item, entry.count())));
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    private static int countTokens(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.JOB_CHANGE_TOKEN.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeTokens(ServerPlayer player, int amount) {
        int left = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (stack.is(ModItems.JOB_CHANGE_TOKEN.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
    }
}
