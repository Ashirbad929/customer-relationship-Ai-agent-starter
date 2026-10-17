# Customer Relationship Agent — Build Flow

## Goal

Build a Java application that uses FalkorDB to explore customer, household, and account relationships. An agent answers questions by calling backend tools that query the graph, then explains the evidence behind its answer and any service suggestion.

For the initial demo, service suggestions are informational and require human review. Shared addresses or contact details indicate possible connections, not confirmed family relationships.

## Suggested repository layout

```text
customer-relationship-agent/
├── backend/                 # Java + Spring Boot API, graph access, agent
├── frontend/                # Add the web UI after the backend flow works
├── data/                    # Input dataset or synthetic/demo data
├── docker-compose.yml       # Local FalkorDB service
└── README.md
```

## Build steps

### 1. Create the repository and Java backend

- Create the repository with `backend/` as a Spring Boot project.
- Use a current supported Java LTS version and Maven or Gradle.
- Add configuration through environment variables; do not commit credentials.
- Create a health endpoint and a clear README with local run instructions.

**Done when:** the backend starts locally and exposes a health endpoint.

### 2. Run FalkorDB locally

- Add FalkorDB to `docker-compose.yml` with a persistent local volume.
- Configure the backend to connect using environment-based host, port, and password settings.
- Keep database credentials out of source control.

**Done when:** the backend can connect to FalkorDB and select a named graph.

### 3. Define the graph model

Start with these nodes:

- `Customer`: `customer_id`, name, date of birth, gender
- `Address`: `address_id`, street, unit, city, postal code
- `Account`: `account_id`, account type, opened date
- `Phone`: normalized phone value
- `Email`: normalized email value

Start with these relationships:

- `(Customer)-[:LIVES_AT]->(Address)`
- `(Customer)-[:HOLDS {role}]->(Account)`
- `(Customer)-[:HAS_PHONE]->(Phone)`
- `(Customer)-[:HAS_EMAIL]->(Email)`

Preserve source IDs. Normalize phone numbers and emails consistently, and avoid placing unnecessary personal data in logs or demo responses.

**Done when:** the model is documented and IDs/relationship directions are consistent.

### 4. Import the supplied data

- Implement an idempotent Java import command or startup-independent admin command.
- Import nodes before relationships, then connect them using stable IDs.
- Load the duplicate holds file only once.
- Report counts and rejected rows without printing full personal records.
- Keep the initial demo data local or use synthetic data in any public deployment.

**Done when:** re-running the import does not create duplicate graph entities, and expected node/edge counts can be inspected.

### 5. Implement graph queries in the backend

Create a graph repository/service layer with parameterized queries for:

- Looking up a customer by ID.
- Listing their accounts and account-holder roles.
- Finding other customers who share an address, phone, or email.
- Returning the exact evidence and relationship path for each possible connection.

Keep the first traversal bounded (for example, one or two relationship hops) and return only fields needed by the answer.

**Done when:** an API call for a customer returns linked customers, accounts, evidence types, and paths in a structured response.

### 6. Add transparent household-link scoring

- Keep evidence separate: shared address, shared phone, and shared email should be distinguishable.
- If you use a score, make it a simple, documented heuristic for the demo—not a claim of verified kinship.
- Include caveats such as shared addresses also belonging to roommates or businesses.
- Do not infer family from demographic similarity alone.

**Done when:** every suggested connection includes its evidence and a clear uncertainty label.

### 7. Add the agent and its tools

Connect a Java agent framework and an LLM provider after the graph queries work. Expose narrow, typed backend tools such as:

- `getCustomerSummary(customerId)`
- `findPossibleHouseholdConnections(customerId)`
- `getCustomerAccounts(customerId)`
- `suggestRelevantServices(customerId, retrievedEvidence)`

The agent should retrieve facts through these tools, never invent missing graph facts, and explain which evidence supports each answer. Use deterministic demo rules for service suggestions; keep suggestions subject to staff review. Do not give the model unrestricted database credentials or arbitrary query execution.

**Done when:** a natural-language question invokes the appropriate tools and the response cites returned graph evidence.

### 8. Expose a stable API

- Add endpoints for customer lookup, relationship exploration, and agent chat.
- Use request/response DTOs rather than returning database-specific objects.
- Handle unknown customer IDs, empty results, and database/LLM failures clearly.
- Add basic request limits and safe error messages.

**Done when:** the backend can be used without the frontend, for example through an API client.

### 9. Build the frontend in the same repository

After the backend flow works, add a frontend under `frontend/` with:

- Customer search or selection.
- A question/chat panel.
- A relationship graph or readable path list.
- Account types and holder roles.
- Evidence labels, uncertainty, and service suggestion rationale.

**Done when:** a demo user can select a customer, ask a question, and inspect the graph evidence behind the answer.

### 10. Prepare the hackathon demo

- Use a scripted scenario with a known customer and expected relationship paths.
- Show the FalkorDB graph doing the relationship lookup, not only storing data.
- Demonstrate an answer with its path/evidence and a cautious service suggestion.
- Document setup, environment variables, import steps, and a short demo walkthrough.

**Done when:** another person can follow the README and run the demo locally.

## End-to-end flow

```text
Question from user
  → Java API
  → Agent selects a narrow tool
  → Tool runs a parameterized FalkorDB query
  → Backend returns relationships plus evidence
  → Agent explains the result and suggests an optional service
  → UI displays the answer, path, and uncertainty
```

## Initial scope

Build in this order: **Spring Boot skeleton → local FalkorDB → import → relationship queries → agent tools → API → frontend**. Keep the first version read-only and human-reviewed; adding automated product decisions or actions is outside the initial demo scope.
