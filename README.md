# SimpleVeinEnchants
Very simple veinmining enchants for ores (Veinmine), trees (Lumberjack) and crops (Harvest) + a bonus Excavator enchant (3x3 mining)

## WARNING: Made with AI (cuz idk how to use Java)

I am a random dev, and you should really not trust random stuff you find on the Internet. Take a look at the source code which is only a few hundred lines long. (click on the GitHub "view source" link on the side). And if you prefer, compile the project yourself with `mvn clean package` (requires Java JDK 21+ and Maven installed).

## Docs

### Veinmine

Can mine up to 64 neighboring ores per level of enchant. Maxes out at level 5 (V) at 320 ores.
Default will probably be changed in a future update.

### Lumberjack

Can mine up to 64 neighboring logs per level of enchant. Maxes out at level 5 (V) at 320 logs.

### Harvest

Can mine up to 64 neighboring crops per level of enchant, and re-places them. Maxes out at level 5 (V) at 320 crops.

### Excavator

Can mine up tunnels efficiently.
- Level I: breaks a 1x2 tunnel (a regular strip mine tunnel)
- Level II: breaks a 2x2 zone (in 2D)
- Level III: breaks a 2x2x2 zone (in 3D)
- Level IV: breaks a 3x3 zone (in 2D)
- Level V: breaks a 3x3x3 zone (in 3D) (regular excavator)
