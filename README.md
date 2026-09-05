# NCCommandControl

NubCraft's permission-aware command visibility system for Paper and Velocity.

The Paper plugin calculates the command policy that each player may see and
sends it to the Velocity proxy. The proxy filters the Brigadier command tree so
ordinary commands remain alphabetized at `/`, while permitted WorldEdit
selection commands are grouped beneath the second slash (`//`).

## Layout

- `src/`"È›y¯ßy‘ Paper plugin and policy configuration.
- `velocity/`+ßuÁ‚ùÁT Velocity command-tree and tab-completion filter.

## Build

Both modules require Java 21 or newer. The current Velocity API build requires
JDK 25 for its annotation processor on the NubCraft build host.

```bash
mvn clean package
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 mvn -f velocity/pom.xml clean package
```

Artifacts are written to each module's `target/` directory.

## Deployment notes

- Paper artifact: `NCCommandControl.jar`
- Velocity artifact: `NCCommandControlProxy.jar`
- Proxy 0.3.1 removes synthetic Brigadier placeholders and retains only the
  permitted FAWE `//` literal nodes. WorldEdit entries sort ahead of normal
  commands and continue to support native argument completion.
- Always retain a timestamped copy of the deployed JAR before replacement.
