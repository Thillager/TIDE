package config;

import java.awt.Color;

/**
 * Zentrales Theme-Objekt für TIDE.
 *
 * flatLafClass  →  welcher FlatLaf-LookAndFeel wird geladen
 * "dark" | "light" | "mac-dark" | "mac-light"
 *
 * syntaxTheme   →  RSyntaxTextArea-Theme-Name (aus dem Classpath)
 * Gültige Werte: "dark", "default", "default-alt",
 * "druid", "eclipse", "idea", "monokai", "vs"
 * → geladen via Theme.load("org/fife/ui/rsyntaxtextarea/themes/<name>.xml")
 */
public class Theme {

    public final String name;

    // ── Swing-Farben ────────────────────────────────────────────────────────
    public final Color background;       // Haupthintergrund (Panels, Dialoge)
    public final Color backgroundLight;  // Etwas hellere Variante (Buttons, Tabs)
    public final Color backgroundHover;  // Button-Hover
    public final Color foreground;       // Primäre Schriftfarbe
    public final Color foregroundDim;    // Gedämpfte Schriftfarbe (Labels, Hints)
    public final Color accent;           // Gelb/Blau – Highlights, Git-Menü
    public final Color accentGreen;      // Run-Button, Erfolg
    public final Color accentRed;        // Stop-Button, Fehler
    public final Color border;           // Button-Rahmen, Trennlinien
    public final Color toolbar;          // Toolbar-Hintergrund

    // ── Externe Theme-Bezeichner ─────────────────────────────────────────────
    public final String flatLafClass;    // Steuert welcher FlatLaf-LnF geladen wird
    public final String syntaxTheme;     // RSyntaxTextArea-Theme-Dateiname (ohne .xml)

    public Theme(String name,
                 Color background, Color backgroundLight, Color backgroundHover,
                 Color foreground,  Color foregroundDim,
                 Color accent,      Color accentGreen, Color accentRed,
                 Color border,      Color toolbar,
                 String flatLafClass, String syntaxTheme) {
        this.name             = name;
        this.background       = background;
        this.backgroundLight  = backgroundLight;
        this.backgroundHover  = backgroundHover;
        this.foreground       = foreground;
        this.foregroundDim    = foregroundDim;
        this.accent           = accent;
        this.accentGreen      = accentGreen;
        this.accentRed        = accentRed;
        this.border           = border;
        this.toolbar          = toolbar;
        this.flatLafClass     = flatLafClass;
        this.syntaxTheme      = syntaxTheme;
    }

    // ── Built-in Themes ──────────────────────────────────────────────────────

    public static final Theme DARK = new Theme(
        "Dark",
        new Color(43, 45, 48),       // background
        new Color(55, 58, 62),       // backgroundLight
        new Color(75, 78, 82),       // backgroundHover
        new Color(220, 220, 220),    // foreground
        new Color(180, 180, 180),    // foregroundDim
        new Color(255, 200, 80),     // accent
        new Color(80, 200, 120),     // accentGreen
        new Color(230, 75, 75),      // accentRed
        Color.DARK_GRAY,             // border
        new Color(43, 45, 48),       // toolbar
        "dark",                      // flatLafClass
        "dark"                       // syntaxTheme
    );

    public static final Theme LIGHT = new Theme(
        "Light",
        new Color(245, 245, 245),
        new Color(225, 225, 225),
        new Color(200, 200, 200),
        new Color(30, 30, 30),
        new Color(90, 90, 90),
        new Color(0, 100, 200),
        new Color(0, 140, 70),
        new Color(200, 40, 40),
        new Color(180, 180, 180),
        new Color(230, 230, 230),
        "light",
        "idea"
    );

    public static final Theme NORD = new Theme(
        "Nord",
        new Color(46, 52, 64),
        new Color(59, 66, 82),
        new Color(67, 76, 94),
        new Color(236, 239, 244),
        new Color(180, 190, 210),
        new Color(136, 192, 208),
        new Color(163, 190, 140),
        new Color(191, 97, 106),
        new Color(76, 86, 106),
        new Color(46, 52, 64),
        "mac-dark",
        "dark"
    );

    public static final Theme Fire = new Theme(
        "Fire",
        new Color(15, 10, 10),       // Noch dunkleres Hintergrund-Schwarz
        new Color(35, 20, 20),       
        new Color(55, 25, 25),       
        new Color(245, 245, 245),    
        new Color(150, 130, 130),    
        new Color(255, 0, 0),        // Akzent: Richtiges reines Rot (FF0000)
        new Color(50, 180, 90),      
        new Color(255, 0, 0),        
        new Color(45, 30, 30),       
        new Color(15, 10, 10),       
        "dark",                      
        "monokai"
    );

    public static final Theme MONOKAI = new Theme(
        "Monokai",
        new Color(39, 40, 34),
        new Color(50, 51, 44),
        new Color(62, 63, 55),
        new Color(248, 248, 242),
        new Color(180, 180, 170),
        new Color(230, 219, 116),
        new Color(166, 226, 46),
        new Color(249, 38, 114),
        new Color(70, 70, 60),
        new Color(39, 40, 34),
        "dark",
        "monokai"
    );

    public static final Theme SOLARIZED = new Theme(
        "Solarized Dark",
        new Color(0, 43, 54),
        new Color(7, 54, 66),
        new Color(15, 70, 85),
        new Color(131, 148, 150),
        new Color(88, 110, 117),
        new Color(181, 137, 0),
        new Color(133, 153, 0),
        new Color(220, 50, 47),
        new Color(7, 54, 66),
        new Color(0, 43, 54),
        "mac-dark",
        "dark"
    );

    // ── Registry ─────────────────────────────────────────────────────────────

    // Fire wurde hier in das Array eingefügt
    public static final Theme[] ALL = { DARK, LIGHT, NORD, Fire, MONOKAI, SOLARIZED };

    public static Theme byName(String name) {
        if (name == null) return DARK;
        for (Theme t : ALL)
            if (t.name.equals(name)) return t;
        return DARK;
    }
}