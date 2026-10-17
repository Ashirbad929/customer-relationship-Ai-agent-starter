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

2. Copy `.env.example` to `.env` in the project root and edit the values there. Spring Boot imports this file automatically. `.env` is ignored by Git.

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
- `POST /api/customers/relationships` — accepts a JSON body with `customerId`, then returns the customer's accounts, total customer-holder count per account, and possible customer connections sharing an address, phone, or email, with evidence types.
- `POST /api/agent/chat` — when the agent is enabled, accepts a customer ID and question, then lets the model call the bounded FalkorDB relationship tool and explain the result.

Example request:

```http
POST http://localhost:8080/api/customers/relationships
Content-Type: application/json

{"customerId":"C0001"}
```

A missing customer returns HTTP 404. Shared address/contact records are evidence of a possible connection, not proof of a family relationship. The relationship service is read-only and is also used by the agent's graph tool. The Vite + React frontend is in `frontend/` and proxies `/api` to the Spring Boot server during local development.

## Frontend

With the backend running on port 8080, open a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and enter a customer ID to view the relationship graph and account-holder analytics. The frontend sends `{ "customerId": "..." }` to `POST /api/customers/relationships`; graph lookup does not require OpenAI. The assistant panel sends `{ "customerId": "...", "message": "..." }` to `POST /api/agent/chat` and requires `AGENT_ENABLED=true`, model configuration, and an available API key/credit balance. The frontend uses Vite's development proxy, so requests reach Spring Boot at `http://localhost:8080`.

## Agent integration

The agent uses Spring AI's `ChatClient` tool-calling loop. It can call separate read-only tools for relationships/accounts, addresses, and phone numbers; each delegates to its corresponding Java service and parameterized FalkorDB query. It can also call `searchPublicWeb` for current or external information. That tool uses OpenAI's Responses API with its hosted `web_search` tool and returns citations to the agent for its final answer.

Before a web query leaves the backend, the web-search tool removes the selected customer's ID, name, connected-customer names and IDs, account IDs, street/unit address, phone numbers, and email addresses. Local web searches should use only public location details such as city and postal code. The customer graph itself is not sent to the web-search endpoint. Web search requires an OpenAI API key and can incur additional API usage charges.

The selected customer ID for graph tools is supplied by the backend through tool context. The model cannot submit arbitrary Cypher or choose another customer ID through those tools.

To enable the agent, set these values in the project-root `.env` file:

| Variable | Value |
| --- | --- |
| `AGENT_ENABLED` | `true` |
| `SPRING_AI_MODEL_CHAT` | `openai` |
| `OPENAI_API_KEY` | Your OpenAI API key (keep it secret; never commit it) |
| `OPENAI_MODEL` | `gpt-5-mini` by default; can be changed to a model available to your API account |

The agent is disabled by default so the graph API can run without AI credentials. With the agent enabled, call:

```http
POST http://localhost:8080/api/agent/chat
Content-Type: application/json

{"customerId":"C0001","message":"What accounts does this customer have, and who may be connected to them? Explain the evidence."}
```

The tool only returns graph evidence for the selected customer. The assistant is instructed not to assert family relationships from shared records or claim card eligibility; the current graph does not contain product eligibility, preferences, or balances. Each chat request is stateless; conversation memory can be added later.

## Logging

Application classes log startup, graph connectivity, tool selection, and result counts. The `dev.customergraph` package is set to `DEBUG` in `application.properties`. Customer addresses, phone numbers, chat messages, API keys, and search queries are not written to application logs. Request/response DTO records contain data only and do not need logger fields.
