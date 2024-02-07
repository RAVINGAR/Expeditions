# Expeditions

Expeditions is a core feature plugin for the Minecraft server RogueMC.

## BetonQuest Integration

*Note, identifiers for POIs and NPCs that have spaces must be replaced with an _ in the BetonQuest config*
*Note, everything is case-insensitive here*

*Additionally, with POI lists, if you specify 'all' (in lowercase), this will consider the objective for all POIs
*Similarly with Expeditions, if you specify 'all' (in lowercase), this will also consider the objective for all expeditions.

### Objectives

**Loot:** `expeditionloot`  
To complete this objective, the player must loot a specific amount of crates from the specified POIs. The POIs must be
specified in a comma-separated list, where any names with spaces should be replaced with an '_'  

In the example below, a player must loot 3 crates in either the 'Ancient Ruins' or 'Quarry' POIs to complete the objective.
> expeditionloot Battlegrounds Ancient_Ruins,Quarry 3

**Extraction:** `expeditionextraction`  
To complete this objective, the player must extract from any expedition a specific number of times.  

In the example below the player will complete the objective after they extract twice from a specific extraction zone.
> expeditionextraction Battlegrounds Extraction_Zone 2

**NPC Extraction:** `expeditionnpcextraction`  
To complete this objective, the player must extract a specific type of npc and specified amount of times. Similarly,
if the NPC has spaces in it's identifier as specified in the map.yml - then you must replace in BetonQuest any spaces
with an '_'.  

In the example below the player will complete the objective after they extract once whilst with the 'Lost Architect' NPC.
> expeditionextraction Battlegrounds Lost_Architect 1

**Mob Kill:** `expeditionmobkill`  
To complete this objective, the player must kill a specific number of mobs of a specific type in a specific area. This links to the same
identifier format used when configuring mobs for a POI; eg. `'mythic:SkeletonKing'` or `vanilla:skeleton`.

In the example below the player will complete the objective after kill three Skeletal Minions from Mythic Mobs
> expeditionmobkill Battlegrounds SkeletalMinion Ancient_Ruins,Quarry 3