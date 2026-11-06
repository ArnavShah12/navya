# Store and platform flow

> Subject: E-Commerce | Deliverable: store or platform flow | Data: real, each step is covered by an automated test or the browser test

```mermaid
flowchart TD
  A[Visitor opens home page] --> B{Search or filter}
  B --> C[Open a listing]
  C --> D{Logged in?}
  D -- no --> L[Log in or sign up] --> C
  D -- yes --> E[Message seller]
  E --> F[Chat, live]
  F --> G[Meet in a public place, pay in person]
  G --> H[Seller marks the item sold]
  C --> R[Report] --> M[Navya team reviews] --> N{Action}
  N -- remove --> X[Listing taken down]
  N -- dismiss --> Y[Listing stays]
  S[Seller: Sell] --> P[Fill form, add photos] --> Q[Listing is live]
  Q --> K[Boost?] --> T[Pay with test card] --> U{Result}
  U -- approved --> V[Pinned 7 days]
  U -- declined --> W[Error shown, try again]
  U -- slow bank --> Z[Pending, finishes in the background]
```

## Pages

Home with search, chips, filters and paging. Listing detail with photos, seller card, save, message, report. Sell and edit form. My listings. Chats and chat screen. Seller profile. Boost and Orders. Reports (team only).

## Error paths that are handled

Sold listing (chat refused). Own listing (chat and report refused). Declined card. Slow bank (pending, then settled by the background thread). Expired login (sent back to log in). Photo that is not a JPEG or is too large. Fifth photo. Wrong owner on edit, delete, status, boost. Rate limits on login, sign-up, writes, uploads, messages and payments.
