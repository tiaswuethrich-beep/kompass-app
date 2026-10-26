# Kompass

Persönliche Android-App für den Weg bis August 2027: Schlaf, Routinen, Gym, Snus-Ausstieg, Ordnung, Finanzen, Bildschirmzeit, Lesen.
Alles bleibt lokal auf dem Handy. Kein Konto, keine Cloud.

## Installieren

1. Auf GitHub unter **Releases** die neueste `Kompass.apk` auf dem Handy herunterladen.
2. Öffnen. Android fragt einmal, ob der Browser Apps installieren darf: erlauben.
3. Kompass öffnen und dem Einstieg folgen.

Updates installierst du genauso, die Daten bleiben erhalten (gleicher Signaturschlüssel).

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine APK und veröffentlicht sie als Release.
Lokal: `./gradlew assembleRelease` (braucht Android SDK 35 und JDK 17).

## Aufbau

- `plan/PhasePlan.kt`: die sechs Phasen und alle Gewohnheiten mit Mindest- und Vollversion
- `plan/Engine.kt`: Schlafverschiebung, Snus-Abbauplan, automatisches Abhaken, Phasen-Freischaltung
- `alarm/`: Wecker (AlarmManager), Erinnerungen, Benachrichtigungs-Aktionen
- `util/ScreenTime.kt`: Bildschirmzeit über den Nutzungszugriff
- `util/Io.kt`: Kalender, CSV-Import aus dem E-Banking, Sicherung
- `ui/screens/`: alle Bildschirme (Jetpack Compose)
