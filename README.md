# Kompass

Persönliche Android-App für den Weg bis August 2027: Schlaf, Routinen, Gym, Snus-Ausstieg, Ordnung, Finanzen, Bildschirmzeit, Lesen.
Alles bleibt lokal auf dem Handy. Kein Konto, keine Cloud. Einzige Ausnahme ist die optionale KI (siehe unten), die nur auf Knopfdruck mit Claude spricht.

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

## KI (optional, Claude)

Unter **Mehr › KI (Claude)** den eigenen API-Key von [console.anthropic.com](https://console.anthropic.com/settings/keys) eintragen. Ohne Key ist alles wie vorher.

- **Wo:** Coach-Chat (Tracker › Coach oder Heute), Tagesimpuls auf Heute, Morgen- und Abend-Check-in, Schlaf, Snus, Craving, Gym, Stimmung, Finanzen, Bildschirmzeit, Plan.
- **Günstig:** Standard ist Claude Haiku 5.5 mit Aufwand "low" und kurzen Antworten. Eine Antwort pro Tag und Bereich wird zwischengespeichert. Im Chat hilft der Prompt-Cache. Hartes Monatsbudget (Standard 1 Dollar) und Tageslimit.
- **Sicher:** Der Key ist nie im Code. Er wird mit dem Android-Keystore verschlüsselt, liegt in einer eigenen Datei und ist nicht Teil der Sicherung. Nur HTTPS mit System-Zertifikaten, nur an api.anthropic.com.
- **Privat:** Gesendet wird nur auf Knopfdruck und nur eine kurze Zusammenfassung der letzten 7 Tage aus den freigegebenen Bereichen. Nie der Name, nie Buchungstexte, Notizen nur mit Freigabe. "Vorschau" zeigt genau, was rausgeht.
- Code: `ai/ClaudeClient.kt` (SDK-Aufruf), `ai/AiStore.kt` (Key, Budget, Cache), `ai/AiContext.kt` (Datenzusammenfassung), `ai/Ai.kt` (Funktionen und Prompts), `ui/screens/Coach.kt` (Oberfläche).
