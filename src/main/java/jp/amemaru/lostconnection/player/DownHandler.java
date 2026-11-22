package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.DownStateS2C;
import jp.amemaru.lostconnection.party.PartyService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ダウンと蘇生・教会での復活・デスペナルティ（Phase 4。Phase 0-A 20章「ダウンとデスペナルティ」・24章の回答、Phase 0-B 2章「死亡は独自の流れに置き換える」）。
 *
 * <ul>
 *   <li>HPが0になると、バニラの死亡の代わりに「ダウン」になる（config の {@code downSeconds}、初期値30秒）。ダウンの間は
 *       動けず、攻撃・スキル・アイテムを使えない。ダメージを受けず、魔物にも狙われない。</li>
 *   <li>近く（{@code reviveRange}、2.5ブロック）でしゃがみ続けると、仲間を起こせる（{@code reviveSeconds}、3秒。
 *       ヒーラーは {@code healerReviveSeconds}、1.5秒）。しゃがむのをやめる・離れると、たまった分は0に戻る。誰でも起こせる
 *       （パーティでなくても）【補完】。起き上がると、HPは最大の30%、2秒はダメージを受けない【補完】。</li>
 *   <li>時間切れで、教会の呼び戻しの円（{@link ChurchPoint}）へ戻る。呼び戻しの儀の代価として所持金の5%
 *       （{@code deathMoneyPercent}）を失う。経験値とアイテムは失わない。HPとMPは全快、60秒の「衰弱」
 *       （{@code weaknessSeconds}・{@code weaknessPercent}：HP・MP・攻撃・魔力・防御・素早さ・会心が10%下がる）。</li>
 *   <li>ダウン中にリタイアのキー（初期値H）を3秒押し続けると、時間切れを待たずに教会へ戻る（代価も同じ。11月22日のご指示）。</li>
 *   <li>ダウン中に抜けた（サーバーが止まった）ら、入り直したときに教会へ戻す（代価も払う）【補完】。</li>
 *   <li>奈落と /kill はバニラの死亡のまま（すぐにリスポーンし、教会へ戻す。代価も払う）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class DownHandler {
    /** ダウンの間、バニラの体力として置く値（0にすると死亡になるため） */
    private static final float DOWN_HEALTH = 0.01F;
    /** ダウンを知らせる距離（パーティのメンバーには、どこにいても知らせる） */
    private static final double NOTICE_RANGE = 32.0;
    /** ダウンした人を狙っていた魔物の狙いを外す範囲 */
    private static final double AGGRO_CLEAR_RANGE = 48.0;
    /** ヒーラーの職業（Phase 6で作る。職業のIDの最後がこれなら、早く起こせる） */
    private static final String HEALER_JOB = "healer";

    private DownHandler() {
    }

    // ================================================================ 調べる

    public static boolean isDown(Player player) {
        return PlayerDataCapability.get(player).map(data -> data.down().isDown()).orElse(false);
    }

    // ================================================================ 倒れる

    /** HPが0になった：バニラの死亡を止めて、ダウンにする（奈落と /kill はそのまま）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        Optional<PlayerData> found = PlayerDataCapability.get(player);
        if (found.isEmpty()) {
            return;
        }
        PlayerData data = found.get();
        DamageSource source = event.getSource();
        // 決闘の間は、倒れずに決闘が終わる（Phase 6 その4。ダウン・デスペナルティはない）
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && jp.amemaru.lostconnection.social.DuelService.interceptDeath(player)) {
            event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // 奈落・/kill：バニラの死亡。リスポーンしたら教会へ戻す（onRespawn）
            data.down().setDiedForReal(true);
            return;
        }
        event.setCanceled(true);
        player.setHealth(DOWN_HEALTH);
        if (!data.down().isDown()) {
            goDown(player, data);
        }
    }

    /** ダウンにする（管理者のコマンドからも呼ぶ）。 */
    public static void goDown(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        data.down().start(now, ServerConfig.DOWN_SECONDS.get() * 20);
        data.setHp(0);
        data.countDown();
        SkillService.cancelCast(player, data, false);
        player.stopUsingItem();
        player.stopRiding();
        player.stopFallFlying();
        player.setSprinting(false);
        if (player.getHealth() > DOWN_HEALTH) {
            player.setHealth(DOWN_HEALTH);
        }
        data.setExpectedVanillaHealth(player.getHealth());
        // 狙っていた魔物は、ほかの人を探す
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AGGRO_CLEAR_RANGE),
                mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.4, player.getZ(), 24, 0.4, 0.2, 0.4, 0.01);
        Component name = player.getDisplayName();
        for (ServerPlayer other : noticeTargets(player)) {
            other.sendSystemMessage(Component.translatable("message.amemaru.down.other", name).withStyle(ChatFormatting.RED));
        }
        AmemaruMod.LOGGER.info("[amemaru] {} is down", player.getGameProfile().getName());
        sendState(player, data);
        PartyService.markChanged(player);
    }

    // ================================================================ 毎 tick

    /** ダウン中の毎 tick（PlayerEvents から）：起こす人を探し、時間切れなら教会へ戻す。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        DownState down = data.down();
        if (!down.isDown()) {
            return;
        }
        long now = player.level().getGameTime();
        if (now >= down.until()) {
            returnToChurch(player, data);
            return;
        }
        ServerPlayer reviver = findReviver(player, down.reviver());
        if (reviver == null) {
            if (down.reviver() != null) {
                down.resetRevive();
            }
        } else {
            if (!reviver.getUUID().equals(down.reviver())) {
                down.startRevive(reviver.getUUID(), reviveTicks(reviver));
            }
            if (down.addProgress()) {
                revive(player, data, reviver);
                return;
            }
        }
        if (down.consumeChanged() || now % 20 == 0) {
            sendState(player, data);
        }
    }

    /** 起こしている人（しゃがみ続けている人。前の人がまだ起こしていれば、その人のまま）。 */
    @Nullable
    private static ServerPlayer findReviver(ServerPlayer downed, @Nullable UUID current) {
        double range = ServerConfig.REVIVE_RANGE.get();
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : downed.serverLevel().players()) {
            if (!canRevive(downed, other, range)) {
                continue;
            }
            if (other.getUUID().equals(current)) {
                return other;
            }
            double distance = other.distanceToSqr(downed);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    /** その人が、いま起こしているか（しゃがんでいて、近くて、自分はダウンしていない）。 */
    public static boolean canRevive(ServerPlayer downed, ServerPlayer other, double range) {
        if (other == downed || !other.isAlive() || other.isSpectator() || !other.isShiftKeyDown() || isDown(other)) {
            return false;
        }
        return other.level() == downed.level() && other.position().distanceToSqr(downed.position()) <= range * range;
    }

    /** 起こすのにかかる tick（ヒーラーは早い）。 */
    public static int reviveTicks(ServerPlayer reviver) {
        boolean healer = PlayerDataCapability.get(reviver)
                .map(data -> data.getCombatJob() != null && HEALER_JOB.equals(data.getCombatJob().getPath()))
                .orElse(false);
        double seconds = healer ? ServerConfig.HEALER_REVIVE_SECONDS.get() : ServerConfig.REVIVE_SECONDS.get();
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    // ================================================================ 起き上がる

    /** 起き上がる（起こした人がいる。管理者のコマンドでは null）。 */
    public static void revive(ServerPlayer player, PlayerData data, @Nullable ServerPlayer reviver) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        data.down().clear();
        data.down().setInvulnerableUntil(now + Math.round(ServerConfig.REVIVE_INVULNERABLE_SECONDS.get() * 20.0));
        if (!data.isStatsReady()) {
            LevelService.recompute(player, data);
        }
        double maxHp = data.getStats().maxHp();
        data.setHp(Math.max(1.0, Math.floor(maxHp * ServerConfig.REVIVE_HP_PERCENT.get() / 100.0)));
        data.markCombat(now);
        VitalsService.pushVanillaHealth(player, data);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.1F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.8, player.getZ(), 30, 0.5, 0.6, 0.5, 0.04);
        Component name = player.getDisplayName();
        Set<UUID> told = new HashSet<>();
        told.add(player.getUUID());
        if (reviver != null) {
            PlayerDataCapability.get(reviver).ifPresent(PlayerData::countRevive);
            // Phase 6 その3：縁帳（起こした・起こしてくれた）と称号の数
            jp.amemaru.lostconnection.guild.GuildHooks.onRevive(reviver, player);
            player.sendSystemMessage(Component.translatable("message.amemaru.down.revived_by", reviver.getDisplayName())
                    .withStyle(ChatFormatting.GREEN));
            reviver.sendSystemMessage(Component.translatable("message.amemaru.down.revived_other", name).withStyle(ChatFormatting.GREEN));
            told.add(reviver.getUUID());
            AmemaruMod.LOGGER.info("[amemaru] {} was revived by {}", player.getGameProfile().getName(),
                    reviver.getGameProfile().getName());
        } else {
            player.sendSystemMessage(Component.translatable("message.amemaru.down.revived_admin").withStyle(ChatFormatting.GREEN));
        }
        for (ServerPlayer member : PartyService.onlineMembers(player)) {
            if (told.add(member.getUUID())) {
                member.sendSystemMessage(Component.translatable("message.amemaru.down.revived_party", name).withStyle(ChatFormatting.GREEN));
            }
        }
        sendState(player, data);
        PartyService.markChanged(player);
    }

    // ================================================================ リタイア

    /**
     * ダウン中のリタイア（11月22日のご指示）：決まったキー（初期値H）を3秒押し続けると、30秒を待たずに教会へ戻る。代価・衰弱・
     * HPとMPの全快は、時間切れと同じ。起こされている途中でもできる【補完】。ダウンしていなければ何もしない。
     *
     * @return 教会へ戻したら true
     */
    public static boolean retire(ServerPlayer player, PlayerData data) {
        if (!data.down().isDown()) {
            return false;
        }
        AmemaruMod.LOGGER.info("[amemaru] {} retired while down", player.getGameProfile().getName());
        // そばで起こそうとしていた人に知らせる（パーティの仲間には、教会に戻った知らせが出る）
        Set<UUID> party = new HashSet<>();
        for (ServerPlayer member : PartyService.onlineMembers(player)) {
            party.add(member.getUUID());
        }
        Component name = player.getDisplayName();
        for (ServerPlayer other : noticeTargets(player)) {
            if (!party.contains(other.getUUID())) {
                other.sendSystemMessage(Component.translatable("message.amemaru.down.retired_other", name).withStyle(ChatFormatting.GRAY));
            }
        }
        returnToChurch(player, data);
        return true;
    }

    // ================================================================ 教会へ戻る（呼び戻しの儀）

    /** 教会の呼び戻しの円へ戻す：代価（所持金の5%）、HPとMPは全快、衰弱。 */
    public static void returnToChurch(ServerPlayer player, PlayerData data) {
        double percent = ServerConfig.DEATH_MONEY_PERCENT.get();
        long lost = Wallet.take(player, data, Wallet.percentOf(Wallet.balance(data), percent), "death penalty");
        data.down().clear();
        data.countChurch();
        ChurchPoint.Spot spot = ChurchPoint.resolve(player.server);
        Vec3 at = ChurchPoint.scatter(spot, ServerConfig.CHURCH_SCATTER.get(), player.getRandom());
        player.stopRiding();
        player.teleportTo(spot.level(), at.x, at.y, at.z, spot.yaw(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.clearFire();
        int weakSeconds = ServerConfig.WEAKNESS_SECONDS.get();
        if (weakSeconds > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.WEAKENED.get(), weakSeconds * 20, 0, false, true, true));
        }
        LevelService.recompute(player, data);
        data.setHp(data.getStats().maxHp());
        data.setMp(data.getStats().maxMp());
        VitalsService.pushVanillaHealth(player, data);

        ServerLevel level = spot.level();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40, 0.6, 1.0, 0.6, 0.03);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.amemaru.down.church_title")
                .withStyle(ChatFormatting.AQUA)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.amemaru.down.church_subtitle")));
        Component money = lost > 0
                ? Component.translatable("message.amemaru.down.church_money", formatPercent(percent), lost)
                : Component.translatable("message.amemaru.down.church_free");
        player.sendSystemMessage(money.copy().withStyle(ChatFormatting.AQUA));
        if (weakSeconds > 0) {
            player.sendSystemMessage(Component.translatable("message.amemaru.down.weakened", weakSeconds,
                    formatPercent(ServerConfig.WEAKNESS_PERCENT.get())).withStyle(ChatFormatting.GRAY));
        }
        Component name = player.getDisplayName();
        for (ServerPlayer member : PartyService.onlineMembers(player)) {
            if (member != player) {
                member.sendSystemMessage(Component.translatable("message.amemaru.down.church_party", name).withStyle(ChatFormatting.GRAY));
            }
        }
        AmemaruMod.LOGGER.info("[amemaru] {} returned to the church ({}, lost {} knots)", player.getGameProfile().getName(),
                spot.source(), lost);
        sendState(player, data);
        PartyService.markChanged(player);
    }

    private static String formatPercent(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.valueOf(value);
    }

    // ================================================================ 入る・出る・リスポーン

    /** 入ったとき：ダウン中に抜けていたら（サーバーが止まった場合も）、教会へ戻す。 */
    public static void onLoggedIn(ServerPlayer player, PlayerData data) {
        if (data.down().leftWhileDown() && !(player instanceof FakePlayer)) {
            returnToChurch(player, data);
        }
    }

    /** リスポーン（PlayerEvents から）。バニラの死亡（奈落・/kill）のあとなら教会へ戻して true。 */
    public static boolean onRespawn(ServerPlayer player, PlayerData data) {
        if (!data.down().diedForReal() || player instanceof FakePlayer) {
            return false;
        }
        returnToChurch(player, data);
        return true;
    }

    /** まわりの人が、ダウンしている人を見始めた：いまの様子を送る。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            PlayerDataCapability.get(target).ifPresent(data -> {
                if (data.down().isDown()) {
                    ModNetwork.sendTo(viewer, DownStateS2C.of(target, data));
                }
            });
        }
    }

    // ================================================================ ダウンの間にできないこと

    /** ダウンの間と、起き上がってすぐはダメージを受けない（奈落と /kill は受ける）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            if (data.down().isDown() || data.down().invulnerable(player.level().getGameTime())) {
                event.setCanceled(true);
            }
        });
    }

    /** 魔物は、ダウンしている人を狙わない。 */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer target && isDown(target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ================================================================ 衰弱

    /** 衰弱の間は、ステータスが下がる（HP・MP・攻撃・魔力・防御・素早さ・会心）。 */
    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || !player.hasEffect(ModEffects.WEAKENED.get())) {
            return;
        }
        double percent = ServerConfig.WEAKNESS_PERCENT.get();
        for (StatType type : StatType.allocatable()) {
            event.addPercent(type, -percent);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        recomputeIfWeakness(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        recomputeIfWeakness(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        recomputeIfWeakness(event.getEntity(), event.getEffectInstance());
    }

    private static void recomputeIfWeakness(net.minecraft.world.entity.LivingEntity entity, @Nullable MobEffectInstance effect) {
        if (entity instanceof ServerPlayer player && effect != null && effect.getEffect() == ModEffects.WEAKENED.get()) {
            // 効果が付く・消えるのは、その tick の終わりに計算し直す（このイベントの間は、まだ付いていない・残っている）
            PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        }
    }

    // ================================================================ 送る

    /** 本人とまわりの人に、ダウンの様子を送る。 */
    public static void sendState(ServerPlayer player, PlayerData data) {
        ModNetwork.sendToTrackingAndSelf(player, DownStateS2C.of(player, data));
    }

    /** ダウンを知らせる相手：パーティのメンバー（どこにいても）と、近くの人。 */
    private static List<ServerPlayer> noticeTargets(ServerPlayer player) {
        List<ServerPlayer> list = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(player.getUUID());
        for (ServerPlayer member : PartyService.onlineMembers(player)) {
            if (seen.add(member.getUUID())) {
                list.add(member);
            }
        }
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other.distanceToSqr(player) <= NOTICE_RANGE * NOTICE_RANGE && seen.add(other.getUUID())) {
                list.add(other);
            }
        }
        return list;
    }
}
