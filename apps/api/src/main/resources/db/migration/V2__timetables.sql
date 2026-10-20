CREATE TABLE timetable_faculty (
    id varchar(80) PRIMARY KEY,
    campus_id varchar(80) NOT NULL,
    name varchar(150) NOT NULL
);
CREATE TABLE timetable_scopes (
    id varchar(36) PRIMARY KEY,
    campus_id varchar(80) NOT NULL,
    scope_key varchar(160) NOT NULL,
    UNIQUE (campus_id, scope_key)
);
CREATE TABLE timetable_revisions (
    id varchar(36) PRIMARY KEY,
    scope_id varchar(36) NOT NULL REFERENCES timetable_scopes(id),
    filename varchar(200) NOT NULL,
    sha256 varchar(64) NOT NULL,
    content bytea NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','REVIEWED','PUBLISHED')),
    version integer NOT NULL DEFAULT 0,
    valid_from date NOT NULL,
    valid_until date NOT NULL,
    sessions jsonb NOT NULL DEFAULT '[]',
    issues jsonb NOT NULL DEFAULT '[]',
    coverage_room_ids jsonb NOT NULL DEFAULT '[]',
    acknowledged boolean NOT NULL DEFAULT false,
    created_by varchar(36) NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    CHECK (valid_from <= valid_until)
);
CREATE INDEX timetable_revisions_scope ON timetable_revisions(scope_id);
CREATE TABLE timetable_publications (
    id varchar(36) PRIMARY KEY,
    scope_id varchar(36) NOT NULL REFERENCES timetable_scopes(id),
    revision_id varchar(36) NOT NULL REFERENCES timetable_revisions(id),
    valid_from date NOT NULL,
    valid_until date NOT NULL,
    CHECK (valid_from <= valid_until),
    EXCLUDE USING gist (scope_id WITH =, daterange(valid_from, valid_until, '[]') WITH &&)
);
CREATE INDEX timetable_publications_dates ON timetable_publications(valid_from, valid_until);
