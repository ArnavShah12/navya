# Decision tables

> Subject: Software Testing | Deliverable: decision-table test design | Data: real, each rule is one automated test

## 1. Boost price (TC-07, TC-08)

Conditions: is it Saturday or Sunday in India (IST)? Rules apply in order: weekend discount, then round to whole rupee.

| Rule | Weekend (IST) | Base fee | After discount | After rounding | Test |
|---|---|---|---|---|---|
| R1 | No (Wednesday 06:00 UTC) | 4,900 paise | 4,900 | 4,900 | TC-07 |
| R2 | Yes (Saturday 06:00 UTC) | 4,900 | 3,920 | 3,900 | TC-08 |
| R3 | Yes (Friday 20:00 UTC, which is Saturday 01:30 IST) | 4,900 | 3,920 | 3,900 | TC-08 |
| R4 | No (Sunday 20:00 UTC, which is Monday 01:30 IST) | 4,900 | 4,900 | 4,900 | TC-08 |

## 2. Boost payment (TC-43 to TC-47, TC-50)

| Rule | Caller owns listing | Listing available | Key already used | Gateway answer | Outcome | Test |
|---|---|---|---|---|---|---|
| R1 | No | any | any | n/a | ForbiddenException | TC-47 |
| R2 | Yes | No (sold) | No | n/a | ListingNotAvailableException | TC-47 |
| R3 | Yes | Yes | blank key | n/a | InvalidInputException | TC-47 |
| R4 | Yes | Yes | Yes (same user) | n/a | Returns the first order, no new charge | TC-45, TC-50 |
| R5 | Yes | Yes | No | Success in time | Order paid, boost applied once | TC-43 |
| R6 | Yes | Yes | No | Declined | PaymentFailedException, order failed, nothing pinned | TC-44 |
| R7 | Yes | Yes | No | No answer in 3 s | Order pending, answer 202 | TC-46, TC-55 |
| R8 | Yes | Yes | pending order | Later success | Reaper marks paid and boosts once | TC-46 |

## 3. Who may do what to a listing (TC-27 to TC-29, TC-52, TC-57)

| Action | Visitor | Other member | Seller | Admin |
|---|---|---|---|---|
| View live listing | Yes | Yes | Yes | Yes |
| View removed or expired listing | No (404) | No (404) | Yes | Yes |
| Edit | 401 | 403 | Yes (if available or sold) | 403 |
| Mark sold or available | 401 | 403 | Yes | 403 |
| Delete | 401 | 403 | Yes | Yes (audited) |
| Boost | 401 | 403 | Yes | 403 |
| Report | 401 | Yes | No | Yes |
| Take down through a report | 401 | 403 | 403 | Yes |

## 4. Start a chat (TC-36, TC-37)

| Rule | Logged in | Own listing | Listing available | Existing chat | Outcome |
|---|---|---|---|---|---|
| R1 | No | any | any | any | 401 |
| R2 | Yes | Yes | any | any | SelfChatException |
| R3 | Yes | No | No | any | ListingNotAvailableException |
| R4 | Yes | No | Yes | No | New chat |
| R5 | Yes | No | Yes | Yes | The existing chat |
