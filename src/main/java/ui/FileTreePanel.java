package ui;

import model.FileNode;
import config.LanguageManager;
import config.TIDEProperties;
import config.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings({"serial", "this-escape"})
public class FileTreePanel extends JScrollPane {

	private JTree fileTree;
	private DefaultTreeModel treeModel;
	private ConsolePanel consolePanel;
	private JFrame parent;

	private File clipboard = null;
	private boolean isCut  = false;

	private Consumer<File> onFileOpen;

	public FileTreePanel(JFrame parent, ConsolePanel consolePanel, Consumer<File> onFileOpen) {
		this.parent       = parent;
		this.consolePanel = consolePanel;
		this.onFileOpen   = onFileOpen;

		Theme t = MainWindow.THEME;

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(LanguageManager.t("folder.opened"));
		treeModel = new DefaultTreeModel(root);
		fileTree  = new JTree(treeModel);
		fileTree.setBackground(t.background);
		fileTree.setForeground(t.foreground);
		fileTree.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Custom cell renderer für Theme-Farben
		fileTree.setCellRenderer(new DefaultTreeCellRenderer() {
				{
					setBackgroundNonSelectionColor(t.background);
					setBackgroundSelectionColor(t.backgroundLight);
					setTextNonSelectionColor(t.foreground);
					setTextSelectionColor(t.foreground);
					setBorderSelectionColor(t.border);
				}
			});

		fileTree.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent me) {
					if (me.getClickCount() == 2) {
						DefaultMutableTreeNode node =
						(DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
						if (node != null && node.getUserObject() instanceof FileNode) {
							File selectedFile = ((FileNode) node.getUserObject()).getFile();
							if (selectedFile.isFile()) onFileOpen.accept(selectedFile);
						}
					}
				}
			});

		setViewportView(fileTree);
		getViewport().setBackground(t.background);
		setBackground(t.background);
		setPreferredSize(new Dimension(TIDEProperties.FILETREE_WIDTH, 0));
		setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, t.border));
	}

	// ======= Baum aktualisieren (mit Expand-State-Erhalt) =======

	public void updateFileTree(File rootFolder) {
		if (rootFolder == null) return;

		Set<String> expandedPaths = getExpandedPaths();

		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FileNode(rootFolder));
		buildTree(rootFolder, rootNode);
		treeModel.setRoot(rootNode);

		restoreExpandedPaths(rootNode, expandedPaths);

		fileTree.revalidate();
		fileTree.repaint();
		revalidate();
		repaint();
	}

	private Set<String> getExpandedPaths() {
		Set<String> paths = new HashSet<>();
		int rowCount = fileTree.getRowCount();
		for (int i = 0; i < rowCount; i++) {
			if (fileTree.isExpanded(i)) {
				TreePath tp = fileTree.getPathForRow(i);
				if (tp != null) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
					if (node.getUserObject() instanceof FileNode fn) {
						paths.add(fn.getFile().getAbsolutePath());
					}
				}
			}
		}
		return paths;
	}

	private void restoreExpandedPaths(DefaultMutableTreeNode root, Set<String> expandedPaths) {
		if (expandedPaths.isEmpty()) return;
		Enumeration<?> e = root.depthFirstEnumeration();
		while (e.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
			if (node.getUserObject() instanceof FileNode fn) {
				if (expandedPaths.contains(fn.getFile().getAbsolutePath())) {
					fileTree.expandPath(new TreePath(node.getPath()));
				}
			}
		}
	}

	private void buildTree(File folder, DefaultMutableTreeNode node) {
		File[] files = folder.listFiles();
		if (files == null) return;
		Arrays.sort(files, (a, b) -> a.isDirectory() == b.isDirectory()
			? a.getName().compareToIgnoreCase(b.getName())
			: a.isDirectory() ? -1 : 1);
		for (File file : files) {
			if (file.getName().startsWith(".") || file.getName().equals("out")) continue;
			DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new FileNode(file));
			node.add(childNode);
			if (file.isDirectory()) buildTree(file, childNode);
		}
	}

	// ======= Kontextmenü =======

	public void showFileTreePopup(MouseEvent me, File currentProjectFolder, Runnable refreshCallback) {
		JPopupMenu popup = new JPopupMenu();
		TreePath path = fileTree.getPathForLocation(me.getX(), me.getY());

		JMenuItem neuDatei      = new JMenuItem(LanguageManager.t("neuDatei"));
		JMenuItem neuOrdner     = new JMenuItem(LanguageManager.t("neuOrdner"));
		JMenuItem umbenennen    = new JMenuItem(LanguageManager.t("umbenennen"));
		JMenuItem delete        = new JMenuItem(LanguageManager.t("delete"));
		JMenuItem explorer      = new JMenuItem(LanguageManager.t("explorer"));
		JMenuItem copy          = new JMenuItem(LanguageManager.t("copy"));
		JMenuItem cut           = new JMenuItem(LanguageManager.t("cut"));
		JMenuItem paste         = new JMenuItem(LanguageManager.t("paste"));
		JMenuItem aktualisieren = new JMenuItem(LanguageManager.t("aktualisieren"));

		aktualisieren.addActionListener(e -> {
				if (currentProjectFolder != null) updateFileTree(currentProjectFolder);
			});

		if (path != null) {
			fileTree.setSelectionPath(path);
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
			File clickedFile = null;
			if (node.getUserObject() instanceof FileNode fn) {
				clickedFile = fn.getFile();
			}
			final File finalClickedFile = clickedFile;

			neuDatei.addActionListener(e   -> createNewFile(finalClickedFile, currentProjectFolder));
			neuOrdner.addActionListener(e  -> createNewFolder(finalClickedFile, currentProjectFolder));
			copy.addActionListener(e       -> { clipboard = finalClickedFile; isCut = false;
					consolePanel.log("[INFO] Kopiert: " + finalClickedFile.getName() + "\n", Color.LIGHT_GRAY); });
			cut.addActionListener(e        -> { clipboard = finalClickedFile; isCut = true;
					consolePanel.log("[INFO] Ausgeschnitten: " + finalClickedFile.getName() + "\n", Color.LIGHT_GRAY); });
			paste.addActionListener(e      -> pasteFile(finalClickedFile, currentProjectFolder));
			delete.addActionListener(e     -> deleteFile(finalClickedFile, currentProjectFolder));
			umbenennen.addActionListener(e -> renameFile(finalClickedFile, currentProjectFolder));
			explorer.addActionListener(e   -> openExplorer(finalClickedFile));

			paste.setEnabled(clipboard != null);

			popup.add(neuDatei);
			popup.add(neuOrdner);
			popup.addSeparator();
			popup.add(umbenennen);
			popup.add(delete);
			popup.addSeparator();
			popup.add(explorer);
			popup.addSeparator();
			popup.add(copy);
			popup.add(cut);
			popup.add(paste);
			popup.addSeparator();
			popup.add(aktualisieren);
		} else {
			neuDatei.addActionListener(e  -> createNewFile(currentProjectFolder, currentProjectFolder));
			neuOrdner.addActionListener(e -> createNewFolder(currentProjectFolder, currentProjectFolder));
			paste.addActionListener(e     -> pasteFile(currentProjectFolder, currentProjectFolder));
			paste.setEnabled(clipboard != null);

			popup.add(neuDatei);
			popup.add(neuOrdner);
			popup.addSeparator();
			popup.add(paste);
			popup.addSeparator();
			popup.add(aktualisieren);
		}

		popup.show(fileTree, me.getX(), me.getY());
	}

	// ======= Dateioperationen =======

	public void createNewFile(File target, File currentProjectFolder) {
		if (target == null || currentProjectFolder == null) return;
		File zielOrdner = target.isDirectory() ? target : target.getParentFile();
		String name = (String) JOptionPane.showInputDialog(
			parent, LanguageManager.t("file.name"),
			"Neue Datei", JOptionPane.PLAIN_MESSAGE, null, null, "");
		if (name != null && !name.trim().isEmpty()) {
			try {
				new File(zielOrdner, name.trim()).createNewFile();
				updateFileTree(currentProjectFolder);
			} catch (IOException ex) {
				consolePanel.log("[FEHLER] Datei konnte nicht erstellt werden.\n", Color.RED);
			}
		}
	}

	public void createNewFolder(File target, File currentProjectFolder) {
		if (target == null || currentProjectFolder == null) return;
		File zielOrdner = target.isDirectory() ? target : target.getParentFile();
		String name = (String) JOptionPane.showInputDialog(
			parent, "Wie soll der neue Ordner heißen?",
			"Neuer Ordner", JOptionPane.PLAIN_MESSAGE, null, null, "");
		if (name != null && !name.trim().isEmpty()) {
			new File(zielOrdner, name.trim()).mkdirs();
			updateFileTree(currentProjectFolder);
		}
	}

	private void pasteFile(File target, File currentProjectFolder) {
		if (clipboard == null || currentProjectFolder == null) return;
		File zielOrdner = (target != null && target.isDirectory()) ? target
		: (target != null ? target.getParentFile() : currentProjectFolder);
		File dest = new File(zielOrdner, clipboard.getName());

		try {
			if (clipboard.isDirectory()) {
				copyDirectoryRecursive(clipboard, dest);
			} else {
				Files.copy(clipboard.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			if (isCut) {
				Files.walk(clipboard.toPath())
				.sorted(java.util.Comparator.reverseOrder())
				.map(java.nio.file.Path::toFile)
				.forEach(File::delete);
				clipboard = null;
				isCut     = false;
			}
			updateFileTree(currentProjectFolder);
			consolePanel.log("[INFO] Eingefügt: " + dest.getName() + "\n", Color.GREEN);
		} catch (IOException ex) {
			consolePanel.log("[FEHLER] Einfügen fehlgeschlagen: " + ex.getMessage() + "\n", Color.RED);
		}
	}

	private void copyDirectoryRecursive(File src, File dest) throws IOException {
		if (src.isDirectory()) {
			dest.mkdirs();
			for (File child : src.listFiles()) {
				copyDirectoryRecursive(child, new File(dest, child.getName()));
			}
		} else {
			Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

private void deleteFile(File file, File currentProjectFolder) {
    if (file == null) return;

    int confirm = JOptionPane.showConfirmDialog(
            parent,
            "'" + file.getName() + "' wirklich löschen?",
            "Löschen bestätigen",
            JOptionPane.YES_NO_OPTION
    );

    if (confirm != JOptionPane.YES_OPTION) {
        return;
    }

    try (var paths = Files.walk(file.toPath())) {

        paths.sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        updateFileTree(currentProjectFolder);

        consolePanel.log(
                "[INFO] Gelöscht: " + file.getName() + "\n",
                Color.LIGHT_GRAY
        );

    } catch (IOException ex) {
        consolePanel.log(
                "[FEHLER] Löschen fehlgeschlagen: " +
                ex.getMessage() + "\n",
                Color.RED
        );

    } catch (RuntimeException ex) {
        Throwable cause = ex.getCause();

        consolePanel.log(
                "[FEHLER] Löschen fehlgeschlagen: " +
                (cause != null ? cause.getMessage() : ex.getMessage()) +
                "\n",
                Color.RED
        );
    }
}


	private void renameFile(File file, File currentProjectFolder) {
		if (file == null) return;
		String name = (String) JOptionPane.showInputDialog(
			parent, "Neuer Name:", "Umbenennen",
			JOptionPane.PLAIN_MESSAGE, null, null, file.getName());
		if (name != null && !name.trim().isEmpty()) {
			file.renameTo(new File(file.getParentFile(), name.trim()));
			updateFileTree(currentProjectFolder);
		}
	}

	private void openExplorer(File file) {
		try {
			File ordner = file.isDirectory() ? file : file.getParentFile();
			Desktop.getDesktop().open(ordner);
		} catch (IOException ex) {
			consolePanel.log("[FEHLER] Explorer konnte nicht geöffnet werden.\n", Color.RED);
		}
	}
}