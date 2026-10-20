import "../styles/club-logo.css";

interface ClubLogoProps {
  name: string;
  src?: string;
  large?: boolean;
}

export function ClubLogo({ name, src, large = false }: ClubLogoProps) {
  return (
    <span className={`club-logo${large ? " club-logo-large" : ""}`}>
      {src ? (
        <img src={src} alt={`${name} logo`} />
      ) : (
        <span aria-label={`${name} initials`} role="img">
          {name
            .split(/\s+/)
            .map((word) => word[0])
            .slice(0, 2)
            .join("")}
        </span>
      )}
    </span>
  );
}
