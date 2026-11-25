package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.casino.CasinoData;
import jp.amemaru.lostconnection.casino.CasinoService;
import jp.amemaru.lostconnection.fishing.FishingData;
import jp.amemaru.lostconnection.fishing.FishingRules;
import jp.amemaru.lostconnection.fishing.FishingTournament;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 管理者のコマンド：催し（Phase 0-B 10章「催し｜event start &lt;催し&gt;・event stop &lt;催し&gt;｜釣り大会・襲撃・祭り・品評会などを
 * 始める・終える」）。日程を決めずに、管理者が好きなときに始める（Phase 0-A 26章の回答4）。
 *
 * <ul>
 *   <li>{@code event list}：催しの一覧と、今やっているか</li>
 *   <li>{@code event start <催し> [分]}：始める（分を書かなければ、その催しの決まりの長さ）</li>
 *   <li>{@code event stop <催し>}：終える（釣り大会は、順位と賞品を出して終える）</li>
 * </ul>
 *
 * <p>催しは Phase 7 その2の「釣り大会」と、その3の「カジノナイト」。襲撃・祭り・品評会（Phase 8）も {@link #register} で足す。
 * 催しごとの細かい操作（釣り大会を何も渡さずに取りやめる、など）は、それぞれのコマンド（{@code fishing tournament …}）で。
 */
public final class EventCommands {
    /** 催し。名前は lang の {@code event.amemaru.<名前>}。 */
    public interface AdminEvent {
        boolean running(MinecraftServer server);

        /** 始める（minutes が 0 なら決まりの長さ）。もうやっている・始められないときは false。 */
        boolean start(MinecraftServer server, int minutes);

        /** 終える。やっていなければ false。 */
        boolean stop(MinecraftServer server);
    }

    private static final Map<String, AdminEvent> EVENTS = new LinkedHashMap<>();

    static {
        // 釣り大会（Phase 0-A 15章「夜の催し」：3日目ほか。制限時間内の大物・珍しい魚を競う）
        register("fishing_tournament", new AdminEvent() {
            /** 始まるまでの合図の間も「開催中」（取りやめられるように）。 */
            @Override
            public boolean running(MinecraftServer server) {
                return FishingData.get(server).tournamentOn();
            }

            @Override
            public boolean start(MinecraftServer server, int minutes) {
                FishingRules rules = FishingRules.current();
                return FishingTournament.start(server, minutes > 0 ? minutes : rules.tournamentMinutes(), rules.tournamentCountdown());
            }

            @Override
            public boolean stop(MinecraftServer server) {
                if (!running(server)) {
                    return false;
                }
                FishingTournament.finish(server, true);
                return true;
            }
        });
        // カジノナイト（Phase 0-A 15章「夜の催し：不定期（運営が開催）。4人以上の卓に配当ボーナス。生放送に合わせて」。Phase 7 その3）
        register("casino_night", new AdminEvent() {
            @Override
            public boolean running(MinecraftServer server) {
                return CasinoData.get(server).night();
            }

            @Override
            public boolean start(MinecraftServer server, int minutes) {
                return CasinoService.startNight(server, minutes);
            }

            @Override
            public boolean stop(MinecraftServer server) {
                return CasinoService.endNight(server);
            }
        });
    }

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(EVENTS.keySet(), builder);

    private EventCommands() {
    }

    /** 催しを足す（同じ名前なら置きかえる）。 */
    public static void register(String id, AdminEvent event) {
        EVENTS.put(id, event);
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(EVENTS.keySet());
    }

    /** 催しを始める（コマンドと自動テストから）。名前がない・もうやっているときは false。 */
    public static boolean start(MinecraftServer server, String id, int minutes) {
        AdminEvent event = EVENTS.get(id);
        return event != null && !event.running(server) && event.start(server, minutes);
    }

    /** 催しを終える。名前がない・やっていないときは false。 */
    public static boolean stop(MinecraftServer server, String id) {
        AdminEvent event = EVENTS.get(id);
        return event != null && event.running(server) && event.stop(server);
    }

    public static boolean running(MinecraftServer server, String id) {
        AdminEvent event = EVENTS.get(id);
        return event != null && event.running(server);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> eventCommand() {
        return Commands.literal("event")
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(IDS)
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "event"), 0))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "event"),
                                                IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(IDS)
                                .executes(c -> stop(c.getSource(), StringArgumentType.getString(c, "event")))));
    }

    private static Component name(String id) {
        return Component.translatable("event.amemaru." + id);
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable("command.amemaru.event.list"), false);
        for (Map.Entry<String, AdminEvent> e : EVENTS.entrySet()) {
            Component state = Component.translatable(e.getValue().running(server)
                    ? "command.amemaru.event.state_on" : "command.amemaru.event.state_off");
            source.sendSuccess(() -> Component.translatable("command.amemaru.event.line", e.getKey(), name(e.getKey()), state), false);
        }
        return EVENTS.size();
    }

    private static int start(CommandSourceStack source, String id, int minutes) {
        MinecraftServer server = source.getServer();
        if (!EVENTS.containsKey(id)) {
            source.sendFailure(Component.translatable("command.amemaru.event.unknown", id));
            return 0;
        }
        if (running(server, id)) {
            source.sendFailure(Component.translatable("command.amemaru.event.already", name(id)));
            return 0;
        }
        if (!start(server, id, minutes)) {
            source.sendFailure(Component.translatable("command.amemaru.event.failed", name(id)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.event.started", name(id)), true);
        return 1;
    }

    private static int stop(CommandSourceStack source, String id) {
        MinecraftServer server = source.getServer();
        if (!EVENTS.containsKey(id)) {
            source.sendFailure(Component.translatable("command.amemaru.event.unknown", id));
            return 0;
        }
        if (!stop(server, id)) {
            source.sendFailure(Component.translatable("command.amemaru.event.not_running", name(id)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.event.stopped", name(id)), true);
        return 1;
    }
}
