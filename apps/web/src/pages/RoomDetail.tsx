import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, MapPin, Users } from "lucide-react";
import { api } from "../lib/api";
import type { RoomSchedule } from "../lib/types";
import { campusToday, displayDate, time } from "../lib/format";
import { canRequest, useAuth } from "../lib/auth";
import { ErrorText, Loading, Notice } from "../components/ui";
const days = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];
export function RoomDetail() {
  const { id = "" } = useParams();
  const [params] = useSearchParams();
  const { user } = useAuth();
  const date = params.get("date") || campusToday();
  const [data, setData] = useState<RoomSchedule | null>(null);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    let active = true;
    api
      .schedule(id, date)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(e);
      });
    return () => {
      active = false;
    };
  }, [id, date]);
  const requestUrl = `/requests/new?room_id=${id}&date=${date}&start_time=${params.get("start_time") || "10:00"}&end_time=${params.get("end_time") || "11:00"}`;
  return (
    <div className="container page">
      <Link to={`/?${params}`} className="back-link">
        <ArrowLeft size={16} /> Back to rooms
      </Link>
      {error ? (
        <ErrorText error={error} />
      ) : !data ? (
        <Loading />
      ) : (
        <>
          <div className="room-detail-top">
            <div>
              <div className="campus-label">Classrooms & lecture theatres</div>
              <h1>
                Room {data.room.code}
                <span className="red-dot">.</span>
              </h1>
              <p>
                <MapPin size={17} />
                {data.room.floor || "Floor not confirmed"}
                {data.room.building ? ` · ${data.room.building}` : ""}
                <span>
                  <Users size={17} />
                  {data.room.capacity === null
                    ? "Capacity not recorded"
                    : `${data.room.capacity} seats`}
                </span>
              </p>
            </div>
            {(!user || canRequest(user.role)) && (
              <Link className="button button-primary" to={requestUrl}>
                Request this room <ArrowUpRight size={18} />
              </Link>
            )}
          </div>
          <Notice>
            Check the room in person before sitting. Last-minute classes or
            events may not appear in the timetable.
          </Notice>
          <div className="schedule-title">
            <h2>Weekly schedule</h2>
            <span>Week containing {displayDate(date)} · IST</span>
          </div>
          <div className="week-grid">
            {days.map((day, index) => {
              const sessions = data.sessions
                .filter((session) => session.day_of_week === index)
                .sort((a, b) => a.start_time.localeCompare(b.start_time));
              return (
                <section className="week-day" key={day}>
                  <h3>{day}</h3>
                  {sessions.length ? (
                    sessions.map((session, i) => (
                      <div
                        className="schedule-event"
                        key={`${i}-${session.start_time}`}
                      >
                        <span>
                          {time(session.start_time)}–{time(session.end_time)}
                        </span>
                        <strong>{session.title}</strong>
                        {session.source_label && (
                          <small>{session.source_label}</small>
                        )}
                      </div>
                    ))
                  ) : (
                    <p className="no-session">
                      No sessions in the supplied schedule.
                    </p>
                  )}
                </section>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
