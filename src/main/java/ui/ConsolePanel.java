package ui;

import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

import config.TIDEProperties;
import config.Theme;

@SuppressWarnings({"serial", "this-escape"})
public class ConsolePanel extends JPanel {

    private JTextPane consolePane;
    private JTextField terminalInput;

    public ConsolePanel() {
        super(new BorderLayout());

        Theme t = MainWindow.THEME;

        consolePane = new JTextPane();
        consolePane.setBackground(t.background);
        consolePane.setForeground(t.foreground);
        consolePane.setFont(new Font("Consolas", Font.PLAIN, 14));
        consolePane.setEditable(false);

        ((javax.swing.text.DefaultCaret) consolePane.getCaret())
            .setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);

        JScrollPane consoleScroll = new JScrollPane(consolePane);
        consoleScroll.setBorder(null);
        consoleScroll.setBackground(t.background);
        consoleScroll.getViewport().setBackground(t.background);

        terminalInput = new JTextField();
        terminalInput.setBackground(t.backgroundLight);
        terminalInput.setForeground(t.foreground);
        terminalInput.setCaretColor(t.accentGreen);
        terminalInput.setFont(new Font("Consolas", Font.PLAIN, 14));
        terminalInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, t.border),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        add(consoleScroll, BorderLayout.CENTER);
        add(terminalInput, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(0, TIDEProperties.CONSOLE_HEIGHT));
    }

    public JTextPane getConsolePane() { return consolePane; }
    public JTextField getTerminalInput() { return terminalInput; }

    public void log(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = consolePane.getStyledDocument();
            if (doc.getLength() > TIDEProperties.CONSOLE_MAX_CHARS) {
                try {
                    doc.remove(0, TIDEProperties.CONSOLE_TRIM_CHARS);
                } catch (Exception e) {
				e.printStackTrace();
                	}
            }
            Style style = consolePane.addStyle("style", null);
            StyleConstants.setForeground(style, color);
            try {
                doc.insertString(doc.getLength(), msg, style);
                if (config.TIDEPreferences.getConsoleAutoScroll()) {
                    consolePane.setCaretPosition(doc.getLength());
                }
            } catch (Exception e) {
            	e.printStackTrace();
            	}
        });
    }

    public void clear() {
        consolePane.setText("");
    }
}