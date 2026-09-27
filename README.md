
# Architecure Ref. Card 01
Standalone Spring Boot Application

Link zur Übersicht:<br/>
https://gitlab.com/bbwrl/m346-ref-card-overview

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
```git clone git@gitlab.com:bbwrl/m346-ref-card-01.git```
<br/>
```cd architecture-refcard-01```

### Projekt bauen und starten
Die Ausführung der Befehle erfolgt im Projektordner

Builden mit Maven<br/>
```$ mvn package```

Das Projekt wird gebaut und die entsprechende Jar-Datei im Ordner Target erstellt (Artefakt).

Die erstellte Datei kann nun direkt mit Java gestartet werden.<br/>
```$ java -jar target/architecture-refcard-01-0.0.1-SNAPSHOT.jar```

Die App kann nun im Browser unter der URL http://localhost:8080 betrachtet werden.



### Inbetriebnahme mit Docker Container
folgt...





