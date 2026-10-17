# EventHub Booking Service

## Ownership and tenant authorization

Booking creation requires the global `CUSTOMER` role. A user can read a booking or its tickets only when the booking belongs to their verified `X-User-Id`; `PLATFORM_ADMIN` can administer all bookings.

Ticket validation requires `TENANT_ADMIN` or `EVENT_ORGANIZER` for the event's tenant. Booking Service verifies the ticket's event tenant with Event Service before marking the ticket used; it returns an error instead of validating when Event Service cannot be reached.
