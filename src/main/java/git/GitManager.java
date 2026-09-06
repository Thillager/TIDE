package git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import ui.ConsolePanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class GitManager {

	private final JFrame parent;
	private final ConsolePanel consolePanel;
	private File currentProjectFolder;

	private Runnable onRefreshFileTree;
	private Runnable onSaveCurrentFile;

	public GitManager(JFrame parent, ConsolePanel consolePanel) {
		this.parent       = parent;
		this.consolePanel = consolePanel;
	}

	public void setCurrentProjectFolder(File folder) {
		this.currentProjectFolder = folder;
	}

	public void setOnRefreshFileTree(Runnable r) {
		this.onRefreshFileTree = r;
	}

	public void setOnSaveCurrentFile(Runnable r) {
		this.onSaveCurrentFile = r;
	}

	// ======= Credentials =======

	public String[] loadGitCredentials() {
		File credFile = new File(System.getProperty("user.home"), ".git-credentials");
		if (credFile.exists()) {
			try {
				java.util.List<String> lines = Files.readAllLines(credFile.toPath());
				for (String line : lines) {
					line = line.trim();
					if (line.contains("github.com") && line.startsWith("https://")) {
						String part   = line.substring("https://".length());
						int atIdx     = part.lastIndexOf('@');
						if (atIdx > 0) {
							String userPass = part.substring(0, atIdx);
							int colonIdx    = userPass.indexOf(':');
							if (colonIdx > 0) {
								return new String[]{
									userPass.substring(0, colonIdx),
									userPass.substring(colonIdx + 1)
								};
							}
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				}
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("git", "credential", "fill");
			pb.redirectErrorStream(false);
			pb.environment().put("HOME", System.getProperty("user.home"));
			pb.directory(new File(System.getProperty("user.home")));
			Process p = pb.start();
			new Thread(() -> {
					try {
						p.getOutputStream().write("protocol=https\nhost=github.com\n\n".getBytes());
						p.getOutputStream().flush();
						p.getOutputStream().close();
					} catch (Exception ex) {
						ex.printStackTrace();
						}
				}).start();
			String out = new String(p.getInputStream().readAllBytes()).trim();
			p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
			String user = null, pass = null;
			for (String l : out.split("[\r\n]+")) {
				if (l.startsWith("username=")) user = l.substring(9).trim();
				if (l.startsWith("password=")) pass = l.substring(9).trim();
			}
			if (user != null && pass != null && !user.isEmpty() && !pass.isEmpty()) {
				return new String[]{user, pass};
			}
		} catch (Exception e) {
			e.printStackTrace();
			}

		if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
			try {
				Process p = new ProcessBuilder("cmdkey", "/list:git:https://github.com")
				.redirectErrorStream(true)
				.directory(new File(System.getProperty("user.home")))
				.start();
				String out = new String(p.getInputStream().readAllBytes()).trim();
				p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
				for (String l : out.split("[\r\n]+")) {
					l = l.trim();
					if (l.startsWith("Benutzername:") || l.startsWith("User name:")) {
						String user = l.substring(l.indexOf(':') + 1).trim();
						if (!user.isEmpty()) {
							return new String[]{user, ""};
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				}
		}

		// 4. Nur Username aus git config
		try {
			ProcessBuilder pb = new ProcessBuilder("git", "config", "--global", "user.name");
			pb.redirectErrorStream(true);
			pb.directory(new File(System.getProperty("user.home")));
			Process p = pb.start();
			String name = new String(p.getInputStream().readAllBytes()).trim();
			p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
			if (!name.isEmpty()) return new String[]{name, null};
		} catch (Exception e) {
			e.printStackTrace();
			}

		return null;
	}

	// Oeffnet das Git-Repo im aktuellen Projektordner
	private Git openRepo() throws IOException {
		if (currentProjectFolder == null) throw new IOException("Kein Projektordner geöffnet.");
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repo = builder.findGitDir(currentProjectFolder).readEnvironment().build();
		return new Git(repo);
	}

	private UsernamePasswordCredentialsProvider getCredentials() {
		String[] c = loadGitCredentials();
		if (c == null || c[1] == null || c[1].isEmpty()) return null;
		return new UsernamePasswordCredentialsProvider(c[0], c[1]);
	}

	// ======= Git-Operationen =======

	public void gitCommit() {
		if (currentProjectFolder == null) {
			consolePanel.log("[GIT] Bitte zuerst einen Projektordner öffnen.\n", Color.ORANGE);
			return;
		}
		if (onSaveCurrentFile != null) onSaveCurrentFile.run();

		String[] creds   = loadGitCredentials();
		String userHint  = creds != null ? " (als " + creds[0] + ")" : " (nicht angemeldet)";

		String message = (String) JOptionPane.showInputDialog(parent,
			"Commit-Nachricht:" + userHint,
			"Git Commit", JOptionPane.PLAIN_MESSAGE, null, null, "Update");
		if (message == null || message.trim().isEmpty()) return;

		new Thread(() -> {
				consolePanel.log("[GIT] Committe Änderungen...\n", new Color(255, 200, 80));
				try (Git git = openRepo()) {
					Status status = git.status().call();
					if (status.isClean()) {
						consolePanel.log("[GIT] Nichts zu committen – alles sauber.\n", Color.LIGHT_GRAY);
						return;
					}
					git.add().addFilepattern(".").call();
					for (String missing : status.getMissing()) {
						git.rm().addFilepattern(missing).call();
					}
					RevCommit commit = git.commit().setMessage(message.trim()).call();
					consolePanel.log("[GIT] ✓ Commit: " + commit.getShortMessage()
						+ " [" + commit.abbreviate(7).name() + "]\n", new Color(80, 200, 120));
				} catch (Exception e) {
					consolePanel.log("[GIT FEHLER] Commit fehlgeschlagen: " + e.getMessage() + "\n", Color.RED);
					if (e.getMessage() != null && e.getMessage().contains("git dir not found")) {
						consolePanel.log("[GIT] Tipp: Repo noch nicht initialisiert. TBuild → Git → Lokales Repo erstellen.\n", Color.ORANGE);
					}
				}
			}).start();
	}

	public void gitPush() {
		if (currentProjectFolder == null) {
			consolePanel.log("[GIT] Bitte zuerst einen Projektordner öffnen.\n", Color.ORANGE);
			return;
		}
		UsernamePasswordCredentialsProvider cp = getCredentials();
		if (cp == null) {
			String[] creds = loadGitCredentials();
			if (creds != null && creds[0] != null) {
				consolePanel.log("[GIT] Git-Nutzer erkannt (" + creds[0] + "), aber kein Token gefunden.\n", Color.ORANGE);
				consolePanel.log("[GIT] Bitte in TBuild → Git → Anmelden einen PAT eintragen.\n", Color.ORANGE);
			} else {
				consolePanel.log("[GIT] Nicht angemeldet. Bitte in TBuild → Git → Anmelden.\n", Color.ORANGE);
			}
			return;
		}
		String[] creds = loadGitCredentials();
		new Thread(() -> {
				consolePanel.log("[GIT] Pushe zu Remote...\n", new Color(255, 200, 80));
				try (Git git = openRepo()) {
					git.push()
					.setCredentialsProvider(cp)
					.setRemote("origin")
					.call();
					consolePanel.log("[GIT] ✓ Push erfolgreich" + (creds != null ? " (als " + creds[0] + ")" : "") + ".\n",
						new Color(80, 200, 120));
				} catch (Exception e) {
					consolePanel.log("[GIT FEHLER] Push fehlgeschlagen: " + e.getMessage() + "\n", Color.RED);
					if (e.getMessage() != null && e.getMessage().contains("no remote")) {
						consolePanel.log("[GIT] Tipp: Kein Remote gesetzt. TBuild → Git → Remote hinzufügen.\n", Color.ORANGE);
					}
				}
			}).start();
	}

	public void gitPull() {
		if (currentProjectFolder == null) {
			consolePanel.log("[GIT] Bitte zuerst einen Projektordner öffnen.\n", Color.ORANGE);
			return;
		}
		UsernamePasswordCredentialsProvider cp = getCredentials();
		new Thread(() -> {
				consolePanel.log("[GIT] Pulle vom Remote...\n", new Color(255, 200, 80));
				try (Git git = openRepo()) {
					org.eclipse.jgit.api.PullCommand pullCmd = git.pull();
					if (cp != null) pullCmd.setCredentialsProvider(cp);
					PullResult result = pullCmd.call();
					if (result.isSuccessful()) {
						String mergeMsg = result.getMergeResult() != null
						? result.getMergeResult().getMergeStatus().toString()
						: "OK";
						consolePanel.log("[GIT] Pull erfolgreich: " + mergeMsg + "\n", new Color(80, 200, 120));
						if (onRefreshFileTree != null)
						SwingUtilities.invokeLater(onRefreshFileTree);
					} else {
						consolePanel.log("[GIT FEHLER] Pull nicht erfolgreich.\n", Color.RED);
					}
				} catch (Exception e) {
					consolePanel.log("[GIT FEHLER] Pull fehlgeschlagen: " + e.getMessage() + "\n", Color.RED);
				}
			}).start();
	}

	public void checkGitStatusOnOpen() {
		new Thread(() -> {
				String[] creds = loadGitCredentials();
				if (creds != null && creds[1] != null && !creds[1].isEmpty()) {
					consolePanel.log("[GIT]  Angemeldet als: " + creds[0] + "\n", new Color(255, 200, 80));
				} else if (creds != null && creds[0] != null) {
					consolePanel.log("[GIT]  Git-Nutzer: " + creds[0] + " (kein Token – Push/Pull nicht möglich ohne TBuild-Anmeldung)\n", Color.ORANGE);
				} else {
					consolePanel.log("[GIT]  Nicht angemeldet. TBuild → Git → Anmelden.\n", Color.ORANGE);
				}
				try (Git git = openRepo()) {
					String branch = git.getRepository().getBranch();
					Status status = git.status().call();
					consolePanel.log("[GIT]  Branch: " + branch, new Color(255, 200, 80));
					if (!status.isClean()) {
						int changed = status.getModified().size() + status.getAdded().size()
						+ status.getUntracked().size() + status.getRemoved().size();
						consolePanel.log("  (" + changed + " Änderungen)\n", Color.ORANGE);
					} else {
						consolePanel.log("  (sauber)\n", new Color(80, 200, 120));
					}
				} catch (Exception e) {
					e.printStackTrace();
					}
			}).start();
	}
}
