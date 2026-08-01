package dev.xeaf.simpleveinenchants;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.EquipmentSlotGroup;

public class SimpleVeinEnchantsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {

        context.getLogger().info(">>> BOOTSTRAPPER IS RUNNING! INJECTING ENCHANTMENTS! <<<");

        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {

            var miningTag = event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING);

            event.registry().register(
                SimpleVeinEnchants.VEINMINE_KEY,
                b -> b.description(Component.text("Veinmine").color(NamedTextColor.GRAY))
                      .supportedItems(miningTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.LUMBERJACK_KEY,
                b -> b.description(Component.text("Lumberjack").color(NamedTextColor.GRAY))
                      .supportedItems(miningTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.HARVEST_KEY,
                b -> b.description(Component.text("Harvest").color(NamedTextColor.GRAY))
                      .supportedItems(miningTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.EXCAVATOR_KEY,
                b -> b.description(Component.text("Excavator").color(NamedTextColor.GRAY))
                      .supportedItems(miningTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );
        }));
    }
}
