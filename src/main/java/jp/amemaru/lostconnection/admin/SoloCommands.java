package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.SoloTest;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * ひとり確認モードのコマンド（11月26日のご指示「ボス戦までのギミックをソロでテストできるようにして動作確認できるように、おなじく
 * ボス戦でのギミックの確認も」）【補完】。OP（権限レベル2以上）だけ。
 *
 * <ul>
 *   <li>{@code /amemaru admin solo on|off}：ひとり確認モード（{@link SoloTest}）を入れる・切る。{@code /amemaru admin solo} で今の様子</li>
 *   <li>{@code /amemaru admin solo reset <階>}：その階の仕掛けを朝の形に戻し、自分の石柱・名のある魔物・合言葉の印・宝箱も戻し、
 *       ボスを出し直す（もう一度はじめから、ひとりで確かめる）</li>
 *   <li>{@code /amemaru admin solo ready <階>}：その階のボスの封印の条件（石柱・迷いの森・水門・壁画・氷像・光の迷路・合言葉・鐘・
 *       名のある魔物）を、自分の分まで満たし、ボスの部屋の門の前へ移る（ボス戦のギミックだけを確かめるとき）</li>
 * </ul>
 * 中では、すでにある管理者のコマンド（{@code floor woods}・{@code floor floodgate} など）をそのまま使う。手順は {@code docs/solo_check.md}。
 */
public final class SoloCommands {
    private static final SuggestionProvider<CommandSourceStack> FLOORS = (context, builder) -> {
        for (FloorDef def : FloorDefs.all()) {
            builder.suggest(def.number());
        }
        return builder.buildFuture();
    };

    /** 封印の条件の並び（言語ファイルの名前 {@code commands.amemaru.solo.part.<条件>} もこの順に出す）。 */
    private static final List<String> TOKENS = List.of(FloorFeatures.SEAL_PILLARS, FloorFeatures.SEAL_LOST_WOODS,
            FloorFeatures.SEAL_FLOODGATES, FloorFeatures.SEAL_MURALS, FloorFeatures.SEAL_STATUES, FloorFeatures.SEAL_LIGHTS,
            FloorFeatures.SEAL_PASSPHRASE, FloorFeatures.SEAL_BELLS, FloorFeatures.SEAL_LINEAGE, FloorFeatures.SEAL_GUARDIANS);

    private SoloCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> soloCommand() {
        return Commands.literal("solo")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .then(Commands.literal("ready")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> ready(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> reset(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(SoloTest.on() ? "commands.amemaru.solo.status_on" : "commands.amemaru.solo.status_off")
                .withStyle(SoloTest.on() ? ChatFormatting.GOLD : ChatFormatting.GRAY), false);
        return SoloTest.on() ? 1 : 0;
    }

    private static int set(CommandSourceStack source, boolean value) {
        SoloTest.set(value);
        AdminCommands.log(source, "solo " + (value ? "on" : "off"));
        SoloTest.tellAll(source.getServer(), value);
        if (value) {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.solo.on_detail").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    /** その階のボスの封印の条件を、自分の分まで満たし、ボスの部屋の門の前へ。 */
    private static int ready(CommandSourceStack source, int floor) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null || def.bossRoom() == null) {
            source.sendFailure(Component.translatable("commands.amemaru.floor.no_boss", floor));
            return 0;
        }
        FloorFeatures features = def.features();
        List<String> done = new ArrayList<>();
        // 石柱はいつも（封印の条件が石柱でない階でも、次の階を開くのに数える）
        run(source, "amemaru admin pillars complete " + floor + " @s", done, FloorFeatures.SEAL_PILLARS);
        if (features.requires(FloorFeatures.SEAL_LOST_WOODS)) {
            run(source, "amemaru admin floor woods " + floor + " tie all", done, FloorFeatures.SEAL_LOST_WOODS);
        }
        if (features.requires(FloorFeatures.SEAL_FLOODGATES)) {
            run(source, "amemaru admin floor floodgate " + floor + " open all", done, FloorFeatures.SEAL_FLOODGATES);
        }
        if (features.requires(FloorFeatures.SEAL_MURALS)) {
            run(source, "amemaru admin floor murals " + floor + " solve", done, FloorFeatures.SEAL_MURALS);
        }
        if (features.requires(FloorFeatures.SEAL_STATUES)) {
            run(source, "amemaru admin floor statues " + floor + " thaw", done, FloorFeatures.SEAL_STATUES);
        }
        if (features.requires(FloorFeatures.SEAL_LIGHTS)) {
            run(source, "amemaru admin floor lights " + floor + " solve", done, FloorFeatures.SEAL_LIGHTS);
        }
        if (features.requires(FloorFeatures.SEAL_PASSPHRASE)) {
            run(source, "amemaru admin floor passphrase " + floor + " done @s", done, FloorFeatures.SEAL_PASSPHRASE);
        }
        if (features.requires(FloorFeatures.SEAL_BELLS)) {
            run(source, "amemaru admin floor bells " + floor + " solve", done, FloorFeatures.SEAL_BELLS);
        }
        if (features.requires(FloorFeatures.SEAL_LINEAGE)) {
            run(source, "amemaru admin floor lineage " + floor + " solve", done, FloorFeatures.SEAL_LINEAGE);
        }
        if (features.requires(FloorFeatures.SEAL_GUARDIANS)) {
            run(source, "amemaru admin floor guardians " + floor + " done @s", done, FloorFeatures.SEAL_GUARDIANS);
        }
        run(source, "amemaru admin floor tp " + floor + " boss @s", new ArrayList<>(), "");
        AdminCommands.log(source, "solo ready " + floor + " " + String.join("+", done));
        MutableComponent parts = parts(done);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.solo.ready", floor, parts).withStyle(ChatFormatting.GOLD), true);
        if (!SoloTest.on()) {
            player.sendSystemMessage(Component.translatable("commands.amemaru.solo.ready_hint").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /** その階の仕掛けを朝の形に戻し、自分の印も戻し、ボスを出し直す。 */
    private static int reset(CommandSourceStack source, int floor) throws CommandSyntaxException {
        source.getPlayerOrException();
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null) {
            source.sendFailure(Component.translatable("commands.amemaru.map.no_floor", floor));
            return 0;
        }
        FloorFeatures features = def.features();
        List<String> done = new ArrayList<>();
        run(source, "amemaru admin pillars reset " + floor + " @s", done, FloorFeatures.SEAL_PILLARS);
        if (!features.lostWoods().isEmpty()) {
            run(source, "amemaru admin floor woods " + floor + " reset", done, FloorFeatures.SEAL_LOST_WOODS);
        }
        if (!features.floodgates().isEmpty()) {
            run(source, "amemaru admin floor floodgate " + floor + " reset", done, FloorFeatures.SEAL_FLOODGATES);
        }
        if (features.requires(FloorFeatures.SEAL_MURALS)) {
            run(source, "amemaru admin floor murals " + floor + " reset", done, FloorFeatures.SEAL_MURALS);
        }
        if (features.requires(FloorFeatures.SEAL_STATUES)) {
            run(source, "amemaru admin floor statues " + floor + " reset", done, FloorFeatures.SEAL_STATUES);
        }
        if (features.requires(FloorFeatures.SEAL_LIGHTS)) {
            run(source, "amemaru admin floor lights " + floor + " reset", done, FloorFeatures.SEAL_LIGHTS);
        }
        if (features.requires(FloorFeatures.SEAL_PASSPHRASE)) {
            run(source, "amemaru admin floor passphrase " + floor + " reset @s", done, FloorFeatures.SEAL_PASSPHRASE);
        }
        if (!features.bells().isEmpty()) {
            run(source, "amemaru admin floor bells " + floor + " reset", done, FloorFeatures.SEAL_BELLS);
        }
        if (features.lineage() != null) {
            run(source, "amemaru admin floor lineage " + floor + " reset", done, FloorFeatures.SEAL_LINEAGE);
        }
        run(source, "amemaru admin floor guardians " + floor + " reset @s", done, FloorFeatures.SEAL_GUARDIANS);
        run(source, "amemaru admin floor alert " + floor + " clear", new ArrayList<>(), "");
        run(source, "amemaru admin chests reset " + floor + " @s", new ArrayList<>(), "");
        // 8階：割れた薄氷を、すぐに凍らせ直す
        ServerLevel tower = FloorDefs.towerLevel(source.getServer());
        if (features.lake() != null && tower != null) {
            jp.amemaru.lostconnection.tower.lake.ThinIceService.refreeze(tower, true);
        }
        if (def.bossRoom() != null) {
            run(source, "amemaru admin boss respawn " + floor, new ArrayList<>(), "");
        }
        AdminCommands.log(source, "solo reset " + floor + " " + String.join("+", done));
        MutableComponent parts = parts(done);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.solo.reset", floor, parts).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /** 管理者のコマンドを、出力を出さずに打つ。うまくいったら、条件の名前を {@code done} に足す。 */
    private static void run(CommandSourceStack source, String command, List<String> done, String token) {
        int result = source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), command);
        if (result > 0 && !token.isEmpty() && !done.contains(token)) {
            done.add(token);
        }
    }

    private static MutableComponent parts(List<String> done) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String token : TOKENS) {
            if (!done.contains(token)) {
                continue;
            }
            if (!first) {
                out.append(Component.translatable("commands.amemaru.solo.separator"));
            }
            out.append(Component.translatable("commands.amemaru.solo.part." + token));
            first = false;
        }
        return out;
    }
}
