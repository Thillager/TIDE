import com.formdev.flatlaf.FlatDarkLaf;

import ui.MainWindow;
import update.UpdateManager;
import config.TIDEPreferences;
import config.Theme;

import javax.swing.*;
import java.awt.*;

public class ThIDE {

	public static void main(String[] args) {

		try {
			Toolkit.getDefaultToolkit().getDesktopProperty("awt.appID");
			System.setProperty("sun.awt.warmup", "false");
			// Setze hier einen eindeutigen Namen für deine App (z.B. "Thillager.ThIDE.4.7")
			Class<?> shellClass = Class.forName("com.sun.jna.platform.win32.Shell32");
			// Alternativ über jni/jna falls im Projekt, ansonsten reicht oft der obige jpackage-Wrapper
		} catch (Exception e) {
			// Ignorieren auf Linux/Mac
		}

		String os = System.getProperty("os.name", "").toLowerCase();

		// ── 1. HARDWARE-BESCHLEUNIGUNG ──────────────────────────────────────
		// Moegliche Werte: "auto" | "on" | "off"
		// "auto" erkennt automatisch ob JAR- oder jpackage-Start

		UpdateManager envCheck = new UpdateManager(null, null, "", "");
		String hwMode = TIDEPreferences.getHwAccelMode();

		boolean enableAccel;
		switch (hwMode) {
			case "on"  -> {
				 enableAccel = true;
			}
			case "off" -> {
				enableAccel = false;
			}
			default    -> {
				// auto: JAR-Start = kein HW-Accel, jpackage-Start = HW-Accel an
				enableAccel = !envCheck.isRunningAsJar();
			}
		}

		if (!enableAccel) {
			// Alle potenziell fehlerhaften Grafik-Pipelines deaktivieren
			System.setProperty("sun.java2d.d3d",        "false");
			System.setProperty("sun.java2d.opengl",     "false");
			System.setProperty("sun.java2d.xrender",    "false");
			System.setProperty("swing.bufferPerWindow", "false");
		} else {
			if (os.contains("win")) {
				// Windows: Direct3D ist schneller als OpenGL
				System.setProperty("sun.java2d.d3d",     "true");
				System.setProperty("sun.java2d.noddraw", "false");
			} else {
				// Linux / macOS: OpenGL-Pipeline
				System.setProperty("sun.java2d.opengl",  "true");
			}
			// VolatileImage immer im VRAM halten (wichtig fuer Blur-Effekt)
			System.setProperty("sun.java2d.accthreshold", "0");
		}

		String savedTheme = config.TIDEPreferences.getTheme();
		config.Theme currentTheme = config.Theme.byName(savedTheme);

		// ── 2. FlatLaf-Theme ─────────────────────────────────────────────────
		// Das eigentliche Theme (korrekte FlatLaf-LnF-Klasse bzw. für Fire/
		// eigene Themes die zugehörige .properties-Ressource, siehe
		// MainWindow.applyFlatLafTheme) wird beim Erzeugen von MainWindow
		// gesetzt. Hier werden bewusst keine einzelnen UIManager-Farbwerte
		// mehr manuell vorab überschrieben - das lief vorher ohnehin ins
		// Leere, da UIManager.setLookAndFeel(...) die komplette UIDefaults-
		// Tabelle ersetzt und solche Vorab-Overrides dabei verwirft.

		// ── 3. GUI START ───────────────────────────────────────────────────
		SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
	}
}