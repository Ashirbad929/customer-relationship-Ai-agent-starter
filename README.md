# Customer Relationship Agent

A Java Spring Boot and React demo for exploring customer, account, address, contact, card, bill, loan, and transaction relationships in FalkorDB. A chat assistant can select read-only graph tools to retrieve evidence and explain the paths it used.

## Run the complete demo locally

The repository includes the Cypher dataset under `data/cypher/`; you do not need a separate `cust-rel` folder. Run the commands from the project root unless a step says otherwise. On Windows, use PowerShell and `py`. On macOS/Linux, use `python3` in place of `py`.

### Prerequisites

- JDK 21
- Maven 3.6.3 or newer
- Docker Desktop (or Docker Engine with Compose)
- Python 3.10 or newer
- Node.js and npm

### 1. Start FalkorDB

In terminal 1:

```powershell
docker compose up -d
docker compose ps
```

This starts FalkorDB on `localhost:6379` and FalkorDB Browser on `http://localhost:3000`. The Compose file stores graph data in a Docker volume, so it persists across container restarts.

### 2. Load the repository’s dataset

In terminal 2, still at the project root:

```powershell
py -m pip install -r requirements-demo.txt
py scripts/load_demo_data.py
```

The loader defaults to `data/cypher/`, runs the numbered Cypher files in order, and skips the supplemental guide text. Wait for `Done.` before starting the app. The importer does not clear the graph; load into an empty graph for the initial setup. If the dataset has already been loaded, do not run the full import again.

macOS/Linux equivalent:

```bash
python3 -m pip install -r requirements-demo.txt
python3 scripts/load_demo_data.py
```

### 3. Configure AI chat (optional)

The graph APIs and frontend can run without an AI key, but chat requires an API key and is disabled by default. Copy `.env.example` to `.env` in the project root, then edit `.env`:

```powershell
Copy-Item .env.example .env
```

Set `AGENT_ENABLED=true` and configure one chat provider:

```dotenv
AGENT_ENABLED=true
AI_PROVIDER=auto
GEMINI_API_KEY=your_gemini_key
OPENAI_API_KEY=your_openai_key
```

Use the key for the provider you want. With `AI_PROVIDER=auto`, Gemini is selected when its key is set; otherwise OpenAI is selected when its key is set. To choose explicitly, set `AI_PROVIDER=google-genai` or `AI_PROVIDER=openai`. If you use Gemini for chat, public ATM/branch web search still requires `OPENAI_API_KEY` in this project. Leave keys blank if you only want to explore the graph without chat. Keep `.env` private; it is ignored by Git.

### 4. Start the Spring Boot backend

In terminal 3, at the project root:

```powershell
mvn spring-boot:run
```

Wait for the startup log. Verify these URLs:

- `http://localhost:8080/api/health` — application status
- `http://localhost:8080/api/graph/health` — FalkorDB connection status

Default settings are port `8080`, FalkorDB at `redis://localhost:6379`, and graph name `customer_relationships`. Override them in `.env` if needed.

### 5. Start the React frontend

In terminal 4:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, look up a customer such as `C0001`, then use the chat panel. Vite proxies `/api` requests to the Spring Boot backend on port `8080`. Keep all four processes running while using the demo.

## Try it

With AI chat enabled, try:

> Summarize this customer's accounts and possible connections. Explain the evidence paths and show their synthetic merchant activity.

The chat endpoint accepts JSON, for example:

```http
POST http://localhost:8080/api/agent/chat
Content-Type: application/json

{"customerId":"C0001","message":"Summarize this customer's accounts and possible connections. Explain the evidence."}
```

## Included data

- `data/cypher/` contains the full numbered Cypher import files used by the demo.
- The graph includes 1,000 base customers and 30 additional demo customers (`C1001`–`C1030`).
- Supplemental demo files add card, loan, and bill records for the 30 additional customers.
- `23_demo_transactions.txt` adds 4,503 synthetic transactions across 1,501 accounts. The account holdings connect those activity records to all 1,030 customers.
- The transaction and supplemental financial records are synthetic. Shared synthetic merchant activity is for graph traversal demonstrations and is not evidence of real purchases, relationships, fraud, or customer behavior.
- Pipe-delimited examples directly under `data/` are reference fixtures; the loader executes the numbered Cypher files under `data/cypher/`.

To load only the transaction file, first ensure the customer/account files are already loaded, then run:

```powershell
py scripts/load_demo_data.py data/cypher/23_demo_transactions.txt
```

## Graph model and traversal

The imported data uses customer, account, address, phone, email, card, bill, loan, transaction, and merchant nodes. Key paths include:

```text
(Customer)-[:HOLDS {role}]->(Account)
(Customer)-[:LIVES_AT]->(Address)
(Customer)-[:HAS_PHONE]->(Phone)
(Customer)-[:HAS_EMAIL]->(Email)
(Account)-[:HAS_TRANSACTION]->(BankTransaction)-[:AT_MERCHANT]->(Merchant)
```

Supplemental financial relationships connect customers/accounts to their card products, bills, and loans. The application issues parameterized, read-only Cypher traversals; the model chooses among the Java graph tools and cannot send arbitrary Cypher. Shared address/contact or joint-account paths indicate possible links and do not prove family relationships. Activity on a joint account cannot be attributed to a single holder.

## API endpoints

- `GET /api/health`
- `GET /api/graph/health`
- `POST /api/customers/relationships` — customer accounts and possible connections
- `POST /api/customers/addresses`
- `POST /api/customers/phone-numbers`
- `POST /api/customers/email-addresses`
- `POST /api/customers/cards`
- `POST /api/customers/bills`
- `POST /api/customers/loans`
- `POST /api/agent/chat` — requires `AGENT_ENABLED=true` and a configured chat provider
- `POST /api/search/openai` — public web search through OpenAI; requires `OPENAI_API_KEY`

Customer endpoints use a JSON body, for example:

```json
{"customerId":"C0001"}
```

## Configuration

Project-root `.env` is optional for the graph-only app. `.env.example` lists the supported settings and defaults:

| Setting | Default | Purpose |
| --- | --- | --- |
| `FALKORDB_URL` | `redis://localhost:6379` | FalkorDB connection |
| `FALKORDB_GRAPH` | `customer_relationships` | Graph name |
| `SERVER_PORT` | `8080` | Spring Boot port |
| `AGENT_ENABLED` | `false` | Enable AI chat endpoints |
| `AI_PROVIDER` | `auto` | `auto`, `google-genai`, or `openai` |
| `GEMINI_API_KEY` | empty | Gemini chat key |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Gemini chat model |
| `OPENAI_API_KEY` | empty | OpenAI chat key when selected; required for web search |
| `OPENAI_MODEL` | `gpt-5-mini` | OpenAI chat model |

Provider selection checks whether a key is configured, not whether that provider has quota. A quota/rate-limit error is returned by the provider; there is no automatic fallback after such an error.

## Troubleshooting

- **Docker is not running:** start Docker Desktop, then rerun `docker compose up -d`.
- **FalkorDB connection refused:** check `docker compose ps` and confirm the backend is using `FALKORDB_URL=redis://localhost:6379`.
- **Customer not found:** ensure the dataset loader completed successfully and the backend graph name matches `FALKORDB_GRAPH`.
- **Chat endpoint returns 404:** set `AGENT_ENABLED=true` in `.env`, configure a provider key, and restart Spring Boot.
- **Chat reports a missing provider key:** check `AI_PROVIDER` and populate the matching key in `.env`.
- **ATM/branch web search fails:** `OPENAI_API_KEY` is required for the web-search feature even when Gemini handles chat; provider quota and web availability also apply.
- **Frontend cannot reach the backend:** start Spring Boot on port `8080`; Vite’s `/api` proxy points there.

## Project notes

- Build plan and initial schema: [BUILD_FLOW.md](BUILD_FLOW.md)
- Hackathon pitch and walkthrough notes: [DEMO_GUIDE.md](DEMO_GUIDE.md)
- The assistant’s answers are decision-support examples, not banking decisions. Do not use this demo with real customer data or for production decisions without appropriate security, access control, privacy, data-quality, and compliance work.
