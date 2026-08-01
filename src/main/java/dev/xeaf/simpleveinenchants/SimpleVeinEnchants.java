package dev.xeaf.simpleveinenchants;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class SimpleVeinEnchants extends JavaPlugin implements Listener {

    public static final TypedKey<Enchantment> VEINMINE_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:veinmine"));
    public static final TypedKey<Enchantment> LUMBERJACK_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:lumberjack"));
    public static final TypedKey<Enchantment> HARVEST_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:harvest"));
    public static final TypedKey<Enchantment> EXCAVATOR_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:excavator"));

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
        if (IS_FOLIA && isOwnedMethod != null) {
            try {
                return (boolean) isOwnedMethod.invoke(null, block.getLocation());
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleVeinEnchants Loaded!");
    }

    private int getEnchantLevel(ItemStack tool, TypedKey<Enchantment> typedKey) {
        if (tool == null || !tool.hasItemMeta()) return 0;
        Enchantment enchant = Registry.ENCHANTMENT.get(typedKey.key());
        if (enchant == null) return 0;
        return tool.getEnchantmentLevel(enchant);
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS || name.endsWith("RAW_COPPER_BLOCK") || name.endsWith("RAW_IRON_BLOCK") || name.endsWith("RAW_GOLD_BLOCK");
    }

    private static final Set<Material> EXCAVATOR_BLOCKS = EnumSet.of(
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CALCITE, Material.DRIPSTONE_BLOCK,
            Material.NETHERRACK, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE, Material.GILDED_BLACKSTONE,
            Material.MAGMA_BLOCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            Material.END_STONE, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.COBBLESTONE, Material.STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES, Material.REINFORCED_DEEPSLATE, Material.INFESTED_STONE,
            Material.INFESTED_COBBLESTONE, Material.INFESTED_STONE_BRICKS, Material.INFESTED_CRACKED_STONE_BRICKS,
            Material.INFESTED_CHISELED_STONE_BRICKS
    );

    private boolean isExcavatorBlock(Material material) {
        return EXCAVATOR_BLOCKS.contains(material);
    }

    private String getBaseOreName(Material material) {
        return material.name().replace("DEEPSLATE_", "").replace("STONE_", "").replace("NETHER_", "");
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block startBlock = event.getBlock();

        int vLvl = getEnchantLevel(tool, VEINMINE_KEY);
        int lLvl = getEnchantLevel(tool, LUMBERJACK_KEY);
        int hLvl = getEnchantLevel(tool, HARVEST_KEY);
        int eLvl = getEnchantLevel(tool, EXCAVATOR_KEY);

        String mode = "";
        int maxBlocks = 0;
        Material targetMat = startBlock.getType();

        if (vLvl > 0 && isOre(targetMat)) {
            mode = "veinmine"; maxBlocks = vLvl * 64;
        } else if (lLvl > 0 && isVerticalLog(startBlock)) {
            mode = "lumberjack"; maxBlocks = lLvl * 64;
        } else if (hLvl > 0 && isMatureCrop(startBlock)) {
            mode = "harvest"; maxBlocks = hLvl * 64;
        } else if (eLvl > 0 && isExcavatorBlock(targetMat) && tool.getType().name().endsWith("_PICKAXE")) {
            mode = "excavator"; maxBlocks = -1; // Excavator uses a fixed bounds system, not a dynamic limit
        } else {
            return;
        }

        event.setCancelled(true);
        Set<Block> toBreak = new HashSet<>();

        if (mode.equals("excavator")) {
            toBreak.add(startBlock);
            Vector dir = player.getLocation().getDirection();
            int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;

            if (eLvl == 1) {
                minY = -1; // 1x2 Vertical
            } else if (eLvl == 2 || eLvl == 3) {
                // 2x2 cross-section, mapped to player direction (same plane as level 2)
                if (Math.abs(dir.getY()) > 0.5) { maxX = 1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { maxY = -1; maxZ = 1; }
                else { maxX = 1; maxY = -1; }

                if (eLvl == 3) {
                    // 2x2x2: extend the depth axis forward one more block, starting FROM
                    // the target block rather than centering on it, so a continuous
                    // tunnel doesn't re-target already-broken blocks or leave gaps.
                    if (Math.abs(dir.getY()) > 0.5) {
                        if (dir.getY() > 0) { maxY = 1; } else { minY = -1; }
                    } else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                        if (dir.getX() > 0) { maxX = 1; } else { minX = -1; }
                    } else {
                        if (dir.getZ() > 0) { maxZ = 1; } else { minZ = -1; }
                    }
                }
            } else if (eLvl == 4 || eLvl >= 5) {
                // 3x3 cross-section, centered on the target block (same plane as level 4)
                if (Math.abs(dir.getY()) > 0.5) { minX = -1; maxX = 1; minZ = -1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { minY = -1; maxY = 1; minZ = -1; maxZ = 1; }
                else { minX = -1; maxX = 1; minY = -1; maxY = 1; }

                if (eLvl >= 5) {
                    // 3x3x3: extend the depth axis forward two more blocks, starting FROM
                    // the target block (first layer) rather than centering on it.
                    if (Math.abs(dir.getY()) > 0.5) {
                        if (dir.getY() > 0) { maxY = 2; } else { minY = -2; }
                    } else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                        if (dir.getX() > 0) { maxX = 2; } else { minX = -2; }
                    } else {
                        if (dir.getZ() > 0) { maxZ = 2; } else { minZ = -2; }
                    }
                }
            }

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dy = minY; dy <= maxY; dy++) {
                    for (int dz = minZ; dz <= maxZ; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = startBlock.getRelative(dx, dy, dz);

                        if (isExcavatorBlock(neighbor.getType()) && isSafeToProcess(neighbor)) {
                            toBreak.add(neighbor);
                        }
                    }
                }
            }
        } else {
            // Standard Breadth-First Search for Veinmine, Lumberjack, and Harvest
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
        }

        // Universal Drop Processing
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
