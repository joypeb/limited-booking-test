CREATE TABLE resources (
    id uuid NOT NULL PRIMARY KEY,
    name varchar(100) NOT NULL,
    capacity integer NOT NULL,
    available integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_resources_capacity CHECK (capacity BETWEEN 0 AND 1000),
    CONSTRAINT ck_resources_available CHECK (available BETWEEN 0 AND capacity)
);
