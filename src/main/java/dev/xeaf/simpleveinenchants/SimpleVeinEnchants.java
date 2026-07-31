package dev.xeaf.simpleveinenchants;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SimpleVeinEnchants extends JavaPlugin implements Listener {

    private NamespacedKey VEINMINE_KEY;
    private NamespacedKey LUMBERJACK_KEY;
    private NamespacedKey HARVEST_KEY;

    // --- CROSS-COMPATIBILITY FOLIA BRIDGE ---
    private static final boolean IS_FOLIA = checkFolia();
    private static Method isOwnedMethod;

    static {
        if (IS_FOLIA) {
            try {
                isOwnedMethod = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            } catch (Exception e) {
                isOwnedMethod = null;
            }
        }
    }

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isSafeToProcess(Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;

        // If on Folia, dynamically invoke region check to prevent cross-thread crashes
        if (IS_FOLIA && isOwnedMethod != null) {
            try {
                return (boolean) isOwnedMethod.invoke(null, block.getLocation());
            } catch (Exception e) {
                return false;
            }
        }

        // Standard Paper/Spigot is strictly single-threaded, so it's always safe
        return true;
    }
    // ----------------------------------------

    @Override
    public void onEnable() {
        VEINMINE_KEY = new NamespacedKey(this, "veinmine");
        LUMBERJACK_KEY = new NamespacedKey(this, "lumberjack");
        HARVEST_KEY = new NamespacedKey(this, "harvest");

        getServer().getPluginManager().registerEvents(this, this);

        if (IS_FOLIA) {
            getLogger().info("SimpleVeinEnchants loaded! Running in Folia Multithreaded Mode.");
        } else {
            getLogger().info("SimpleVeinEnchants loaded! Running in Standard Paper/Spigot Mode.");
        }
    }

    private int getEnchantLevel(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        return Math.min(5, item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0));
    }

    private void applyEnchant(ItemMeta meta, NamespacedKey key, String name, int level) {
        if (level <= 0) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);

        String roman = switch (level) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(level);
        };

        Component lineComponent = LegacyComponentSerializer.legacySection().deserialize("§7" + name + " " + roman);
        List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();

        lore.removeIf(comp -> PlainTextComponentSerializer.plainText().serialize(comp).contains(name));
        lore.add(0, lineComponent);
        meta.lore(lore);
    }

    private boolean isApplicable(ItemStack item, String type) {
        Material mat = item.getType();
        if (mat == Material.ENCHANTED_BOOK || mat == Material.BOOK) return true;
        String name = mat.name();
        return switch (type) {
            case "veinmine" -> name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL");
            case "lumberjack" -> name.endsWith("_AXE");
            case "harvest" -> name.endsWith("_HOE");
            default -> false;
        };
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS || name.endsWith("RAW_COPPER_BLOCK") || name.endsWith("RAW_IRON_BLOCK") || name.endsWith("RAW_GOLD_BLOCK");
    }

    private String getBaseOreName(Material material) {
        return material.name().replace("DEEPSLATE_", "");
    }

    private boolean isVerticalLog(Block block) {
        String name = block.getType().name();
        if (!(name.endsWith("_LOG") || name.endsWith("_WOOD"))) return false;
        return block.getBlockData() instanceof Orientable orientable && orientable.getAxis() == Axis.Y;
    }

    private boolean isMatureCrop(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        return false;
    }

    private Material getSeedMaterial(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case PITCHER_CROP -> Material.PITCHER_POD;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            default -> crop;
        };
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        if (left == null || right == null) return;

        ItemStack result = left.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        if (result.getType() == Material.BOOK) result.setType(Material.ENCHANTED_BOOK);

        boolean updated = false;
        int totalCost = 0;

        String[] types = {"veinmine", "lumberjack", "harvest"};
        NamespacedKey[] keys = {VEINMINE_KEY, LUMBERJACK_KEY, HARVEST_KEY};
        String[] names = {"Veinmine", "Lumberjack", "Harvest"};

        for (int i = 0; i < 3; i++) {
            int leftLvl = getEnchantLevel(left, keys[i]);
            int rightLvl = getEnchantLevel(right, keys[i]);

            if (rightLvl > 0 && isApplicable(left, types[i])) {
                int newLvl = (leftLvl == rightLvl) ? Math.min(5, leftLvl + 1) : Math.max(leftLvl, rightLvl);
                if (newLvl > leftLvl) {
                    applyEnchant(resultMeta, keys[i], names[i], newLvl);
                    updated = true;
                    totalCost += newLvl * 2;
                }
            }
        }

        if (updated) {
            result.setItemMeta(resultMeta);
            event.setResult(result);
            event.getInventory().setRepairCost(Math.max(1, totalCost));
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (event.getExpLevelCost() >= 30 && ThreadLocalRandom.current().nextDouble() < 0.35) {
            List<Integer> validIndexes = new ArrayList<>();
            String[] types = {"veinmine", "lumberjack", "harvest"};
            for (int i = 0; i < 3; i++) {
                if (isApplicable(event.getItem(), types[i])) validIndexes.add(i);
            }

            if (!validIndexes.isEmpty()) {
                int choice = validIndexes.get(ThreadLocalRandom.current().nextInt(validIndexes.size()));
                NamespacedKey[] keys = {VEINMINE_KEY, LUMBERJACK_KEY, HARVEST_KEY};
                String[] names = {"Veinmine", "Lumberjack", "Harvest"};
                int level = ThreadLocalRandom.current().nextInt(1, 4);

                ItemMeta meta = event.getItem().getItemMeta();
                if (meta != null) {
                    applyEnchant(meta, keys[choice], names[choice], level);
                    event.getItem().setItemMeta(meta);
                }
            }
        }
    }

    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        if (event.getEntity() instanceof Villager villager && villager.getProfession() == Villager.Profession.LIBRARIAN) {
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                int choice = ThreadLocalRandom.current().nextInt(3);
                NamespacedKey[] keys = {VEINMINE_KEY, LUMBERJACK_KEY, HARVEST_KEY};
                String[] names = {"Veinmine", "Lumberjack", "Harvest"};
                int level = ThreadLocalRandom.current().nextInt(1, 4);

                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = book.getItemMeta();
                applyEnchant(meta, keys[choice], names[choice], level);
                book.setItemMeta(meta);

                int emeraldCost = ThreadLocalRandom.current().nextInt(12, 37);
                MerchantRecipe recipe = new MerchantRecipe(book, 12);
                recipe.addIngredient(new ItemStack(Material.EMERALD, emeraldCost));
                recipe.addIngredient(new ItemStack(Material.BOOK, 1));

                event.setRecipe(recipe);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block startBlock = event.getBlock();

        int vLvl = getEnchantLevel(tool, VEINMINE_KEY);
        int lLvl = getEnchantLevel(tool, LUMBERJACK_KEY);
        int hLvl = getEnchantLevel(tool, HARVEST_KEY);

        String mode = "";
        int maxBlocks = 0;
        Material targetMat = startBlock.getType();

        if (vLvl > 0 && isOre(targetMat)) {
            mode = "veinmine"; maxBlocks = vLvl * 64;
        } else if (lLvl > 0 && isVerticalLog(startBlock)) {
            mode = "lumberjack"; maxBlocks = lLvl * 64;
        } else if (hLvl > 0 && isMatureCrop(startBlock)) {
            mode = "harvest"; maxBlocks = hLvl * 64;
        } else {
            return;
        }

        event.setCancelled(true);

        Set<Block> toBreak = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        Set<Block> visited = new HashSet<>();

        queue.add(startBlock);
        visited.add(startBlock);

        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            Block current = queue.poll();
            toBreak.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = (mode.equals("harvest") ? 0 : -1); dy <= (mode.equals("harvest") ? 0 : 1); dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);

                        if (!visited.add(neighbor)) continue;

                        // Universal compatibility check
                        if (!isSafeToProcess(neighbor)) continue;

                        boolean isValid = switch (mode) {
                            case "veinmine" -> getBaseOreName(neighbor.getType()).equals(getBaseOreName(targetMat)) && isOre(neighbor.getType());
                            case "lumberjack" -> neighbor.getType() == targetMat && isVerticalLog(neighbor);
                            case "harvest" -> neighbor.getType() == targetMat && isMatureCrop(neighbor);
                            default -> false;
                        };

                        if (isValid) queue.add(neighbor);
                    }
                }
            }
        }

        for (Block block : toBreak) {
            Collection<ItemStack> drops = block.getDrops(tool);
            if (mode.equals("harvest")) {
                Ageable data = (Ageable) block.getBlockData();
                data.setAge(0);
                block.setBlockData(data);

                Material requiredSeed = getSeedMaterial(targetMat);
                boolean seedRemoved = false;

                for (ItemStack drop : drops) {
                    if (!seedRemoved && drop.getType() == requiredSeed) {
                        drop.setAmount(drop.getAmount() - 1);
                        seedRemoved = true;
                    }
                    if (drop.getAmount() > 0) {
                        block.getWorld().dropItemNaturally(block.getLocation(), drop);
                    }
                }
            } else {
                for (ItemStack drop : drops) {
                    if (drop.getAmount() > 0) block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
                int exp = event.getExpToDrop();
                if (exp > 0 && mode.equals("veinmine")) {
                    ExperienceOrb orb = block.getWorld().spawn(block.getLocation(), ExperienceOrb.class);
                    orb.setExperience(exp);
                }
                block.setType(Material.AIR);
            }
        }

        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + toBreak.size());
            tool.setItemMeta(meta);
            if (damageable.getDamage() > tool.getType().getMaxDurability()) {
                tool.setAmount(0);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }
}
