package ch.bbw.archictecturerefcard01;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Primäre Startklasse des PARALLAX-Observatoriums.
 *
 * <p>{@link SpringBootApplication} aktiviert die Autokonfiguration und das Scannen
 * der Komponenten in diesem Paket ({@link HomeController}, {@link RestAPIController}).
 * Der eingebettete Tomcat-Server startet auf dem in {@code application.properties}
 * festgelegten Port.</p>
 */
@SpringBootApplication
public class ArchitectureRefCard01Application {

    public static void main(String[] args) {
        SpringApplication.run(ArchitectureRefCard01Application.class, args);
    }

}
