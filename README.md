# EventHub Booking Service

## Ownership and tenant authorization

Booking creation requires the global `CUSTOMER` role. A user can read a booking or its tickets only when the booking belongs to their verified `X-User-Id`; `PLATFORM_ADMIN` can administer all bookings.

Ticket validation requires `TENANT_ADMIN` or `EVENT_ORGANIZER` for the event's tenant. Booking Service verifies the ticket's event tenant with Event Service before marking the ticket used; it returns an error instead of validating when Event Service cannot be reached.

## Session and inventory model

Bookings and issued tickets carry a `sessionId`, with optional seat assignments persisted on the booking and ticket. Event Service owns the event's session, tier, capacity, and seat-map definitions. Booking Service is the single owner of mutable inventory: general-admission pools are keyed by event/session/tier, while numbered seats are keyed by event/session/seat and track `AVAILABLE`, `HELD`, or `SOLD`. Relational uniqueness constraints prevent duplicate inventory definitions.

Booking requests can optionally carry `attendeeData`, a list of one key/value map per ticket. Booking validates that the list matches the ticket quantity and persists it with the booking. Validation of keys, required fields, and allowed values against Event Service's `registrationFields` is not active yet.

For legacy single-occurrence events without an explicit session, the inventory initialization phase will use their `eventId` as the stable implicit session ID in Booking.

These inventory tables establish the authoritative persistence model. Loading the definitions into Booking and performing atomic holds, expiry, release, and payment-time transitions are the next phase; this phase does not yet make the create-booking endpoint claim or decrement stock.
