# FalkorDB datasets

## Executable Cypher imports

`cypher/` contains the numbered Cypher `.txt` imports used to load the graph. It includes the existing customer/account/address/contact data and supplemental card, loan, and bill records, plus a separate generated demo extension with 30 additional customers (`C1001`–`C1030`).

Run from the project root:

```powershell
python -m pip install -r requirements-demo.txt
python scripts/load_demo_data.py
```

The loader executes numbered Cypher files in order and skips the supplemental guide text. Most queries use `MERGE`, so the import can be rerun; the loader also skips identical duplicate query files. The 30 added customers are grouped three per synthetic address and joint savings account. Their individual accounts include 10 credit cards, 10 loans, and 10 savings accounts, with corresponding demo card, loan, and bill details. Demo financial values are illustrative only.

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
