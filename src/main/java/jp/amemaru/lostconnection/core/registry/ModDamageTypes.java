package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * このModのダメージの種類（{@code data/amemaru/damage_type/}）。死亡したときのメッセージにも使う。
 */
public final class ModDamageTypes {
    /** スキルのダメージ。 */
    public static final ResourceKey<DamageType> SKILL = ResourceKey.create(Registries.DAMAGE_TYPE, AmemaruMod.id("skill"));
    /** 盾結びで、仲間の代わりに受けたダメージ。 */
    public static final ResourceKey<DamageType> BOND_SHARE = ResourceKey.create(Registries.DAMAGE_TYPE, AmemaruMod.id("bond_share"));

    private ModDamageTypes() {
    }

    /**
     * スキルのダメージの出どころ。
     *
     * @param direct 直接当たったもの（弾ならその弾、そうでなければ使った人）
     * @param owner  使った人
     */
    public static DamageSource skill(Level level, @Nullable Entity direct, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SKILL), direct, owner);
    }

    /** 盾結びで肩代わりしたダメージの出どころ。 */
    public static DamageSource bondShare(Level level, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BOND_SHARE), attacker, attacker);
    }
}
