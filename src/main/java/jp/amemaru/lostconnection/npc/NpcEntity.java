package jp.amemaru.lostconnection.npc;

import jp.amemaru.lostconnection.story.DialogueManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * 街のNPC（Phase 0-B 4.4「倒せない、押されない、その場から動かない。定義IDから、名前・見た目・会話・役割をJSONで読む」）。
 *
 * <ul>
 *   <li>右クリックで話す（{@link DialogueManager}）。話している間は、話している人の方を向く</li>
 *   <li>ダメージを受けない（{@code /kill} だけは効く）。押されない。重力がなく、立つ所から動かない</li>
 *   <li>見た目は、凹凸のある立体のモデル（{@link NpcDef#model()}。GeckoLib。11月22日から）か、プレイヤーと同じ形のスキン
 *       （{@link NpcDef#skin()}）。表情（{@code expression}）で顔の絵を差し替える。モデルは息・まばたき・髪とマントの揺れ・歩く動き</li>
 *   <li>オープニングなど、本人の画面だけの演出では、クライアントだけにいる「役者」として同じクラスを使う</li>
 * </ul>
 */
public class NpcEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_NPC = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_EXPRESSION = SynchedEntityData.defineId(NpcEntity.class,
            EntityDataSerializers.STRING);

    /** 話している人（サーバーだけ）。 */
    @Nullable
    private ServerPlayer talkingTo;
    private int talkTicks;
    @Nullable
    private Vec3 home;
    private float homeYaw;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** モデルの動き（モデルの名前ごと。最初に使うときに作る） */
    @Nullable
    private String animName;
    @Nullable
    private RawAnimation animIdle;
    @Nullable
    private RawAnimation animWalk;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_NPC, "");
        entityData.define(DATA_EXPRESSION, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 7.0F, 0.6F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    // ---------------------------------------------------------------- 定義

    /** NPCの定義ID（{@code amemaru:lilia} など。なければ null）。 */
    @Nullable
    public ResourceLocation npcId() {
        String text = entityData.get(DATA_NPC);
        return text.isEmpty() ? null : ResourceLocation.tryParse(text);
    }

    public void setNpcId(ResourceLocation id) {
        entityData.set(DATA_NPC, id.toString());
        NpcDef def = NpcDefs.get(id);
        if (def != null) {
            setCustomName(Component.literal(def.name()));
        }
    }

    /** サーバーの定義（クライアントでは null）。 */
    @Nullable
    public NpcDef def() {
        return NpcDefs.get(npcId());
    }

    public String expression() {
        return entityData.get(DATA_EXPRESSION);
    }

    public void setExpression(String expression) {
        entityData.set(DATA_EXPRESSION, expression == null ? "" : expression);
    }

    public void setHome(Vec3 pos, float yaw) {
        home = pos;
        homeYaw = yaw;
    }

    // ---------------------------------------------------------------- 話す

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // しゃがんで品を持って話しかけると、贈り物（Phase 6 その3：親密度）
            if (serverPlayer.isShiftKeyDown() && !serverPlayer.getMainHandItem().isEmpty()
                    && jp.amemaru.lostconnection.guild.BondService.gift(serverPlayer, this)) {
                return InteractionResult.SUCCESS;
            }
            // 11月25日：魚の名前を忘れたポッカ爺に、魚を見せる（手に持って話しかける）
            if (NpcMemoryService.showFish(serverPlayer, this)) {
                return InteractionResult.SUCCESS;
            }
            DialogueManager.talk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /** 話し始めた（話している間は、その人の方を向く）。 */
    public void startTalking(ServerPlayer player) {
        talkingTo = player;
        talkTicks = 20 * 60;
    }

    public void stopTalking(ServerPlayer player) {
        if (talkingTo == player) {
            talkingTo = null;
            talkTicks = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (talkingTo != null) {
                if (--talkTicks <= 0 || talkingTo.isRemoved() || talkingTo.distanceToSqr(this) > 16 * 16) {
                    talkingTo = null;
                } else {
                    getLookControl().setLookAt(talkingTo, 30.0F, 30.0F);
                }
            }
            // 立つ所から動かない（何かに押し出されても戻す）
            if (home != null && position().distanceToSqr(home) > 0.04) {
                moveTo(home.x, home.y, home.z, getYRot(), getXRot());
            }
            if (talkingTo == null && home != null && tickCount % 200 == 0) {
                setYBodyRot(homeYaw);
            }
        }
    }

    // ---------------------------------------------------------------- 守り

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // /kill だけは効く（建て直すときに消すため）
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ---------------------------------------------------------------- 保存

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag own = new CompoundTag();
        own.putString("npc", entityData.get(DATA_NPC));
        if (home != null) {
            own.putDouble("hx", home.x);
            own.putDouble("hy", home.y);
            own.putDouble("hz", home.z);
            own.putFloat("hyaw", homeYaw);
        }
        tag.put("Amemaru", own);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag own = tag.getCompound("Amemaru");
        entityData.set(DATA_NPC, own.getString("npc"));
        if (own.contains("hx")) {
            home = new Vec3(own.getDouble("hx"), own.getDouble("hy"), own.getDouble("hz"));
            homeYaw = own.getFloat("hyaw");
        }
    }

    // ---------------------------------------------------------------- 立体のモデルの動き（GeckoLib。クライアントで使う）

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainAnimation));
    }

    private PlayState mainAnimation(AnimationState<NpcEntity> state) {
        NpcDef def = NpcDefs.client(npcId());
        if (def == null || def.model() == null) {
            return PlayState.STOP;
        }
        String path = def.model().getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (!name.equals(animName)) {
            animName = name;
            animIdle = RawAnimation.begin().thenLoop("animation.npc_" + name + ".idle");
            animWalk = RawAnimation.begin().thenLoop("animation.npc_" + name + ".walk");
        }
        return state.setAndContinue(state.isMoving() ? animWalk : animIdle);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
