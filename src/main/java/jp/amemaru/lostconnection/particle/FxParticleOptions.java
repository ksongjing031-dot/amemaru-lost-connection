package jp.amemaru.lostconnection.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * このModの演出用パーティクルの値：色（0〜1）・大きさ・続く時間（tick）。
 *
 * <p>演出はクライアントで作るため、サーバーから送ることはほとんどないが、バニラの決まり（/particle コマンド・通信）にも合わせてある。
 * 例：{@code /particle amemaru:glow 1 0.8 0.3 0.5 20 ~ ~1 ~}</p>
 *
 * @param type     パーティクルの種類（{@link jp.amemaru.lostconnection.core.registry.ModParticles}）
 * @param red      赤（0〜1）
 * @param green    緑（0〜1）
 * @param blue     青（0〜1）
 * @param size     大きさ（ブロック）
 * @param lifetime 続く時間（tick）
 */
public record FxParticleOptions(ParticleType<FxParticleOptions> type, float red, float green, float blue, float size, int lifetime)
        implements ParticleOptions {

    @SuppressWarnings("deprecation")
    public static final Deserializer<FxParticleOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public FxParticleOptions fromCommand(ParticleType<FxParticleOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float red = reader.readFloat();
            reader.expect(' ');
            float green = reader.readFloat();
            reader.expect(' ');
            float blue = reader.readFloat();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            int lifetime = reader.readInt();
            return new FxParticleOptions(type, red, green, blue, size, lifetime);
        }

        @Override
        public FxParticleOptions fromNetwork(ParticleType<FxParticleOptions> type, FriendlyByteBuf buf) {
            return new FxParticleOptions(type, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
        }
    };

    public static Codec<FxParticleOptions> codec(ParticleType<FxParticleOptions> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.fieldOf("r").forGetter(FxParticleOptions::red),
                        Codec.FLOAT.fieldOf("g").forGetter(FxParticleOptions::green),
                        Codec.FLOAT.fieldOf("b").forGetter(FxParticleOptions::blue),
                        Codec.FLOAT.fieldOf("size").forGetter(FxParticleOptions::size),
                        Codec.INT.fieldOf("lifetime").forGetter(FxParticleOptions::lifetime))
                .apply(instance, (red, green, blue, size, lifetime) -> new FxParticleOptions(type, red, green, blue, size, lifetime)));
    }

    /** 色を 0xRRGGBB で指定して作る。 */
    public static FxParticleOptions of(ParticleType<FxParticleOptions> type, int rgb, float size, int lifetime) {
        return new FxParticleOptions(type, ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, size,
                lifetime);
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(red);
        buf.writeFloat(green);
        buf.writeFloat(blue);
        buf.writeFloat(size);
        buf.writeVarInt(lifetime);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f %d", BuiltInRegistries.PARTICLE_TYPE.getKey(type), red, green, blue,
                size, lifetime);
    }
}
