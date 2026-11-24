package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.CastState;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.SkillCooldown;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * スキルを使う（Phase 0-B 9章「スキルを使う」）。
 *
 * <ol>
 *   <li>クライアントは、スキル枠の番号と狙い（相手・地点）だけを送る（{@code UseSkillC2S}）。</li>
 *   <li>サーバーは、職業・習得・クールダウン・MPとゲージ・武器・射程と視線・詠唱中でないかを確かめる。</li>
 *   <li>詠唱があれば詠唱を始め、終わったときにもう一度確かめてから放つ。なければすぐに放つ。</li>
 *   <li>MPとゲージを使い、クールダウンを始め、効果と演出を出す。</li>
 * </ol>
 * <p>詠唱中に同じスキルのキーをもう一度押すと、詠唱をやめる。</p>
 * <p>溜めるスキル（{@code charge}）は、キーを押すと溜めを始め（確かめるのはここまでと同じ）、離したときに
 * {@link #tryRelease} で放つ。MPとクールダウンは放ったときに使う。溜めきってから1秒たつと、自動で放つ。</p>
 */
public final class SkillService {
    /** 使えなかった理由（画面に出す文の名前）。 */
    public enum Failure {
        NO_JOB("message.amemaru.skill.no_job", true),
        EMPTY_SLOT("message.amemaru.skill.empty_slot", true),
        NOT_LEARNED("message.amemaru.skill.not_learned", true),
        COOLDOWN("message.amemaru.skill.cooldown", true),
        NO_MP("message.amemaru.skill.no_mp", true),
        NO_GAUGE("message.amemaru.skill.no_gauge", true),
        CASTING("message.amemaru.skill.casting", true),
        NO_WEAPON("message.amemaru.skill.no_weapon", true),
        NO_TARGET("message.amemaru.skill.no_target", true),
        NO_ALLY("message.amemaru.skill.no_ally", true),
        CANCELLED("message.amemaru.skill.cast_cancelled", true),
        DEAD("", false),
        SPAM("", false),
        NOT_READY("", false),
        NO_BRANDED("message.amemaru.skill.no_branded", true),
        NOT_CHARGING("", false),
        /** ダウン中（Phase 4）。番号を変えないように、いちばん後ろに足す */
        DOWN("message.amemaru.skill.down", true);

        private final String key;
        private final boolean shown;

        Failure(String key, boolean shown) {
            this.key = key;
            this.shown = shown;
        }

        public String key() {
            return key;
        }

        /** 本人に理由を出すか。 */
        public boolean shown() {
            return shown;
        }

        public static Failure byIndex(int index) {
            Failure[] values = values();
            return index >= 0 && index < values.length ? values[index] : NOT_READY;
        }
    }

    /** 溜めきってから、自動で放つまで（tick）。 */
    private static final int CHARGE_HOLD_TICKS = 20;

    /** 結果（成功なら failure は null）。 */
    public record Result(@Nullable Failure failure) {
        static final Result OK = new Result(null);

        public boolean ok() {
            return failure == null;
        }
    }

    private SkillService() {
    }

    // ---------------------------------------------------------------- 使う

    /**
     * スキル枠のスキルを使う。
     *
     * @param entityAim クライアントが狙った相手（ないときは -1）
     * @param pointAim  クライアントが狙った地点（ないときは null）
     */
    public static Result tryUse(ServerPlayer player, PlayerData data, int slot, int entityAim, @Nullable Vec3 pointAim) {
        long now = player.level().getGameTime();
        if (!data.allowSkillRequest(now, ServerConfig.SKILL_REQUESTS_PER_SECOND.get())) {
            AmemaruMod.LOGGER.warn("[amemaru] Too many skill requests from {} (dropped)", player.getGameProfile().getName());
            return new Result(Failure.SPAM);
        }
        if (!player.isAlive() || player.isSpectator()) {
            return new Result(Failure.DEAD);
        }
        if (data.down().isDown()) {
            return fail(player, Failure.DOWN);
        }
        if (!data.isStatsReady()) {
            // 持ちかえなどで計算し直す印がついているだけなら、ここで計算する
            LevelService.recompute(player, data);
            if (!data.isStatsReady()) {
                return new Result(Failure.NOT_READY);
            }
        }
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        JobDef job = snapshot.job(data.getCombatJob());
        if (job == null) {
            return fail(player, Failure.NO_JOB);
        }
        ResourceLocation skillId = data.getLoadout(slot);
        if (skillId == null) {
            return fail(player, Failure.EMPTY_SLOT);
        }
        SkillDef skill = snapshot.skill(skillId);
        int rank = data.getSkillRank(skillId);
        if (skill == null || rank <= 0 || !skill.isActive() || !skill.job().equals(job.id())) {
            return fail(player, Failure.NOT_LEARNED);
        }
        rank = Math.min(rank, skill.maxRank());

        CastState casting = data.getCasting();
        if (casting != null) {
            if (casting.charging()) {
                // 溜めている間の押下（離したことが届く前の押し直しなど）は、何もしない
                return new Result(casting.slot() == slot ? Failure.NOT_CHARGING : Failure.CASTING);
            }
            if (casting.slot() == slot) {
                cancelCast(player, data, false);
                return fail(player, Failure.CANCELLED);
            }
            return fail(player, Failure.CASTING);
        }
        SkillCooldown cooldown = data.getCooldown(skillId);
        if (cooldown != null && cooldown.endTick() > now) {
            player.displayClientMessage(Component.translatable(Failure.COOLDOWN.key(), skill.name(),
                    String.format(Locale.ROOT, "%.1f", cooldown.remaining(now) / 20.0)).withStyle(ChatFormatting.RED), true);
            return new Result(Failure.COOLDOWN);
        }
        if (!hasWeapon(player, job)) {
            player.displayClientMessage(Component.translatable(Failure.NO_WEAPON.key(), job.name(), job.weaponText())
                    .withStyle(ChatFormatting.RED), true);
            return new Result(Failure.NO_WEAPON);
        }
        if (data.getMp() + 1.0E-6 < skill.mpCost(rank)) {
            return fail(player, Failure.NO_MP);
        }
        if (data.getGauge() + 1.0E-6 < skill.gaugeCost(rank)) {
            JobDef.GaugeDef gauge = job.gauge();
            player.displayClientMessage(Component.translatable(Failure.NO_GAUGE.key(), gauge == null ? "" : gauge.name(),
                    skill.gaugeCost(rank)).withStyle(ChatFormatting.RED), true);
            return new Result(Failure.NO_GAUGE);
        }

        int index = snapshot.skillIndex(skillId);
        if (skill.isCharged()) {
            // 溜め：狙いは離したときに決める
            startCharge(player, data, skill, index, rank, slot, now);
            return Result.OK;
        }

        SkillTargeting.Aim aim = SkillTargeting.resolve(player, skill, rank, entityAim, pointAim);
        if (aim.failure() != null) {
            return fail(player, aim.failure());
        }

        int castTicks = castTicks(data, skill, rank, now);
        if (castTicks > 0) {
            startCast(player, data, skill, index, rank, slot, castTicks, aim, now);
            return Result.OK;
        }
        execute(player, data, skill, index, rank, aim, now, 1.0);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 溜め

    private static void startCharge(ServerPlayer player, PlayerData data, SkillDef skill, int index, int rank, int slot, long now) {
        int ticks = Math.max(1, Mth.ceil(skill.chargeTime() * 20.0));
        data.setCasting(new CastState(skill.id(), rank, slot, now, now + ticks, -1, null, true));
        player.addEffect(new MobEffectInstance(ModEffects.CHARGING.get(), ticks + CHARGE_HOLD_TICKS + 4, 0, false, false, true));
        new SkillFxS2C(SkillFxS2C.Phase.CHARGE_START, player.getId(), index, player.position(), player.position(),
                (float) skill.delivery().radius(rank), ticks, new int[0], 0.0F).sendNear(player.level().dimension(), player.position());
        SoundEvent sound = ModSounds.byId(skill.castSound());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound != null ? sound : ModSounds.get("skill.charge"),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        // 11月24日：重ねる音（溜めを始めたとき）
        jp.amemaru.lostconnection.job.def.SkillSounds.play(player.serverLevel(), player.position(), skill,
                jp.amemaru.lostconnection.job.def.SkillSounds.Phase.CAST);
    }

    /**
     * 溜めを離して放つ（キーを離したとき。溜めきって1秒たったときは自動で）。
     *
     * @param entityAim クライアントが狙った相手（ないときは -1）
     * @param pointAim  クライアントが狙った地点（ないときは null）
     */
    public static Result tryRelease(ServerPlayer player, PlayerData data, int slot, int entityAim, @Nullable Vec3 pointAim) {
        CastState casting = data.getCasting();
        if (casting == null || !casting.charging() || casting.slot() != slot) {
            return new Result(Failure.NOT_CHARGING);
        }
        return releaseCharge(player, data, casting, player.level().getGameTime(), entityAim, pointAim);
    }

    private static Result releaseCharge(ServerPlayer player, PlayerData data, CastState casting, long now, int entityAim,
                                        @Nullable Vec3 pointAim) {
        data.setCasting(null);
        player.removeEffect(ModEffects.CHARGING.get());
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        SkillDef skill = snapshot.skill(casting.skill());
        JobDef job = snapshot.job(data.getCombatJob());
        int index = snapshot.skillIndex(casting.skill());
        Failure failure = null;
        if (!player.isAlive()) {
            failure = Failure.DEAD;
        } else if (data.down().isDown()) {
            failure = Failure.DOWN;
        } else if (skill == null || job == null || !skill.job().equals(job.id()) || data.getSkillRank(skill.id()) <= 0) {
            failure = Failure.NOT_LEARNED;
        } else if (!hasWeapon(player, job)) {
            failure = Failure.NO_WEAPON;
        } else if (data.getMp() + 1.0E-6 < skill.mpCost(casting.rank())) {
            failure = Failure.NO_MP;
        } else if (data.getGauge() + 1.0E-6 < skill.gaugeCost(casting.rank())) {
            failure = Failure.NO_GAUGE;
        }
        SkillTargeting.Aim aim = null;
        if (failure == null) {
            aim = SkillTargeting.resolve(player, skill, casting.rank(), entityAim, pointAim);
            failure = aim.failure();
        }
        if (failure != null) {
            new SkillFxS2C(SkillFxS2C.Phase.CAST_CANCEL, player.getId(), index, player.position(), player.position(), 0, 0, new int[0])
                    .sendNear(player.level().dimension(), player.position());
            return fail(player, failure);
        }
        execute(player, data, skill, index, casting.rank(), aim, now, casting.chargeRatio(now));
        return Result.OK;
    }

    private static Result fail(ServerPlayer player, Failure failure) {
        if (failure.shown()) {
            player.displayClientMessage(Component.translatable(failure.key()).withStyle(ChatFormatting.RED), true);
            if (failure != Failure.CANCELLED) {
                player.playNotifySound(ModSounds.get("skill.fail"), SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
        return new Result(failure);
    }

    /**
     * 職業の武器を持っているか（configで確かめないこともできる）。職業の武器のタグにあるか、武器の定義（equipment の jobs）に
     * その職業が書いてあれば持っている。11月25日：2階・4階・5階の武器（古樹の枝杖・祈り手の鈴・守護者シリーズ）がタグに
     * 抜けていて、その職業で持ってもスキルが使えなかった（タグも直した。定義からも見るので、足し忘れても使える）。
     */
    public static boolean hasWeapon(ServerPlayer player, JobDef job) {
        if (!ServerConfig.REQUIRE_JOB_WEAPON.get() || job.weapons() == null) {
            return true;
        }
        return isJobWeapon(player.getMainHandItem(), job);
    }

    /** その品が、職業の武器か（タグか、武器の定義の jobs）。 */
    public static boolean isJobWeapon(net.minecraft.world.item.ItemStack stack, JobDef job) {
        if (stack.isEmpty()) {
            return false;
        }
        if (job.weapons() != null && stack.is(job.weapons())) {
            return true;
        }
        jp.amemaru.lostconnection.equipment.EquipmentDef def = jp.amemaru.lostconnection.equipment.EquipmentDefs.server().get(stack);
        return def != null && def.slot() == jp.amemaru.lostconnection.equipment.GearSlot.WEAPON && def.jobs().contains(job.id());
    }

    // ---------------------------------------------------------------- 詠唱

    /** 詠唱の長さ（tick）。詠唱短縮と、支援を受けているときの短縮をかける。 */
    public static int castTicks(PlayerData data, SkillDef skill, int rank, long now) {
        double seconds = skill.cast(rank);
        if (seconds <= 0) {
            return 0;
        }
        seconds *= PassiveService.castMultiplier(data);
        if (data.getBuffs().supported(now)) {
            seconds *= skill.castSupportMultiplier();
        }
        return Math.max(1, Mth.ceil(seconds * 20.0));
    }

    private static void startCast(ServerPlayer player, PlayerData data, SkillDef skill, int index, int rank, int slot, int ticks,
                                  SkillTargeting.Aim aim, long now) {
        data.setCasting(new CastState(skill.id(), rank, slot, now, now + ticks, aim.entityId(), aim.point(), false));
        player.addEffect(new MobEffectInstance(ModEffects.CHANTING.get(), ticks + 2, 0, false, false, true));
        Vec3 to = aim.point() != null ? aim.point() : aim.entity() != null ? aim.entity().position() : player.position();
        // 線の技は、詠唱の間に向く先の印を出すため、半径の代わりに長さを送る
        float shape = (float) (skill.delivery().type() == DeliveryDef.Type.LINE ? skill.delivery().range(rank) : skill.delivery().radius(rank));
        new SkillFxS2C(SkillFxS2C.Phase.CAST_START, player.getId(), index, player.position(), to, shape, ticks, new int[0])
                .sendNear(player.level().dimension(), player.position());
        SoundEvent sound = jp.amemaru.lostconnection.core.registry.ModSounds.byId(skill.castSound());
        if (sound != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        // 11月24日：重ねる音（詠唱を始めたとき）
        jp.amemaru.lostconnection.job.def.SkillSounds.play(player.serverLevel(), player.position(), skill,
                jp.amemaru.lostconnection.job.def.SkillSounds.Phase.CAST);
    }

    /** 詠唱（溜め）をやめる。 */
    public static void cancelCast(ServerPlayer player, PlayerData data, boolean notify) {
        CastState casting = data.getCasting();
        if (casting == null) {
            return;
        }
        data.setCasting(null);
        player.removeEffect(casting.charging() ? ModEffects.CHARGING.get() : ModEffects.CHANTING.get());
        int index = SkillRegistry.get().skillIndex(casting.skill());
        new SkillFxS2C(SkillFxS2C.Phase.CAST_CANCEL, player.getId(), index, player.position(), player.position(), 0, 0, new int[0])
                .sendNear(player.level().dimension(), player.position());
        if (notify) {
            player.displayClientMessage(Component.translatable(Failure.CANCELLED.key()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** 詠唱が終わったら、もう一度確かめてから放つ。 */
    private static void finishCast(ServerPlayer player, PlayerData data, CastState casting, long now) {
        data.setCasting(null);
        player.removeEffect(ModEffects.CHANTING.get());
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        SkillDef skill = snapshot.skill(casting.skill());
        JobDef job = snapshot.job(data.getCombatJob());
        int index = snapshot.skillIndex(casting.skill());
        Failure failure = null;
        if (skill == null || job == null || !skill.job().equals(job.id()) || data.getSkillRank(skill.id()) <= 0) {
            failure = Failure.NOT_LEARNED;
        } else if (!hasWeapon(player, job)) {
            failure = Failure.NO_WEAPON;
        } else if (data.getMp() + 1.0E-6 < skill.mpCost(casting.rank())) {
            failure = Failure.NO_MP;
        } else if (data.getGauge() + 1.0E-6 < skill.gaugeCost(casting.rank())) {
            failure = Failure.NO_GAUGE;
        }
        SkillTargeting.Aim aim = null;
        if (failure == null) {
            // 詠唱の間に向きを変えても、ロックしていた敵に向き直して放つ（11月24日）
            LockOnFacing.refresh(player);
            aim = SkillTargeting.recheck(player, skill, casting.rank(), casting.targetEntity(), casting.targetPoint());
            failure = aim.failure();
        }
        if (failure != null) {
            new SkillFxS2C(SkillFxS2C.Phase.CAST_CANCEL, player.getId(), index, player.position(), player.position(), 0, 0, new int[0])
                    .sendNear(player.level().dimension(), player.position());
            if (failure == Failure.NO_WEAPON && job != null) {
                player.displayClientMessage(Component.translatable(failure.key(), job.name(), job.weaponText())
                        .withStyle(ChatFormatting.RED), true);
            } else if (failure == Failure.NO_GAUGE && job != null && job.gauge() != null) {
                player.displayClientMessage(Component.translatable(failure.key(), job.gauge().name(),
                        skill.gaugeCost(casting.rank())).withStyle(ChatFormatting.RED), true);
            } else {
                fail(player, failure);
            }
            return;
        }
        execute(player, data, skill, index, casting.rank(), aim, now, 1.0);
    }

    // ---------------------------------------------------------------- 放つ

    private static void execute(ServerPlayer player, PlayerData data, SkillDef skill, int index, int rank, SkillTargeting.Aim aim,
                                long now, double chargeRatio) {
        // MPとゲージを使い、クールダウンを始める
        data.setMp(data.getMp() - skill.mpCost(rank));
        double gaugeSpent = 0.0;
        if (skill.gaugeCost(rank) > 0 && GaugeService.consume(player, data, skill.gaugeCost(rank))) {
            gaugeSpent = skill.gaugeCost(rank);
        }
        int cooldownTicks = cooldownTicks(data, skill, rank, now);
        // 矢筒の名手（アーチャー）：その流派のスキルは、ときどきクールダウンが半分（Phase 6 その2）
        double quiver = PassiveService.schoolValue(data, jp.amemaru.lostconnection.job.def.PassiveDef.Type.QUIVER_MASTER, skill);
        if (quiver > 0 && cooldownTicks > 0 && player.getRandom().nextDouble() * 100.0 < quiver) {
            cooldownTicks = Math.max(1, cooldownTicks / 2);
        }
        if (cooldownTicks > 0) {
            data.setCooldown(skill.id(), new SkillCooldown(now + cooldownTicks, cooldownTicks));
        }

        SkillContext ctx = new SkillContext(player, data.getStats(), skill, index, rank).loadPassives(data);
        ctx.gaugeSpent = gaugeSpent;
        if (skill.isCharged()) {
            ctx.chargeRatio = Math.max(0.0, Math.min(1.0, chargeRatio));
            ctx.fullCharge = ctx.chargeRatio >= 0.999;
            ctx.multiplier *= skill.chargeMultiplier(ctx.chargeRatio);
        }
        // 連撃（モンク）：違うスキルを回すほど強くなる（Phase 6 その2）
        ComboService.Step step = ComboService.onSkillUse(player, data, skill, now);
        ctx.multiplier *= step.multiplier();
        ctx.forceCrit = step.forceCrit();
        BuffState stateNow = data.getBuffs();
        // 百烈・結：次のスキルが強くなる（使うと消える）
        double empower = stateNow.empower(now);
        if (empower > 0) {
            ctx.multiplier *= 1.0 + empower / 100.0;
            stateNow.empowerUntil = 0;
            player.removeEffect(ModEffects.EMPOWERED.get());
        }
        // 狙撃の構え：次の矢が強くなる（矢のスキルで1本ずつ使う）
        if (skill.hasTag("arrow") && stateNow.aiming(now)) {
            ctx.multiplier *= 1.0 + stateNow.aimBonus / 100.0;
            stateNow.aimShots--;
            if (stateNow.aimShots <= 0) {
                stateNow.aimUntil = 0;
                player.removeEffect(ModEffects.AIMING.get());
            }
        }
        // 連携支援：ほかの人の「支援」を受けていれば、このスキルの効果が上がる
        BuffState buffs = data.getBuffs();
        if (buffs.supported(now) && buffs.supportFrom != null && !buffs.supportFrom.equals(player.getUUID())) {
            ctx.multiplier *= 1.0 + buffs.supportBonus / 100.0;
            ctx.supported = true;
            ServerPlayer supporter = player.server.getPlayerList().getPlayer(buffs.supportFrom);
            int[] involved = supporter == null ? new int[]{player.getId()} : new int[]{supporter.getId(), player.getId()};
            CombatTextS2C.send(player, buffs.supportBonus, CombatTextS2C.Kind.SUPPORT, involved);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("combat.support"),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            buffs.consumeSupport();
            player.removeEffect(ModEffects.LINK_SUPPORT.get());
        }
        SkillExecutor.deliver(ctx, player, aim);
        // スキルを使うたびに、武器の耐久が減る（Phase 3）
        jp.amemaru.lostconnection.equipment.GearService.wearWeapon(player, true);
    }

    /** クールダウンの長さ（tick）。クールダウン短縮をかける。 */
    public static int cooldownTicks(PlayerData data, SkillDef skill, int rank) {
        double seconds = skill.cooldown(rank);
        if (seconds <= 0) {
            return 0;
        }
        double reduction = Math.min(ServerConfig.COOLDOWN_REDUCTION_CAP.get(), data.getStats().get(StatType.COOLDOWN_REDUCTION));
        // 速射の心得など（その流派のスキルのクールダウン -%）
        double school = PassiveService.schoolValue(data, jp.amemaru.lostconnection.job.def.PassiveDef.Type.SCHOOL_HASTE, skill);
        double ticks = seconds * (1.0 - Math.max(0.0, reduction) / 100.0) * (1.0 - Math.min(80.0, school) / 100.0) * 20.0;
        return Math.max(1, Mth.ceil(ticks));
    }

    /** クールダウンの長さ（tick）。疾風の弓舞の間は、早く戻る（Phase 6 その2）。 */
    public static int cooldownTicks(PlayerData data, SkillDef skill, int rank, long now) {
        int ticks = cooldownTicks(data, skill, rank);
        double dance = data.getBuffs().bowDance(now);
        return dance > 0 && ticks > 0 ? Math.max(1, Mth.ceil(ticks / (1.0 + dance / 100.0))) : ticks;
    }

    // ---------------------------------------------------------------- 毎 tick

    /** 毎 tick、サーバーで呼ぶ：詠唱の完了・ゲージ・パッシブの見直し・終わったクールダウンの片付け。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        long now = player.level().getGameTime();
        CastState casting = data.getCasting();
        if (casting != null) {
            if (!player.isAlive()) {
                cancelCast(player, data, false);
            } else if (casting.charging()) {
                if (now >= casting.endTick() + CHARGE_HOLD_TICKS) {
                    // 溜めきって自動で放つ：ロックしていれば、その敵に向き直して狙う（11月24日）
                    LockOnFacing.refresh(player);
                    releaseCharge(player, data, casting, now, LockOnFacing.lockedId(player), null);
                }
            } else if (now >= casting.endTick()) {
                finishCast(player, data, casting, now);
            }
        }
        // 障壁の時間切れ（HUDの表示を消すため）。祝福の盾は、消えるときに癒す
        BuffState buffs = data.getBuffs();
        if (buffs.barrierAmount > 0 && now >= buffs.barrierUntil) {
            double healOnEnd = buffs.barrierHealOnEnd;
            buffs.clearBarrier();
            data.markDirty(PlayerData.SYNC_VITALS);
            StatusService.sync(player, StatusFxS2C.Event.NONE);
            SkillExecutor.onBarrierEnded(player, data, healOnEnd);
        }
        GaugeService.tick(player, data, now);
        PassiveService.tick(player, data);
        // Phase 6 その2：回復し続ける・命の糸・静の呼吸、気配消し、星の環
        HealService.tick(player, data, now);
        StealthService.tick(player, data, now);
        StarRingService.tick(player, data, now);
        if (player.tickCount % 100 == 0) {
            data.pruneCooldowns(now);
        }
    }
}
