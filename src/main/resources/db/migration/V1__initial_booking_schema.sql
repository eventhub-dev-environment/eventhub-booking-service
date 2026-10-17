CREATE TABLE bookings (
    id varchar(255) PRIMARY KEY,
    event_id varchar(255) NOT NULL,
    ticket_tier_id varchar(255) NOT NULL,
    user_id varchar(255) NOT NULL,
    user_email varchar(255),
    quantity integer NOT NULL,
    total_price numeric NOT NULL,
    status varchar(255) NOT NULL,
    expires_at timestamp without time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone
);

CREATE TABLE tickets (
    id varchar(255) PRIMARY KEY,
    booking_id varchar(255) NOT NULL,
    event_id varchar(255) NOT NULL,
    ticket_tier_id varchar(255) NOT NULL,
    qr_code_hash varchar(255),
    status varchar(255) NOT NULL,
    issued_at timestamp without time zone NOT NULL
);
