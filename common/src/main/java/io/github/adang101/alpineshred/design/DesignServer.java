package io.github.adang101.alpineshred.design;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server side of a save (design doc section 22, "Server validation on save"): the seven rules
 * in order, the cost charged in the same step as the write, and the events.
 */
public final class DesignServer {
    /** Game time of each player's last save, for rule 6. */
    private static final Map<UUID, Long> LAST_SAVE = new ConcurrentHashMap<>();

    private DesignServer() {
    }

    /** {@code c2s/design_save}'s payload. {@code reset} removes the design (free). */
    public record SaveRequest(int containerId, boolean reset, DesignKind kind, byte[] top, byte[] base, int edge,
                              Design.Pair pair, String name) {
    }

    public static DesignStatus handle(ServerPlayer player, SaveRequest req) {
        // Rule 5: the feature and the player's permission.
        if (!AlpineConfig.DESIGN_ENABLED.on()) return DesignStatus.DISABLED;
        if (!player.hasPermissions(AlpineConfig.DESIGN_MIN_PERMISSION_LEVEL.value())) return DesignStatus.FORBIDDEN;
        // Rule 1: the bench is open, the item is still in its slot, and it is the kind the design is for.
        if (!(player.containerMenu instanceof PaintBenchMenu menu) || menu.containerId != req.containerId() || !menu.stillValid(player)) {
            return DesignStatus.INVALID;
        }
        ItemStack gear = menu.gear();
        DesignKind kind = DesignNbt.kindOf(gear);
        if (gear.isEmpty() || kind == null || kind != req.kind()) return DesignStatus.INVALID;
        // Rule 6: one save per cooldown.
        long now = player.serverLevel().getGameTime();
        Long last = LAST_SAVE.get(player.getUUID());
        if (last != null && now - last < AlpineConfig.DESIGN_SAVE_COOLDOWN_TICKS.value() && now >= last) return DesignStatus.RATE_LIMITED;

        if (req.reset()) {
            DesignNbt.clear(gear);
            LAST_SAVE.put(player.getUUID(), now);
            menu.broadcastChanges();
            return DesignStatus.OK;
        }
        // Rules 2 to 5: sizes, inflation, alpha, outline, edge, name; the server writes the author.
        Design design;
        try {
            design = DesignCodec.decode(kind, req.top(), req.base(), req.edge(), req.pair(), req.name(),
                    player.getGameProfile().getName(), player.getUUID(), AlpineConfig.DESIGN_MAX_COMPRESSED_BYTES.value());
        } catch (DesignCodec.InvalidDesign e) {
            AlpineShred.LOGGER.debug("Design save by {} rejected: {}", player.getName().getString(), e.getMessage());
            return e.status;
        }
        // Rule 7: the cost, checked before and taken with the write.
        int cost = player.getAbilities().instabuild ? 0 : AlpineConfig.DESIGN_SAVE_COST_COUNT.value();
        ItemStack dye = menu.dye();
        if (cost > 0 && (!dye.is(ModTags.Items.DESIGN_COST) || dye.getCount() < cost)) return DesignStatus.NO_COST;
        if (DesignEvents.BEFORE_SAVE.invoker().beforeSave(player, gear, design).isFalse()) return DesignStatus.FORBIDDEN;
        if (cost > 0) menu.takeDye(cost);
        DesignNbt.write(gear, design);
        LAST_SAVE.put(player.getUUID(), now);
        menu.broadcastChanges();
        DesignEvents.SAVED.invoker().saved(player, gear, design);
        return DesignStatus.OK;
    }

    public static void register() {
        dev.architectury.event.events.common.PlayerEvent.PLAYER_QUIT.register(DesignServer::forget);
    }

    /** Logout: forget the cooldown. */
    public static void forget(ServerPlayer player) {
        LAST_SAVE.remove(player.getUUID());
    }
}
