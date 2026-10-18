# FalkorDB datasets

## Executable Cypher imports

`cypher/` contains the numbered Cypher `.txt` imports used to load the graph. It includes the existing customer/account/address/contact data and supplemental card, loan, and bill records, plus a generated demo extension with 30 additional customers (`C1001`–`C1030`). The final import adds 60 synthetic account transactions for 20 demo customers and connects them to merchant nodes, enabling multi-hop customer → account → transaction → merchant analysis.

Run from the project root:

```powershell
python -m pip install -r requirements-demo.txt
python scripts/load_demo_data.py
```

The loader executes numbered Cypher files in order and skips the supplemental guide text. Most queries use `MERGE`, so the import can be rerun; the loader also skips identical duplicate query files. The 30 added customers are grouped three per synthetic address and joint savings account. Their individual accounts include 10 credit cards, 10 loans, and 10 savings accounts, with corresponding demo card, loan, and bill details. The transaction fixture creates three synthetic transactions for each demo customer with a credit-card or savings account; paired customers in each synthetic household share two demo merchants. Demo financial values and activity are illustrative only and must not be treated as real banking behavior.

If the included demo extension (`C1001`–`C1030`, files 13–22) is already in the graph and you only want to add transactions, load just the new file so earlier imports are not rerun:

```powershell
py scripts/load_demo_data.py data/cypher/23_demo_transactions.txt
```

For a fresh graph, use the full import command above; the transaction file links to the demo accounts created by the earlier imports.

To load a different existing dataset directory instead, pass its path:

```powershell
python scripts/load_demo_data.py "C:\path\to\cust-rel"
```

## Pipe-delimited fixtures

The pipe-delimited `.txt` files directly under `data/` are supplemental fixture examples, not executable Cypher, and are not consumed by this loader. A separate importer would be needed to parse those rows.

A suggested graph shape for these fixture records is:

```text
(Customer)-[:HAS_CARD {role, status, openedDate}]->(CreditCard)-[:OF_PRODUCT]->(CardProduct)
(CreditCard)-[:USES_ACCOUNT]->(Account)
(Customer)-[:HAS_LOAN {status, principal, outstanding, emi, nextDueDate}]->(Loan)
(CreditCard)-[:HAS_BILL]->(CardBill)
(Customer)-[:OWES]->(CardBill)
```

Keep each bill linked to its card/account and customer so queries can explain where the amount came from. Treat `PAST_DUE` and `OVERDUE` as distinct sample states; any real-bank interpretation should follow the bank's own definitions.
