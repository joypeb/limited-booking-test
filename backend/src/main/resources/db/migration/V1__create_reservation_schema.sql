CREATE TABLE resources (
    id uuid NOT NULL PRIMARY KEY,
    name varchar(100) NOT NULL,
    capacity integer NOT NULL,
    available integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_resources_capacity CHECK (capacity BETWEEN 0 AND 1000),
    CONSTRAINT ck_resources_available CHECK (available BETWEEN 0 AND capacity)
);

CREATE TABLE reservations (
    id uuid NOT NULL PRIMARY KEY,
    resource_id uuid NOT NULL CONSTRAINT fk_reservations_resource REFERENCES resources(id),
    user_id uuid NOT NULL,
    idempotency_key varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    cancelled_at timestamptz,
    CONSTRAINT ck_reservations_status_cancelled_at CHECK ((status = 'CONFIRMED' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)),
    CONSTRAINT uq_reservations_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_reservations_resource_id ON reservations(resource_id);
