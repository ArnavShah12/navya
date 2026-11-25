# UML and design diagrams

> Subject: Java | Deliverable: UML diagram | Data: real, drawn from the code in `src/main/java/com/navya`. Mermaid renders on GitHub.

## Classes

```mermaid
classDiagram
  class User {
    <<abstract>>
    +id +email +name +city +passwordHash
    +role() Role
    +badge() String
  }
  User <|-- Member
  User <|-- Admin

  class Listing {
    <<record>>
    id sellerId title pricePaise category
    condition size city description images status boostedUntil
  }
  class Order { <<record>> id userId listingId amountPaise status idempotencyKey }
  class Conversation { <<record>> id listingId buyerId sellerId }
  class Message { <<record>> id conversationId senderId body }
  class Report { <<record>> id listingId reporterId reason status }

  class NavyaException { +status() +code() }
  NavyaException <|-- InvalidInputException
  NavyaException <|-- NotFoundException
  NavyaException <|-- ForbiddenException
  NavyaException <|-- UnauthorizedException
  NavyaException <|-- PaymentFailedException
  NavyaException <|-- DuplicateEmailException
  NavyaException <|-- ListingNotAvailableException
  NavyaException <|-- SelfChatException
  NavyaException <|-- TooManyImagesException
  NavyaException <|-- RateLimitedException
  NavyaException <|-- PersistenceException

  class PricingRule { <<interface>> adjust(amount, now) }
  PricingRule <|.. WeekendDiscountRule
  PricingRule <|.. WholeRupeeRule
  PricingService o-- PricingRule

  class PaymentGateway { <<interface>> charge() status() }
  PaymentGateway <|.. MockGateway

  class FileStorage { <<interface>> put() get() exists() delete() }
  FileStorage <|.. LocalFileStorage
  FileStorage <|.. S3FileStorage

  class BoostService
  BoostService --> PricingService
  BoostService --> PaymentGateway
  BoostService --> OrderRepo
  ListingService --> ListingRepo
  ListingService --> FileStorage
  ChatService --> ChatRepo
  ChatService --> Notifier
  AuthService --> UserRepo
  ChatHub ..> ChatRepo : LISTEN navya_msg
  Api --> AppContext
  AppContext --> BoostService
  AppContext --> ListingService
  AppContext --> ChatService
  AppContext --> AuthService
```

## Data model

```mermaid
erDiagram
  USERS ||--o{ LISTINGS : sells
  LISTINGS ||--o{ LISTING_IMAGES : has
  USERS ||--o{ FAVOURITES : saves
  LISTINGS ||--o{ FAVOURITES : "saved in"
  LISTINGS ||--o{ CONVERSATIONS : "chat about"
  USERS ||--o{ CONVERSATIONS : "buyer or seller"
  CONVERSATIONS ||--o{ MESSAGES : contains
  LISTINGS ||--o{ REPORTS : "reported in"
  USERS ||--o{ ORDERS : pays
  LISTINGS ||--o{ ORDERS : "boosted by"
```

Constraints that matter: unique `lower(email)`, unique `(listing_id, buyer_id)` on conversations, `buyer_id <> seller_id`, `status` and `category` check lists, `price_paise` range, message length 1 to 2,000, unique `idempotency_key` on orders. Full schema: `src/main/resources/db/schema.sql`.

## Boost payment, with a slow bank

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as Api
  participant S as BoostService
  participant P as Pay thread pool
  participant G as MockGateway
  participant D as PostgreSQL
  participant R as Reaper thread
  B->>A: POST /api/listings/id/boost (token, key)
  A->>S: boost()
  S->>D: insert order pending (unique key)
  S->>P: charge(order id)
  P->>G: charge
  alt answers within 3 s
    G-->>S: success
    S->>D: pending to paid and extend boost (one transaction)
    A-->>B: 200 paid
  else bank is slow
    S-->>A: pending
    A-->>B: 202 pending
    loop every 30 s
      R->>S: reconcile()
      S->>G: status(order id)
      G-->>S: success
      S->>D: pending to paid and extend boost
    end
  end
```

## Live chat across two instances

```mermaid
sequenceDiagram
  participant Ben as Ben's browser
  participant A as Instance A
  participant D as PostgreSQL
  participant B as Instance B
  participant Anna as Anna's browser
  Anna->>B: WebSocket /ws/chat/id, first message {token}
  B-->>Anna: ready
  Ben->>A: POST message
  A->>D: insert message, pg_notify(navya_msg, chat id)
  D-->>B: notification (B runs LISTEN)
  B-->>Anna: {"type":"new"}
  Anna->>B: GET messages
```

## Threads

| Thread | Created in | Job |
|---|---|---|
| `qtp-*` (64 max) | Javalin | Request handling, bounded queue of 500 |
| `navya-pay-1..8` | `AppContext` | Payment calls with a 3 s timeout |
| `navya-reaper` | `AppContext` | Every 30 s: expire old listings, settle slow payments |
| `navya-notifier` | `Notifier` | Writes notices from a queue to `outbox.log` and the log |
| `navya-chat-listener` | `ChatHub` | Holds the `LISTEN` connection, pings WebSocket sessions |

## Deployment view

See `../5-Cloud-Computing/01-cloud-architecture-design.md`.
