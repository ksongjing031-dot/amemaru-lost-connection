package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        for (String name : NAMES) {
            BY_NAME.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AmemaruMod.id(name))));
        }
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
