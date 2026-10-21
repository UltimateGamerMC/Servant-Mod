# Servant Mod

A Fabric mod that adds **servants** — hired staff who actually do the work. This
is not a companion/reskin mod: a servant takes one order at a time and carries it
out for real, then carries what it gathers in its own pack.

Supports **Minecraft 1.21.11** and **26.3** (separate builds).

## What a servant does

Give a servant a **duty** and it works that duty:

| Duty | Behaviour |
| --- | --- |
| `FOLLOW` | Trails you, sprints to catch up, teleports if you get far away |
| `STAY` | Holds position and does nothing |
| `GUARD` | Returns to its post and fights nearby hostiles |
| `MINE` | Finds ore (then stone), walks to it and breaks it, collecting drops |
| `CHOP` | Fells logs and collects them |
| `FARM` | Harvests mature crops, replants, and sows seeds on bare farmland |
| `HAUL` | Picks up dropped items in the area |
| `DEPOSIT` | Walks to the nearest chest/barrel and empties its pack into it |

Servants have a **9-slot pack** that persists across saves, and an **energy**
reserve that drains as they work. When they run out they stop and ask for food —
hand them any food to top them back up, or they will eat from their own pack.

## Giving orders

- **Right-click** a servant with an empty hand: shows its status.
- **Sneak + right-click**: advances it to the next duty.
- **Right-click with the Servant Ledger**: advances to the next duty.
- **Right-click with a tool**: the servant equips it and works faster/cleaner.
- **Right-click with food**: feeds it (restores energy).
- **Right-click with a name tag**: renames it.
- **Voice orders in chat**: say `servant mine`, `servant chop`, `servant farm`,
  `servant store`, `servant guard`, `servant follow`, `servant stay`, etc. You can
  also address one by name: `Alfred chop`. (Works on servers — no permissions needed.)

## Items

- **Servant Contract** (`servant-mod:servant_summoner`) — hires a servant bound to you.
  Crafted from a heart of the sea surrounded by emeralds.
- **Servant Ledger** (`servant-mod:servant_ledger`) — right-click a servant for the next
  duty, or right-click the air to recall every servant you own. Crafted from a book + emerald.

Both also appear in the creative Spawn Eggs / Tools tabs.

## Commands

All affect the closest servant you own.

- `/servant summon` — spawn a servant.
- `/servant duty <duty>` — set a duty directly.
- `/servant list` — list your servants and their status.
- `/servant pack` — show what the closest servant is carrying.
- `/servant recall` — bring every servant to you and set them to follow.
- `/servant dismiss` — send the closest servant away.
- `/servant give [ledger]` — give yourself a contract or ledger.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

## Notes

- The servant renderer reuses the player model with six built-in uniform textures.
- Servants never fight their owner, and owner hits do no damage.
- Damage/behaviour is fully server-authoritative; the client only shows a small
  duty card under the crosshair when you look at a servant.
