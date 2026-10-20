CREATE TABLE clubs (
    id varchar(36) PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(150) NOT NULL,
    summary varchar(400) NOT NULL,
    description varchar(2000) NOT NULL,
    email varchar(255) NOT NULL,
    instagram varchar(500) NOT NULL,
    linkedin varchar(500) NOT NULL,
    campus_id varchar(80) NOT NULL,
    source_label varchar(300) NOT NULL
);

CREATE TABLE club_leadership (
    club_id varchar(36) NOT NULL REFERENCES clubs(id),
    position integer NOT NULL,
    name varchar(150) NOT NULL,
    role varchar(150) NOT NULL,
    PRIMARY KEY(club_id,position)
);

CREATE TABLE club_managers (
    club_id varchar(36) NOT NULL REFERENCES clubs(id),
    user_id varchar(36) NOT NULL REFERENCES users(id),
    PRIMARY KEY(club_id,user_id)
);

CREATE TABLE club_memberships (
    id varchar(36) PRIMARY KEY,
    club_id varchar(36) NOT NULL REFERENCES clubs(id),
    student_id varchar(36) NOT NULL REFERENCES users(id),
    status varchar(24) NOT NULL CHECK(status IN ('PENDING','VERIFIED','REJECTED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    reviewed_by varchar(36) REFERENCES users(id),
    reason varchar(500) NOT NULL DEFAULT '',
    UNIQUE(club_id,student_id)
);
CREATE INDEX club_membership_review ON club_memberships(club_id,status,created_at);

INSERT INTO clubs(id,slug,name,summary,description,email,instagram,linkedin,campus_id,source_label)
VALUES ('codev','codev','Co-Dev Club',
    'The club behind Aarambh, Graphic Era Hill University''s technical, sports and cultural festival.',
    'Co-Dev brings students together through Aarambh for technical challenges, sports and cultural events. The leadership listed here comes from the Aarambh 4.0 organizing committee for 2026–2027.',
    'codev@gehu.ac.in','https://www.instagram.com/codev.gehu',
    'https://www.linkedin.com/company/co-dev-club/','gehu-hill-dehradun',
    'Aarambh 4.0 organizing committee, 2026–2027');

INSERT INTO club_leadership(club_id,position,name,role) VALUES
('codev',1,'Priyanshu Negi','President'),
('codev',2,'Nupoor Pandey','Vice President'),
('codev',3,'Priyanshu Rawat','Chief Secretary'),
('codev',4,'Simran','Finance Head'),
('codev',5,'Sourav','Technical Head'),
('codev',6,'Pramod','Technical Co-Head'),
('codev',7,'Shekhar','Technical Co-Head'),
('codev',8,'Shubham','Management Head'),
('codev',9,'Prince','Management Co-Head'),
('codev',10,'Shivang','Management Co-Head'),
('codev',11,'Tripti','Designing Head'),
('codev',12,'Nitin Chauhan','Spokesperson'),
('codev',13,'Sarthak','Social Media Head'),
('codev',14,'Ishant','Advisor'),
('codev',15,'Sandeep','Advisor'),
('codev',16,'Mr. Sushant Chamoli','Faculty Mentor');

