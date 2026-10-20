import { useEffect, useState } from "react";
import { MapPin, Search } from "lucide-react";
import { api } from "../lib/api";
import { campusToday, time } from "../lib/format";
import { useAuth } from "../lib/auth";
import { Link } from "react-router-dom";
import { SelectField } from "../components/SelectField";
import {
  Empty,
  ErrorText,
  Loading,
  Notice,
  PageHeading,
} from "../components/ui";
type Schedule = Awaited<ReturnType<typeof api.teacherSchedule>>;
export function Teachers() {
  const { user } = useAuth();
  const [teachers, setTeachers] = useState<{ id: string; name: string }[]>([]);
  const [teacher, setTeacher] = useState("");
  const [date, setDate] = useState(campusToday());
  const [query, setQuery] = useState("");
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (user) api.teachers().then(setTeachers).catch(setError);
  }, [user]);
  useEffect(() => {
    if (!teacher) return;
    let active = true;
    setLoading(true);
    setError(null);
    api
      .teacherSchedule(teacher, date)
      .then((result) => {
        if (active) setSchedule(result);
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [teacher, date]);
  return (
    <div className="container page">
      <PageHeading
        title="Faculty timetable"
        text="Look up scheduled teaching venues from published timetables."
      />
      <Notice>
        Scheduled teaching venues are not live locations. A gap between classes
        does not mean a teacher is available for a meeting.
      </Notice>
      {!user ? (
        <div className="empty">
          <h2>Sign in to view teaching schedules.</h2>
          <p>Room search remains open to everyone.</p>
          <Link to="/login?returnTo=%2Fteachers" className="button button-dark">
            Sign in
          </Link>
        </div>
      ) : (
        <>
          <div className="teacher-search">
            <label>
              <span>
                <Search size={16} />
                Find a teacher
              </span>
              <input
                value={query}
                placeholder="Search by name"
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <label>
              Teacher
              <SelectField
                aria-label="Teacher"
                value={teacher}
                onValueChange={setTeacher}
                options={[
                  { value: "", label: "Choose a teacher" },
                  ...teachers
                    .filter((person) =>
                      person.name.toLowerCase().includes(query.toLowerCase()),
                    )
                    .map((person) => ({
                      value: person.id,
                      label: person.name,
                    })),
                ]}
              />
            </label>
            <label>
              Date
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
              />
            </label>
          </div>
          {!!error && <ErrorText error={error} />}{" "}
          {loading ? (
            <Loading />
          ) : schedule ? (
            <>
              <h2>{schedule.teacher.name}</h2>
              {schedule.sessions.length ? (
                <div className="room-list">
                  {schedule.sessions.map((session, index) => (
                    <div className="teacher-session" key={index}>
                      <strong>
                        {time(session.start_time)}–{time(session.end_time)}
                      </strong>
                      <span>{session.subject}</span>
                      <span>
                        <MapPin size={16} />
                        {session.room_code
                          ? `Room ${session.room_code} · ${session.building || ""}`
                          : "Room unresolved"}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <Empty title="No teaching sessions in the published data for this date.">
                  This does not establish meeting availability or physical
                  location.
                </Empty>
              )}
            </>
          ) : (
            <Empty title="Start with a teacher and a date.">
              Only published, mapped teaching sessions will appear.
            </Empty>
          )}
        </>
      )}
    </div>
  );
}
