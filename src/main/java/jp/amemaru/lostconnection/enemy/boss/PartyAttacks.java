package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 参加者全員を狙う技（11月28日のご指示「現在ボスが一人をターゲットしてその人にしか攻撃をしないので、ボス参加している人全員を
 * ターゲットにする攻撃を追加して他のプレイヤーが一方的に攻撃できる状態を減らす（すごくでかい範囲攻撃やプレイヤーの立っている場所に
 * 攻撃を出すなど）」【補完】）。どのボスも、敵の定義に技があれば使う。名前・色・強さはボスごとに、定義と言葉で決める。
 *
 * <ul>
 *   <li>{@code volley}（一斉の印）：参加者<b>全員の足もと</b>に、同時に赤い円（半径 {@code range}）。{@code windup} 秒で打つ。
 *       {@code waves} 回（第3段階と怒ったときは1回多く）、{@code gap} 秒ずつ遅れて、そのとき立っている所を追いかける。
 *       止まって叩き続けている人に当たる</li>
 *   <li>{@code nova}（大技）：部屋のほぼ全体を打つ。{@code kinds} の形（1：ボスのまわりの大きな円（半径 {@code range}）・
 *       2：ボスのそばの金の円（半径 {@code safe_radius}）の外ぜんぶ・4：部屋に出る金の円（{@code safe_count} 個・半径
 *       {@code safe_radius}）の外ぜんぶ）を、足した数のうちから順に使う。逃げこむ形は、部屋の広さに合わせて予兆を長くする</li>
 * </ul>
 * どちらも、最初は {@code first} 秒のあとで、そのあとは {@code cooldown} 秒ごと（第3段階は0.8倍。人数と怒りで短くなる）。
 * {@code phase} の段階から使う。一度に出すのは1つだけ。ボスごとに、出してはいけない間（静寂の帳・よろめき・ギミックの最中）は
 * {@link AmemaruBoss#partyAttackAllowed} で止める。
 */
public final class PartyAttacks {
    /** 大技の形：ボスのまわり。 */
    public static final int NOVA_OUT = 1;
    /** 大技の形：ボスのそばの金の円の外ぜんぶ。 */
    public static final int NOVA_IN = 2;
    /** 大技の形：部屋に出る金の円の外ぜんぶ。 */
    public static final int NOVA_SAFE = 4;
    private static final int[] ORDER = {NOVA_OUT, NOVA_IN, NOVA_SAFE};

    /** 一斉の印の1回分（足もとの円の並び）。 */
    private record Wave(List<Vec3> spots, double radius, int[] left, Set<UUID> hit) {
    }

    private final AmemaruBoss boss;
    private int volleyCooldown = -1;
    private int novaCooldown = -1;
    private final List<Wave> waves = new ArrayList<>();
    private int wavesLeft;
    private int nextWaveIn;
    private int novaIndex = -1;
    /** 出ている大技（なければ 0）。 */
    private int novaKind;
    private int novaLeft;
    @Nullable
    private Vec3 novaCenter;
    private double novaRadius;
    private final List<Vec3> safeSpots = new ArrayList<>();
    private double safeRadius;
    private final Set<String> tips = new HashSet<>();

    PartyAttacks(AmemaruBoss boss) {
        this.boss = boss;
    }

    /** 戦いをはじめからにする。 */
    public void reset() {
        volleyCooldown = -1;
        novaCooldown = -1;
        waves.clear();
        wavesLeft = 0;
        nextWaveIn = 0;
        novaKind = 0;
        novaLeft = 0;
        novaCenter = null;
        safeSpots.clear();
        tips.clear();
    }

    /** 出ている印と大技を、打たずに取り消す（管理者のコマンドで、別の技を出させるとき）。 */
    public void cancel() {
        waves.clear();
        wavesLeft = 0;
        nextWaveIn = 0;
        novaKind = 0;
        novaLeft = 0;
        novaCenter = null;
        safeSpots.clear();
    }

    /** 全員を狙う技の名前か（出ている間、ボスのほかの技の名前で上書きしない）。 */
    public static boolean partyCast(String skill) {
        return skill.equals("volley") || skill.startsWith("nova_") || skill.equals("share");
    }

    /** 全員を狙う技を出している途中か（印が残っている・大技の予兆の間）。 */
    public boolean active() {
        return wavesLeft > 0 || !waves.isEmpty() || novaKind != 0;
    }

    /** 自動テスト：今の大技の金の円（逃げこむ所）。 */
    public List<Vec3> safeSpots() {
        return List.copyOf(safeSpots);
    }

    /** 自動テスト：今の大技の形（なければ 0）。 */
    public int novaKind() {
        return novaKind;
    }

    /** 自動テスト：まだ打っていない一斉の印の場所。 */
    public List<Vec3> pendingSpots() {
        List<Vec3> list = new ArrayList<>();
        for (Wave wave : waves) {
            list.addAll(wave.spots());
        }
        return list;
    }

    @Nullable
    private EnemyDef.Skill skill(String name) {
        EnemyDef def = boss.def();
        return def == null ? null : def.skills().get(name);
    }

    /** ボスに定義がある全員を狙う技の名前（管理者のコマンドで出せる名前）。 */
    public List<String> names() {
        List<String> list = new ArrayList<>();
        if (skill("volley") != null) {
            list.add("volley");
        }
        EnemyDef.Skill nova = skill("nova");
        if (nova != null) {
            list.add("nova");
            int kinds = kinds(nova);
            if ((kinds & NOVA_OUT) != 0) {
                list.add("nova_out");
            }
            if ((kinds & NOVA_IN) != 0) {
                list.add("nova_in");
            }
            if ((kinds & NOVA_SAFE) != 0) {
                list.add("nova_safe");
            }
        }
        return list;
    }

    private static int kinds(EnemyDef.Skill nova) {
        int kinds = (int) Math.round(nova.param("kinds", NOVA_SAFE));
        return kinds & (NOVA_OUT | NOVA_IN | NOVA_SAFE);
    }

    public static String kindName(int kind) {
        return switch (kind) {
            case NOVA_OUT -> "out";
            case NOVA_IN -> "in";
            default -> "safe";
        };
    }

    // ================================================================ 毎 tick

    void tick(ServerLevel level) {
        BossRoom room = boss.room();
        if (room == null || !room.fighting() || !room.owns(boss)) {
            if (active() || volleyCooldown >= 0 || novaCooldown >= 0) {
                reset();
            }
            return;
        }
        List<ServerPlayer> targets = targets(level, room.participants());
        tickAttacks(level, targets);
        EnemyDef.Skill volley = skill("volley");
        EnemyDef.Skill nova = skill("nova");
        if (volley != null) {
            if (volleyCooldown < 0) {
                volleyCooldown = seconds(volley.param("first", 15.0));
            } else if (volleyCooldown > 0) {
                volleyCooldown--;
            }
        }
        if (nova != null) {
            if (novaCooldown < 0) {
                novaCooldown = seconds(nova.param("first", 32.0));
            } else if (novaCooldown > 0) {
                novaCooldown--;
            }
        }
        if (active() || targets.isEmpty() || !boss.partyAttackAllowed() || !AmemaruBoss.partyAuto) {
            return;
        }
        if (nova != null && novaCooldown == 0 && boss.phase() >= (int) Math.round(nova.param("phase", 1.0))) {
            int kind = nextKind(nova);
            if (startNova(level, kind, targets, room.arenaCenter(), room.def().arenaRadius(), room.def().startRadius())) {
                volleyCooldown = Math.max(volleyCooldown, 80);
            } else {
                novaCooldown = 40;
            }
            return;
        }
        if (volley != null && volleyCooldown == 0 && boss.phase() >= (int) Math.round(volley.param("phase", 1.0))) {
            if (startVolley(level, targets)) {
                novaCooldown = Math.max(novaCooldown, 80);
            } else {
                volleyCooldown = 40;
            }
        }
    }

    /** 狙える参加者（生きていて、倒れていない・同じ世界）。 */
    private static List<ServerPlayer> targets(ServerLevel level, List<ServerPlayer> participants) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : participants) {
            if (player.isAlive() && player.level() == level && !player.isSpectator() && !player.isCreative() && !BossRoom.isDown(player)) {
                list.add(player);
            }
        }
        return list;
    }

    private static int seconds(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    private int cooldownTicks(EnemyDef.Skill skill) {
        double scale = boss.phase() >= 3 ? 0.8 : 1.0;
        return Math.max(60, (int) Math.round(skill.cooldownTicks() * scale * boss.pace()));
    }

    private int nextKind(EnemyDef.Skill nova) {
        int kinds = kinds(nova);
        if (kinds == 0) {
            kinds = NOVA_SAFE;
        }
        for (int step = 1; step <= ORDER.length; step++) {
            int index = Math.floorMod(novaIndex + step, ORDER.length);
            if ((kinds & ORDER[index]) != 0) {
                novaIndex = index;
                return ORDER[index];
            }
        }
        return NOVA_SAFE;
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            boss.tip("boss.amemaru.party.tip." + key);
        }
    }

    // ================================================================ 一斉の印

    /** 一斉の印を出す（足もとの円を、全員に。続きは {@link #tickAttacks} が追いかけて出す）。出せたら true。 */
    public boolean startVolley(ServerLevel level, List<ServerPlayer> targets) {
        EnemyDef.Skill volley = skill("volley");
        if (volley == null || targets.isEmpty()) {
            return false;
        }
        int count = (int) Math.round(volley.param("waves", 2.0)) + (boss.phase() >= 3 || boss.enraged() ? 1 : 0);
        wavesLeft = Math.max(1, Math.min(5, count));
        nextWaveIn = 0;
        int windup = Math.max(20, volley.windupTicks());
        int gap = seconds(volley.param("gap", 0.9));
        volleyCooldown = cooldownTicks(volley);
        boss.setCast("volley", windup + gap * (wavesLeft - 1));
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 1.3F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 30,
                boss.getBbWidth() * 0.5, 0.6, boss.getBbWidth() * 0.5, 0.2);
        tipOnce("volley");
        return true;
    }

    private void placeWave(ServerLevel level, List<ServerPlayer> targets) {
        EnemyDef.Skill volley = skill("volley");
        if (volley == null) {
            return;
        }
        double radius = volley.range() > 0.0 ? volley.range() : 2.5;
        int windup = Math.max(20, volley.windupTicks());
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer player : targets) {
            Vec3 at = ground(level, player.position());
            boolean near = false;
            for (Vec3 other : spots) {
                near |= other.distanceToSqr(at) < 0.8 * 0.8;
            }
            if (near) {
                continue;
            }
            spots.add(at);
            GroundMarker.place(level, at, (float) radius, windup, GroundMarker.KIND_DANGER);
        }
        if (!spots.isEmpty()) {
            waves.add(new Wave(spots, radius, new int[]{windup}, new HashSet<>()));
            Vec3 first = spots.get(0);
            level.playSound(null, first.x, first.y, first.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
    }

    private void land(ServerLevel level, Wave wave, List<ServerPlayer> targets) {
        EnemyDef.Skill volley = skill("volley");
        double power = volley == null ? 1.0 : volley.power();
        double reach = wave.radius() + 0.3;
        int sounds = 0;
        for (Vec3 at : wave.spots()) {
            boss.impactFx("volley", AmemaruMob.TELEGRAPH_CIRCLE, at, 0.0F, (float) wave.radius(), 0.0F);
            if (sounds++ < 3) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9F, 1.2F);
            }
            for (ServerPlayer player : targets) {
                double dx = player.getX() - at.x;
                double dz = player.getZ() - at.z;
                if (dx * dx + dz * dz <= reach * reach && Math.abs(player.getY() - at.y) < 3.0 && wave.hit().add(player.getUUID())
                        && EnemyCombat.strike(boss, player, power, null)) {
                    player.push(0.0, volley == null ? 0.4 : volley.param("lift", 0.4), 0.0);
                    player.hurtMarked = true;
                    boss.onPartyHit(player, "volley");
                }
            }
        }
    }

    // ================================================================ 大技

    /**
     * 大技を出す（{@code center}・{@code fieldRadius}：部屋の真ん中と、打つ広さ。{@code playRadius}：金の円を置く広さ）。
     * 出せたら true。
     */
    public boolean startNova(ServerLevel level, int kind, List<ServerPlayer> targets, Vec3 center, double fieldRadius, double playRadius) {
        EnemyDef.Skill nova = skill("nova");
        if (nova == null || targets.isEmpty()) {
            return false;
        }
        safeSpots.clear();
        Vec3 here = ground(level, boss.position());
        double play = Math.max(8.0, playRadius);
        int windup;
        switch (kind) {
            case NOVA_OUT -> {
                novaCenter = here;
                novaRadius = nova.range() > 0.0 ? nova.range() : 14.0;
                windup = Math.max(30, nova.windupTicks());
                GroundMarker.place(level, here, (float) novaRadius, windup, GroundMarker.KIND_DANGER);
            }
            case NOVA_IN -> {
                novaCenter = ground(level, center);
                novaRadius = Math.max(fieldRadius, play + 4.0);
                safeRadius = nova.param("safe_radius", 7.0);
                safeSpots.add(here);
                windup = Math.max(nova.windupTicks(), seconds(1.5 + 0.12 * play));
                GroundMarker.place(level, novaCenter, (float) novaRadius, windup, GroundMarker.KIND_FIELD);
                GroundMarker.place(level, here, (float) safeRadius, windup, GroundMarker.KIND_SAFE);
            }
            default -> {
                kind = NOVA_SAFE;
                novaCenter = ground(level, center);
                novaRadius = Math.max(fieldRadius, play + 4.0);
                safeRadius = nova.param("safe_radius", 4.5);
                int count = (int) Math.round(nova.param("safe_count", 3.0)) + (targets.size() >= 5 ? 1 : 0);
                chooseSafeSpots(level, novaCenter, play, Math.max(1, Math.min(6, count)));
                if (safeSpots.isEmpty()) {
                    // 置ける所がない（部屋の形）：ボスのまわりを打つ形にする
                    novaCenter = here;
                    novaRadius = nova.range() > 0.0 ? nova.range() : 14.0;
                    kind = NOVA_OUT;
                    windup = Math.max(30, nova.windupTicks());
                    GroundMarker.place(level, here, (float) novaRadius, windup, GroundMarker.KIND_DANGER);
                    break;
                }
                windup = Math.max(nova.windupTicks(), seconds(1.5 + 0.11 * play));
                GroundMarker.place(level, novaCenter, (float) novaRadius, windup, GroundMarker.KIND_FIELD);
                for (Vec3 spot : safeSpots) {
                    GroundMarker.place(level, spot, (float) safeRadius, windup, GroundMarker.KIND_SAFE);
                }
            }
        }
        // 11月26日：ひとり確認モード・ひとりで戦っているときは、逃げこむ間を少し長く
        if (kind != NOVA_OUT && jp.amemaru.lostconnection.tower.SoloTest.aloneInFight(targets)) {
            windup += 20;
        }
        novaKind = kind;
        novaLeft = windup;
        novaCooldown = cooldownTicks(nova);
        boss.setCast("nova_" + kindName(kind), windup);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.4F, 0.6F);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        tipOnce("nova_" + kindName(kind));
        return true;
    }

    /** 金の円を、部屋のまん中のまわりに、ばらけさせて置く（立てる所だけ。ボスのそばと、ほかの円のそばはさける）。 */
    private void chooseSafeSpots(ServerLevel level, Vec3 center, double play, int count) {
        double base = boss.getRandom().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 24; attempt++) {
                double a = base + Math.PI * 2.0 * i / count + (boss.getRandom().nextDouble() - 0.5) * (attempt < 12 ? 0.5 : 1.6);
                double d = play * (0.35 + boss.getRandom().nextDouble() * (attempt < 12 ? 0.35 : 0.5));
                Vec3 want = new Vec3(center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d);
                Vec3 spot = standable(level, want, 5);
                if (spot == null) {
                    continue;
                }
                if (Math.hypot(spot.x - boss.getX(), spot.z - boss.getZ()) < safeRadius + 4.0) {
                    continue;
                }
                boolean crowded = false;
                for (Vec3 other : safeSpots) {
                    crowded |= Math.hypot(other.x - spot.x, other.z - spot.z) < safeRadius * 2.5;
                }
                if (!crowded) {
                    safeSpots.add(spot);
                    break;
                }
            }
        }
    }

    private void landNova(ServerLevel level, List<ServerPlayer> targets) {
        EnemyDef.Skill nova = skill("nova");
        Vec3 center = novaCenter;
        int kind = novaKind;
        novaKind = 0;
        novaCenter = null;
        if (nova == null || center == null) {
            safeSpots.clear();
            return;
        }
        boss.impactFx("nova", AmemaruMob.TELEGRAPH_CIRCLE, center, 0.0F, (float) Math.min(novaRadius, 40.0), 0.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.2F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.6F, 0.7F);
        for (ServerPlayer player : targets) {
            if (!inNova(kind, center, player.position())) {
                continue;
            }
            boss.impactFx("nova_hit", AmemaruMob.TELEGRAPH_CIRCLE, ground(level, player.position()), 0.0F, 2.6F, 0.0F);
            if (EnemyCombat.strike(boss, player, nova.power(), null)) {
                player.push(0.0, nova.param("lift", 0.6), 0.0);
                player.hurtMarked = true;
                boss.onPartyHit(player, "nova");
            }
        }
        for (Vec3 spot : safeSpots) {
            level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.4, spot.z, 24, safeRadius * 0.4, 0.2, safeRadius * 0.4, 0.05);
        }
        safeSpots.clear();
    }

    /** その所が大技に打たれるか。 */
    boolean inNova(int kind, Vec3 center, Vec3 pos) {
        return hitBy(kind, center, novaRadius, safeSpots, safeRadius, pos);
    }

    /**
     * 大技に打たれる所か（{@link #NOVA_OUT}：ボスのまわりの円の中。ほかの形：金の円のどれにも入っていない）。高さが6ブロックより
     * 離れていれば当たらない。自動テストからも使う。
     */
    public static boolean hitBy(int kind, Vec3 center, double radius, List<Vec3> safes, double safeRadius, Vec3 pos) {
        if (Math.abs(pos.y - center.y) > 6.0) {
            return false;
        }
        if (kind == NOVA_OUT) {
            return Math.hypot(pos.x - center.x, pos.z - center.z) <= radius + 0.4;
        }
        for (Vec3 spot : safes) {
            if (Math.hypot(pos.x - spot.x, pos.z - spot.z) <= safeRadius + 0.3) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 印と大技の時を進める

    /** 一斉の印の続き（追いかける円）と、打つ時を進める（毎 tick。自動テストからも、狙う人を渡して呼べる）。 */
    public void tickAttacks(ServerLevel level, List<ServerPlayer> targets) {
        if (wavesLeft > 0 && --nextWaveIn <= 0) {
            placeWave(level, targets);
            wavesLeft--;
            EnemyDef.Skill volley = skill("volley");
            nextWaveIn = seconds(volley == null ? 0.9 : volley.param("gap", 0.9));
        }
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave wave = waves.get(i);
            if (--wave.left()[0] <= 0) {
                waves.remove(i);
                land(level, wave, targets);
            }
        }
        if (novaKind != 0) {
            Vec3 center = novaCenter;
            if (center != null && novaLeft % 8 == 0) {
                // ボスに、力が集まっていく光
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 16,
                        boss.getBbWidth() * 0.7, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.7, 0.4);
            }
            if (--novaLeft <= 0) {
                landNova(level, targets);
            }
        }
    }

    // ================================================================ 足もと

    /** その所の足もと（下へ8ブロックまで、立てる面か水を探す。なければ、そのまま）。 */
    static Vec3 ground(ServerLevel level, Vec3 pos) {
        BlockPos feet = BlockPos.containing(pos.x, pos.y + 0.2, pos.z);
        for (int i = 0; i < 8; i++) {
            BlockPos at = feet.below(i);
            if (!level.getFluidState(at).isEmpty()) {
                // 水の中：その高さに置く（印は、水面まで持ち上げて描く）
                return new Vec3(pos.x, at.getY(), pos.z);
            }
            BlockPos under = at.below();
            VoxelShape shape = level.getBlockState(under).getCollisionShape(level, under);
            if (!shape.isEmpty()) {
                return new Vec3(pos.x, under.getY() + shape.max(Direction.Axis.Y), pos.z);
            }
        }
        return pos;
    }

    /** {@code want} のまわりの高さ（上下 {@code span}）で、2マスあいていて、下が固い所（なければ null）。 */
    @Nullable
    static Vec3 standable(ServerLevel level, Vec3 want, int span) {
        BlockPos base = BlockPos.containing(want.x, want.y, want.z);
        for (int dy = span; dy >= -span; dy--) {
            BlockPos feet = base.above(dy);
            BlockState below = level.getBlockState(feet.below());
            if (below.getCollisionShape(level, feet.below()).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                continue;
            }
            if (!level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isSource()) {
                continue;
            }
            return new Vec3(want.x, feet.getY(), want.z);
        }
        return null;
    }
}
