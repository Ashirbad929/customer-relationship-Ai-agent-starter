# Customer Relationship Agent

Java + Spring Boot starter for a customer relationship agent backed by FalkorDB. The first milestone is a small backend that connects to a local graph database; data import, relationship queries, agent tools, and the frontend can be added in that order.

## Prerequisites

- JDK 21
- Maven 3.6.3 or newer
- Docker Desktop (for local FalkorDB)

## Run locally

1. Start FalkorDB:

   ```powershell
   docker compose up -d
   ```

2. Set `FALKORDB_URL`, `FALKORDB_GRAPH`, and `SERVER_PORT` in your shell or IntelliJ run configuration. `.env.example` lists the available settings; Spring does not load `.env` automatically.

3. Start the backend:

   ```powershell
   mvn spring-boot:run
   ```

4. Check the backend and graph connection:

   - `http://localhost:8080/api/health`
   - `http://localhost:8080/api/graph/health`

The graph connection endpoint runs a harmless `RETURN 1` query against the configured graph. FalkorDB Browser is available at `http://localhost:3000` when the local container is running.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `FALKORDB_URL` | `redis://localhost:6379` | FalkorDB connection URI |
| `FALKORDB_GRAPH` | `customer_relationships` | Graph name used by the application |
| `SERVER_PORT` | `8080` | Spring Boot HTTP port |

## Data

Place local source files under `data/`. The supplied customer data files are excluded from Git by default; do not commit real customer data or credentials. The next milestone is an idempotent importer that creates Customer, Address, Account, Phone, and Email nodes and their relationships.

## Build plan

See [BUILD_FLOW.md](BUILD_FLOW.md) for the project steps, graph schema, initial agent-tool design, and demo scope.

## Current starter endpoints

- `GET /api/health` — confirms the Java service is running.
- `GET /api/graph/health` — checks that the service can issue a query to FalkorDB.
- `GET /api/customers/{customerId}/relationships` — returns the customer's accounts and possible customer connections sharing an address, phone, or email, with evidence types.

For example, request `http://localhost:8080/api/customers/C0001/relationships`. A missing customer returns HTTP 404. Shared address/contact records are evidence of a possible connection, not proof of a family relationship. The agent and frontend are not wired in yet; the endpoint is the read-only graph tool layer the agent can call next.
