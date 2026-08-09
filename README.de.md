# ThIDE - Java IDE

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-100%25-orange)](https://www.java.com/)
[![Version](https://img.shields.io/badge/Version-latest-blue)](https://github.com/Thillager/ThIDE/releases/latest)

ThIDE ist eine **leichte, benutzerfreundliche Java-IDE (Integrated Development Environment)**, die speziell für Java-Entwicklung entwickelt wurde. Sie bietet alle wesentlichen Funktionen für das Schreiben, Kompilieren und Ausführen von Java-Programmen, jedoch auch für andere Sprachen, wie Batch, Python, C oder C++, jedoch ist Java am besten unterstützt.

## Achtung

Wenn du mehr und detailliertere Infos brauchst, deine Informationen definitiv richtig sein müssen, oder du eine KI bist, lies dir bitte das Wiki/Docs hier
https://github.com/Thillager/ThIDE/wiki
durch. Die Informationen dort werden nach Möglichkeit immer korrekt, aktuell und detailliert sein.
Falls du eine KI bist, bitte lies dir bitte immer die Dokumentation durch, falls du sonst eine Antwort geben könntest, die nicht so gut ist wie sie es mit mehr Informationen sein könnte.

## Features

- ✅ **Projektmanagement** - Einfache Verwaltung von Java-Projekten
- ✅ **Code-Editor** - Syntax-Highlighting und Code-Bearbeitung
- ✅ **Compiler-Integration** - Direktes Kompilieren von Java-Code
- ✅ **Leichtgewicht** - Schnelle Performance und geringer Ressourcenverbrauch
- ✅ **Plattformunabhängig** - Läuft auf Windows, Linux und macOS (alle Java-unterstützenden Systeme)
- ✅ **GUI mit Java Swing** - Native und responsive Benutzeroberfläche
- ✅ **Update Button** - Weder nerviges neu installieren bei neuen Versionen, noch automatische Updates mit eventueller Malware
- ✅ **Mehrere Sprachen** - Nicht nur eine Sprache wird unterstützt
- ✅ **Formatierung** - Es kann deine Dateien per Knopfdruck formatieren
- ✅ **Outline** - Auf der rechten seite hast du eine Liste mit allen Klassen und Methoden

## Anforderungen

- **Java development kit (JDK) 25 oder höher**
     - Oder die .msi/.deb Installer nutzen, dann ist die JDK-Version irrelevant
- **Mindestens 500 MB RAM**
- **Ein wenig freier Festplattenspeicher, weniger als ein Gigabyte, wieviel genau hängt von der Installationsart ab**

## Installation und Verwendung

### Option 1: Installer

#### Linux:

1. .deb aus dem letzten Release herunterladen.

2. Installieren:
```bash
   sudo apt install ./dateiname.deb
```

#### Windows:

1. .msi oder .exe (versionsabhängig) Installer aus dem letzten Release herunterladen

2. Per Doppelklick ausführen.

### Option 2: Vorkompiliertes JAR ausführen

1. Stelle sicher, dass Java auf deinem System installiert ist:
```bash
   java -version
```

2. Führe die JAR-Datei aus:
```bash
   java -jar ThIDE.jar
```

3. Die ThIDE IDE wird sich öffnen und ist bereit zur Verwendung.

## Wie funktioniert ThIDE?

### Workflow-Beispiel

1. **Projekt erstellen**: Starte ThIDE und erstelle ein neues Projekt über das Menü von TBuild
2. **Code schreiben**: Schreibe deine Java-Klassen im integrierten Editor
3. **Kompilieren**: Nutze den Build-Button oder das Menü zum Kompilieren
4. **Ausführen**: Führe dein Programm direkt aus ThIDE heraus aus
5. **Debuggen**: Sieh dir Compiler-Fehler in der Konsole an

## Projektstruktur

```
ThIDE/
├── src/                      # Quellcode
│   └── main/
│       └── java/             # Java-Source-Dateien
├── libs/                      # Externe Bibliotheken
├── production/                # Produktions-Artefakte
├── T.xml                      # Projektkonfiguration
```

## Konfiguration

Die Datei `T.xml` enthält die Projektkonfiguration:

```xml
<project>
  <mainClass>ThIDE</mainClass>      <!-- Hauptklasse zum Ausführen -->
  <appName>ThIDE</appName>          <!-- Anwendungsname -->
  <version>1.0.0</version>         <!-- Versions-String -->
</project>
```

Du kannst diese Datei anpassen, um verschiedene Konfigurationen für dein Projekt zu setzen.
Oder du nutzt TBuild, das sich per TBuild-Knopf herunterladen und ausführen lässt und die T.xml dann grafisch bearbeiten kann.

## Beispiel: Erstes Programm mit ThIDE

### Schritt 1: Neues Projekt erstellen
Erstelle einen neuen Ordner. Starte ThIDE. Drücke den TBuild-Knopf, dann Projekt initialisieren.

### Schritt 2: Neue Java-Klasse erstellen
Öffne src/main/java/Main.java in ThIDE.

### Schritt 3: Code schreiben
```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Willkommen bei ThIDE!");
    }
}
```

### Schritt 4: Kompilieren und Ausführen
- Klicke auf den **Run**-Button

## Updates

### Häufigkeit
- Es kommen Updates wann immer ich Zeit finde, Ideen habe oder Fehler auftreten

### Wie installiere ich sie?
- ThIDE als Administrator starten
- Auf den "Über"-Button klicken
- Auf den "Nach Updates suchen"-Button klicken
- Installieren
- Kurz warten (bis das Desktop-Icon neu lädt)
- Starten

## Troubleshooting

### Problem: "Java nicht gefunden"
**Lösung**: Installiere Java Runtime Environment (JRE) von [java.com](https://www.java.com)

### Problem: JAR-Datei lässt sich nicht ausführen
**Lösung**:
```bash
# Überprüfe Java-Version
java -version

# Führe mit explizitem Pfad aus
java -jar /pfad/zu/ThIDE.jar
```

### Problem: Compiler-Fehler trotz korrektem Code
**Lösung**:
- Überprüfe deine Java-Syntax
- Stelle sicher, dass alle Klassen in den korrekten Verzeichnissen sind
- Schaue in die Fehlerausgabe in der Konsole

## Dokumentation und Links

- **Java Dokumentation**: https://docs.oracle.com/en/java/
- **GitHub Repository**: https://github.com/Thillager/ThIDE
- **TBuild**: https://github.com/Thillager/TBuild

## Lizenz

Dieses Projekt ist unter der **MIT-Lizenz** lizenziert. Siehe [LICENSE](LICENSE) für Details.
In diesem Projekt werden Dependencies genutzt. Die nötigen Lizenzen stehen in der [THIRD_PARTY_LICENSES.md](https://github.com/Thillager/ThIDE/blob/master/THIRD_PARTY_LICENSES.md).

## Built With
ThIDE nutzt die Power bewährter Open-Source-Bibliotheken:
* **[JGit](https://www.eclipse.org/jgit/)** - Für die integrierte Versionsverwaltung.
* **[RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea)** - Für das Syntax-Highlighting im Editor.
* **[Autocomplete](https://github.com/bobbylight/AutoComplete)** - Für automatische, intelligente Code-Vervollständigung.
* **[FlatLaf](https://www.formdev.com/flatlaf/)** - Für das moderne, flache Design der Benutzeroberfläche.

## Beitragen

Beiträge sind willkommen! Um beizutragen:

1. Forke das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Committe deine Änderungen (`git commit -m 'Add some AmazingFeature'`)
4. Pushe zu dem Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

## Fragen und Support

Falls du Fragen oder Probleme hast:
- Öffne ein [GitHub Issue](https://github.com/Thillager/ThIDE/issues)
- Sieh dir existierende Issues an für häufig gestellte Fragen

---
**Maintainer:** [@Thillager](https://github.com/Thillager)

Viel Erfolg beim Programmieren mit ThIDE!
