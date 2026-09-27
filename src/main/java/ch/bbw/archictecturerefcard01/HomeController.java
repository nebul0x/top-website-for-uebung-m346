package ch.bbw.archictecturerefcard01;

import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Liefert die Hauptseite (Thymeleaf-Template {@code templates/index.html}) aus.
 *
 * <p>Die Seite selbst wird nur einmal serverseitig gerendert. Alle dynamischen
 * Messdaten lädt sie anschliessend asynchron über den {@link RestAPIController}.</p>
 */
@Controller
public class HomeController {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'", new Locale("de", "CH"))
            .withZone(ZoneOffset.UTC);

    @GetMapping("/")
    public String home(Model model) {

        Message message = new Message();

        String serverIp;
        String hostName;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            serverIp = localHost.getHostAddress();
            hostName = localHost.getHostName();
        } catch (Exception e) {
            serverIp = "---";
            hostName = "---";
        }

        message.setText("IP Address (Server): " + serverIp);

        String springBootVersion = SpringBootVersion.getVersion();

        model.addAttribute("message", message.getText());
        model.addAttribute("serverIp", serverIp);
        model.addAttribute("hostName", hostName);
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("springBootVersion", springBootVersion != null ? springBootVersion : "unbekannt");
        model.addAttribute("renderedAt", UTC_FORMAT.format(Instant.now()));
        return "index";
    }

}
