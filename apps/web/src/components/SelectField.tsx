import { useId, useRef, useState } from "react";
import * as Select from "@radix-ui/react-select";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import "../styles/select.css";

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

interface SelectFieldProps {
  options: readonly SelectOption[];
  value?: string;
  defaultValue?: string;
  onValueChange?(value: string): void;
  name?: string;
  required?: boolean;
  disabled?: boolean;
  id?: string;
  "aria-label"?: string;
  placeholder?: string;
  className?: string;
}

const emptyOption = "__quickslot_empty_option__";

export function SelectField({
  options,
  value,
  defaultValue = "",
  onValueChange,
  name,
  required,
  disabled,
  id,
  "aria-label": ariaLabel,
  placeholder,
  className = "",
}: SelectFieldProps) {
  const generatedId = useId();
  const [internalValue, setInternalValue] = useState(defaultValue);
  const [invalid, setInvalid] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const selected = value ?? internalValue;
  const emptyLabel = options.find((option) => option.value === "")?.label;

  return (
    <span
      className="select-field"
      onInvalid={(event) => {
        event.preventDefault();
        setInvalid(true);
        trigger.current?.focus();
      }}
    >
      <Select.Root
        value={selected}
        onValueChange={(next) => {
          const nextValue = next === emptyOption ? "" : next;
          if (value === undefined) setInternalValue(nextValue);
          setInvalid(false);
          onValueChange?.(nextValue);
        }}
        name={name}
        required={required}
        disabled={disabled}
      >
        <Select.Trigger
          ref={trigger}
          id={id || generatedId}
          aria-label={ariaLabel}
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? `${generatedId}-error` : undefined}
          className={`select-trigger ${className}`.trim()}
        >
          <Select.Value
            placeholder={placeholder || emptyLabel || "Choose an option"}
          />
          <Select.Icon className="select-chevron">
            <ChevronDown size={18} aria-hidden="true" />
          </Select.Icon>
        </Select.Trigger>
        <Select.Portal>
          <Select.Content
            className="select-content"
            position="popper"
            sideOffset={6}
            collisionPadding={12}
          >
            <Select.ScrollUpButton className="select-scroll">
              <ChevronUp size={16} aria-hidden="true" />
            </Select.ScrollUpButton>
            <Select.Viewport className="select-viewport">
              {options.map((option) => (
                <Select.Item
                  className="select-option"
                  key={option.value}
                  value={option.value || emptyOption}
                  disabled={option.disabled}
                  data-selected={option.value === selected ? "true" : undefined}
                  aria-selected={option.value === selected}
                >
                  <Select.ItemText>{option.label}</Select.ItemText>
                  {option.value === "" && selected === "" ? (
                    <Check
                      className="select-check"
                      size={17}
                      aria-hidden="true"
                    />
                  ) : (
                    <Select.ItemIndicator className="select-check">
                      <Check size={17} aria-hidden="true" />
                    </Select.ItemIndicator>
                  )}
                </Select.Item>
              ))}
            </Select.Viewport>
            <Select.ScrollDownButton className="select-scroll">
              <ChevronDown size={16} aria-hidden="true" />
            </Select.ScrollDownButton>
          </Select.Content>
        </Select.Portal>
      </Select.Root>
      {invalid && (
        <span className="select-error" id={`${generatedId}-error`} role="alert">
          Choose an option.
        </span>
      )}
    </span>
  );
}
