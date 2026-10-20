ALTER TABLE rooms ADD COLUMN floor varchar(60);

UPDATE rooms SET floor = CASE
    WHEN code ~ '^0[0-9]{2}$' THEN 'Ground floor'
    WHEN code ~ '^1[0-9]{2}$' THEN 'First floor'
    WHEN code ~ '^2[0-9]{2}$' THEN 'Second floor'
    ELSE NULL
END
WHERE source_label = 'Provided CSE timetable';
