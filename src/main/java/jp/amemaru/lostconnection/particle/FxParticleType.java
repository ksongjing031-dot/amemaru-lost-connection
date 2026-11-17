package jp.amemaru.lostconnection.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;

/** 演出用パーティクルの種類（値は {@link FxParticleOptions}）。 */
public class FxParticleType extends ParticleType<FxParticleOptions> {
    private final Codec<FxParticleOptions> codec;

    @SuppressWarnings("deprecation")
    public FxParticleType() {
        super(false, FxParticleOptions.DESERIALIZER);
        this.codec = FxParticleOptions.codec(this);
    }

    @Override
    public Codec<FxParticleOptions> codec() {
        return codec;
    }
}
