ALTER TABLE rooms ALTER COLUMN capacity DROP NOT NULL;
ALTER TABLE rooms ADD COLUMN source_label varchar(240) NOT NULL DEFAULT '';
ALTER TABLE rooms DROP CONSTRAINT IF EXISTS rooms_campus_id_building_code_key;
ALTER TABLE rooms ADD CONSTRAINT room_identity UNIQUE(campus_id, building, code, kind);

CREATE TABLE room_sources (
    room_id varchar(36) NOT NULL REFERENCES rooms(id),
    filename varchar(120) NOT NULL,
    sha256 varchar(64) NOT NULL,
    printed_label varchar(80) NOT NULL,
    PRIMARY KEY (room_id, sha256)
);
