import { ArrowUpRight } from "lucide-react";
import { Link } from "react-router-dom";
import { Status } from "../../components/ui";
import type { Availability } from "../../lib/types";

interface RoomDirectoryProps {
  rooms: Availability["rooms"];
  date: string;
  start: string;
  end: string;
}

const roomStateClass: Record<Availability["rooms"][number]["state"], string> = {
  AVAILABLE: "room-card-available",
  OCCUPIED: "room-card-occupied",
  UNKNOWN: "room-card-unknown",
};

export function RoomDirectory({ rooms, date, start, end }: RoomDirectoryProps) {
  return (
    <div className="room-card-grid">
      {rooms.map((room) => (
        <Link
          className={`room-row room-card ${roomStateClass[room.state]}`}
          key={room.id}
          to={`/rooms/${room.id}?date=${date}&start_time=${start}&end_time=${end}`}
        >
          <div className="room-card-heading">
            <div>
              <span className="room-kind">
                {room.kind === "LT" ? "Lecture theatre" : "Classroom"}
              </span>
              <h3 className="room-number">
                {room.code} <span>({room.kind === "LT" ? "LT" : "CR"})</span>
              </h3>
            </div>
            {room.state !== "UNKNOWN" && <Status value={room.state} />}
          </div>
          <dl className="room-details">
            <div>
              <dt>Floor</dt>
              <dd>{room.floor || "Not confirmed"}</dd>
            </div>
            <div>
              <dt>Capacity</dt>
              <dd>
                {room.capacity === null
                  ? "Not confirmed"
                  : `${room.capacity} seats`}
              </dd>
            </div>
          </dl>
          <p className="room-reason">
            {room.state === "UNKNOWN"
              ? "Check the room in person before use."
              : room.reason}
          </p>
          <div className="room-card-footer">
            <span>View schedule</span>
            <ArrowUpRight size={16} />
          </div>
        </Link>
      ))}
    </div>
  );
}
