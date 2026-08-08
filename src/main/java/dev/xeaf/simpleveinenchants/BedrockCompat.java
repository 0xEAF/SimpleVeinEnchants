package dev.xeaf.simpleveinenchants;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around the Floodgate API.
 *
 * This is deliberately the ONLY class in the plugin that references Floodgate types.
 * The JVM only resolves/loads a class's imports when that class itself is loaded, so
 * as long as SimpleVeinEnchants checks `hasFloodgate` (set from
 * getPluginManager().getPlugin("floodgate") != null) BEFORE ever calling into this
 * class, servers that don't run Floodgate never attempt to load FloodgateApi at all -
 * avoiding a NoClassDefFoundError. This makes the Floodgate dependency a true soft/
 * optional dependency instead of a hard requirement.
 */
final class BedrockCompat {

    // A false negative here means "treat as Java", which means "let vanilla/Geyser
    // handle it" - i.e. silently falling straight back into the exact anvil/enchant-
    // table bug this class exists to fix. Log the first occurrence loudly so that
    // failure isn't invisible; only once, so a persistently misbehaving Floodgate
    // install doesn't spam the console every time a Bedrock player breaks a block.
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private BedrockCompat() {
    }

    static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable t) {
            if (WARNED.compareAndSet(false, true)) {
                Bukkit.getLogger().warning(
                        "[SimpleVeinEnchants] Floodgate lookup failed unexpectedly - Bedrock anvil/enchanting-table " +
                                "compatibility fixes will be skipped until this is resolved (further occurrences suppressed): "
                                + t);
            }
            return false;
        }
    }
}
