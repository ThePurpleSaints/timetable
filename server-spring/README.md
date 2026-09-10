# Timetable Server

Spring Boot backend for the Timetable Android app. It serves the timetable
dataset as a JSON API, exposes authenticated admin endpoints for editing it,
and tracks a SQLite database for runtime data (overrides, edits).

- Java 17, Spring Boot 4, SQLite (via `sqlite-jdbc`).
- Build with Gradle (`./gradlew bootJar` or `./gradlew run`).

## Config

| Env var                | Purpose                                                    | Default |
| -----------------------| ---------------------------------------------------------- | ------- |
| `PORT`                 | HTTP listen port                                          | `8003`  |
| `TIMETABLE_ADMIN_TOKEN`| Bearer token required by every admin endpoint             | `insecure-development-token` |
| `TIMETABLE_DATA_DIR`   | Directory containing `timetable.json` / `calender.json`   | repo `server/data` |
| `TIMETABLE_WEB_DIR`    | Static web build served at `/`                             | repo `server/webdist` |
| `TIMETABLE_SQLITE_FILE`| Path to the SQLite database file                          | `<dataDir>/timetable.db` |

If you're not inside the repo layout, set `TIMETABLE_DATA_DIR` (required) —
otherwise the server exits at startup.

Always set `TIMETABLE_ADMIN_TOKEN` to something real on any public deployment.

## Dataset

The data model is driven entirely by JSON, not hardcoded day/period counts:

- `server/data/timetable.json` — sections, weekly timetables, breaks
  (`period == 0` marks a break), courses. Top level also carries
  `timetableId`, e.g. `TT-ODD-26-27-Y2-CSE`, and plain-language fields
  (`academicYear`, `department`, `year`, `semester`).
- `server/data/calender.json` — academic calendar events (holidays, exams).
  On first start the server imports this into the SQLite
  `calendar_events` table, tagging each row with a schedule ID such as
  `CAL-ODD-26-27-Y2`; after that the database is the source of truth.
- `server/data/version_policy.json` — optional client sunset policy:

  ```json
  {
    "min_client_version": "4.2",
    "sunset_message": "This version of the app has reached the end of its life. Please update."
  }
  ```

  When present, `min_client_version` and `sunset_message` are included in the
  `meta` block of every response, so clients can warn/block themselves.

### Identifier scheme

Every dataset is keyed by an ID that encodes term, academic year, year of
study and department, so adding data scales by just adding a new dataset:

```
Timetable : TT-ODD-26-27-Y2-CSE
Calendar  : CAL-ODD-26-27-Y2
```

`ODD` = term starts near year's end (Aug–Jan), `EVE` = starts early in the year.
`Y2` = year of study, `CSE` = department.

## Integrity hash

`meta` responses include a deterministic hash of the dataset built via
canonical JSON serialization — the app uses it for fast change detection and
cache invalidation. Admin writes update the dataset and the hash together.

## Public API

All public responses without a body object include a `meta` block with
`version` (a deterministic hash of the dataset, via canonical JSON
serialization — the app uses it for fast change detection) and `updated_at`
(last dataset write). When a sunset policy is configured, `min_client_version`
and `sunset_message` are also present.

### `GET /api/v1/meta`
Sections available:
```json
{
  "meta": { ... },
  "section_count": 9,
  "section_ids": ["A", "B", "C", "..."]
}
```

### `GET /api/v1/timetable?class=A`
Single section's weekly timetable, plus the `meta` block.
Omitting `class` returns the whole dataset.

### `GET /api/v1/db/timetable?class=A`
The section's timetable surfaced from the SQLite store (mirrors admin edits)
together with its overrides.

### `GET /api/v1/calendar`
Academic calendar events with the `meta` block. Events come from the
`calendar_events` database table and carry `date`, `day`, `details`,
`holiday` and `scheduleId`.

### `GET /api/v1/overrides`
All one-off date overrides (cancelled classes, room changes, ...).

### `GET /timetable.json`
The raw dataset as plain JSON.

## Admin API

`/api/v1/admin/*` — every call requires
`Authorization: Bearer <TIMETABLE_ADMIN_TOKEN>`.

| Method | Path                   | Purpose                                |
| ------ | ---------------------- | -------------------------------------- |
| GET    | `/meta`                | `{"authenticated": bool}` no-throw check |
| PUT    | `/meta`                | Update dataset headers (timetableId, academicYear, department, semester, year) |
| GET    | `/editor`              | Full editing snapshot                  |
| PUT    | `/day`                 | Replace one day's cells for a section  |
| POST   | `/section`             | Add a section                          |
| PUT    | `/section/{sectionId}` | Update a section's fields (name, classroom, ...) |
| PUT    | `/cell`                | Update a cell at `ordinal`             |
| POST   | `/cell`                | Append a cell, returns new `ordinal`   |
| DELETE | `/cell`                | `?sectionId&day&ordinal`               |
| POST   | `/override`            | Upsert an override                     |
| DELETE | `/override/{overrideId}` | Delete an override                   |
| GET    | `/calendar`            | `{scheduleIds, events}` overview       |
| POST   | `/calendar`            | Replace all events for a `scheduleId`  |
| DELETE | `/calendar/{eventId}`  | Delete one calendar event              |

Admin writes are persisted to SQLite, then re-exported to the dataset JSON and
mirrored to the app asset files, so the on-device default stays in sync.

## Run

```sh
TIMETABLE_ADMIN_TOKEN=some-secret ./gradlew bootJar
java -jar build/libs/timetable-server-1.2.0.jar
```

Or run in place: `TIMETABLE_ADMIN_TOKEN=some-secret ./gradlew run`