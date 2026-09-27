package ch.bbw.archictecturerefcard01;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * REST-Schnittstelle des PARALLAX-Observatoriums für dynamische astrophysikalische Messdaten.
 *
 * <p>Endpunkte:</p>
 * <ul>
 *     <li>{@code GET /api} – Statusmeldung (wie in der ursprünglichen Ref. Card)</li>
 *     <li>{@code GET /api/telemetry} – Zeit, System Erde–Sonne–Mond und Weltraumwetter</li>
 *     <li>{@code GET /api/orbits?days=0} – heliozentrische Planetenpositionen aus Keplerbahnen</li>
 *     <li>{@code GET /api/lightcurve} – Transit-Photometrie des Exoplaneten HD 209458 b im Zeitraffer</li>
 *     <li>{@code GET /api/cosmology?z=1} – ΛCDM-Kosmologie-Rechner für eine Rotverschiebung z</li>
 * </ul>
 *
 * <p><b>Architektur:</b> Der Controller ist zustandslos. Jeder Wert wird bei jeder Anfrage
 * aus der aktuellen Uhrzeit berechnet. Simulierte Grössen verwenden deterministisches
 * Rauschen (Hash-Funktion statt Zufallsgenerator). Dadurch liefern mehrere Instanzen hinter
 * einem Load Balancer identische Messreihen, und die Anwendung lässt sich beliebig
 * horizontal skalieren.</p>
 *
 * <p><b>Herkunft der Werte:</b> "berechnet" = astronomische Formel (Meeus, JPL, Planck 2018),
 * "Modell" = physikalisch plausible Simulation, "Literatur" = veröffentlichter Messwert.
 * Es werden keine Live-Daten von Raumsonden abgerufen.</p>
 */
@RestController
@RequestMapping("/api")
public class RestAPIController {

    // ------------------------------------------------------------------
    // Physikalische und astronomische Konstanten (SI, IAU 2012/2015)
    // ------------------------------------------------------------------

    private static final double AU_KM = 149_597_870.7;
    private static final double AU_M = AU_KM * 1000.0;
    private static final double GM_SUN = 1.32712440018e20;          // m³/s²
    private static final double LIGHT_SECONDS_PER_AU = 499.004783836;
    private static final double SOLAR_RADIUS_M = 6.957e8;
    private static final double JUPITER_RADIUS_M = 7.1492e7;
    private static final double SPEED_OF_LIGHT_KMS = 299_792.458;
    private static final double J2000 = 2451545.0;
    private static final double DAYS_PER_CENTURY = 36525.0;
    private static final double SYNODIC_MONTH_DAYS = 29.530588853;
    private static final double CARRINGTON_ROTATION_DAYS = 27.2753;

    private static final String SRC_CALC = "berechnet";
    private static final String SRC_MODEL = "Modell";
    private static final String SRC_LITERATURE = "Literatur";

    private static final Locale SWISS = new Locale("de", "CH");
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'", SWISS)
            .withZone(ZoneOffset.UTC);

    // ------------------------------------------------------------------
    // Planetenbahnen: mittlere Keplerelemente zur Epoche J2000 und deren
    // Änderung pro julianischem Jahrhundert (E. M. Standish, JPL,
    // "Approximate Positions of the Planets", gültig 1800–2050).
    // Spalten: a [AE], e, I [°], L [°], Länge Perihel ϖ [°], Länge aufst. Knoten Ω [°]
    // ------------------------------------------------------------------

    private static final String[] PLANET_NAMES = {
            "Merkur", "Venus", "Erde", "Mars", "Jupiter", "Saturn", "Uranus", "Neptun"
    };

    private static final String[] PLANET_SYMBOLS = {
            "☿", "♀", "⊕", "♂", "♃", "♄", "♅", "♆"
    };

    private static final double[][] ELEMENTS = {
            {0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593},
            {0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255},
            {1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0},  // Erde-Mond-Schwerpunkt
            {1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891},
            {5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909},
            {9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448},
            {19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503},
            {30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574}
    };

    private static final double[][] RATES = {
            {0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081},
            {0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418},
            {0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0},
            {0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343},
            {-0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106},
            {-0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794},
            {-0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589},
            {0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664}
    };

    private static final int EARTH_INDEX = 2;

    /** Maximaler Zeitversatz für die Planetenpositionen: ±100 Jahre. */
    private static final double MAX_ORBIT_OFFSET_DAYS = 36525.0;

    // ------------------------------------------------------------------
    // Exoplanet HD 209458 b "Osiris" – erster Exoplanet mit beobachtetem Transit (1999).
    // Systemparameter nach Torres et al. (2008) und Knutson et al. (2007).
    // ------------------------------------------------------------------

    private static final String TRANSIT_TARGET = "HD 209458 b";
    private static final String TRANSIT_NICKNAME = "Osiris";
    private static final String TRANSIT_HOST = "HD 209458 · Spektraltyp G0V · Sternbild Pegasus · ≈ 158 Lichtjahre";
    private static final double TRANSIT_PERIOD_DAYS = 3.52474859;
    private static final double TRANSIT_STAR_RADIUS = 1.155;          // Sonnenradien
    private static final double TRANSIT_STAR_MASS = 1.119;            // Sonnenmassen
    private static final double TRANSIT_STAR_TEFF = 6065.0;           // Kelvin
    private static final double TRANSIT_RADIUS_RATIO = 0.12086;       // Rp / R*
    private static final double TRANSIT_INCLINATION_DEG = 86.71;
    private static final double LIMB_DARKENING_U1 = 0.38;            // quadratisches Gesetz, angenähert
    private static final double LIMB_DARKENING_U2 = 0.26;
    private static final double RED_NOISE_AMPLITUDE = 0.00035;        // langsame Systematik (Luftmasse, Sternaktivität)
    private static final int MAX_LIGHTCURVE_SAMPLES = 2000;

    /** Grosse Halbachse nach dem dritten Keplerschen Gesetz: a³ = G·M·P² / (4π²). */
    private static final double TRANSIT_SEMI_MAJOR_AXIS_M = Math.cbrt(
            GM_SUN * TRANSIT_STAR_MASS * Math.pow(TRANSIT_PERIOD_DAYS * 86400.0, 2) / (4.0 * Math.PI * Math.PI));
    private static final double TRANSIT_A_OVER_RS =
            TRANSIT_SEMI_MAJOR_AXIS_M / (TRANSIT_STAR_RADIUS * SOLAR_RADIUS_M);
    private static final double TRANSIT_IMPACT_PARAMETER =
            TRANSIT_A_OVER_RS * Math.cos(Math.toRadians(TRANSIT_INCLINATION_DEG));
    /** Transitdauer T14 für eine Kreisbahn (Seager & Mallén-Ornelas 2003). */
    private static final double TRANSIT_DURATION_DAYS = TRANSIT_PERIOD_DAYS / Math.PI * Math.asin(
            Math.sqrt(Math.pow(1.0 + TRANSIT_RADIUS_RATIO, 2) - Math.pow(TRANSIT_IMPACT_PARAMETER, 2))
                    / (TRANSIT_A_OVER_RS * Math.sin(Math.toRadians(TRANSIT_INCLINATION_DEG))));

    // ------------------------------------------------------------------
    // Kosmologie: flaches ΛCDM-Modell mit Parametern von Planck 2018
    // ------------------------------------------------------------------

    private static final double H0 = 67.4;                            // km/s/Mpc
    private static final double OMEGA_M = 0.315;                      // Materie (dunkel + baryonisch)
    private static final double OMEGA_R = 9.2e-5;                     // Strahlung (Photonen + Neutrinos)
    private static final double OMEGA_L = 1.0 - OMEGA_M - OMEGA_R;    // Dunkle Energie (flaches Universum)
    private static final double T_CMB_TODAY = 2.7255;                 // Kelvin
    private static final double HUBBLE_TIME_GYR = 977.792221 / H0;    // 1/H0 in Milliarden Jahren
    private static final double HUBBLE_DISTANCE_MPC = SPEED_OF_LIGHT_KMS / H0;
    private static final double GLY_PER_MPC = 3.261563777e-3;
    private static final double MPC_M = 3.0856775814913673e22;
    private static final double GRAVITATIONAL_CONSTANT = 6.67430e-11;   // m³/(kg·s²)
    private static final double PROTON_MASS_KG = 1.67262192369e-27;
    /** Kritische Dichte heute: ρc = 3·H0² / (8πG) in kg/m³. */
    private static final double CRITICAL_DENSITY = 3.0 * Math.pow(H0 * 1000.0 / MPC_M, 2)
            / (8.0 * Math.PI * GRAVITATIONAL_CONSTANT);
    private static final double Z_RECOMBINATION = 1089.9;
    private static final double Z_MIN = 0.001;
    private static final double Z_MAX = 1500.0;
    private static final int INTEGRATION_STEPS = 2000;

    /** Ruhewellenlängen wichtiger Spektrallinien in Nanometern. */
    private static final double LYMAN_ALPHA_NM = 121.567;
    private static final double H_BETA_NM = 486.135;
    private static final double OIII_NM = 500.684;
    private static final double H_ALPHA_NM = 656.281;

    /** Beginn von Sonnenzyklus 25 (Minimum Dezember 2019), Grundlage des Sonnenfleckenmodells. */
    private static final double SOLAR_CYCLE_25_START_JD = julianDate(Instant.parse("2019-11-15T00:00:00Z"));
    private static final double SOLAR_CYCLE_MONTHS = 132.0;

    // ------------------------------------------------------------------
    // Instanzzustand (nur Konfiguration, keine fachlichen Daten)
    // ------------------------------------------------------------------

    private final Instant bootedAt = Instant.now();
    private final String serverIp;
    private final double timeLapse;
    private final double cadenceMinutes;
    private final double windowHours;
    private final double noisePpm;

    public RestAPIController(
            @Value("${parallax.photometry.time-lapse:3000}") double timeLapse,
            @Value("${parallax.photometry.cadence-minutes:6}") double cadenceMinutes,
            @Value("${parallax.photometry.window-hours:90}") double windowHours,
            @Value("${parallax.photometry.noise-ppm:1100}") double noisePpm) {
        this.timeLapse = clamp(timeLapse, 1.0, 100_000.0);
        this.cadenceMinutes = clamp(cadenceMinutes, 0.5, 60.0);
        this.windowHours = clamp(windowHours, 1.0, 240.0);
        this.noisePpm = clamp(noisePpm, 0.0, 20_000.0);
        this.serverIp = resolveServerIp();
    }

    // ==================================================================
    // Endpunkte
    // ==================================================================

    /**
     * Statusmeldung der Schnittstelle, kompatibel zur ursprünglichen Ref. Card.
     */
    @GetMapping
    public ResponseEntity<Message> hello() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "PARALLAX – Observatorium für dynamische Astrophysik");
        info.put("bootedAt", bootedAt);
        info.put("uptimeSeconds", Duration.between(bootedAt, Instant.now()).getSeconds());
        info.put("endpoints", Arrays.asList(
                "GET /api/telemetry",
                "GET /api/orbits?days={versatz}",
                "GET /api/lightcurve",
                "GET /api/cosmology?z={rotverschiebung}"));

        return respond("status", "Application Ref. Card 01 - API hello - Server IP Address: " + serverIp, info);
    }

    /**
     * Telemetrie-Schnappschuss: Zeitmasse, System Erde–Sonne–Mond und Weltraumwetter.
     */
    @GetMapping("/telemetry")
    public ResponseEntity<Message> telemetry() {
        Instant now = Instant.now();
        double jd = julianDate(now);
        double d = jd - J2000;                          // Tage seit J2000.0
        double t = d / DAYS_PER_CENTURY;                // julianische Jahrhunderte seit J2000.0
        double minutes = now.toEpochMilli() / 60_000.0; // Zeitachse für das Rauschen

        List<Message.Reading> readings = new ArrayList<>();

        // --- Zeit und Erdrotation ------------------------------------------------
        double gmstDeg = normalize(280.46061837 + 360.98564736629 * d
                + 0.000387933 * t * t - t * t * t / 38_710_000.0, 360.0);
        double gmstHours = gmstDeg / 15.0;

        readings.add(reading("julian-date", "zeit", "Julianisches Datum", jd, "JD", 5, null, null, SRC_CALC,
                "Fortlaufende Tageszählung seit 1. Januar 4713 v. Chr., 12:00 UT"));
        readings.add(reading("gmst", "zeit", "Sternzeit Greenwich", gmstHours, "h", 4, 0.0, 24.0, SRC_CALC,
                hms(gmstHours) + " – Stundenwinkel des Frühlingspunkts"));

        // --- Erde und Sonne ------------------------------------------------------
        double sunMeanAnomaly = Math.toRadians(normalize(357.52911 + 0.98560028 * d, 360.0));
        double sunDistanceAu = 1.00014 - 0.01671 * Math.cos(sunMeanAnomaly) - 0.00014 * Math.cos(2 * sunMeanAnomaly);
        double lightSeconds = sunDistanceAu * LIGHT_SECONDS_PER_AU;

        readings.add(reading("sun-distance", "erde-sonne-mond", "Distanz Erde – Sonne", sunDistanceAu, "AE", 6,
                0.98329, 1.01671, SRC_CALC,
                String.format(SWISS, "%,.0f km · Perihel Anfang Januar, Aphel Anfang Juli", sunDistanceAu * AU_KM)));
        readings.add(reading("light-time", "erde-sonne-mond", "Lichtlaufzeit Sonne → Erde", lightSeconds, "s", 2,
                490.6, 507.4, SRC_CALC,
                String.format(SWISS, "%d min %05.2f s – so alt ist das Sonnenlicht, das Sie sehen",
                        (int) (lightSeconds / 60), lightSeconds % 60)));

        // --- Mond (Meeus, Astronomical Algorithms, Kap. 47 und 48) ---------------
        double elongation = Math.toRadians(normalize(297.8501921 + 445267.1114034 * t, 360.0));
        double sunAnomaly = Math.toRadians(normalize(357.5291092 + 35999.0502909 * t, 360.0));
        double moonAnomaly = Math.toRadians(normalize(134.9633964 + 477198.8675055 * t, 360.0));

        double phaseAngleDeg = 180.0 - Math.toDegrees(elongation)
                - 6.289 * Math.sin(moonAnomaly)
                + 2.100 * Math.sin(sunAnomaly)
                - 1.274 * Math.sin(2 * elongation - moonAnomaly)
                - 0.658 * Math.sin(2 * elongation)
                - 0.214 * Math.sin(2 * moonAnomaly)
                - 0.110 * Math.sin(elongation);
        double illumination = (1.0 + Math.cos(Math.toRadians(phaseAngleDeg))) / 2.0;
        double lunarCycle = normalize(180.0 - phaseAngleDeg, 360.0) / 360.0;
        double moonAge = lunarCycle * SYNODIC_MONTH_DAYS;

        double moonDistanceKm = 385000.56
                - 20905.355 * Math.cos(moonAnomaly)
                - 3699.111 * Math.cos(2 * elongation - moonAnomaly)
                - 2955.968 * Math.cos(2 * elongation)
                - 569.925 * Math.cos(2 * moonAnomaly)
                + 48.888 * Math.cos(sunAnomaly)
                + 246.158 * Math.cos(2 * elongation - 2 * moonAnomaly)
                - 152.138 * Math.cos(2 * elongation - sunAnomaly - moonAnomaly)
                - 170.733 * Math.cos(2 * elongation + moonAnomaly)
                - 204.586 * Math.cos(2 * elongation - sunAnomaly)
                + 108.743 * Math.cos(elongation);

        readings.add(reading("moon-age", "erde-sonne-mond", "Mondalter", moonAge, "d", 2, 0.0, SYNODIC_MONTH_DAYS,
                SRC_CALC, String.format(SWISS, "%s · %.1f %% des synodischen Monats",
                        moonPhaseName(lunarCycle), lunarCycle * 100.0)));
        readings.add(reading("moon-illumination", "erde-sonne-mond", "Beleuchteter Mondanteil", illumination * 100.0,
                "%", 1, 0.0, 100.0, SRC_CALC,
                String.format(SWISS, "Phasenwinkel %.1f°", normalize(phaseAngleDeg, 360.0))));
        readings.add(reading("moon-distance", "erde-sonne-mond", "Distanz Erde – Mond", moonDistanceKm, "km", 0,
                356_400.0, 406_700.0, SRC_CALC,
                String.format(SWISS, "Lichtlaufzeit %.3f s · Perigäum ≈ 356 400 km, Apogäum ≈ 406 700 km",
                        moonDistanceKm / SPEED_OF_LIGHT_KMS)));

        // --- Weltraumwetter (Modell, Messort Lagrange-Punkt L1) ------------------
        double rotation = Math.sin(2 * Math.PI * d / CARRINGTON_ROTATION_DAYS); // wiederkehrende koronale Löcher

        double windSpeed = clamp(420.0 + 90.0 * rotation
                + 70.0 * noise(minutes / 180.0, 11)
                + 22.0 * noise(minutes / 15.0, 12)
                + 6.0 * noise(minutes * 4.0, 13), 260.0, 900.0);
        double density = clamp(6.0 - 2.6 * rotation
                + 2.4 * noise(minutes / 150.0, 21)
                + 0.8 * noise(minutes / 10.0, 22)
                + 0.25 * noise(minutes * 4.0, 23), 0.4, 30.0);
        double bz = 4.2 * noise(minutes / 90.0, 31) + 2.2 * noise(minutes / 8.0, 32) + 0.7 * noise(minutes * 4.0, 33);
        double by = 4.0 * noise(minutes / 110.0, 41) + 1.8 * noise(minutes / 9.0, 42);
        double bt = Math.hypot(by, bz);
        double clockAngle = Math.atan2(by, bz);          // 0 = nordwärts, π = südwärts

        // Kopplungsfunktion nach Newell et al. (2007): Energieeintrag des Sonnenwinds in die Magnetosphäre
        double coupling = Math.pow(windSpeed, 4.0 / 3.0) * Math.pow(bt, 2.0 / 3.0)
                * Math.pow(Math.abs(Math.sin(clockAngle / 2.0)), 8.0 / 3.0);
        double kp = clamp(0.4 + 8.4 * (1.0 - Math.exp(-coupling / 21_000.0)) + 0.0025 * (windSpeed - 400.0), 0.0, 9.0);
        double auroraLatitude = 66.5 - 2.25 * kp;

        double sunspotBase = sunspotModel(jd);
        double sunspots = Math.max(0.0, sunspotBase
                * (1.0 + 0.12 * Math.sin(2 * Math.PI * d / CARRINGTON_ROTATION_DAYS + 1.3))
                + 6.0 * noise(minutes / 720.0, 51));
        double cosmicRays = 1.2 - 0.032 * sunspots - 0.0045 * (windSpeed - 400.0) + 0.35 * noise(minutes / 60.0, 61);

        readings.add(reading("sunspots", "weltraumwetter", "Sonnenflecken-Relativzahl", sunspots, "R", 0, 0.0, 250.0,
                SRC_MODEL, "Sonnenzyklus " + solarCycleNumber(jd) + " · Hathaway-Funktion mit 27-Tage-Rotation"));
        readings.add(reading("solar-wind-speed", "weltraumwetter", "Sonnenwind-Geschwindigkeit", windSpeed, "km/s", 0,
                250.0, 800.0, SRC_MODEL,
                String.format(SWISS, "Laufzeit L1 → Erde ≈ %.0f min", 1_500_000.0 / windSpeed / 60.0)));
        readings.add(reading("proton-density", "weltraumwetter", "Protonendichte", density, "p/cm³", 1, 0.0, 20.0,
                SRC_MODEL, "Langsamer Sonnenwind ist dichter als schneller"));
        readings.add(reading("imf-bz", "weltraumwetter", "Magnetfeld Bz (GSM)", bz, "nT", 1, -15.0, 15.0, SRC_MODEL,
                String.format(SWISS, "|B| = %.1f nT · %s", bt,
                        bz < 0 ? "südwärts – koppelt an das Erdmagnetfeld" : "nordwärts – Magnetosphäre abgeschirmt")));
        readings.add(reading("kp-index", "weltraumwetter", "Geomagnetischer Kp-Index", kp, "Kp", 1, 0.0, 9.0,
                SRC_MODEL, stormLevel(kp)));
        readings.add(reading("aurora-latitude", "weltraumwetter", "Polarlichtgrenze", auroraLatitude, "° N", 1,
                45.0, 67.0, SRC_MODEL,
                auroraLatitude <= 48.5 ? "Polarlicht bis in die Schweiz möglich!" : "Geomagnetische Breite des Polarlichtovals"));
        readings.add(reading("cosmic-rays", "weltraumwetter", "Kosmische Strahlung", cosmicRays, "%", 2, -8.0, 4.0,
                SRC_MODEL, "Neutronenrate relativ zum Mittel – sinkt bei hoher Sonnenaktivität"));

        String text = "Telemetrie-Schnappschuss " + UTC_FORMAT.format(now);
        return respond("telemetry", text, readings);
    }

    /**
     * Heliozentrische Positionen der acht Planeten.
     *
     * @param days Zeitversatz gegenüber jetzt in Tagen (±36 525)
     */
    @GetMapping("/orbits")
    public ResponseEntity<Message> orbits(@RequestParam(defaultValue = "0") double days) {
        double offset = Double.isFinite(days) ? clamp(days, -MAX_ORBIT_OFFSET_DAYS, MAX_ORBIT_OFFSET_DAYS) : 0.0;
        Instant epoch = Instant.now().plusMillis(Math.round(offset * 86_400_000.0));
        double jd = julianDate(epoch);
        double t = (jd - J2000) / DAYS_PER_CENTURY;

        double[][] positions = new double[PLANET_NAMES.length][];
        for (int i = 0; i < PLANET_NAMES.length; i++) {
            positions[i] = heliocentricPosition(i, t);
        }
        double[] earth = positions[EARTH_INDEX];

        List<Message.Body> bodies = new ArrayList<>();
        for (int i = 0; i < PLANET_NAMES.length; i++) {
            double[] p = positions[i];
            double x = p[0];
            double y = p[1];
            double z = p[2];
            double a = p[3];
            double r = Math.sqrt(x * x + y * y + z * z);
            double distanceEarth = i == EARTH_INDEX ? 0.0
                    : Math.sqrt(Math.pow(x - earth[0], 2) + Math.pow(y - earth[1], 2) + Math.pow(z - earth[2], 2));
            // Vis-Viva-Gleichung: v² = GM · (2/r − 1/a)
            double speedKms = Math.sqrt(GM_SUN * (2.0 / (r * AU_M) - 1.0 / (a * AU_M))) / 1000.0;
            double periodDays = 360.0 * DAYS_PER_CENTURY / RATES[i][3];

            bodies.add(Message.Body.builder()
                    .name(PLANET_NAMES[i])
                    .symbol(PLANET_SYMBOLS[i])
                    .semiMajorAxisAu(round(a, 6))
                    .eccentricity(round(p[4], 6))
                    .inclinationDeg(round(p[5], 4))
                    .longitudeDeg(round(normalize(Math.toDegrees(Math.atan2(y, x)), 360.0), 4))
                    .x(round(x, 6))
                    .y(round(y, 6))
                    .z(round(z, 6))
                    .distanceSunAu(round(r, 6))
                    .distanceEarthAu(round(distanceEarth, 6))
                    .lightTimeMinutes(round(distanceEarth * LIGHT_SECONDS_PER_AU / 60.0, 3))
                    .orbitalSpeedKms(round(speedKms, 3))
                    .periodDays(round(periodDays, 2))
                    .build());
        }

        Message.OrbitSnapshot snapshot = Message.OrbitSnapshot.builder()
                .julianDate(round(jd, 5))
                .epoch(epoch)
                .dayOffset(round(offset, 3))
                .bodies(bodies)
                .build();

        return respond("orbits", "Heliozentrische Positionen für " + UTC_FORMAT.format(epoch), snapshot);
    }

    /**
     * Lichtkurve des Exoplaneten HD 209458 b im Zeitraffer.
     *
     * <p>Die virtuelle Beobachtungszeit läuft {@code time-lapse}-mal schneller als die Echtzeit.
     * Geliefert wird immer das komplette Beobachtungsfenster bis "jetzt"; der Client muss
     * deshalb keinen Zustand über mehrere Anfragen halten.</p>
     */
    @GetMapping("/lightcurve")
    public ResponseEntity<Message> lightCurve() {
        long nowMillis = System.currentTimeMillis();
        double virtualMinutes = nowMillis / 60_000.0 * timeLapse;
        double periodMinutes = TRANSIT_PERIOD_DAYS * 1440.0;
        double sigma = noisePpm * 1e-6;

        int count = (int) Math.min(MAX_LIGHTCURVE_SAMPLES, Math.round(windowHours * 60.0 / cadenceMinutes));
        long last = (long) Math.floor(virtualMinutes / cadenceMinutes);

        List<Message.FluxSample> samples = new ArrayList<>(count);
        for (long n = last - count + 1; n <= last; n++) {
            double sampleMinutes = n * cadenceMinutes;
            double model = transitFlux(frac(sampleMinutes / periodMinutes));
            double flux = model + sigma * gaussian(n) + RED_NOISE_AMPLITUDE * noise(sampleMinutes / 240.0, 71);
            samples.add(new Message.FluxSample(round(sampleMinutes / 60.0, 4), round(flux, 6), round(model, 6)));
        }

        double phase = frac(virtualMinutes / periodMinutes);
        double signedPhase = phase > 0.5 ? phase - 1.0 : phase;
        double halfDurationPhase = TRANSIT_DURATION_DAYS / (2.0 * TRANSIT_PERIOD_DAYS);
        boolean inTransit = Math.abs(signedPhase) < halfDurationPhase;
        double periodHours = TRANSIT_PERIOD_DAYS * 24.0;
        double nextIngressHours = frac(-halfDurationPhase - signedPhase) * periodHours;
        Double egressInHours = inTransit ? round((halfDurationPhase - signedPhase) * periodHours, 4) : null;

        Message.LightCurve curve = Message.LightCurve.builder()
                .target(TRANSIT_TARGET)
                .nickname(TRANSIT_NICKNAME)
                .host(TRANSIT_HOST)
                .timeLapse(timeLapse)
                .cadenceMinutes(cadenceMinutes)
                .windowHours(round(count * cadenceMinutes / 60.0, 4))
                .periodHours(round(periodHours, 6))
                .transitDurationHours(round(TRANSIT_DURATION_DAYS * 24.0, 4))
                .nowHour(round(virtualMinutes / 60.0, 4))
                .phase(round(phase, 7))
                .inTransit(inTransit)
                .nextIngressHours(round(nextIngressHours, 4))
                .egressInHours(egressInHours)
                .radiusRatio(TRANSIT_RADIUS_RATIO)
                .scaledSemiMajorAxis(round(TRANSIT_A_OVER_RS, 4))
                .inclinationDeg(TRANSIT_INCLINATION_DEG)
                .samples(samples)
                .parameters(transitParameters())
                .build();

        String text = inTransit
                ? String.format(SWISS, "Transit läuft – %s zieht vor seinem Stern vorbei", TRANSIT_TARGET)
                : String.format(SWISS, "Nächster Transit von %s in %.1f h Beobachtungszeit", TRANSIT_TARGET, nextIngressHours);
        return respond("lightcurve", text, curve);
    }

    /**
     * Kosmologie-Rechner: Entfernungen und Zeiten für eine Rotverschiebung z im ΛCDM-Modell.
     *
     * @param z Rotverschiebung (0.001 bis 1500)
     */
    @GetMapping("/cosmology")
    public ResponseEntity<Message> cosmology(@RequestParam(defaultValue = "1.0") double z) {
        double redshift = Double.isFinite(z) ? clamp(z, Z_MIN, Z_MAX) : 1.0;
        double scaleFactor = 1.0 / (1.0 + redshift);
        double sqrtScale = Math.sqrt(scaleFactor);

        // Substitution a = u² glättet die Integranden bei kleinen Skalenfaktoren (frühes Universum).
        DoubleUnaryOperator ageIntegrand = u -> 2.0 * u * u * u / expansion(u * u);
        DoubleUnaryOperator distanceIntegrand = u -> 2.0 * u / expansion(u * u);

        double ageTodayGyr = HUBBLE_TIME_GYR * simpson(ageIntegrand, 0.0, 1.0, INTEGRATION_STEPS);
        double ageThenGyr = HUBBLE_TIME_GYR * simpson(ageIntegrand, 0.0, sqrtScale, INTEGRATION_STEPS);
        double lookbackGyr = ageTodayGyr - ageThenGyr;

        double comovingMpc = HUBBLE_DISTANCE_MPC * simpson(distanceIntegrand, sqrtScale, 1.0, INTEGRATION_STEPS);
        double luminosityMpc = comovingMpc * (1.0 + redshift);
        double angularMpc = comovingMpc / (1.0 + redshift);
        double distanceModulus = 5.0 * Math.log10(luminosityMpc * 1e6 / 10.0);
        double kpcPerArcsec = angularMpc * 1000.0 * Math.PI / 648_000.0;
        double hubbleParameter = H0 * Math.sqrt(OMEGA_R * Math.pow(1 + redshift, 4)
                + OMEGA_M * Math.pow(1 + redshift, 3) + OMEGA_L);
        double recessionVelocity = comovingMpc * H0 / SPEED_OF_LIGHT_KMS;
        double cmbTemperature = T_CMB_TODAY * (1.0 + redshift);
        double matterDensityToday = OMEGA_M * CRITICAL_DENSITY / PROTON_MASS_KG;     // Protonenmassen pro m³
        double matterDensityThen = matterDensityToday * Math.pow(1.0 + redshift, 3);
        double accelerationRedshift = Math.cbrt(2.0 * OMEGA_L / OMEGA_M) - 1.0;

        List<Message.Reading> readings = new ArrayList<>();
        readings.add(reading("redshift", "eingabe", "Rotverschiebung z", redshift, "", 4, null, null, SRC_CALC,
                "λ_beobachtet = (1 + z) · λ_Ruhe"));
        readings.add(reading("lookback-time", "zeit", "Rückblickzeit", lookbackGyr, "Mrd. J.", 3, null, null, SRC_CALC,
                "So lange war das Licht zu uns unterwegs"));
        readings.add(reading("age-then", "zeit", "Alter des Universums damals", ageThenGyr, "Mrd. J.", 4, null, null,
                SRC_CALC, formatAge(ageThenGyr)));
        readings.add(reading("age-today", "zeit", "Alter des Universums heute", ageTodayGyr, "Mrd. J.", 3, null, null,
                SRC_CALC, "Planck 2018: H₀ = 67.4 km/s/Mpc, Ωm = 0.315, ΩΛ = 0.685"));
        readings.add(reading("comoving-distance", "distanz", "Mitbewegte Entfernung", comovingMpc * GLY_PER_MPC, "Mrd. Lj",
                3, null, null, SRC_CALC, String.format(SWISS, "%,.1f Mpc – Entfernung heute", comovingMpc)));
        readings.add(reading("light-travel-distance", "distanz", "Lichtlaufdistanz", lookbackGyr, "Mrd. Lj", 3, null, null,
                SRC_CALC, "Zurückgelegter Weg des Lichts – nicht die heutige Entfernung"));
        readings.add(reading("luminosity-distance", "distanz", "Leuchtkraftentfernung", luminosityMpc * GLY_PER_MPC,
                "Mrd. Lj", 3, null, null, SRC_CALC,
                String.format(SWISS, "Entfernungsmodul m − M = %.2f mag", distanceModulus)));
        readings.add(reading("angular-diameter-distance", "distanz", "Winkeldurchmesser-Entfernung",
                angularMpc * GLY_PER_MPC, "Mrd. Lj", 3, null, null, SRC_CALC,
                "Maximal bei z ≈ 1.6 – noch fernere Objekte erscheinen wieder grösser"));
        readings.add(reading("angular-scale", "distanz", "Winkelmassstab", kpcPerArcsec, "kpc/″", 3, null, null, SRC_CALC,
                "Ausdehnung, die einer Bogensekunde am Himmel entspricht"));
        readings.add(reading("scale-factor", "expansion", "Skalenfaktor a", scaleFactor, "", 5, 0.0, 1.0, SRC_CALC,
                String.format(SWISS, "Das Universum war %.1f-mal kleiner als heute", 1.0 + redshift)));
        readings.add(reading("hubble-parameter", "expansion", "Hubble-Parameter H(z)", hubbleParameter, "km/s/Mpc", 1,
                null, null, SRC_CALC, "Expansionsrate zur Zeit der Lichtaussendung"));
        readings.add(reading("recession-velocity", "expansion", "Fluchtgeschwindigkeit heute", recessionVelocity, "c", 3,
                null, null, SRC_CALC, recessionVelocity > 1.0
                        ? "Schneller als Licht: Der Raum selbst dehnt sich aus – kein Widerspruch zur Relativität"
                        : "Anteil der Lichtgeschwindigkeit (Hubble-Lemaître-Gesetz v = H₀ · D)"));
        readings.add(reading("cmb-temperature", "strahlung", "Temperatur der Hintergrundstrahlung", cmbTemperature, "K",
                2, null, null, SRC_CALC, "T = 2.7255 K · (1 + z)"));
        readings.add(reading("matter-density", "materie", "Materiedichte damals", matterDensityThen, "p/m³",
                matterDensityThen < 100.0 ? 2 : 0, null, null, SRC_CALC,
                String.format(SWISS, "Protonenmassen pro m³ inkl. Dunkler Materie · heute %.2f", matterDensityToday)));
        readings.add(reading("lyman-alpha", "spektrum", "Lyman-α (121.6 nm)", LYMAN_ALPHA_NM * (1 + redshift), "nm", 1,
                null, null, SRC_CALC, "Wasserstoff, Ultraviolett"));
        readings.add(reading("h-beta", "spektrum", "H-β (486.1 nm)", H_BETA_NM * (1 + redshift), "nm", 1, null, null,
                SRC_CALC, "Wasserstoff, Balmer-Serie"));
        readings.add(reading("oiii", "spektrum", "[O III] (500.7 nm)", OIII_NM * (1 + redshift), "nm", 1, null, null,
                SRC_CALC, "Zweifach ionisierter Sauerstoff"));
        readings.add(reading("h-alpha", "spektrum", "H-α (656.3 nm)", H_ALPHA_NM * (1 + redshift), "nm", 1, null, null,
                SRC_CALC, "Wasserstoff, Balmer-Serie"));

        return respond("cosmology", cosmicEpoch(redshift, accelerationRedshift), readings);
    }

    /**
     * Einheitliche Fehlermeldung bei ungültigen Parametern, z. B. {@code /api/cosmology?z=abc}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Message> invalidParameter(MethodArgumentTypeMismatchException ex) {
        Message message = new Message(
                "Ungültiger Wert für Parameter '" + ex.getName() + "': " + ex.getValue(),
                "error", serverIp, Instant.now(), null);
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(message);
    }

    // ==================================================================
    // Himmelsmechanik
    // ==================================================================

    /**
     * Berechnet die heliozentrische, ekliptikale Position eines Planeten.
     *
     * @param index Index in {@link #ELEMENTS}
     * @param t     julianische Jahrhunderte seit J2000.0
     * @return {x, y, z, a, e, I} – Koordinaten in AE, a in AE, I in Grad
     */
    private static double[] heliocentricPosition(int index, double t) {
        double a = ELEMENTS[index][0] + RATES[index][0] * t;
        double e = ELEMENTS[index][1] + RATES[index][1] * t;
        double inclination = ELEMENTS[index][2] + RATES[index][2] * t;
        double meanLongitude = ELEMENTS[index][3] + RATES[index][3] * t;
        double perihelionLongitude = ELEMENTS[index][4] + RATES[index][4] * t;
        double nodeLongitude = ELEMENTS[index][5] + RATES[index][5] * t;

        double argumentOfPerihelion = Math.toRadians(perihelionLongitude - nodeLongitude);
        double node = Math.toRadians(nodeLongitude);
        double incl = Math.toRadians(inclination);
        double meanAnomaly = Math.toRadians(normalize(meanLongitude - perihelionLongitude + 180.0, 360.0) - 180.0);
        double eccentricAnomaly = solveKepler(meanAnomaly, e);

        // Position in der Bahnebene (x-Achse zeigt zum Perihel)
        double xOrbit = a * (Math.cos(eccentricAnomaly) - e);
        double yOrbit = a * Math.sqrt(1.0 - e * e) * Math.sin(eccentricAnomaly);

        // Drehung in das ekliptikale Koordinatensystem J2000
        double cosW = Math.cos(argumentOfPerihelion);
        double sinW = Math.sin(argumentOfPerihelion);
        double cosO = Math.cos(node);
        double sinO = Math.sin(node);
        double cosI = Math.cos(incl);
        double sinI = Math.sin(incl);

        double x = (cosW * cosO - sinW * sinO * cosI) * xOrbit + (-sinW * cosO - cosW * sinO * cosI) * yOrbit;
        double y = (cosW * sinO + sinW * cosO * cosI) * xOrbit + (-sinW * sinO + cosW * cosO * cosI) * yOrbit;
        double z = (sinW * sinI) * xOrbit + (cosW * sinI) * yOrbit;

        return new double[]{x, y, z, a, e, inclination};
    }

    /**
     * Löst die Keplergleichung M = E − e·sin(E) mit dem Newton-Verfahren.
     */
    private static double solveKepler(double meanAnomaly, double e) {
        double eccentricAnomaly = meanAnomaly + e * Math.sin(meanAnomaly);
        for (int i = 0; i < 30; i++) {
            double delta = (eccentricAnomaly - e * Math.sin(eccentricAnomaly) - meanAnomaly)
                    / (1.0 - e * Math.cos(eccentricAnomaly));
            eccentricAnomaly -= delta;
            if (Math.abs(delta) < 1e-12) {
                break;
            }
        }
        return eccentricAnomaly;
    }

    private static String moonPhaseName(double cycle) {
        if (cycle < 0.0339 || cycle >= 0.9661) {
            return "Neumond";
        } else if (cycle < 0.2161) {
            return "Zunehmende Sichel";
        } else if (cycle < 0.2839) {
            return "Erstes Viertel";
        } else if (cycle < 0.4661) {
            return "Zunehmender Dreiviertelmond";
        } else if (cycle < 0.5339) {
            return "Vollmond";
        } else if (cycle < 0.7161) {
            return "Abnehmender Dreiviertelmond";
        } else if (cycle < 0.7839) {
            return "Letztes Viertel";
        }
        return "Abnehmende Sichel";
    }

    // ==================================================================
    // Weltraumwetter
    // ==================================================================

    /**
     * Sonnenflecken-Modell: Überlagerung von 11-Jahres-Zyklen mit der Hathaway-Funktion
     * R(t) = A·t³ / (exp(t²/b²) − c), kalibriert auf das Maximum von Zyklus 25 (Oktober 2024).
     */
    private static double sunspotModel(double jd) {
        double months = (jd - SOLAR_CYCLE_25_START_JD) / 30.436875;
        double cycle = Math.floor(months / SOLAR_CYCLE_MONTHS);
        double sum = 0.0;
        for (int k = -1; k <= 0; k++) {       // ausklingender und aktueller Zyklus überlagern sich
            sum += hathaway(months - (cycle + k) * SOLAR_CYCLE_MONTHS);
        }
        return sum;
    }

    private static double hathaway(double monthsSinceStart) {
        if (monthsSinceStart <= 0) {
            return 0.0;
        }
        final double amplitude = 0.00202;
        final double width = 54.0;
        final double asymmetry = 0.71;
        double x = monthsSinceStart / width;
        return amplitude * Math.pow(monthsSinceStart, 3) / (Math.exp(x * x) - asymmetry);
    }

    private static long solarCycleNumber(double jd) {
        double months = (jd - SOLAR_CYCLE_25_START_JD) / 30.436875;
        return 25 + (long) Math.floor(months / SOLAR_CYCLE_MONTHS);
    }

    /**
     * Einteilung nach der NOAA-Skala für geomagnetische Stürme.
     */
    private static String stormLevel(double kp) {
        if (kp >= 9.0) {
            return "G5 – extremer geomagnetischer Sturm";
        } else if (kp >= 8.0) {
            return "G4 – schwerer geomagnetischer Sturm";
        } else if (kp >= 7.0) {
            return "G3 – starker geomagnetischer Sturm";
        } else if (kp >= 6.0) {
            return "G2 – mässiger geomagnetischer Sturm";
        } else if (kp >= 5.0) {
            return "G1 – kleiner geomagnetischer Sturm";
        } else if (kp >= 4.0) {
            return "G0 – aktives Magnetfeld";
        } else if (kp >= 2.0) {
            return "G0 – leicht unruhiges Magnetfeld";
        }
        return "G0 – ruhiges Magnetfeld";
    }

    // ==================================================================
    // Transit-Photometrie
    // ==================================================================

    /**
     * Relativer Sternfluss zur Bahnphase (0 = Transitmitte) mit quadratischer Randverdunkelung.
     */
    private static double transitFlux(double phase) {
        double angle = 2.0 * Math.PI * phase;
        if (Math.cos(angle) <= 0.0) {
            return 1.0;                                     // Planet hinter dem Stern
        }
        double x = TRANSIT_A_OVER_RS * Math.sin(angle);
        double y = TRANSIT_IMPACT_PARAMETER * Math.cos(angle);
        double separation = Math.sqrt(x * x + y * y);        // Abstand der Scheibenmitten in Sternradien
        double k = TRANSIT_RADIUS_RATIO;
        if (separation >= 1.0 + k) {
            return 1.0;
        }

        double overlap = circleOverlap(separation, k);
        // Näherung für kleine Planeten: lokale Intensität an der verdeckten Stelle
        double r = separation <= 1.0 - k ? separation : (separation - k + 1.0) / 2.0;
        double mu = Math.sqrt(Math.max(0.0, 1.0 - r * r));
        double intensity = 1.0 - LIMB_DARKENING_U1 * (1.0 - mu) - LIMB_DARKENING_U2 * Math.pow(1.0 - mu, 2);
        double meanIntensity = 1.0 - LIMB_DARKENING_U1 / 3.0 - LIMB_DARKENING_U2 / 6.0;
        return 1.0 - overlap / Math.PI * intensity / meanIntensity;
    }

    /**
     * Überlappungsfläche zweier Kreise (Stern mit Radius 1, Planet mit Radius k) im Abstand z.
     */
    private static double circleOverlap(double z, double k) {
        if (z >= 1.0 + k) {
            return 0.0;
        }
        if (z <= 1.0 - k) {
            return Math.PI * k * k;
        }
        double kappa0 = Math.acos(clamp((k * k + z * z - 1.0) / (2.0 * k * z), -1.0, 1.0));
        double kappa1 = Math.acos(clamp((1.0 - k * k + z * z) / (2.0 * z), -1.0, 1.0));
        double root = Math.sqrt(Math.max(0.0, 4.0 * z * z - Math.pow(1.0 + z * z - k * k, 2)));
        return k * k * kappa0 + kappa1 - 0.5 * root;
    }

    private static List<Message.Reading> transitParameters() {
        double depthPercent = (1.0 - transitFlux(0.0)) * 100.0;
        double planetRadiusM = TRANSIT_RADIUS_RATIO * TRANSIT_STAR_RADIUS * SOLAR_RADIUS_M;
        double equilibriumTemperature = TRANSIT_STAR_TEFF * Math.sqrt(1.0 / (2.0 * TRANSIT_A_OVER_RS));
        double orbitalSpeedKms = 2.0 * Math.PI * TRANSIT_SEMI_MAJOR_AXIS_M / (TRANSIT_PERIOD_DAYS * 86400.0) / 1000.0;

        List<Message.Reading> parameters = new ArrayList<>();
        parameters.add(reading("period", "exoplanet", "Umlaufperiode", TRANSIT_PERIOD_DAYS, "d", 5, null, null,
                SRC_LITERATURE, "Ein Jahr auf Osiris dauert 3½ Erdtage"));
        parameters.add(reading("depth", "exoplanet", "Transittiefe", depthPercent, "%", 2, null, null, SRC_CALC,
                "Verdeckter Sternfluss inkl. Randverdunkelung"));
        parameters.add(reading("duration", "exoplanet", "Transitdauer", TRANSIT_DURATION_DAYS * 24.0, "h", 2, null, null,
                SRC_CALC, "Vom ersten bis zum vierten Kontakt"));
        parameters.add(reading("planet-radius", "exoplanet", "Planetenradius", planetRadiusM / JUPITER_RADIUS_M, "R♃", 3,
                null, null, SRC_CALC, String.format(SWISS, "%,.0f km · aus Rp = k · R*", planetRadiusM / 1000.0)));
        parameters.add(reading("semi-major-axis", "exoplanet", "Grosse Halbachse", TRANSIT_SEMI_MAJOR_AXIS_M / AU_M, "AE",
                4, null, null, SRC_CALC, "3. Keplersches Gesetz mit M* = 1.119 M☉"));
        parameters.add(reading("a-over-rs", "exoplanet", "Bahnradius / Sternradius", TRANSIT_A_OVER_RS, "", 2, null, null,
                SRC_CALC, "Der Planet umkreist seinen Stern extrem eng"));
        parameters.add(reading("impact", "exoplanet", "Stossparameter b", TRANSIT_IMPACT_PARAMETER, "", 3, null, null,
                SRC_CALC, "Minimaler Abstand zur Sternmitte in Sternradien"));
        parameters.add(reading("t-eq", "exoplanet", "Gleichgewichtstemperatur", equilibriumTemperature, "K", 0, null, null,
                SRC_CALC, "Albedo 0, vollständige Wärmeumverteilung"));
        parameters.add(reading("orbital-speed", "exoplanet", "Bahngeschwindigkeit", orbitalSpeedKms, "km/s", 1, null, null,
                SRC_CALC, "Fast fünfmal schneller als die Erde"));
        return parameters;
    }

    // ==================================================================
    // Kosmologie
    // ==================================================================

    /**
     * Dimensionslose Expansionsfunktion a²·E(a) = √(Ωr + Ωm·a + ΩΛ·a⁴).
     */
    private static double expansion(double a) {
        return Math.sqrt(OMEGA_R + OMEGA_M * a + OMEGA_L * a * a * a * a);
    }

    private static String cosmicEpoch(double z, double accelerationRedshift) {
        if (z >= Z_RECOMBINATION + 60.0) {
            return "Vor der Rekombination: Das Universum ist ein undurchsichtiges, glühendes Plasma.";
        } else if (z >= Z_RECOMBINATION - 60.0) {
            return "Rekombination: Aus Protonen und Elektronen entstehen Atome – das Universum wird durchsichtig, "
                    + "die kosmische Hintergrundstrahlung wird frei.";
        } else if (z >= 20.0) {
            return "Dunkles Zeitalter: neutraler Wasserstoff, noch leuchtet kein einziger Stern.";
        } else if (z >= 6.0) {
            return "Kosmische Morgendämmerung: Die ersten Sterne und Galaxien reionisieren das Universum.";
        } else if (z >= 1.5) {
            return "Kosmischer Mittag: Die Galaxien bilden so viele Sterne wie nie wieder.";
        } else if (z >= accelerationRedshift) {
            return String.format(SWISS, "Gebremste Expansion: Die Materie dominiert noch – ab z ≈ %.2f beschleunigt die Dunkle Energie.",
                    accelerationRedshift);
        }
        return String.format(SWISS, "Beschleunigte Expansion: Seit z ≈ %.2f dominiert die Dunkle Energie.",
                accelerationRedshift);
    }

    private static String formatAge(double gyr) {
        double years = gyr * 1e9;
        if (years < 1e6) {
            return String.format(SWISS, "≈ %,.0f Jahre nach dem Urknall", years);
        } else if (years < 1e9) {
            return String.format(SWISS, "≈ %,.0f Mio. Jahre nach dem Urknall", years / 1e6);
        }
        return String.format(SWISS, "≈ %.2f Mrd. Jahre nach dem Urknall", gyr);
    }

    // ==================================================================
    // Numerik und Hilfsfunktionen
    // ==================================================================

    /**
     * Numerische Integration mit der Simpsonregel.
     */
    private static double simpson(DoubleUnaryOperator f, double from, double to, int steps) {
        if (to <= from) {
            return 0.0;
        }
        int n = steps % 2 == 0 ? steps : steps + 1;
        double h = (to - from) / n;
        double sum = f.applyAsDouble(from) + f.applyAsDouble(to);
        for (int i = 1; i < n; i++) {
            sum += f.applyAsDouble(from + i * h) * (i % 2 == 0 ? 2.0 : 4.0);
        }
        return sum * h / 3.0;
    }

    /**
     * Julianisches Datum eines Zeitpunkts (UT).
     */
    private static double julianDate(Instant instant) {
        return instant.toEpochMilli() / 86_400_000.0 + 2440587.5;
    }

    /**
     * SplitMix64-Mischfunktion: bildet eine Zahl gleichmässig auf 64 Bit ab.
     */
    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Deterministische Pseudozufallszahl im Intervall [0, 1).
     */
    private static double hashUnit(long seed, long n) {
        return (mix64(seed * 0x9E3779B97F4A7C15L + n) >>> 11) * 0x1.0p-53;
    }

    /**
     * Glattes, deterministisches Wertrauschen im Intervall [−1, 1].
     */
    private static double noise(double x, long seed) {
        long cell = (long) Math.floor(x);
        double f = x - cell;
        double smooth = f * f * (3.0 - 2.0 * f);
        double a = hashUnit(seed, cell);
        double b = hashUnit(seed, cell + 1);
        return (a + (b - a) * smooth) * 2.0 - 1.0;
    }

    /**
     * Deterministische, normalverteilte Zahl (Box-Muller-Verfahren).
     */
    private static double gaussian(long n) {
        double u1 = Math.max(hashUnit(97, n), 1e-12);
        double u2 = hashUnit(98, n);
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    private static Message.Reading reading(String id, String group, String label, double value, String unit,
                                           int decimals, Double min, Double max, String source, String note) {
        return Message.Reading.builder()
                .id(id)
                .group(group)
                .label(label)
                .value(round(value, decimals + 3))
                .unit(unit)
                .decimals(decimals)
                .min(min)
                .max(max)
                .source(source)
                .note(note)
                .build();
    }

    private ResponseEntity<Message> respond(String channel, String text, Object data) {
        Message message = new Message(text, channel, serverIp, Instant.now(), data);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(message);
    }

    private static String resolveServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "---";
        }
    }

    private static String hms(double hours) {
        long seconds = (long) Math.floor(normalize(hours, 24.0) * 3600.0);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static double normalize(double value, double modulus) {
        double result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, Math.max(0, Math.min(decimals, 9)));
        return Math.round(value * factor) / factor;
    }
}
