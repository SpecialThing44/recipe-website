## sbt project compiled with Scala 3

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
