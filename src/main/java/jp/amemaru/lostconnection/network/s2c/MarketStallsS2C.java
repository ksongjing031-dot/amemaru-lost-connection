package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.economy.MarketService;
import jp.amemaru.lostconnection.economy.MarketStalls;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * フリーマーケットの露店の様子（Phase 6）：区画ごとの借り手・店名・最後の日・出品の数と、浮かべて見せる品と場所（世界の座標）。
 * 市場監督の小屋の範囲も（右クリックの所）。全員に送る（露店の名前と品を、世界の中に描く）。街を建てていなければ空。
 */
public record MarketStallsS2C(List<View> stalls, @Nullable BlockPos boothFrom, @Nullable BlockPos boothTo, List<BlockPos> boothExcept,
                              int today, long rentPrice, int rentDays, boolean expanded) {
    /** 1つの区画。owner は借りていなければ null */
    public record View(int num, boolean active, boolean more, String title, @Nullable UUID owner, String ownerName, String name,
                       int lastDay, int listings, Vec3 namePos, List<Vec3> displayPos, List<ItemStack> displayItems, BlockPos from,
                       BlockPos to) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= from.getX() && pos.getX() <= to.getX() && pos.getY() >= from.getY() && pos.getY() <= to.getY()
                    && pos.getZ() >= from.getZ() && pos.getZ() <= to.getZ();
        }
    }

    public static MarketStallsS2C current(MinecraftServer server) {
        Optional<BlockPos> origin = MarketService.origin(server);
        int today = MarketService.today(server);
        long rent = ServerConfig.MARKET_STALL_RENT.get();
        int days = ServerConfig.MARKET_STALL_DAYS.get();
        MarketData data = MarketData.get(server);
        if (origin.isEmpty()) {
            return new MarketStallsS2C(List.of(), null, null, List.of(), today, rent, days, data.expanded());
        }
        BlockPos o = origin.get();
        Vec3 ov = Vec3.atLowerCornerOf(o);
        List<View> views = new ArrayList<>();
        for (MarketStalls.Stall stall : MarketStalls.all()) {
            MarketData.StallRent r = data.stall(stall.num());
            boolean active = MarketService.stallActive(server, stall);
            List<ItemStack> items = new ArrayList<>();
            int listings = 0;
            if (r != null) {
                List<MarketData.Listing> own = data.listingsOf(r.owner);
                listings = own.size();
                for (int i = own.size() - 1; i >= 0 && items.size() < stall.displays().size(); i--) {
                    ItemStack one = own.get(i).item.copy();
                    one.setCount(1);
                    items.add(one);
                }
            }
            List<Vec3> spots = new ArrayList<>();
            for (Vec3 spot : stall.displays()) {
                spots.add(spot.add(ov));
            }
            String shown = r == null ? "" : (r.hidden ? "" : r.name);
            views.add(new View(stall.num(), active, stall.more(), stall.title(), r == null ? null : r.owner, r == null ? "" : r.ownerName,
                    shown, r == null ? 0 : r.lastDay, listings, stall.name().add(ov), spots, items,
                    stall.box().from().offset(o), stall.box().to().offset(o)));
        }
        MarketStalls.Box booth = MarketStalls.booth();
        List<BlockPos> except = new ArrayList<>();
        for (MarketStalls.Layout layout : MarketStalls.loader().all().values()) {
            for (BlockPos pos : layout.boothExcept()) {
                except.add(pos.offset(o));
            }
        }
        return new MarketStallsS2C(views, booth == null ? null : booth.from().offset(o), booth == null ? null : booth.to().offset(o), except,
                today, rent, days, data.expanded());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stalls.size());
        for (View v : stalls) {
            buf.writeVarInt(v.num());
            buf.writeBoolean(v.active());
            buf.writeBoolean(v.more());
            buf.writeUtf(v.title(), 64);
            buf.writeBoolean(v.owner() != null);
            if (v.owner() != null) {
                buf.writeUUID(v.owner());
            }
            buf.writeUtf(v.ownerName(), 64);
            buf.writeUtf(v.name(), 128);
            buf.writeVarInt(v.lastDay());
            buf.writeVarInt(v.listings());
            writeVec(buf, v.namePos());
            buf.writeVarInt(v.displayPos().size());
            for (Vec3 spot : v.displayPos()) {
                writeVec(buf, spot);
            }
            buf.writeVarInt(v.displayItems().size());
            for (ItemStack stack : v.displayItems()) {
                buf.writeItem(stack);
            }
            buf.writeBlockPos(v.from());
            buf.writeBlockPos(v.to());
        }
        buf.writeBoolean(boothFrom != null && boothTo != null);
        if (boothFrom != null && boothTo != null) {
            buf.writeBlockPos(boothFrom);
            buf.writeBlockPos(boothTo);
        }
        buf.writeVarInt(boothExcept.size());
        for (BlockPos pos : boothExcept) {
            buf.writeBlockPos(pos);
        }
        buf.writeVarInt(today);
        buf.writeVarLong(rentPrice);
        buf.writeVarInt(rentDays);
        buf.writeBoolean(expanded);
    }

    public static MarketStallsS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<View> views = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int num = buf.readVarInt();
            boolean active = buf.readBoolean();
            boolean more = buf.readBoolean();
            String title = buf.readUtf(64);
            UUID owner = buf.readBoolean() ? buf.readUUID() : null;
            String ownerName = buf.readUtf(64);
            String name = buf.readUtf(128);
            int lastDay = buf.readVarInt();
            int listings = buf.readVarInt();
            Vec3 namePos = readVec(buf);
            int spots = Math.min(buf.readVarInt(), 8);
            List<Vec3> displayPos = new ArrayList<>();
            for (int j = 0; j < spots; j++) {
                displayPos.add(readVec(buf));
            }
            int items = Math.min(buf.readVarInt(), 8);
            List<ItemStack> displayItems = new ArrayList<>();
            for (int j = 0; j < items; j++) {
                displayItems.add(buf.readItem());
            }
            views.add(new View(num, active, more, title, owner, ownerName, name, lastDay, listings, namePos, displayPos, displayItems,
                    buf.readBlockPos(), buf.readBlockPos()));
        }
        BlockPos from = null;
        BlockPos to = null;
        if (buf.readBoolean()) {
            from = buf.readBlockPos();
            to = buf.readBlockPos();
        }
        int m = Math.min(buf.readVarInt(), 16);
        List<BlockPos> except = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            except.add(buf.readBlockPos());
        }
        return new MarketStallsS2C(views, from, to, except, buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readBoolean());
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    /** 小屋の範囲（書見台・呼び鈴を除く）か。 */
    public boolean inBooth(BlockPos pos) {
        if (boothFrom == null || boothTo == null || boothExcept.contains(pos)) {
            return false;
        }
        return pos.getX() >= boothFrom.getX() && pos.getX() <= boothTo.getX() && pos.getY() >= boothFrom.getY() && pos.getY() <= boothTo.getY()
                && pos.getZ() >= boothFrom.getZ() && pos.getZ() <= boothTo.getZ();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMarketStalls(this));
    }
}
