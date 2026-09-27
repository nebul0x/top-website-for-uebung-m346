
# PARALLAX – Observatorium für dynamische Astrophysik
Architecture Ref. Card 01 · Standalone Spring Boot Application (Modul M346)

Eine Webanwendung zum Thema Weltraum und Astrophysik. Die Seite wird einmal mit Thymeleaf gerendert
und lädt anschliessend alle Messdaten asynchron über eine REST-Schnittstelle nach – ohne Neuladen.

Link zur Übersicht:<br/>
https://gitlab.com/bbwrl/m346-ref-card-overview

## Funktionen

| Abschnitt | Inhalt | Datenquelle |
|---|---|---|
| Hero | Rotierende Spiralgalaxie und Sternfeld (Canvas-Partikelsystem), schwebender Ringplanet mit Mond (CSS-Keyframes), Live-Uhr mit Julianischem Datum und Sternzeit Zürich | Browser |
| Live-Telemetrie | Julianisches Datum, Sternzeit, Distanz Erde–Sonne, Mondphase und -distanz, Sonnenwind, Magnetfeld, Kp-Index, Polarlichtgrenze | `GET /api/telemetry` alle 4 s |
| Planeten | Sonnensystem mit den aktuellen Planetenpositionen, Zeitreise ±10 Jahre, Zeitraffer | `GET /api/orbits?days=…` |
| Transit | Lichtkurve des Exoplaneten HD 209458 b im Zeitraffer 1 : 3000, Sichtlinie auf den Stern, Countdown | `GET /api/lightcurve` alle 2 s |
| Kosmologie | Rechner für eine Rotverschiebung z: Rückblickzeit, Entfernungen, Zeitstrahl, verschobene Spektrallinien | `GET /api/cosmology?z=…` |
| Instanz | Server-IP (wie in der ursprünglichen Ref. Card), Laufzeitdaten, Status aller Endpunkte | Thymeleaf + `GET /api` |

**Berechnet** sind: Zeitmasse, Erde–Sonne–Mond (Formeln nach J. Meeus), Planetenbahnen
(Keplerelemente des JPL, Lösung der Keplergleichung), Transitgeometrie (3. Keplersches Gesetz)
und Kosmologie (ΛCDM-Modell mit Planck-2018-Parametern, numerische Integration).<br/>
**Simuliert** sind: Weltraumwetter und das Messrauschen der Lichtkurve. Es werden keine Live-Daten
von Raumsonden abgerufen.

## Projektstruktur

```
pom.xml                                   Maven: Spring Boot Web, Thymeleaf, Lombok
mvnw, mvnw.cmd, .mvn/                     Maven Wrapper
src/main/java/ch/bbw/archictecturerefcard01/
    ArchitectureRefCard01Application.java Startklasse
    HomeController.java                   liefert die Hauptseite aus (GET /)
    RestAPIController.java                REST-Schnittstelle mit allen Berechnungen (GET /api/…)
    Message.java                          Datentransferobjekt für alle API-Antworten
src/main/resources/
    application.properties                Serverkonfiguration und Parameter der Photometrie
    templates/index.html                  Oberfläche mit eingebettetem CSS und JavaScript
```

## REST-Schnittstelle

Alle Antworten haben dasselbe Format (`Message`):

```json
{
  "text": "Telemetrie-Schnappschuss 27.09.2026 16:51:41 UTC",
  "channel": "telemetry",
  "serverIp": "172.17.0.2",
  "timestamp": "2026-09-27T16:51:41.391Z",
  "data": [ ... ]
}
```

| Endpunkt | Beschreibung |
|---|---|
| `GET /api` | Statusmeldung, Laufzeit und Liste der Endpunkte |
| `GET /api/telemetry` | 14 Mess- und Rechenwerte mit Einheit, Skala und Herkunft |
| `GET /api/orbits?days=0` | Positionen, Distanzen und Geschwindigkeiten der acht Planeten; `days` = Zeitversatz (±36 525) |
| `GET /api/lightcurve` | 900 Messpunkte der Transit-Lichtkurve und die abgeleiteten Planetenparameter |
| `GET /api/cosmology?z=1` | Entfernungen, Alter und Temperatur des Universums für die Rotverschiebung `z` (0.001 bis 1500) |

Ungültige Parameter (z. B. `?z=abc`) werden mit HTTP 400 und einer `Message` vom Kanal `error` beantwortet.

**Zustandslos:** Der Server hält keine Messdaten im Speicher. Das Rauschen wird deterministisch aus
der Uhrzeit berechnet. Mehrere Instanzen hinter einem Load Balancer liefern deshalb dieselben Werte,
und das Feld `serverIp` zeigt, welche Instanz geantwortet hat.

## Konfiguration

In `src/main/resources/application.properties`:

| Eigenschaft | Standard | Bedeutung |
|---|---|---|
| `server.port` | `${PORT:8080}` | HTTP-Port, per Umgebungsvariable `PORT` überschreibbar |
| `parallax.photometry.time-lapse` | `3000` | Zeitraffer der Lichtkurve |
| `parallax.photometry.cadence-minutes` | `6` | Abstand der Messpunkte (Beobachtungszeit) |
| `parallax.photometry.window-hours` | `90` | Länge des Beobachtungsfensters |
| `parallax.photometry.noise-ppm` | `1100` | Photometrisches Rauschen pro Messpunkt |

## Installation der benötigten Werkzeuge

Maven Tutorial for Beginners<br/>
https://www.simplilearn.com/tutorials/maven-tutorial

Installation von Maven<br/>
https://maven.apache.org/install.html

Apache Maven starten<br/>
https://maven.apache.org/run.html

Anleitung zur Installation von Maven und Java<br/>
https://www.digitalocean.com/community/tutorials/install-maven-mac-os

## Inbetriebnahme auf eigenem Computer

Projekt herunterladen<br/>
```git clone https://github.com/nebul0x/top-website-for-uebung-m346.git```
<br/>
```cd top-website-for-uebung-m346```

### Projekt bauen und starten
Die Ausführung der Befehle erfolgt im Projektordner

Builden mit Maven (oder ohne installiertes Maven mit dem Wrapper `./mvnw package` bzw. `mvnw.cmd package`)<br/>
```$ mvn package```

Das Projekt wird gebaut und die entsprechende Jar-Datei im Ordner Target erstellt (Artefakt).

Die erstellte Datei kann nun direkt mit Java gestartet werden.<br/>
```$ java -jar target/architecture-refcard-01-0.0.1-SNAPSHOT.jar```

Die App kann nun im Browser unter der URL http://localhost:8080 betrachtet werden.

### Inbetriebnahme mit Docker Container
folgt...
