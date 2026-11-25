package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 相手にかかっている状態の見た目（凍え・凍結・感電・星痕・防御崩し・よろめき、プレイヤーの障壁や構え）。
 *
 * <p>バニラは、プレイヤー以外の効果をほかの人に送らないため、このModで送る。状態が変わるたびに、その相手の
 * 「今の状態の全部」を送る（ないものは消えた）。時間切れはクライアントが残り時間で消す。{@code event} は、凍結した・
 * 砕けた・星痕が弾けたなど、その瞬間だけの演出。</p>
 */
public final class StatusFxS2C {
    /** 状態の種類。番号の順番を変えないこと（足すときは最後に足す）。 */
    public enum Kind {
        CHILL, FROZEN, SHOCK, BRAND, ARMOR_BREAK, STAGGER, BARRIER, WARD, PARRY, RAMPART, UNYIELDING, FORTRESS, SHIELD_BOND,
        // Phase 6 その2：敵
        POISON, MARK, ROOTED, ADMONISHED, BLINDED,
        // Phase 6 その2：プレイヤー
        BLESSING, PROTECTED, KEEN, SWIFT, EMPOWERED, AIMING, LIFE_THREAD, GUARDIAN_BELL, STAR_RING, SHADOW_CLONE, VENOM_COAT,
        BOW_DANCE, EVADE, ENDLESS_FLOW, RENEWAL, HIDDEN;

        private static final Kind[] VALUES = values();

        static Kind byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : CHILL;
        }
    }

    /** その瞬間の出来事。 */
    public enum Event {
        NONE, FROZE, SHATTERED, DETONATED, DISCHARGED, ARMOR_BROKEN, PARRIED, BARRIER_BROKEN,
        // Phase 6 その2
        CLEANSED, GUARDIAN_SAVED, THREAD_BURST, POISON_BURST, MARKED, REVIVED, COUNTERED;

        private static final Event[] VALUES = values();

        static Event byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : NONE;
        }
    }

    /** 状態1つ（level は重なった数、ticks は残り時間）。 */
    public record Entry(Kind kind, int level, int ticks) {
    }

    private static final int MAX_ENTRIES = 32;

    private final int entityId;
    private final Event event;
    private final List<Entry> entries;

    public StatusFxS2C(int entityId, Event event, List<Entry> entries) {
        this.entityId = entityId;
        this.event = event;
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(event.ordinal());
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeByte(entry.kind().ordinal());
            buf.writeByte(Math.max(0, Math.min(127, entry.level())));
            buf.writeVarInt(Math.max(0, entry.ticks()));
        }
    }

    public static StatusFxS2C decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Event event = Event.byIndex(buf.readByte());
        int count = Math.max(0, Math.min(MAX_ENTRIES, buf.readByte()));
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(Kind.byIndex(buf.readByte()), buf.readByte(), buf.readVarInt()));
        }
        return new StatusFxS2C(entityId, event, entries);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStatusFx(this));
    }

    public int entityId() {
        return entityId;
    }

    public Event event() {
        return event;
    }

    public List<Entry> entries() {
        return entries;
    }
}
