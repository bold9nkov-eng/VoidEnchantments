# VoidEnchantments

Custom enchantments for VoidMain (Paper 1.21.11 / Java 21).

## Build
`mvn clean package`

Artifact: `target/VoidEnchantments-1.0.0.jar`

## Commands
- `/voidenchants` — open enchantment catalog
- `/ve give <player> <key> [level]` — admin give (permission `voidenchants.admin`)

Keys: `magnet`, `drill`, `autosmelt`, `veinminer`, `haste`, `experience`, `vampirism`, `frost`, `venom`, `execution`, `thunder`, `dragonbreath`, `sneak`, `diver`, `immunity`, `softsteps`, `lavawalker`, `owl`, `sniper`, `wisdom`, `wings`, `hover`, `windspeed`.

Books without levels are deliberately rejected by anvil combination; both input books are preserved.
