# EventHub Booking Service

## Ownership and tenant authorization

Booking creation requires the global `CUSTOMER` role. A user can read a booking or its tickets only when the booking belongs to their verified `X-User-Id`; `PLATFORM_ADMIN` can administer all bookings.

Ticket validation requires `TENANT_ADMIN` or `EVENT_ORGANIZER` for the event's tenant. Booking Service verifies the ticket's event tenant with Event Service before marking the ticket used; it returns an error instead of validating when Event Service cannot be reached.

## Session and inventory model

Bookings and issued tickets carry a `sessionId`, with optional seat assignments persisted on the booking and ticket. Event Service owns the event's session, tier, capacity, and seat-map definitions. Booking Service is the single owner of mutable inventory: general-admission pools are keyed by event/session/tier, while numbered seats are keyed by event/session/seat and track `AVAILABLE`, `HELD`, or `SOLD`. Relational uniqueness constraints prevent duplicate inventory definitions.

Booking requests can optionally carry `attendeeData`, a list of one key/value map per ticket, and `seatIds` for numbered-seat tiers. Attendee fields are stored as normalized rows indexed by ticket and reassembled into the same list-of-maps API shape when bookings are read. Booking validates attendee count and seat selection cardinality, loads the published event definition from Event Service, and calculates the total from the server-side tier price. The submitted `totalPrice` must match that calculated price. Validation of attendee keys, required fields, and allowed values against Event Service's `registrationFields` is not active yet.

For legacy single-occurrence events without an explicit session, Booking uses `eventId` as the stable implicit session ID. On the first booking attempt for a tier, Booking initializes its general-admission pool or numbered seats idempotently from the published Event Service definition. Subsequent attempts verify that the existing capacity/seat map still matches; they never reset remaining, held, or sold counts.

Booking creates the pending booking and claims inventory in one database transaction. General-admission capacity is claimed with a conditional database update; numbered seats are held with conditional state changes in stable seat-ID order. Holds expire after 10 minutes and a scheduled worker releases inventory for expired pending bookings. A valid, timely payment in the configured `COP` currency changes held inventory to sold before tickets are issued. A rejected payment leaves the hold active until expiry so the customer can retry within the hold period. A payment approved after expiry, or for an unexpected amount/currency, creates no tickets and changes the booking to `PAYMENT_REVIEW_REQUIRED`; because Payment Service has no refund operation yet, staff must reconcile/refund that transaction manually.

Booking-created and booking-confirmed events are stored in a database outbox in the same transaction as the booking changes, then published by a scheduled relay after RabbitMQ confirms routing. Delivery remains at-least-once; payment messages carry a stable RabbitMQ message ID and Booking Service records processed IDs in an inbox in the same transaction as ticket issuance. Payment consumer failures retry up to four deliveries with exponential backoff before RabbitMQ routes the message to the corresponding DLQ. Failed outbox entries are marked `FAILED` after the configured attempt limit and require operational review. Database changes are versioned with Flyway; Hibernate validates the schema at startup and does not update it.

Booking creation still has no client idempotency key. Payments that arrive after expiry or require manual review still need staff reconciliation or refund; Payment Service does not yet expose a refund operation.

## Database migrations

Booking Service and Payment Service use Flyway migrations under `src/main/resources/db/migration`; Hibernate runs with `ddl-auto: validate` and does not change the schema. V1 describes the initial service tables currently present in Neon; V2 adds the columns, inventory, outbox, inbox, webhook receipt, indexes, and constraints required by the current entities. V2 checks for and adds missing primary keys on the initial tables. The Auth Service owns the separate `users` table and is not migrated by these services.

Existing Neon databases do not yet have Flyway history. Before deploying this change, back up each service database, verify its schema matches that service's V1 baseline, and run Flyway's `baseline` operation at version 1 from a protected operator environment. If a database already has all V2 objects from a previous Hibernate `update`, verify the complete V2 schema and baseline it at version 2 instead. Automatic baselining is disabled so an unexpected or incorrect database is not silently adopted. On an empty database, Flyway applies V1 and V2 normally. V2 checks initial IDs for nulls/duplicates, payment data for duplicate `booking_id` or non-null `(provider, transaction_id)` values, and monetary/string values that would exceed the current JPA precision or length. It aborts rather than truncate data or add inconsistent keys; resolve any conflicts without deleting payment records.
