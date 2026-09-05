# NCCommandControl

NubCraft's permission-aware command visibility and help plugin for Paper.

The deployed production design currently uses the Paper plugin by itself. The
Velocity proxy module is retained in this repository for history and future
experiments, but it is deliberately not installed on the live Velocity proxy.

## Layout

- `src/`  deployed Paper plugin and policy configuration.
- `velocity/`  inactive experimental Velocity command-tree filter.

## Build

The Paper module requires Java 21:

```bash
mvn clean package
```

The inactive Velocity module requires JDK 25 for its current API annotation
processor:

```bash
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 mvn -f velocity/pom.xml clean package
```

## Known working deployment

Restored on 2026-09-04 at 23:44 EDT from the exact state that ran before the
Velocity command-tree experiments began.

- Paper JAR SHA-256:
  `705204350e12da2a9204c5e64f7d727718b30cfb828703572424fee510205323`
- Paper source baseline: commit `26e81a0`.
- Live Paper path:
  `/var/opt/minecraft/crafty/crafty-4/servers/survival/plugins/NCCommandControl.jar`
- No `NCCommandControlProxy.jar` may be present in Velocity's live `plugins/`
  directory for this deployment.
- The disabled proxy JAR is retained under Velocity's `plugin-backups/`
  directory.
- Always retain a timestamped copy of a deployed JAR before replacement.
- Do not modify third-party plugins for this feature.

## Minecraft 1.21.8 client behavior

The production mixed-root fallback was confirmed with the real FeloniousGru
client on 2026-09-05:

- Pressing `T` and then typing `/` displays the complete list, with FAWE
  double-slash roots first and normal commands below.
- Pressing the dedicated `/` key opens the command screen with `/` already
  initialized. Minecraft does not automatically open suggestions until the
  input changes; type another slash, type a command letter, or press Tab.
- This difference is client-side and is not a missing server completion or
  proxy failure.

Production Paper version: `0.1.5-SNAPSHOT`.
Production JAR SHA-256:
`9fb408168c6ba8b3408f5a6eb3ea453bf54331ece8b433814f7517c56811cd3a`.
The Velocity NCCommandControlProxy remains disabled.

## Channel-list privacy

NCCommandControl intercepts `/ch list`, `/channel list`, `/ch ls`, and
`/channel ls`. All four spellings display only channels the player may join
or leave; ChatControl's native channel/player roster is not exposed. The FAWE
`/;` compatibility alias is also omitted from command suggestions for
non-bypass players.

## Local-chat completion privacy

Plain/local chat never suggests the sender's own player name. This applies to
all players, including staff with `nccommandcontrol.bypass`, and is enforced
through both Paper's asynchronous completion event and its synchronous
fallback.


## WorldGuard region policy

Citizens may see the complete native `/rg` tab-completion tree. WorldGuard
permissions remain authoritative when a suggested subcommand is executed.
The preferred roots `/rg`, `/region`, and `/regions` are published to Citizens;
`/wg` and `/worldguard` remain Administrator-only through `worldguard.*`.

Citizen permissions are deliberately limited to claiming a WorldEdit selection,
selecting or inspecting any region, removing owned regions, and changing the
approved flag allowlist on owned regions. Administrator owns `worldguard.*`,
which is inherited by Senior Administrator and Owner.

## Bolt automation policy

Bolt 1.2.6 remains an unmodified third-party JAR. NubCraft deliberately changes
`plugins/Bolt/config.yml` as follows:

- The `private` protection type grants no implicit `redstone` access.
- A `redstone` access type grants only Bolt's `redstone` permission.
- A `hopper` access type grants only Bolt's `deposit` and `withdraw`
  permissions.
- Unique `redstone` and `block` source types allow those access entries to
  target automation rather than players.
- Existing protectable containers and hoppers continue to auto-protect as
  `private`.

NCCommandControl supplies two player-friendly wrappers. Both prompt the player
to click the protected block to modify:

- `/credstone on|off` controls redstone-signal access.
- `/chopper on|off` controls hopper/block deposit and withdrawal access.

These wrappers dispatch Bolt's native ACL editor as the player; NCCommandControl
does not read or modify Bolt's database.
