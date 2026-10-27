# Kompass

Persönliche Android-App für den Weg bis August 2027: Schlaf, Routinen, Gym, Snus-Ausstieg, Ordnung, Finanzen, Bildschirmzeit, Lesen.
Alles bleibt lokal auf dem Handy. Kein Konto, keine Cloud. Ins Internet geht Kompass nur für Kurse und die Ledger-Abfrage im Portfolio sowie für die optionale KI (siehe unten), die nur auf Knopfdruck mit Claude spricht.

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
- `portfolio/`: Bitcoin (Adressen aus dem Ledger-xpub selbst berechnet, Abfrage über mempool.space oder eigenen Node), ETF-Kurse, Käufe, Sparpläne
- `bank/`: Kontoauszüge im Format camt.053/054 (XML/ZIP) und automatisches Erfassen aus Bank-Push-Nachrichten

## Portfolio und Bank: was geht, was nicht

- **Ledger:** Ledger Live hat keine Schnittstelle für andere Apps. Kompass nimmt darum den öffentlichen Konto-Schlüssel (xpub) aus Ledger Live, rechnet die Adressen auf dem Handy aus und fragt nur deren Guthaben ab. Damit kann niemand Geld bewegen. Der xpub liegt verschlüsselt im Android Keystore und ist nicht in der Sicherung.
- **ETFs (z. B. MSCI World):** Kurse über die öffentliche Yahoo-Finance-Schnittstelle (inoffiziell, kann sich ändern; dann Preis von Hand). Broker wie Swissquote, PostFinance, yuh oder neon bieten Privatkunden keine Schnittstelle, darum werden Käufe von Hand eingetragen.
- **Bank:** Open Banking in der Schweiz (bLink von SIX) ist nur für Banken und lizenzierte, vertraglich angebundene Fintechs offen, nicht für eine selbst gebaute App. Kompass nutzt darum zwei Wege ohne Login und ohne Drittanbieter: den Kontoauszug als camt.053 (genauer als CSV) und Push-Nachrichten der Bank-App, die direkt auf dem Handy gelesen werden.

## KI (optional, Claude)

Unter **Mehr › KI (Claude)** den eigenen API-Key von [console.anthropic.com](https://console.anthropic.com/settings/keys) eintragen. Ohne Key ist alles wie vorher.

- **Wo:** Coach-Chat (Tracker › Coach oder Heute), Tagesimpuls auf Heute, Morgen- und Abend-Check-in, Schlaf, Snus, Craving, Gym, Stimmung, Finanzen (Einschätzung und automatisches Kategorisieren), Bildschirmzeit, Plan, Bücher (Empfehlungen), Essen (Kalorien per Foto).
- **Modelle:** Alles läuft mit Haiku 5.5. Nur für den Coach-Chat lässt sich ein grösseres Modell wählen. Im Hintergrund läuft nie etwas.
- **Günstig:** Standard ist Claude Haiku 5.5 mit Aufwand "low" und kurzen Antworten. Eine Antwort pro Tag und Bereich wird zwischengespeichert. Im Chat hilft der Prompt-Cache. Hartes Monatsbudget (Standard 1 Dollar) und Tageslimit.
- **Sicher:** Der Key ist nie im Code. Er wird mit dem Android-Keystore verschlüsselt, liegt in einer eigenen Datei und ist nicht Teil der Sicherung. Nur HTTPS mit System-Zertifikaten, nur an api.anthropic.com.
- **Privat:** Gesendet wird nur auf Knopfdruck und nur eine kurze Zusammenfassung der letzten 7 Tage aus den freigegebenen Bereichen. Nie der Name, nie Buchungstexte, Notizen nur mit Freigabe. "Vorschau" zeigt genau, was rausgeht.
- Code: `ai/ClaudeClient.kt` (SDK-Aufruf), `ai/AiStore.kt` (Key, Budget, Cache), `ai/AiContext.kt` (Datenzusammenfassung), `ai/Ai.kt` (Funktionen und Prompts), `ai/Meals.kt` (Kalorien), `ai/FinanceAi.kt` (Kategorien mit gelernten Regeln), `ui/screens/Coach.kt` und `ui/screens/AiTools.kt` (Oberfläche).

### Kosten (Schätzung, Haiku 5.5: 0.10 / 0.50 Dollar pro Million Tokens)

| Aktion | Tokens ca. | Kosten pro Aufruf |
|---|---|---|
| KI-Karte (Impuls, Schlaf, Gym …) | 1500 rein, 400 raus | 0.00035 $ |
| Coach-Nachricht | 2500 rein (teils Cache), 500 raus | 0.0005 $ |
| Essen per Foto | 1200 rein, 250 raus | 0.00025 $ |
| Kategorisieren (bis 80 Händler) | 1000 rein, 1500 raus | 0.001 $ |

Viel Nutzung (5 Karten, 3 Mahlzeiten, 10 Chat-Nachrichten pro Tag): etwa 0.25 Dollar im Monat. Mit Sonnet im Chat etwa 3 Dollar, mit Opus etwa 6 Dollar.

## Kompass aus der App heraus anpassen

- **Mehr › Kompass anpassen:** Wunsch eintippen ("Termine ausblenden, alles grün, Feierabend 19:30"). Claude Haiku schlägt Änderungen aus einem festen Katalog vor (Einstellungen, Gewohnheiten, Farben, Textgrösse, Abschnitte auf Heute). Erst nach Bestätigung übernommen, mit Rückgängig.
- **App weiterentwickeln:** Grössere Wünsche (neue Funktionen) gehen als Issue mit Label `kompass-wunsch` an GitHub. `.github/workflows/wunsch.yml` lässt Claude Code (Sonnet 5.5, höchstens 40 Schritte) den Wunsch umsetzen, baut die App und schiebt nur bei erfolgreichem Build auf main. Dann startet der normale Release-Build, und Kompass zeigt "Update bereit".
- Einrichtung: Im Repository unter Settings › Secrets and variables › Actions den Claude-Key als `ANTHROPIC_API_KEY` speichern. In der App einen Fine-grained Token nur für dieses Repository eintragen (Issues: Read and write, Contents: Read-only). Er wird wie der Claude-Key mit dem Android-Keystore verschlüsselt.
- Nur Issues des Repository-Besitzers mit dem Label lösen die Automatik aus. `.github/` und der Signaturschlüssel sind für Claude gesperrt.
