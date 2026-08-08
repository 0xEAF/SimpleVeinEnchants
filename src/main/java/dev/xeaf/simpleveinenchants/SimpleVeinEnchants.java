package dev.xeaf.simpleveinenchants;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
    public static final TypedKey<Enchantment> ANTIGRAVITY_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:antigravity"));

    private static final List<TypedKey<Enchantment>> CUSTOM_ENCHANT_KEYS =
            List.of(VEINMINE_KEY, LUMBERJACK_KEY, HARVEST_KEY, EXCAVATOR_KEY, ANTIGRAVITY_KEY);
    private static final Random RANDOM = new Random();

    // True only if the Floodgate plugin is actually installed. Guards every Bedrock-only
    // code path below, and BedrockCompat (the only class that references Floodgate types)
    // is never touched unless this is true - so servers without Floodgate never try to
    // load a Floodgate class at all.
    private boolean hasFloodgate = false;

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
        hasFloodgate = getServer().getPluginManager().getPlugin("floodgate") != null;
        if (hasFloodgate) {
            getLogger().info("Floodgate detected - Bedrock enchanting-table compatibility fix enabled.");
        } else {
            getLogger().info("Floodgate not found - running in Java-only mode (no Bedrock compatibility fixes needed).");
        }
        getLogger().info("SimpleVeinEnchants Loaded!");
    }

    private int getEnchantLevel(ItemStack tool, TypedKey<Enchantment> typedKey) {
        if (tool == null || !tool.hasItemMeta()) return 0;
        Enchantment enchant = Registry.ENCHANTMENT.get(typedKey.key());
        if (enchant == null) return 0;
        return tool.getEnchantmentLevel(enchant);
    }

    private boolean isCustomEnchant(Enchantment enchant) {
        if (enchant == null) return false;
        String key = enchant.getKey().asString();
        return key.equals("xeaf:veinmine") || key.equals("xeaf:lumberjack")
                || key.equals("xeaf:harvest") || key.equals("xeaf:excavator")
                || key.equals("xeaf:antigravity");
    }

    private boolean isPickaxe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_PICKAXE");
    }

    private boolean isAxe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_AXE");
    }

    private boolean isHoe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_HOE");
    }

    private boolean isShovel(ItemStack tool) {
        return tool != null && (tool.getType().name().endsWith("_SHOVEL") || tool.getType().name().endsWith("_SPADE"));
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS || name.endsWith("RAW_COPPER_BLOCK") || name.endsWith("RAW_IRON_BLOCK") || name.endsWith("RAW_GOLD_BLOCK");
    }

    private static final Set<Material> PICKAXE_EXCAVATOR_BLOCKS = EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CALCITE, Material.DRIPSTONE_BLOCK,
            Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.GILDED_BLACKSTONE,
            Material.END_STONE, Material.REINFORCED_DEEPSLATE, Material.INFESTED_STONE,
            Material.INFESTED_COBBLESTONE
    );

    private static final Set<Material> SHOVEL_EXCAVATOR_BLOCKS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT, Material.ROOTED_DIRT,
            Material.MYCELIUM, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.SNOW, Material.SNOW_BLOCK, Material.SOUL_SAND, Material.SOUL_SOIL
    );

    // Filtered subset of SHOVEL_EXCAVATOR_BLOCKS that are strictly affected by gravity
    private static final Set<Material> ANTIGRAVITY_BLOCKS = EnumSet.of(
            Material.SAND, Material.RED_SAND, Material.GRAVEL
    );

    private boolean isExcavatorBlock(ItemStack tool, Material material) {
        if (isPickaxe(tool)) {
            return PICKAXE_EXCAVATOR_BLOCKS.contains(material);
        } else if (isShovel(tool)) {
            return SHOVEL_EXCAVATOR_BLOCKS.contains(material);
        }
        return false;
    }

    private boolean isAntigravityBlock(Material material) {
        return ANTIGRAVITY_BLOCKS.contains(material);
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
        int aLvl = getEnchantLevel(tool, ANTIGRAVITY_KEY);

        String mode = "single";
        int maxBlocks = 0;
        Material targetMat = startBlock.getType();

        // Check tool restrictions & sneak status before setting mode
        if (vLvl > 0 && isPickaxe(tool) && isOre(targetMat)) {
            if (player.isSneaking()) return; // Ignore veinmine, break normally
            mode = "veinmine"; maxBlocks = vLvl * 64;
        } else if (lLvl > 0 && isAxe(tool) && isVerticalLog(startBlock)) {
            if (player.isSneaking()) return; // Ignore lumberjack, break normally
            mode = "lumberjack"; maxBlocks = lLvl * 64;
        } else if (hLvl > 0 && isHoe(tool) && isMatureCrop(startBlock)) {
            // Do not return on sneak here! We still want the chain-break effect.
            mode = "harvest"; maxBlocks = hLvl * 64;
        } else if (eLvl > 0 && isExcavatorBlock(tool, targetMat)) {
            if (player.isSneaking()) return; // Ignore excavator, break normally
            mode = "excavator"; maxBlocks = -1; // Fixed bounds system
        }

        boolean hasAntigrav = (aLvl > 0 && isShovel(tool) && !player.isSneaking());

        // If no enchantments applied at all, exit out and let vanilla handle the single block.
        if (mode.equals("single") && !hasAntigrav) {
            return;
        }

        Set<Block> toBreak = new HashSet<>();

        if (mode.equals("excavator")) {
            toBreak.add(startBlock);
            Vector dir = player.getLocation().getDirection();
            int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;

            if (eLvl == 1) {
                minY = -1; // 1x2 Vertical
            } else if (eLvl == 2 || eLvl == 3) {
                // 2x2 cross-section mapped to player direction
                if (Math.abs(dir.getY()) > 0.5) { maxX = 1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { maxY = -1; maxZ = 1; }
                else { maxX = 1; maxY = -1; }

                if (eLvl == 3) {
                    // 2x2x2: extend depth axis forward 1 block
                    if (Math.abs(dir.getY()) > 0.5) {
                        if (dir.getY() > 0) { maxY = 1; } else { minY = -1; }
                    } else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                        if (dir.getX() > 0) { maxX = 1; } else { minX = -1; }
                    } else {
                        if (dir.getZ() > 0) { maxZ = 1; } else { minZ = -1; }
                    }
                }
            } else if (eLvl == 4 || eLvl >= 5) {
                // 3x3 cross-section centered on target block
                if (Math.abs(dir.getY()) > 0.5) { minX = -1; maxX = 1; minZ = -1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { minY = -1; maxY = 1; minZ = -1; maxZ = 1; }
                else { minX = -1; maxX = 1; minY = -1; maxY = 1; }

                if (eLvl >= 5) {
                    // 3x3x3: extend depth axis forward 2 blocks
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

                        if (isExcavatorBlock(tool, neighbor.getType()) && isSafeToProcess(neighbor)) {
                            toBreak.add(neighbor);
                        }
                    }
                }
            }
        } else if (mode.equals("single")) {
            toBreak.add(startBlock); // Baseline block to sweep upwards from
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

        // Secondary Phase: Sweep upwards and collect attached gravity blocks
        int addedGravity = 0;
        if (hasAntigrav) {
            int maxGravity = aLvl * 5;
            Set<Block> antigravBlocks = new HashSet<>();

            for (Block b : new ArrayList<>(toBreak)) {
                if (addedGravity >= maxGravity) break;
                Block above = b.getRelative(0, 1, 0);

                while (isAntigravityBlock(above.getType()) && isSafeToProcess(above)) {
                    if (addedGravity >= maxGravity) break;
                    if (antigravBlocks.add(above)) {
                        addedGravity++;
                    }
                    above = above.getRelative(0, 1, 0);
                }
            }
            toBreak.addAll(antigravBlocks);
        }

        // If ONLY antigravity was a valid enchantment, but there were no blocks above to grab, exit out.
        if (mode.equals("single") && addedGravity == 0) {
            return;
        }

        event.setCancelled(true);

        // Universal Drop Processing
        for (Block block : toBreak) {
            Collection<ItemStack> drops = block.getDrops(tool);

            if (mode.equals("harvest") && !player.isSneaking()) {
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

        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemMeta meta = tool.getItemMeta();
            if (meta instanceof Damageable damageable) {
                Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                int unbreakingLevel = unbreaking != null ? tool.getEnchantmentLevel(unbreaking) : 0;

                int damageToApply = 0;
                for (int i = 0; i < toBreak.size(); i++) {
                    if (unbreakingLevel <= 0 || RANDOM.nextInt(unbreakingLevel + 1) == 0) {
                        damageToApply++;
                    }
                }

                if (damageToApply > 0) {
                    damageable.setDamage(damageable.getDamage() + damageToApply);
                    tool.setItemMeta(meta);
                    if (damageable.getDamage() > tool.getType().getMaxDurability()) {
                        tool.setAmount(0);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    }
                }
            }
        }
    }

    /**
     * Lets librarian villagers occasionally offer one of our custom enchantment books.
     */
    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getProfession() != Villager.Profession.LIBRARIAN) return;

        MerchantRecipe original = event.getRecipe();
        if (original.getResult().getType() != Material.ENCHANTED_BOOK) return;

        if (RANDOM.nextInt(6) != 0) return;

        TypedKey<Enchantment> chosenKey = CUSTOM_ENCHANT_KEYS.get(RANDOM.nextInt(CUSTOM_ENCHANT_KEYS.size()));
        Enchantment enchant = Registry.ENCHANTMENT.get(chosenKey.key());
        if (enchant == null) return;

        int level = 1 + RANDOM.nextInt(enchant.getMaxLevel());

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
        bookMeta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(bookMeta);

        int emeraldPrice = Math.min(64, 8 + level * 10);

        MerchantRecipe custom = new MerchantRecipe(
                book,
                0,
                original.getMaxUses(),
                true,
                5 + level * 4,
                original.getPriceMultiplier(),
                0,
                0,
                false
        );
        custom.addIngredient(new ItemStack(Material.EMERALD, emeraldPrice));
        custom.addIngredient(new ItemStack(Material.BOOK, 1));

        event.setRecipe(custom);
    }

    /**
     * Bedrock/Geyser compatibility fix for the enchanting table.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!hasFloodgate) return;
        Player player = event.getEnchanter();
        if (!BedrockCompat.isBedrockPlayer(player)) return;

        boolean hasCustom = event.getEnchantsToAdd().keySet().stream().anyMatch(this::isCustomEnchant);
        if (!hasCustom) return;

        event.setCancelled(true);

        ItemStack enchanted = event.getItem().clone();
        ItemMeta enchMeta = enchanted.getItemMeta();
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            if (enchMeta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            } else {
                enchMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        enchanted.setItemMeta(enchMeta);

        ItemStack lapis = event.getInventory().getItem(1);
        int lapisCost = event.whichButton() + 1;
        if (lapis != null) {
            lapis.setAmount(Math.max(0, lapis.getAmount() - lapisCost));
            event.getInventory().setItem(1, lapis.getAmount() <= 0 ? null : lapis);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(Math.max(0, player.getLevel() - event.getExpLevelCost()));
        }

        event.getInventory().setItem(0, null);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(enchanted);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.updateInventory();
    }
}
