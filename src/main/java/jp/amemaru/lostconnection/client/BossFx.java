package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * ボス戦の曲と演出（11月25日のご指示「ボス戦のドキドキ感やハラハラ感が全くないため、ボス戦ではもっとエフェクトやボス固有のBGMを
 * 追加する」）。クライアントだけで動く。
 *
 * <ul>
 *   <li>曲：ボスのHPの帯が出ている間（戦いの参加者）だけ、そのボスの曲（{@code music.boss.<ボス>}。{@code tools/audio/boss_music.py}
 *       で作ったオリジナル曲）を流し、いつもの曲は止める。戦いが終わると、ゆっくり消える。追いこんだ（HP30%以下・怒った）ときは、
 *       少し速く高くなる。大きさは、Minecraft の「音楽」の音量に従う</li>
 *   <li>はじまり：画面の上下に黒い帯と、ボスの名前・階の名前（3秒あまり）。低い雷鳴と、画面の揺れ</li>
 *   <li>段階が変わった：ボスから光の波と柱が立ち、画面が白く光って揺れる。「第N段階」</li>
 *   <li>追いこんだ：「追いこんだ ― ここが正念場」と、ボスのまわりの赤い気配</li>
 *   <li>ギミックの構え（大地の咆哮・大放水・残響の色・切断判定）：画面のふちが赤く脈打ち、残り3秒から時を刻む音</li>
 *   <li>自分のHPが30%以下：心臓の音と、画面のふちの赤い脈（15%以下で速く）</li>
 *   <li>大きく打たれた（最大HPの15%以上）：画面が揺れ、ふちが赤く光る</li>
 * </ul>
 * 設定（{@code amemaru-client.toml} の {@code boss}）で、曲と演出をそれぞれ止められる。画面の揺れは「画面を揺らす」の設定にも従う。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class BossFx {
    public static final IGuiOverlay OVERLAY = BossFx::render;

    private static final double FIND_RANGE = 96.0;
    private static final long INTRO_MILLIS = 3400L;
    private static final long BANNER_MILLIS = 2400L;
    /** 時を刻む・赤いふちを出す、ボスの様子（ギミックの構え）。 */
    private static final Set<String> DANGER = Set.of("earth_roar", "flood", "echo", "sever");
    /** ボスごとの色（名前・光の波・段階の字）。 */
    private static final Map<String, Integer> ACCENT = Map.of(
            "veldhorn", 0xF2B25C,
            "old_ward", 0x8EE07A,
            "hydram", 0x6CCBFF,
            "cantare", 0xC8A8FF,
            "ancient_guardian", 0xF2D16B);

    // 今の戦い
    private static int bossEntity = -1;
    private static String bossKey = "";
    private static int lastPhase;
    private static int lastFight;
    private static int missingTicks;
    // 曲
    @Nullable
    private static BossTrack track;
    private static String trackKey = "";
    private static int hush;
    // 演出
    private static long introStart = -INTRO_MILLIS;
    private static Component introName = Component.empty();
    private static Component introPlace = Component.empty();
    private static int accent = 0xFF6B6B;
    private static long bannerStart = -BANNER_MILLIS;
    private static Component bannerText = Component.empty();
    private static int bannerColor = 0xFFFFFF;
    private static float flash;
    private static float redHit;
    private static float heart;
    private static int heartIn;
    private static double lastHp = -1.0;
    private static float danger;
    private static float dangerShown;
    private static int lastDangerSecond = -1;

    private BossFx() {
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        // HPの帯が出ている（戦いの参加者）＝ そのボスの曲を流す印（AmemaruBoss で立てる。バニラはジ・エンドの外では使わない）
        boolean bar = mc.gui.getBossOverlay().shouldPlayMusic();
        AmemaruBoss boss = bar ? find(mc) : null;
        if (boss != null) {
            missingTicks = 0;
            onBoss(mc, boss);
        } else if (!bar || ++missingTicks > 60) {
            endFight();
        }
        tickMusic(mc);
        tickPlayer(mc, bossEntity >= 0);
        flash = Math.max(0.0F, flash - 0.07F);
        redHit = Math.max(0.0F, redHit - 0.045F);
        heart = Math.max(0.0F, heart - 0.055F);
        dangerShown += (danger - dangerShown) * 0.2F;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** いちばん近い、戦っているボス（96ブロックまで）。 */
    @Nullable
    private static AmemaruBoss find(Minecraft mc) {
        AmemaruBoss best = null;
        double bestDistance = FIND_RANGE * FIND_RANGE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AmemaruBoss candidate && candidate.isAlive() && candidate.fightState() != AmemaruBoss.FIGHT_NONE) {
                double d = candidate.distanceToSqr(mc.player);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static String keyOf(AmemaruBoss boss) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        return id == null ? "" : id.getPath();
    }

    private static void onBoss(Minecraft mc, AmemaruBoss boss) {
        if (boss.getId() != bossEntity) {
            boolean fresh = bossEntity < 0;
            bossEntity = boss.getId();
            bossKey = keyOf(boss);
            accent = ACCENT.getOrDefault(bossKey, 0xFF6B6B);
            lastPhase = boss.shownPhase();
            lastFight = boss.fightState();
            // 戦いが始まったところ（段階1・HPがほぼ満タン）なら、名前を出す。途中から戻ったときは出さない
            if (fresh && effects() && boss.shownPhase() == 1 && boss.getEnemyHp() >= boss.getEnemyMaxHp() * 0.95) {
                startIntro(mc, boss);
            }
        }
        int phase = boss.shownPhase();
        if (phase > lastPhase && effects()) {
            onPhase(mc, boss, phase);
        }
        lastPhase = phase;
        int fight = boss.fightState();
        if (fight == AmemaruBoss.FIGHT_INTENSE && lastFight != AmemaruBoss.FIGHT_INTENSE && effects()) {
            onIntense(mc, boss);
        }
        lastFight = fight;
        if (fight == AmemaruBoss.FIGHT_INTENSE && effects() && mc.level.getGameTime() % 3 == 0) {
            aura(mc, boss);
        }
        tickDanger(boss);
    }

    private static void endFight() {
        bossEntity = -1;
        bossKey = "";
        lastFight = AmemaruBoss.FIGHT_NONE;
        missingTicks = 0;
        danger = 0.0F;
        lastDangerSecond = -1;
    }

    private static void reset() {
        endFight();
        if (track != null) {
            Minecraft.getInstance().getSoundManager().stop(track);
            track = null;
        }
        trackKey = "";
        introStart = -INTRO_MILLIS;
        bannerStart = -BANNER_MILLIS;
        flash = 0.0F;
        redHit = 0.0F;
        heart = 0.0F;
        dangerShown = 0.0F;
        lastHp = -1.0;
    }

    private static boolean effects() {
        return ClientConfig.bossEffects();
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- 曲

    private static void tickMusic(Minecraft mc) {
        String want = bossEntity >= 0 && ClientConfig.bossMusic() ? bossKey : "";
        if (!want.equals(trackKey)) {
            if (track != null) {
                track.fadeOut();
                track = null;
            }
            trackKey = want;
            if (!want.isEmpty()) {
                start(mc, want);
            }
        }
        if (track == null) {
            return;
        }
        track.setIntense(lastFight == AmemaruBoss.FIGHT_INTENSE);
        // いつもの曲が始まらないように（止めるたびに、次の曲までの間が延びる）
        if (++hush >= 20) {
            hush = 0;
            mc.getMusicManager().stopPlaying();
        }
        // 音の読み直し（F3+T など）で止められた：かけ直す
        if (track.age() > 60 && !mc.getSoundManager().isActive(track)) {
            track = null;
            start(mc, trackKey);
        }
    }

    private static void start(Minecraft mc, String key) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(AmemaruMod.id("music.boss." + key));
        if (sound == null) {
            return;
        }
        track = new BossTrack(sound);
        mc.getSoundManager().play(track);
        mc.getMusicManager().stopPlaying();
        hush = 0;
    }

    /** ボスの曲（くり返す。はじめはふわっと大きくなり、終わりはゆっくり消える）。 */
    private static final class BossTrack extends AbstractTickableSoundInstance {
        private static final float BASE_VOLUME = 0.85F;
        private float level;
        private boolean fading;
        private float pitchGoal = 1.0F;
        private int age;

        BossTrack(SoundEvent sound) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0001F;
            this.pitch = 1.0F;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }

        @Override
        public boolean canStartSilent() {
            // 「音楽」の音量が0でも、かけておく（あとで上げたら聞こえる。かけ直しをくり返さない）
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (fading) {
                level -= 1.0F / 50.0F;
                if (level <= 0.0F) {
                    level = 0.0F;
                    stop();
                }
            } else {
                level = Math.min(1.0F, level + 1.0F / 30.0F);
            }
            volume = Math.max(0.0001F, level * BASE_VOLUME);
            pitch += (pitchGoal - pitch) * 0.04F;
        }

        void fadeOut() {
            fading = true;
        }

        void setIntense(boolean intense) {
            pitchGoal = intense ? 1.04F : 1.0F;
        }

        int age() {
            return age;
        }
    }

    // ---------------------------------------------------------------- 演出

    private static void startIntro(Minecraft mc, AmemaruBoss boss) {
        introStart = Util.getMillis();
        introName = boss.getDisplayName();
        introPlace = Component.translatable("boss.amemaru.intro." + bossKey);
        FxWorld.shake(mc.player.position(), 12, 0.7F);
        play(SoundEvents.TRIDENT_THUNDER, 0.6F, 0.55F);
        play(SoundEvents.BELL_RESONATE, 0.7F, 0.5F);
    }

    private static void onPhase(Minecraft mc, AmemaruBoss boss, int phase) {
        Vec3 at = boss.position();
        FxWorld.add(new FxWorld.Shockwave(at, 1.0F, 18.0F, 26, accent, 3.5F, 0.85F));
        FxWorld.add(new FxWorld.Pillar(FxWorld.Anchor.at(at), 2.2F, 24.0F, accent, 22));
        FxWorld.shake(mc.player.position(), 16, 1.1F);
        flash = 0.6F;
        banner(Component.translatable("boss.amemaru.fx.phase", phase), accent);
        play(SoundEvents.TRIDENT_THUNDER, 0.75F, 0.7F);
        play(SoundEvents.BELL_RESONATE, 0.6F, 0.8F);
    }

    private static void onIntense(Minecraft mc, AmemaruBoss boss) {
        FxWorld.add(new FxWorld.Shockwave(boss.position(), 1.0F, 14.0F, 22, 0xE03C3C, 2.5F, 0.7F));
        FxWorld.shake(mc.player.position(), 10, 0.6F);
        redHit = Math.max(redHit, 0.5F);
        banner(Component.translatable("boss.amemaru.fx.intense"), 0xFF6B5B);
        play(SoundEvents.TRIDENT_THUNDER, 0.9F, 0.45F);
    }

    private static void banner(Component text, int color) {
        bannerStart = Util.getMillis();
        bannerText = text;
        bannerColor = color;
    }

    /** 追いこんだボスのまわりの、赤い気配。 */
    private static void aura(Minecraft mc, AmemaruBoss boss) {
        RandomSource random = mc.level.random;
        float width = boss.getBbWidth();
        for (int i = 0; i < 2; i++) {
            double x = boss.getX() + (random.nextDouble() - 0.5) * width * 1.5;
            double y = boss.getY() + random.nextDouble() * boss.getBbHeight();
            double z = boss.getZ() + (random.nextDouble() - 0.5) * width * 1.5;
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.85F, 0.12F, 0.1F), 1.8F), x, y, z, 0.0, 0.05, 0.0);
        }
    }

    /** ギミックの構えの残り：画面のふちを赤く。残り3秒から、1秒ごとに時を刻む音。 */
    private static void tickDanger(AmemaruBoss boss) {
        String status = boss.status();
        int colon = status.indexOf(':');
        String key = colon < 0 ? status : status.substring(0, colon);
        if (!effects() || !DANGER.contains(key)) {
            danger = 0.0F;
            lastDangerSecond = -1;
            return;
        }
        int second = boss.statusSeconds();
        danger = second <= 3 ? 1.0F : 0.45F;
        if (second != lastDangerSecond && second > 0 && second <= 3) {
            play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9F, 1.0F);
            play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.7F, 0.6F);
        }
        lastDangerSecond = second;
    }

    /** 自分のHP：大きく打たれた（揺れと赤いふち）・30%以下（心臓の音）。 */
    private static void tickPlayer(Minecraft mc, boolean fighting) {
        ClientPlayerState state = ClientPlayerState.get();
        double max = state.maxHp();
        double hp = state.hp();
        if (!fighting || max <= 0.0) {
            lastHp = -1.0;
            heartIn = 0;
            return;
        }
        if (lastHp >= 0.0 && lastHp - hp >= max * 0.15 && effects()) {
            float lost = (float) Math.min(1.0, (lastHp - hp) / max);
            redHit = Math.min(1.0F, 0.45F + lost * 1.5F);
            FxWorld.shake(mc.player.position(), 6 + (int) (lost * 10.0F), 0.5F + lost * 1.5F);
        }
        lastHp = hp;
        double ratio = hp / max;
        if (hp > 0.0 && ratio <= 0.3 && effects()) {
            if (--heartIn <= 0) {
                heartIn = ratio <= 0.15 ? 15 : 22;
                heart = 1.0F;
                play(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F);
            }
        } else {
            heartIn = 0;
        }
    }

    // ---------------------------------------------------------------- 画面

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        long now = Util.getMillis();
        float pulse = 0.5F + 0.5F * Mth.sin(now / (dangerShown > 0.8F ? 90.0F : 160.0F));
        float edge = Math.max(Math.max(heart * 0.6F, redHit * 0.75F), dangerShown * (0.25F + 0.3F * pulse));
        if (edge > 0.01F) {
            edges(graphics, width, height, edge, 0xC81E1E);
        }
        if (flash > 0.01F) {
            graphics.fill(0, 0, width, height, argb(flash * 0.55F, 0xFFFFFF));
        }
        long intro = now - introStart;
        if (intro >= 0 && intro < INTRO_MILLIS) {
            intro(graphics, mc.font, width, height, intro);
        }
        long banner = now - bannerStart;
        if (banner >= 0 && banner < BANNER_MILLIS) {
            banner(graphics, mc.font, width, height, banner);
        }
    }

    /** 画面のふち（外ほど濃い、何重もの枠）。 */
    private static void edges(GuiGraphics graphics, int width, int height, float alpha, int rgb) {
        int bands = 14;
        int thick = Math.max(2, Math.min(width, height) / 70);
        for (int i = 0; i < bands; i++) {
            float fall = 1.0F - i / (float) bands;
            int color = argb(alpha * fall * fall * 0.85F, rgb);
            int o = i * thick;
            if (o * 2 + thick * 2 >= Math.min(width, height)) {
                break;
            }
            graphics.fill(o, o, width - o, o + thick, color);
            graphics.fill(o, height - o - thick, width - o, height - o, color);
            graphics.fill(o, o + thick, o + thick, height - o - thick, color);
            graphics.fill(width - o - thick, o + thick, width - o, height - o - thick, color);
        }
    }

    /** はじまり：上下の黒い帯と、ボスの名前・階の名前。 */
    private static void intro(GuiGraphics graphics, Font font, int width, int height, long t) {
        float in = Mth.clamp(t / 350.0F, 0.0F, 1.0F);
        float out = Mth.clamp((INTRO_MILLIS - t) / 700.0F, 0.0F, 1.0F);
        float a = Math.min(in, out);
        int bar = Math.round(height * 0.1F * ease(a));
        if (bar > 0) {
            graphics.fill(0, 0, width, bar, argb(0.92F, 0x000000));
            graphics.fill(0, height - bar, width, height, argb(0.92F, 0x000000));
        }
        if (a <= 0.02F) {
            return;
        }
        int y = Math.round(height * 0.34F);
        String place = introPlace.getString();
        graphics.drawString(font, place, (width - font.width(place)) / 2, y - 18, argb(a, 0xE0E0E0), true);
        String name = introName.getString();
        float scale = 2.6F * (1.0F + 0.12F * (1.0F - ease(in)));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, name, -font.width(name) / 2, 0, argb(a, accent), true);
        graphics.pose().popPose();
        int lineW = Math.round((font.width(name) * 2.6F + 60.0F) * ease(in));
        graphics.fill(width / 2 - lineW / 2, y + 27, width / 2 + lineW / 2, y + 28, argb(a * 0.8F, accent));
    }

    /** 段階・追いこんだ の字。 */
    private static void banner(GuiGraphics graphics, Font font, int width, int height, long t) {
        float a = Math.min(Mth.clamp(t / 200.0F, 0.0F, 1.0F), Mth.clamp((BANNER_MILLIS - t) / 600.0F, 0.0F, 1.0F));
        if (a <= 0.02F) {
            return;
        }
        String text = bannerText.getString();
        int y = Math.round(height * 0.25F);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, argb(a, bannerColor), true);
        graphics.pose().popPose();
    }

    private static float ease(float x) {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - c) * (1.0F - c);
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 4, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- 自動テスト・CI

    /** 今流しているボスの曲（なければ ""）。 */
    public static String playingTrack() {
        return track != null ? trackKey : "";
    }

    /** 今の戦いのボス（なければ ""）。 */
    public static String currentBoss() {
        return bossKey;
    }
}
