## sbt project compiled with Scala 3

### Toolchain

Use JDK 21, sbt 1.13.0, and Scala 3.10.0. The Play 3.0.12 sbt plugin is published for sbt 1.x, so this build stays on that series. Play also requires Guice 6 and its `javax.inject` support; Guice 7 removes that support.

The Neo4j 6 driver remains compatible with the existing Neo4j 5 server. Testcontainers 2 uses the renamed Neo4j module. The Jackson Scala module is declared explicitly to keep it aligned with the Jackson runtime pulled in by the upgraded dependencies.

Run `sbt test "backend / Universal / stage"` to verify tests and production packaging. Integration tests start an isolated Neo4j container and require ports 7474, 7687, and 2004 to be free.

### Usage

Run with `sbt runBackend`

Requires a neo4j 5.x.y instance available at port 7687

For AI parsing endpoints, configure Ollama for backend runtime:

- OLLAMA_URL (example: http://host.docker.internal:11434 when backend runs in Docker)
- OLLAMA_MODEL (example: qwen3.5:9b)

## Notes

### Auth

Implementing custom auth was a fun exercise, but I should really outsource to something that handles it in a separate store.
I could keep my own separate auth service, but so far auth0s free plan looks good, and I'm familiar with it.

### Filters

After I implement recipes, I should think about an endpoint to support more social queries.

- Recipes similar to ones you like
- Users with similar tastes to you

### Design

- I could maybe delegate more to fetch interactor
- Persistence layer will likely become generic.

Set `AUTH_AUDIENCE` to the access token audience accepted by this backend when configuring an OIDC client other than the default development client.
