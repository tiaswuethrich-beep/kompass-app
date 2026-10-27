# Kompass

Persönliche Android-App für den Weg bis August 2027: Schlaf, Routinen, Gym, Snus-Ausstieg, Ordnung, Finanzen, Bildschirmzeit, Lesen.
Alles bleibt lokal auf dem Handy. Kein Konto, keine Cloud. Ins Internet geht Kompass nur für Kurse, die Ledger-Abfrage und (falls eingeschaltet) die KI.

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
