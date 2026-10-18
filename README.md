# Customer Relationship Agent

Java + Spring Boot customer relationship agent backed by FalkorDB, with a Vite + React frontend and a Python loader for existing Cypher dataset files.

## Prerequisites

- JDK 21
- Maven 3.6.3 or newer
- Python 3.10 or newer
- Docker Desktop (for local FalkorDB)
- Node.js and npm (for the frontend)

## Run locally

1. Start FalkorDB:

   ```powershell
   docker compose up -d
   ```

2. Copy `.env.example` to `.env` in the project root if you want to change the defaults. Spring Boot and the data loader read this file automatically. `.env` is ignored by Git.

3. Load your existing numbered Cypher `.txt` dataset files. Pass the directory containing those files (for example, your `cust-rel` folder):

   ```powershell
   py -m pip install -r requirements-demo.txt
   py scripts/load_demo_data.py "C:\Users\<your-user>\Downloads\cust-rel"
   ```

   The loader runs `.txt` files in numeric filename order and skips non-Cypher guide files. Set `DEMO_DATA_DIR` in `.env` to avoid passing the folder each time. The default folder is `data/cypher/`. Review the dataset before loading it; rerun behavior depends on whether each file's Cypher uses `MERGE` or `CREATE`.

4. Start the backend:

   ```powershell
   mvn spring-boot:run
   ```

5. Check the backend and graph connection:

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

The numbered Cypher import files are included in `data/cypher/`, including a separate synthetic extension for customers `C1001`–`C1030` and 60 synthetic transaction records for 20 of those customers. The loader does not generate records or parse the pipe-delimited fixtures directly under `data/`. If the included demo extension (`C1001`–`C1030`, files 13–22) is already loaded, add only the transaction data with `py scripts/load_demo_data.py data/cypher/23_demo_transactions.txt`; this avoids rerunning earlier dataset files. For a fresh graph, use the full default dataset import so the demo accounts exist before the transaction file runs. Confirm the source dataset is approved for sharing before publishing the repository. Never commit credentials.

For a read-aloud project pitch, feature walkthrough, and checked Windows startup steps, see [DEMO_GUIDE.md](DEMO_GUIDE.md).

## Build plan

See [BUILD_FLOW.md](BUILD_FLOW.md) for the project steps, graph schema, initial agent-tool design, and demo scope.

## Current starter endpoints

- `GET /api/health` — confirms the Java service is running.
- `GET /api/graph/health` — checks that the service can issue a query to FalkorDB.
- `POST /api/customers/relationships` — accepts a JSON body with `customerId`, then returns the customer's accounts, holder counts, and possible customer connections through shared address, phone, email, or jointly held account paths. Each connection includes a safe traversal explanation; phone and email values are withheld.
- `POST /api/customers/addresses`, `/api/customers/phone-numbers`, `/api/customers/email-addresses` — directly fetch the selected customer's contact records from FalkorDB.
- `POST /api/customers/cards`, `/api/customers/bills`, `/api/customers/loans` — directly fetch linked card products, credit-card bills, and loan details.
- `POST /api/agent/chat` — when the agent is enabled, accepts a customer ID and question, then lets the model choose among bounded FalkorDB tools, including a multi-hop synthetic transaction-to-merchant investigation, and explain the evidence.
- `POST /api/search/openai` — when the agent is enabled, sends a public query directly to OpenAI's Responses API web-search tool and returns its answer and sources. Use only public, non-customer-identifying search text.

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

Open `http://localhost:5173` and enter a customer ID to view the relationship graph and account-holder analytics. The Relationships, Cards & bills, Loans, and Contact details views call the direct customer endpoints and do not require an AI-provider API call. The assistant panel can answer questions using separate FalkorDB tools for relationship, address, phone, email, card, bill, and loan data. Chat requires `AGENT_ENABLED=true`, a selected provider, and that provider's API key/quota. Public web searches, including ATM and branch lookups, use OpenAI's Responses API web-search tool and require `OPENAI_API_KEY`, regardless of the chat provider. The frontend uses Vite's development proxy, so requests reach Spring Boot at `http://localhost:8080`.

When a chat question asks about cards, bills, or loans, the frontend fetches the matching graph records and expands the SVG with account-to-product, account-to-bill, or account-to-loan links. Transaction and merchant analysis is available to the agent for the synthetic demo customers who have transaction fixtures.

## Agent integration

The agent uses Spring AI's `ChatClient` tool-calling loop. At startup it selects Gemini when `GEMINI_API_KEY` is present, otherwise OpenAI when `OPENAI_API_KEY` is present. If both are set, Gemini is selected by default. Set `AI_PROVIDER=google-genai` or `AI_PROVIDER=openai` to explicitly choose a provider. Chat uses the selected provider. Public web search always uses OpenAI's Responses API web-search tool, so `OPENAI_API_KEY` is required for web searches even when Gemini is selected for chat. Based on the user's question, the model selects among read-only tools for relationships/accounts, addresses, phone numbers, email addresses, cards, bills, loans, and synthetic transaction activity; each delegates to its corresponding Java service and parameterized FalkorDB query. Relationship traversal follows shared address/contact records and jointly held accounts. Activity analysis traverses customer → account → transaction → merchant and back through other accounts and transactions to identify shared merchant paths. The assistant receives the evidence path and explains that merchant overlap alone does not establish a personal relationship. OpenAI returns source citations to the agent for its final answer.

Before a web query leaves the backend, the web-search tool removes the selected customer's ID, name, connected-customer names and IDs, account IDs, street/unit address, phone numbers, and email addresses. Local web searches should use only public location details such as city and postal code. The customer graph itself is not sent to OpenAI. Selecting a chat provider checks whether its key is configured; it does not detect quota exhaustion or automatically switch chat providers after a failed API request.

The selected customer ID for graph tools is supplied by the backend through tool context. The model cannot submit arbitrary Cypher or choose another customer ID through those tools.

To enable the agent, set these values in the project-root `.env` file:

| Variable | Value |
| --- | --- |
| `AGENT_ENABLED` | `true` |
| `AI_PROVIDER` | `auto` (Gemini wins if both provider keys are set; may be `google-genai` or `openai`) |
| `GEMINI_API_KEY` | Your Gemini API key (keep it secret; never commit it) |
| `GEMINI_MODEL` | `gemini-3.8-flash` by default; choose a model available to your Gemini API project |
| `OPENAI_API_KEY` | Required for OpenAI chat if selected, and always required for OpenAI web search (keep it secret; never commit it) |
| `OPENAI_MODEL` | `gpt-5-mini` by default; choose a model available to your OpenAI API project |

The agent is disabled by default so the graph API can run without AI credentials. Gemini's free-tier availability and limits depend on model and region. The free tier may use submitted prompts to improve Google's products, so use synthetic data for demos. With the agent enabled, call:

```http
POST http://localhost:8080/api/agent/chat
Content-Type: application/json

{"customerId":"C0001","message":"What accounts does this customer have, and who may be connected to them? Explain the evidence."}
```

To test OpenAI web search directly without the chat model's tool selection, call `POST http://localhost:8080/api/search/openai` with `{"query":"ATMs near Pune 411045"}`. This endpoint uses the OpenAI Responses API web-search tool and requires `OPENAI_API_KEY`.

The tools only return graph evidence for the selected customer. The assistant is instructed not to assert family relationships from shared records or claim card eligibility. Supplemental card, bill, loan, and transaction fixtures are synthetic and marked `SYNTHETIC_DEMO`; the assistant identifies their financial values and activity as demo data. Each chat request is stateless; conversation memory can be added later.

## Logging

Application classes log startup, graph connectivity, tool selection, and result counts. The `dev.customergraph` package is set to `DEBUG` in `application.properties`. Customer addresses, phone numbers, chat messages, API keys, and search queries are not written to application logs. Request/response DTO records contain data only and do not need logger fields.
