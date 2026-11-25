package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 効果音の登録。中身は {@code assets/amemaru/sounds.json} で決める。
 *
 * <p>Phase 2では、仮の音としてバニラの音を鳴らす（sounds.json でバニラの音を指している）。専用の音（.ogg）を
 * {@code assets/amemaru/sounds/} に置き、sounds.json の向き先を変えれば、コードを変えずに差し替えられる。</p>
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AmemaruMod.MOD_ID);

    /** 登録する音の名前（sounds.json のキーと同じ）。 */
    public static final List<String> NAMES = List.of(
            "skill.knight.call",
            "skill.knight.shield_bond",
            "skill.knight.rampart",
            "skill.knight.charge_slash",
            "skill.knight.whirlwind",
            "skill.knight.unyielding_vow",
            "skill.knight.unfallen_castle",
            "skill.wizard.lamp_bolt",
            "skill.wizard.lamp_bolt_hit",
            "skill.wizard.frost_circle",
            "skill.wizard.meteor_cast",
            "skill.wizard.meteor_impact",
            "skill.wizard.chain_lightning",
            "skill.wizard.mana_thread",
            "skill.wizard.blink",
            "skill.wizard.starfall_cast",
            "skill.wizard.starfall_impact",
            "skill.knight.binding_chain",
            "skill.knight.parry",
            "skill.knight.oath_circle",
            "skill.knight.heavy_slash",
            "skill.knight.leap_slash",
            "skill.knight.leap_impact",
            "skill.knight.oath_release",
            "skill.wizard.lamp_barrage",
            "skill.wizard.light_veil",
            "skill.wizard.star_burst",
            "skill.wizard.icicle",
            "skill.wizard.frost_wave",
            "skill.wizard.thunder_call",
            "skill.wizard.thunderbolt",
            "skill.knight.heaven_cleave_cast",
            "skill.knight.heaven_cleave",
            "skill.wizard.ice_garden_cast",
            "skill.wizard.ice_garden_impact",
            "skill.charge",
            "skill.charge_full",
            "combat.link",
            "combat.link3",
            "combat.support",
            "skill.fail",
            "skill.learn",
            "job.oath",
            "job.promote",
            "item.reset");

    private static final Map<String, RegistryObject<SoundEvent>> BY_NAME = new LinkedHashMap<>();

    static {
        // 上の一覧と、同梱の sounds.json のキーをすべて登録する（Phase 6 その2：職業が増えて音が多くなったため、
        // sounds.json に足すだけで登録されるようにした）
        Set<String> names = new LinkedHashSet<>(NAMES);
        names.addAll(bundledNames());
        for (String name : names) {
            BY_NAME.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AmemaruMod.id(name))));
        }
    }

    /** Modに同梱した sounds.json のキー（読めなければ空）。 */
    private static List<String> bundledNames() {
        List<String> out = new ArrayList<>();
        try (InputStream stream = ModSounds.class.getResourceAsStream("/assets/" + AmemaruMod.MOD_ID + "/sounds.json")) {
            if (stream == null) {
                AmemaruMod.LOGGER.warn("[amemaru] sounds.json was not found in the mod; only the built-in sound list is registered");
                return out;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : json.keySet()) {
                if (key.matches("[a-z0-9_.]+")) {
                    out.add(key);
                }
            }
        } catch (IOException | RuntimeException e) {
            AmemaruMod.LOGGER.warn("[amemaru] Could not read sounds.json: {}", e.toString());
        }
        return out;
    }

    private ModSounds() {
    }

    /** 名前（{@code skill.fail} など）から音を探す。 */
    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> sound = BY_NAME.get(name);
        if (sound == null) {
            throw new IllegalArgumentException("Unknown amemaru sound: " + name);
        }
        return sound.get();
    }

    /** IDから音を探す（なければ null）。 */
    @Nullable
    public static SoundEvent byId(@Nullable ResourceLocation id) {
        return id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
    }
}
