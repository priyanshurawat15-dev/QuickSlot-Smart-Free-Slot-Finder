import { useEffect, useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { api } from "../lib/api";
import type { Room } from "../lib/types";
import { ErrorText, Loading, Notice, PageHeading } from "../components/ui";
import { SelectField } from "../components/SelectField";
export function RoomRegister() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [show, setShow] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState("");
  useEffect(() => {
    api
      .rooms()
      .then(setRooms)
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);
  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const details = {
        building: String(data.get("building")),
        capacity: data.get("capacity") ? Number(data.get("capacity")) : null,
        floor: String(data.get("floor") || "") || null,
      };
      const room = editing
        ? await api.updateRoomDetails(editing.id, details)
        : await api.createRoom({
            code: String(data.get("code")),
            kind: String(data.get("kind")),
            building: String(data.get("building")),
            capacity: Number(data.get("capacity")),
            floor: details.floor,
          });
      setRooms((current) =>
        editing
          ? current.map((item) => (item.id === room.id ? room : item))
          : [...current, room],
      );
      setShow(false);
      setSuccess(
        `Room ${room.code} ${editing ? "details updated" : "added to the register"}.`,
      );
      setEditing(null);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="container page">
      <PageHeading
        title="Room register."
        text="Maintain the room identities used to match timetables and requests. Room codes keep their leading zeroes."
        action={
          <button
            className="button button-primary"
            onClick={() => {
              setEditing(null);
              setShow(!show);
            }}
          >
            <Plus size={17} />
            Add a room
          </button>
        }
      />
      {!!error && <ErrorText error={error} />}{" "}
      {success && <Notice tone="success">{success}</Notice>}
      {show && (
        <form
          key={editing?.id || "new"}
          className="request-sheet stack-form inventory-form"
          onSubmit={create}
        >
          <h2>
            {editing
              ? `Verify room ${editing.code} details`
              : "Add a classroom or lecture theatre"}
          </h2>
          {editing && (
            <p>
              Enter only details confirmed by the university. Leave unknown
              fields blank.
            </p>
          )}
          <div className="form-columns">
            <label>
              Room code
              <input
                name="code"
                required
                maxLength={40}
                placeholder="e.g. 017"
                defaultValue={editing?.code}
                disabled={!!editing}
              />
            </label>
            <label>
              Building
              <input
                name="building"
                required={!editing}
                maxLength={100}
                defaultValue={editing?.building}
              />
            </label>
            <label>
              Capacity
              <input
                name="capacity"
                type="number"
                required={!editing}
                min={1}
                max={5000}
                defaultValue={editing?.capacity || ""}
              />
            </label>
            <label>
              Floor
              <input
                name="floor"
                maxLength={60}
                defaultValue={editing?.floor || ""}
                placeholder="Confirmed floor"
              />
            </label>
            <label>
              Type
              <SelectField
                aria-label="Type"
                name="kind"
                defaultValue={editing?.kind || "CLASSROOM"}
                disabled={!!editing}
                options={[
                  { value: "CLASSROOM", label: "Classroom" },
                  { value: "LT", label: "Lecture theatre" },
                ]}
              />
            </label>
          </div>
          <div className="form-actions">
            <button
              type="button"
              className="text-link"
              onClick={() => setShow(false)}
            >
              Cancel
            </button>
            <button className="button button-dark" disabled={busy}>
              {busy
                ? "Saving…"
                : editing
                  ? "Save verified details"
                  : "Add room"}
            </button>
          </div>
        </form>
      )}
      {loading ? (
        <Loading />
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Room</th>
                <th>Building</th>
                <th>Floor</th>
                <th>Type</th>
                <th>Capacity</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {rooms.map((room) => (
                <tr key={room.id}>
                  <td>
                    <strong>{room.code}</strong>
                  </td>
                  <td>{room.building || "—"}</td>
                  <td>{room.floor || "Not confirmed"}</td>
                  <td>
                    {room.kind === "LT" ? "Lecture theatre" : "Classroom"}
                  </td>
                  <td>
                    {room.capacity === null ? "—" : `${room.capacity} seats`}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="text-link"
                      onClick={() => {
                        setEditing(room);
                        setShow(true);
                      }}
                    >
                      Verify details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
