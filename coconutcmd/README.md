# CoconutCommand (Paper 1.20+)

## Build
Needs Java 17+ and Maven:  `mvn clean package`  ->  `target/CoconutCommand-1.0.0.jar`
Drop the jar in `plugins/`, restart the server, edit `plugins/CoconutCommand/config.yml`.

## Commands (permission: coconut.admin, ops by default)
- /coconut                    coconuts fall + "LOOK UP!" title (10s, no loot)
- /coconut event              announced loot event, coconuts crack open with small loot (30s)
- /coconut keyall giveall Heavenly 2 Illusionist 2 Party 2 Legendary 1 Common 4
- /coconut dance              everyone spins/hops with a funky note-block groove (5s)
- /coconut sendall spawn      everyone to spawn
- /coconut surprise           THE GOLDEN COCONUT (fireworks + lightning flash + reward + loot shower for one random player)
- /coconut reload

## MUST set in config.yml
- key-command: your PhoenixCrates give-key command (default is a placeholder)
- crates: your exact crate names
- spawn-command: default uses Essentials `spawn <player>`
