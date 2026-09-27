package ch.bbw.archictecturerefcard01;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Datentransferobjekt (DTO) für sämtliche Rückmeldungen der REST-Schnittstelle.
 *
 * <p>Jede Antwort besteht aus einem Umschlag (Text, Kanal, antwortende Server-Instanz,
 * Zeitstempel) und einer fachlichen Nutzlast im Feld {@code data}. Die Nutzlast-Typen
 * sind als statische innere Klassen definiert, damit die vorgegebene Projektstruktur
 * (genau eine Datei für das DTO) eingehalten wird.</p>
 *
 * <p>Beispiel einer serialisierten Antwort:</p>
 * <pre>
 * {
 *   "text": "Telemetrie-Schnappschuss 27.09.2026 16:11:05 UTC",
 *   "channel": "telemetry",
 *   "serverIp": "172.17.0.2",
 *   "timestamp": "2026-09-27T16:11:05.123Z",
 *   "data": [ { "id": "julian-date", "value": 2461311.17436, ... } ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    /** Menschenlesbare Beschreibung der Antwort. */
    private String text;

    /** Fachlicher Kanal: status, telemetry, orbits, lightcurve, cosmology oder error. */
    private String channel;

    /** IP-Adresse der Server-Instanz, welche die Anfrage beantwortet hat. */
    private String serverIp;

    /** Erstellungszeitpunkt der Antwort (UTC, ISO-8601). */
    private Instant timestamp;

    /** Fachliche Nutzlast der Antwort (Typ abhängig vom Kanal). */
    private Object data;

    /**
     * Kompatibilitätskonstruktor zur ursprünglichen Ref. Card, bei der eine
     * Nachricht nur aus einem Text bestand.
     *
     * @param text menschenlesbarer Nachrichtentext
     */
    public Message(String text) {
        this.text = text;
    }

    /**
     * Einzelner Mess- oder Rechenwert, z. B. die Sonnenwindgeschwindigkeit.
     * Wird von den Kanälen {@code telemetry}, {@code lightcurve} und {@code cosmology} genutzt.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Reading {
        /** Technischer, stabiler Schlüssel, z. B. "solar-wind-speed". */
        private String id;
        /** Fachliche Gruppe zur Darstellung, z. B. "weltraumwetter". */
        private String group;
        /** Anzeigename. */
        private String label;
        /** Zahlenwert in der angegebenen Einheit. */
        private double value;
        /** Einheit, z. B. "km/s". */
        private String unit;
        /** Empfohlene Anzahl Nachkommastellen für die Anzeige. */
        private int decimals;
        /** Untere Skalengrenze für Anzeigeinstrumente (optional). */
        private Double min;
        /** Obere Skalengrenze für Anzeigeinstrumente (optional). */
        private Double max;
        /** Herkunft des Werts: "berechnet", "Modell" oder "Literatur". */
        private String source;
        /** Erläuterung oder abgeleitete Zusatzinformation. */
        private String note;
    }

    /**
     * Zustand des Sonnensystems zu einem bestimmten Zeitpunkt (Kanal {@code orbits}).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrbitSnapshot {
        /** Julianisches Datum der Epoche. */
        private double julianDate;
        /** Epoche als UTC-Zeitpunkt. */
        private Instant epoch;
        /** Zeitversatz der Epoche gegenüber jetzt in Tagen. */
        private double dayOffset;
        /** Positionen und Bahndaten aller Planeten. */
        private List<Body> bodies;
    }

    /**
     * Heliozentrischer Zustand eines Planeten.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        /** Deutscher Name des Planeten. */
        private String name;
        /** Astronomisches Symbol. */
        private String symbol;
        /** Grosse Halbachse der Bahn in Astronomischen Einheiten (AE). */
        private double semiMajorAxisAu;
        /** Numerische Exzentrizität der Bahn. */
        private double eccentricity;
        /** Bahnneigung gegenüber der Ekliptik in Grad. */
        private double inclinationDeg;
        /** Heliozentrische ekliptikale Länge in Grad (0° = Frühlingspunkt). */
        private double longitudeDeg;
        /** Ekliptikale Koordinate x in AE. */
        private double x;
        /** Ekliptikale Koordinate y in AE. */
        private double y;
        /** Ekliptikale Koordinate z in AE. */
        private double z;
        /** Momentane Distanz zur Sonne in AE. */
        private double distanceSunAu;
        /** Momentane Distanz zur Erde in AE. */
        private double distanceEarthAu;
        /** Lichtlaufzeit zwischen Planet und Erde in Minuten. */
        private double lightTimeMinutes;
        /** Momentane Bahngeschwindigkeit (Vis-Viva-Gleichung) in km/s. */
        private double orbitalSpeedKms;
        /** Siderische Umlaufzeit in Tagen. */
        private double periodDays;
    }

    /**
     * Photometrische Lichtkurve eines Exoplaneten-Transits (Kanal {@code lightcurve}).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LightCurve {
        /** Bezeichnung des Planeten. */
        private String target;
        /** Inoffizieller Name des Planeten. */
        private String nickname;
        /** Beschreibung des Muttersterns. */
        private String host;
        /** Zeitraffer-Faktor (virtuelle Beobachtungszeit pro Echtzeit). */
        private double timeLapse;
        /** Zeitabstand zwischen zwei Messpunkten in virtuellen Minuten. */
        private double cadenceMinutes;
        /** Länge des gelieferten Beobachtungsfensters in virtuellen Stunden. */
        private double windowHours;
        /** Umlaufperiode in Stunden. */
        private double periodHours;
        /** Dauer des Transits (1. bis 4. Kontakt) in Stunden. */
        private double transitDurationHours;
        /** Aktuelle virtuelle Beobachtungszeit in Stunden. */
        private double nowHour;
        /** Aktuelle Bahnphase (0 = Transitmitte). */
        private double phase;
        /** Befindet sich der Planet gerade vor dem Stern? */
        private boolean inTransit;
        /** Virtuelle Stunden bis zum nächsten Transitbeginn. */
        private double nextIngressHours;
        /** Virtuelle Stunden bis zum Transitende (nur während eines Transits). */
        private Double egressInHours;
        /** Radienverhältnis Planet/Stern. */
        private double radiusRatio;
        /** Grosse Halbachse in Sternradien (a / R*). */
        private double scaledSemiMajorAxis;
        /** Bahnneigung in Grad. */
        private double inclinationDeg;
        /** Messpunkte, zeitlich aufsteigend sortiert. */
        private List<FluxSample> samples;
        /** Abgeleitete physikalische Parameter des Systems. */
        private List<Reading> parameters;
    }

    /**
     * Einzelner Messpunkt einer Lichtkurve.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FluxSample {
        /** Virtuelle Beobachtungszeit in Stunden. */
        private double t;
        /** Gemessener, normierter Fluss (inklusive Rauschen). */
        private double flux;
        /** Rauschfreier Modellfluss. */
        private double model;
    }
}
