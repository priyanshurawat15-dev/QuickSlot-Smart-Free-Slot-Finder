import { send } from "./api";

export interface Club {
  id: string;
  slug: string;
  name: string;
  summary: string;
  description: string;
  email: string;
  instagram: string;
  linkedin: string;
  source_label: string;
  leadership: { name: string; role: string }[];
}

export type MembershipStatus = "NONE" | "PENDING" | "VERIFIED" | "REJECTED";

export interface ClubMembership {
  status: MembershipStatus;
  can_review: boolean;
}

export interface MemberApplication {
  id: string;
  student_name: string;
  status: MembershipStatus;
  created_at: string;
}

const clubPath = (slug: string) => `/clubs/${encodeURIComponent(slug)}`;

export const clubs = {
  list: (signal?: AbortSignal) => send<Club[]>("/clubs", { signal }),
  profile: (slug: string, signal?: AbortSignal) =>
    send<Club>(clubPath(slug), { signal }),
  membership: (slug: string) =>
    send<ClubMembership>(`${clubPath(slug)}/membership`),
  join: (slug: string) =>
    send<MemberApplication>(`${clubPath(slug)}/join`, { method: "POST" }),
  applications: (slug: string) =>
    send<MemberApplication[]>(`${clubPath(slug)}/memberships`),
  decide: (
    slug: string,
    id: string,
    action: "approve" | "reject",
    reason: string,
  ) =>
    send<MemberApplication>(
      `${clubPath(slug)}/memberships/${encodeURIComponent(id)}/${action}`,
      { method: "POST", body: JSON.stringify({ reason }) },
    ),
};
