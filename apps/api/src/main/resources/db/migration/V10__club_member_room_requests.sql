ALTER TABLE clubs ADD COLUMN active boolean NOT NULL DEFAULT true;

ALTER TABLE booking_requests
    ADD COLUMN reason_category varchar(32) NOT NULL DEFAULT 'OTHER'
        CHECK(reason_category IN ('EXTRA_CLASS','WORKSHOP','HACKATHON','CLUB_MEETING','OTHER')),
    ADD COLUMN club_id varchar(36) REFERENCES clubs(id),
    ADD COLUMN club_name varchar(150),
    ADD COLUMN club_membership_required boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT request_club_identity CHECK ((club_id IS NULL) = (club_name IS NULL)),
    ADD CONSTRAINT member_request_has_club CHECK (NOT club_membership_required OR club_id IS NOT NULL);
