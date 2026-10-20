# QuickSlot web

React and TypeScript interface for Hill Dehradun. It uses the API in `apps/api`; it does not simulate successful requests or approvals when that API is unavailable.

## Run locally

Start the API on `http://127.0.0.1:8081` using its README, then run from the repository root:

```powershell
npm ci --prefix apps/web
npm run dev --prefix apps/web
```

Open `http://127.0.0.1:5173`. Vite forwards `/api` to the local backend on port 8081. Set `API_PROXY_TARGET` in a local `.env` file to change this target. For production, serve the built SPA with history fallback and reverse-proxy `/api` on the same origin. Set secure cookies at the API. `npm run preview` is a static preview, not the production server or API proxy.

```powershell
npm run build --prefix apps/web
npm exec --prefix apps/web prettier -- --check "apps/web/src/**/*.{ts,tsx,css}"
```

If your local Node installation needs the Windows trust store, use `NODE_USE_SYSTEM_CA=1` in that shell; certificate verification stays enabled.

## Roles and demo

Public room search needs no login. Approved faculty and club-head accounts can request rooms. The coordinator can decide or forward to the HOD. The HOD can decide; administrators maintain timetable and room records. The API enforces these permissions independently of navigation.

The sign-in screen exposes explicit local seed accounts only in Vite development mode. Start the API with its demo profile and DEMO_SEED=true first. Production bundles omit the demonstration credential panel. There is no automatic demo login and no offline mutation fallback.

## Structure

- `src/pages`: route-level screens and local workflow state.
- `src/features/timetables`: source review table and faculty directory components.
- `src/components`: public/staff layout, whole-hour slot selector and small UI primitives.
- `src/lib`: typed HTTP client, session/capability context, data contracts and campus-time formatting.
- `src/styles`: theme tokens, glass layout, shared controls and responsive page grids.

Timetable review requires explicit room/faculty mappings and source acknowledgement. Coverage claims must include every course using a room. Dirty local edits must be saved before publication, and revision numbers prevent overwriting someone else's review. Original PDF/DOCX files can be downloaded from the authorized review screen. Prior published data remains historical when a scoped replacement takes effect.

All displayed availability follows published schedules; it is not live occupancy. Unknown coverage is visible, and students are reminded to check the actual room. Teacher lookup shows scheduled teaching venues only, never inferred meeting availability.

## Browser checks

Start the Java API with its explicit local demo seed before running API-backed browser checks. These tests exercise real endpoints and do not install fixture responses.

```powershell
npx --prefix apps/web playwright install chromium
npm run test:e2e --prefix apps/web
```

The suite checks public coverage notices, selected-room continuation through login, and denial of booking access for student accounts in desktop and mobile viewports.

The university wordmarks in `public/brand` come from the official GEHU websites. The interface uses the university’s vector emblem with live text on a charcoal glass header, compact status-colored room cards and Aarambh’s Outfit typeface. No official endorsement is claimed. Booking selection uses one-hour slots; timetable review retains printed minute timings.

Club directory and profiles are available at /clubs and /clubs/codev. Students sign in to request verified membership; assigned club heads and administrators can review applications. Blue text and tinted blue panels are excluded from the theme. Membership stays separate from booking permission.
