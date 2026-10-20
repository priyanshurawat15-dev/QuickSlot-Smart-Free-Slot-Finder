import { SelectField } from "./SelectField";

interface HourSlotSelectProps {
  start: string;
  end: string;
  onChange(start: string, end: string): void;
}

export function HourSlotSelect({ start, end, onChange }: HourSlotSelectProps) {
  const slots = Array.from({ length: 10 }, (_, index) => {
    const hour = index + 8;
    const from = `${String(hour).padStart(2, "0")}:00`;
    const until = `${String(hour + 1).padStart(2, "0")}:00`;
    return {
      from,
      until,
      label: `${String(hour).padStart(2, "0")}–${String(hour + 1).padStart(2, "0")}`,
    };
  });

  return (
    <label>
      Time slot
      <SelectField
        aria-label="Time slot"
        required
        value={`${start}-${end}`}
        onValueChange={(value) => {
          const [from, until] = value.split("-");
          onChange(from, until);
        }}
        options={slots.map((slot) => ({
          value: `${slot.from}-${slot.until}`,
          label: slot.label,
        }))}
      />
    </label>
  );
}
