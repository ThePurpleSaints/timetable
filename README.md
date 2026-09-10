[![Timetable](logo-url-here)](https://github.com/ThePurpleSaints/timetable)

# Timetable

A school timetable Android app with a companion backend.

- **Android app** — Compose-based, offline-first, live remote sync, class
  reminders, holiday calendar, admin editing. Lives in `app/`.
- **Backend** — Spring Boot server exposing a JSON API and admin endpoints,
  backed by SQLite. Lives in `server-spring/`.

> This README is a work in progress — edit freely.

## Repo layout

```
app/             Android app (Jetpack Compose, Kotlin)
server-spring/   Spring Boot API server (Java 17)
server/data/     Dataset files (timetable, calendar, version policy)
admin-editor/    Prototype tkinter admin editor for the server API
```

## App features

- Weekly schedule per class with Today / Calendar views
- Live status ("next period is X in 12 min"), offline fallback to bundled data
- Lights-out status card, class reminders, dark theme
- Admin editing of cells/overrides through the app
- Automatic update check against GitHub releases
- Sunset support: the server can flag old clients for an update

## Identifier scheme

Datasets are keyed by self-describing IDs so more departments/years/terms just
add new datasets — `TT-ODD-26-27-Y2-CSE` for timetables, `CAL-ODD-26-27-Y2`
for calendars. See `server-spring/README.md`.

## Building the app

Prerequisites: JDK 17+, Android SDK, Gradle (wrapper included).

```sh
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed release (see .env.example for keys)
```

The app fetches live data from a server you choose. Point it at your
deployment by adding to the git-ignored `local.properties`:

```properties
timetable.baseUrl=https://your-server.example
```

## Backend

See [`server-spring/README.md`](server-spring/README.md) for the API, config,
and how to run it.

## License

TODO