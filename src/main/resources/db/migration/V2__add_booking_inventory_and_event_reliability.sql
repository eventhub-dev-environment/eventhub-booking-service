DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'bookings'::regclass AND contype = 'p'
    ) THEN
        IF EXISTS (SELECT 1 FROM bookings WHERE id IS NULL)
                OR EXISTS (SELECT id FROM bookings GROUP BY id HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION 'Cannot add bookings primary key: id contains null or duplicate values';
        END IF;
        ALTER TABLE bookings ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'tickets'::regclass AND contype = 'p'
    ) THEN
        IF EXISTS (SELECT 1 FROM tickets WHERE id IS NULL)
                OR EXISTS (SELECT id FROM tickets GROUP BY id HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION 'Cannot add tickets primary key: id contains null or duplicate values';
        END IF;
        ALTER TABLE tickets ADD CONSTRAINT tickets_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM bookings
        WHERE total_price <> round(total_price, 2)
           OR total_price >= 100000000
           OR total_price <= -100000000
    ) THEN
        RAISE EXCEPTION 'Cannot constrain bookings.total_price to numeric(10,2): existing values exceed its precision or scale';
    END IF;
END $$;

ALTER TABLE bookings
    ADD COLUMN session_id varchar(255),
    ADD COLUMN payment_transaction_id varchar(255),
    ADD COLUMN inventory_held boolean;

ALTER TABLE bookings
    ALTER COLUMN total_price TYPE numeric(10,2)
    USING total_price::numeric(10,2);

ALTER TABLE tickets
    ADD COLUMN session_id varchar(255),
    ADD COLUMN seat_id varchar(255);

CREATE INDEX idx_booking_pending_expiry ON bookings (status, expires_at);

CREATE TABLE booking_attendee_data (
    booking_id varchar(255) NOT NULL,
    attendee_index integer NOT NULL,
    field_key varchar(255) NOT NULL,
    field_value varchar(255),
    CONSTRAINT fk_booking_attendee_data_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_booking_attendee_data_field
        UNIQUE (booking_id, attendee_index, field_key)
);

CREATE TABLE booking_seats (
    booking_id varchar(255) NOT NULL,
    seat_id varchar(255) NOT NULL,
    seat_index integer NOT NULL,
    CONSTRAINT fk_booking_seats_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_booking_seat
        UNIQUE (booking_id, seat_id)
);

CREATE TABLE inventory_pools (
    id varchar(255) PRIMARY KEY,
    event_id varchar(255) NOT NULL,
    session_id varchar(255) NOT NULL,
    ticket_tier_id varchar(255) NOT NULL,
    total_capacity integer NOT NULL,
    available_quantity integer NOT NULL,
    held_quantity integer NOT NULL,
    sold_quantity integer NOT NULL,
    version bigint,
    CONSTRAINT uk_inventory_pool_event_session_tier
        UNIQUE (event_id, session_id, ticket_tier_id)
);

CREATE TABLE inventory_seats (
    id varchar(255) PRIMARY KEY,
    event_id varchar(255) NOT NULL,
    session_id varchar(255) NOT NULL,
    ticket_tier_id varchar(255) NOT NULL,
    seat_id varchar(255) NOT NULL,
    section varchar(255),
    seat_row varchar(255),
    seat_number varchar(255),
    accessible boolean,
    state varchar(255) NOT NULL,
    booking_id varchar(255),
    hold_expires_at timestamp without time zone,
    CONSTRAINT uk_inventory_seat_event_session_seat
        UNIQUE (event_id, session_id, seat_id)
);

CREATE TABLE booking_outbox_events (
    id varchar(255) PRIMARY KEY,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id varchar(255) NOT NULL,
    event_type varchar(120) NOT NULL,
    exchange varchar(120) NOT NULL,
    routing_key varchar(120) NOT NULL,
    payload text NOT NULL,
    status varchar(20) NOT NULL,
    publish_attempts integer NOT NULL,
    created_at timestamp without time zone NOT NULL,
    published_at timestamp without time zone
);

CREATE INDEX idx_booking_outbox_status_created
    ON booking_outbox_events (status, created_at);

CREATE TABLE payment_event_inbox (
    event_id varchar(200) PRIMARY KEY,
    event_type varchar(120) NOT NULL,
    processed_at timestamp without time zone NOT NULL
);
