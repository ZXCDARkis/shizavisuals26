# PvE recovery matrix

This file tracks the source evidence used by the Minecraft 26.2 port.
`Recovered` means the relevant behavior is present in executable recovered
bytecode. `Partial` means recovered methods are mixed with non-executed stubs.
`Manual` marks behavior reconstructed from settings, cross-references, state
transitions, protocol behavior, and neighboring recovered methods.

| Feature | Source coverage | Port policy | Server scope |
|---|---|---|---|
| AutoFish | Partial | Manual tick state machine; recovered rod selection | Generic |
| AutoArmor | Recovered | Direct behavioral port | Generic |
| AutoPotion | Recovered | Direct behavioral port | Generic |
| AutoUse / AutoEat / AutoInvisibility | Recovered | Direct behavioral port | Generic + FunTime |
| AutoGapple | Stubbed | Manual | Generic |
| AntiAFK | Recovered class, unnamed behavior | Manual integration | Generic |
| AutoLeave | Recovered | Direct behavioral port | Generic + server commands |
| AutoAuth | Recovered | Direct behavioral port with secure secret handling | Generic |
| Nuker | Partial | Recovered selection loop; manual helper reconstruction | Generic + FunTime mine |
| MineHelper | Partial | Recovered modes; repair malformed helper | FunTime |
| AutoTpLoot | Recovered | Direct behavioral port | Generic flight mode |
| AutoMine | Partial | Manual orchestration around recovered states | FunTime |
| BaseFinder | Partial | Manual orchestration around recovered states | FunTime |
| AppleFarmer | Partial | Recovered phases plus manual missing transitions | FunTime |
| CreeperFarm | Stubbed | Manual full state machine | FunTime |
| AutoCrafter | Partial | Recovered state machine plus manual helpers | FunTime |
| AutoTrade | Partial | Manual missing methods around recovered state machine | FunTime |
| AuctionRelist | Recovered | Direct behavioral port | FunTime |
| ClanInvest | Recovered | Direct behavioral port | FunTime |
| ClanUpgrade | Recovered | Direct behavioral port | FunTime |
| GriefJoiner | Partial | Recovered queue flow plus manual retry handling | ReallyWorld |
| Synchronization | Stubbed/partial helpers | Manual local-client protocol | Local clients |
| AutoWarden | Partial | Recovered orchestrator plus manual helper reconstruction | FunTime |

Existing ShizaVisuals features are extended rather than duplicated:

- AutoTool: add normal/silent selection modes needed by mining bots.
- ChestStealer: expose reusable selective-loot transactions.
- AutoTotem: retain the current implementation and expose bot pause state.
- AutoRespawn: add delay and post-respawn command support.
- AutoTpaAccept: add friends-only and PvP guards.
- AutoClicker/NoDelays: add missing active-click and XP-bottle-only behavior.

Every manually reconstructed branch must have a deterministic state-transition
test or parser test before the corresponding feature is considered complete.
