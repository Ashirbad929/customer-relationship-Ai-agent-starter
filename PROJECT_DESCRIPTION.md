# Customer Relationship Explorer

## Project description

When a banker handles an inquiry about a customer, they may need to check several banking applications to understand the customer’s full relationship with the bank. Account details, loans, credit cards, pending bills, and related parties can be spread across separate systems. Moving between those systems takes time and makes it harder to see how the information fits together.

**Customer Relationship Explorer** brings those connections into one searchable view backed by FalkorDB. A banker can ask about a customer and see the accounts and services connected to them, the roles they hold on those accounts, and other customer or business relationships supported by the available records. As additional source data becomes available, the graph can include loans, outstanding credit-card bills, and other obligations.

The AI assistant turns a natural-language question into a lookup using approved graph tools. FalkorDB traverses the connected customer and account data; the assistant then summarizes the returned records and shows the evidence behind each answer. For example, a shared address can be shown as a possible connection, while a joint account can be shown as a recorded account relationship. The assistant should not present an inferred connection as a confirmed family relationship.

## The problem

Bank staff often need to assemble a customer’s relationship context from multiple systems. A question such as “What accounts does this customer have, who else is connected to those accounts, and are there loans or pending bills?” can require several searches and manual cross-checking.

## The solution

Provide a unified, graph-based customer relationship view that lets staff:

- Search for a customer and inspect their connected accounts and services.
- See the customer’s role on each account, such as primary or joint holder, when that information exists in the source data.
- Follow supported connections to other customers, businesses, addresses, contact details, and accounts.
- Review loan and pending-bill details when those data sources are integrated.
- Ask questions in chat and receive a concise summary with the graph evidence used to answer.

## Example question

> Show this customer’s accounts, the customer’s role on each account, and any supported connections to other customers. Include loan or pending-bill information if it is available.

## How it works

1. A staff member selects a customer or asks a question about one.
2. The AI assistant selects the appropriate read-only lookup tools.
3. The application runs parameterized graph queries against FalkorDB.
4. FalkorDB returns the connected records and relationship evidence.
5. The assistant summarizes only those results and identifies which connections are confirmed records versus possible links.
6. The application presents the answer alongside the customer’s relationship graph.

## Graph information

The graph can represent customers, organizations, accounts, loans, credit cards, bills, addresses, phone numbers, and email addresses. Relationships should carry useful context where available, such as account role, relationship type, source system, and observation date. This helps staff understand both what is connected and why it is connected.

## Expected value

- Reduce time spent switching between systems to understand a customer’s relationship with the bank.
- Make connected accounts and services easier to review together.
- Help staff answer customer inquiries with traceable supporting records.
- Provide a foundation for future service analysis using approved, relevant customer information.

## Initial scope and data limitations

The first version focuses on customer, account, address, contact, and hold data already available in the graph. Loan and pending credit-bill details should appear only after their source data has been added and linked. The app must clearly distinguish unavailable information from information showing that no record exists.

Shared demographic details can help surface a **possible connection**, but do not prove that customers are family members or belong to the same household. The assistant should explain the evidence and avoid making unsupported claims or decisions from inferred relationships.

