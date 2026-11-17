package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.particle.FxParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 演出用パーティクルの登録。見た目は {@code assets/amemaru/particles/<名前>.json} と
 * {@code textures/particle/<名前>.png}（16x16）で決まり、動き方はクライアントの {@code FxParticle} で決まる。
 *
 * <ul>
 *   <li>glow：やわらかな光の粒（光を足し合わせる描き方）</li>
 *   <li>spark：飛び散る火花（速さの向きに伸びる）</li>
 *   <li>shard：氷のかけら（回りながら落ちる）</li>
 *   <li>star：またたく星</li>
 *   <li>rune：ゆっくり昇って消える紋字</li>
 *   <li>snow：舞い落ちる雪</li>
 * </ul>
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,
            AmemaruMod.MOD_ID);

    public static final RegistryObject<FxParticleType> GLOW = PARTICLES.register("glow", FxParticleType::new);
    public static final RegistryObject<FxParticleType> SPARK = PARTICLES.register("spark", FxParticleType::new);
    public static final RegistryObject<FxParticleType> SHARD = PARTICLES.register("shard", FxParticleType::new);
    public static final RegistryObject<FxParticleType> STAR = PARTICLES.register("star", FxParticleType::new);
    public static final RegistryObject<FxParticleType> RUNE = PARTICLES.register("rune", FxParticleType::new);
    public static final RegistryObject<FxParticleType> SNOW = PARTICLES.register("snow", FxParticleType::new);

    private ModParticles() {
    }
}
