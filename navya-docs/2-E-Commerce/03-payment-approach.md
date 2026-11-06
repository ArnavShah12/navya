# Payment approach

> Subject: E-Commerce | Deliverable: payment approach | Data: real for the test-mode behaviour, a plan for live payments

## Now: test mode only

The brief allows gateways in test mode only. `PaymentGateway` is an interface with one implementation, `MockGateway`. The card token decides the outcome:

| Token | Result |
|---|---|
| `tok_success` | Approved |
| `tok_declined` | Declined (HTTP 402, order marked failed) |
| `tok_timeout` | Bank is slow: order stays pending, then approves 5 seconds later |
| anything else | Declined |

No card number is typed anywhere, so the app never handles card data.

## Safety rules in the code

- **Idempotency key.** The browser sends a new key per attempt. The same key returns the same order, so a double click does not charge twice (tests TC-45, TC-50). A declined payment is final, the next try uses a new key.
- **Timeout.** The charge runs on a thread pool with a 3-second limit. After that the order is pending and `BoostService.reconcile()` asks the gateway again every 30 seconds.
- **One winner.** An order moves from pending to paid with a conditional update, so the boost is applied once even if two threads settle it.
- **Amounts in paise.** Whole numbers, no floating point.

## Later: live payments

Use a licensed gateway with hosted checkout (card details are typed on the gateway's page, not ours). Add a signed webhook so the gateway tells us the result, and keep `reconcile()` as the safety net. Taxes (GST on the fee) and invoices are not designed yet. The mock keeps results in memory, so a restart forgets pending payments: the reconcile step fails orders the gateway has never seen after 10 minutes.
