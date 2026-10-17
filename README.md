# EventHub Booking Service

## Ownership and tenant authorization

Booking creation requires the global `CUSTOMER` role. A user can read a booking or its tickets only when the booking belongs to their verified `X-User-Id`; `PLATFORM_ADMIN` can administer all bookings.

Ticket validation requires `TENANT_ADMIN` or `EVENT_ORGANIZER` for the event's tenant. Booking Service verifies the ticket's event tenant with Event Service before marking the ticket used; it returns an error instead of validating when Event Service cannot be reached.

## Session and inventory model

Bookings and issued tickets carry a `sessionId`, with optional seat assignments persisted on the booking and ticket. Event Service owns the event's session, tier, capacity, and seat-map definitions. Booking Service is the single owner of mutable inventory: general-admission pools are keyed by event/session/tier, while numbered seats are keyed by event/session/seat and track `AVAILABLE`, `HELD`, or `SOLD`. Relational uniqueness constraints prevent duplicate inventory definitions.

Booking requests can optionally carry `attendeeData`, a list of one key/value map per ticket, and `seatIds` for numbered-seat tiers. Booking validates attendee count and seat selection cardinality, loads the published event definition from Event Service, and calculates the total from the server-side tier price. The submitted `totalPrice` must match that calculated price. Validation of attendee keys, required fields, and allowed values against Event Service's `registrationFields` is not active yet.

For legacy single-occurrence events without an explicit session, Booking uses `eventId` as the stable implicit session ID. On the first booking attempt for a tier, Booking initializes its general-admission pool or numbered seats idempotently from the published Event Service definition. Subsequent attempts verify that the existing capacity/seat map still matches; they never reset remaining, held, or sold counts.

Booking creates the pending booking and claims inventory in one database transaction. General-admission capacity is claimed with a conditional database update; numbered seats are held with conditional state changes in stable seat-ID order. Holds expire after 10 minutes and a scheduled worker releases inventory for expired pending bookings. A valid, timely payment changes held inventory to sold before tickets are issued. A payment approved after expiry, or for an unexpected amount, creates no tickets and changes the booking to `PAYMENT_REVIEW_REQUIRED`; because Payment Service has no refund operation yet, staff must reconcile/refund that transaction manually.

Booking creation still has no client idempotency key, and payment/event publishing is not backed by an outbox. Duplicate request suppression and reliable delivery are addressed in later phases.
