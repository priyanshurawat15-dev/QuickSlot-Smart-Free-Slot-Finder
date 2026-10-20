import type { ReactNode } from "react";
import {
  AlertCircle,
  ArrowUpRight,
  Check,
  CircleHelp,
  LoaderCircle,
  X,
} from "lucide-react";
import { statusName } from "../lib/format";
export function Notice({
  children,
  tone = "info",
}: {
  children: ReactNode;
  tone?: "info" | "error" | "success";
}) {
  return (
    <div
      className={`notice notice-${tone}`}
      role={tone === "error" ? "alert" : "status"}
    >
      {tone === "error" ? (
        <AlertCircle size={18} />
      ) : tone === "success" ? (
        <Check size={18} />
      ) : (
        <CircleHelp size={18} />
      )}
      <div>{children}</div>
    </div>
  );
}
export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={20} />
      {label}
    </div>
  );
}
export function Status({ value }: { value: string }) {
  return (
    <span className={`status status-${value.toLowerCase()}`}>
      <span aria-hidden="true" />
      {statusName(value)}
    </span>
  );
}
export function PageHeading({
  title,
  text,
  action,
}: {
  title: string;
  text?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{title}</h1>
        {text && <p>{text}</p>}
      </div>
      {action}
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <CircleHelp size={28} />
      <h3>{title}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}
export function ExternalIcon() {
  return <ArrowUpRight size={17} aria-hidden="true" />;
}
export function ErrorText({ error }: { error: unknown }) {
  return (
    <Notice tone="error">
      {error instanceof Error ? error.message : String(error)}
    </Notice>
  );
}
export function CloseButton({ onClick }: { onClick(): void }) {
  return (
    <button
      type="button"
      className="icon-button"
      aria-label="Close"
      onClick={onClick}
    >
      <X size={20} />
    </button>
  );
}
