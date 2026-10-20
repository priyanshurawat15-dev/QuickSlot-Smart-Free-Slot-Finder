CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id varchar(36) PRIMARY KEY,
    full_name varchar(150) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role varchar(24) NOT NULL CHECK (role IN ('STUDENT','FACULTY','CLUB_HEAD','COORDINATOR','HOD','ADMIN')),
    campus_id varchar(80) NOT NULL DEFAULT 'gehu-hill-dehradun',
    department varchar(80) NOT NULL DEFAULT 'CS',
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE login_sessions (
    token_hash varchar(64) PRIMARY KEY,
    user_id varchar(36) NOT NULL REFERENCES users(id),
    csrf_token varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL
);

CREATE TABLE login_attempts (
    id varchar(36) PRIMARY KEY,
    key varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_attempt_key ON login_attempts(key,created_at);

CREATE TABLE rooms (
    id varchar(36) PRIMARY KEY,
    code varchar(80) NOT NULL,
    kind varchar(24) NOT NULL CHECK(kind IN ('CLASSROOM','LT')),
    building varchar(100) NOT NULL,
    capacity integer NOT NULL CHECK(capacity > 0),
    campus_id varchar(80) NOT NULL DEFAULT 'gehu-hill-dehradun',
    department varchar(80) NOT NULL DEFAULT 'CS',
    active boolean NOT NULL DEFAULT true,
    UNIQUE(campus_id,building,code)
);

CREATE TABLE allocations (
    user_id varchar(36) NOT NULL REFERENCES users(id),
    room_id varchar(36) NOT NULL REFERENCES rooms(id),
    PRIMARY KEY(user_id,room_id)
);

CREATE TABLE booking_requests (
    id varchar(36) PRIMARY KEY,
    requester_id varchar(36) NOT NULL REFERENCES users(id),
    room_id varchar(36) NOT NULL REFERENCES rooms(id),
    date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    purpose varchar(500) NOT NULL,
    attendance integer NOT NULL CHECK(attendance > 0),
    status varchar(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','FORWARDED','APPROVED','REJECTED','WITHDRAWN','CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK(start_time < end_time)
);
CREATE INDEX request_room_date ON booking_requests(room_id,date);

CREATE TABLE decisions (
    id varchar(36) PRIMARY KEY,
    request_id varchar(36) NOT NULL REFERENCES booking_requests(id),
    actor_id varchar(36) NOT NULL REFERENCES users(id),
    action varchar(32) NOT NULL,
    reason varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX decision_request ON decisions(request_id,created_at);

CREATE TABLE occupancies (
    id varchar(36) PRIMARY KEY,
    room_id varchar(36) NOT NULL REFERENCES rooms(id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    kind varchar(24) NOT NULL,
    source_id varchar(36) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true,
    CHECK(starts_at < ends_at),
    CONSTRAINT no_overlapping_occupancies EXCLUDE USING gist
      (room_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(active)
);

CREATE TABLE audit_events (
    id varchar(36) PRIMARY KEY,
    actor_id varchar(36) NOT NULL REFERENCES users(id),
    action varchar(80) NOT NULL,
    entity_id varchar(36) NOT NULL,
    details jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL REFERENCES users(id),
    request_id varchar(36) NOT NULL REFERENCES booking_requests(id),
    message varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_records (
    user_id varchar(36) NOT NULL REFERENCES users(id),
    key varchar(160) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    request_id varchar(36) NOT NULL REFERENCES booking_requests(id),
    PRIMARY KEY(user_id,key)
);
