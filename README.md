# ESP Mod v2 (Fabric 1.21.11, client-side)

Menu (Right Shift), Fullbright, Block ESP, Player ESP (hitbox + health + armor durability),
Entity ESP en Freecam. Gebruik alleen in singleplayer / op servers waar het mag.

## Online bouwen (GitHub)
1. Upload ALLE bestanden uit deze map naar een nieuwe GitHub-repository (inclusief .github).
2. Tabblad Actions -> wacht tot de build groen is -> Artifacts -> espmod-jar.
3. Pak de zip uit: daarin zit espmod-2.0.0.jar. Zet die + Fabric API in je mods-map.

## Menu
Right Shift opent het menu (ook te veranderen in het menu zelf).
Tabs: General, Block, Player, Entity, Freecam.
- Elke hack heeft [Toggle] [Key: ..] [X]. Klik op Key en druk een toets om te veranderen;
  X (of Delete/Backspace tijdens het kiezen) zet de toets uit. Esc annuleert.
- Kleuren: klik om door de kleuren te wisselen. Eigen hex-kleuren kan met /esp ...
- Block/Entity: typ een id (bv. diamond_ore), klik Add of Remove.
  De kleur van een nieuw item = "New block/entity" knop.

## Standaardtoetsen
Right Shift = menu, G = Fullbright, H = Block ESP, J = Player ESP, K = Entity ESP, V = Freecam

## Freecam
WASD = bewegen, Space = omhoog, Shift = omlaag. Snelheid in het menu.
Je lichaam blijft staan (witte outline). Niet slaan/minen tijdens freecam.

## Commando's (optioneel)
/esp, /esp fullbright, /esp freecam, /esp linewidth 0.025
/esp block|player|entity toggle | range N | tracers | tracercolor HEX | outlinecolor HEX
/esp block|entity add|remove ID, /esp block|entity color HEX ID, list, clear
