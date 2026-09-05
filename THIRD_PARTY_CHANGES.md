# Tracked third-party configuration

This file records intentional NubCraft changes to third-party plugin
configuration. The third-party JARs themselves remain unmodified.

## 2026-09-05 — Bolt 1.2.6

File: plugins/Bolt/config.yml

- Removed implicit redstone access from the default private protection type.
- Added a redstone access type granting only redstone.
- Added a hopper access type granting only deposit and withdraw.
- Added unique redstone and block source types.
- All configured containers and hoppers continue to auto-protect as private.
- NCCommandControl provides /credstone on|off and /chopper on|off wrappers
  around Bolt's native ACL editor.

## 2026-09-05 — WorldGuard 7.0.14

Files: plugins/WorldGuard/worlds/*/regions.yml

The __global__ region supplies the server-wide anti-explosion and interaction
baseline. Named claims, spawn, towns, and arenas remain separate regions and
may override that baseline at a higher priority.

Citizen LuckPerms policy:

- claim from a WorldEdit selection;
- select and inspect any region;
- remove only owned regions;
- modify only the approved flags on owned regions.

The third-party WorldGuardTabCompletion 1.2 JAR remains unmodified and its full
/rg subcommand completion list is intentionally retained.
