package ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.*;

@SuppressWarnings({"serial", "this-escape"})
public class OutlinePanel extends JPanel {

    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "^\\s*(public|protected|private|abstract|final|static)*\\s*" +
        "(class|interface|enum|record)\\s+(\\w+)",
        Pattern.MULTILINE
    );

    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "^[ \\t]+(public|protected|private|static|final|synchronized|abstract|native)*" +
        "(\\s+(public|protected|private|static|final|synchronized|abstract|native))*" +
        "\\s+[\\w<>\\[\\],\\.]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(throws[^{]*)?\\{",
        Pattern.MULTILINE
    );

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode root;
    private final JLabel titleLabel;
    private final JScrollPane scrollPane;
    private RSyntaxTextArea currentEditor;
    private boolean collapsed = false;
    private String currentFileName;

    public OutlinePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(200, 0));

        // Header
        titleLabel = new JLabel("  OUTLINE");
        titleLabel.setForeground(new Color(187, 187, 187));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setBorder(new EmptyBorder(8, 6, 8, 6));
        titleLabel.setOpaque(false);

        JButton toggleBtn = new JButton("⊟");
        toggleBtn.setForeground(new Color(187, 187, 187));
        toggleBtn.setOpaque(false);
        toggleBtn.setContentAreaFilled(false);
        toggleBtn.setBorderPainted(false);
        toggleBtn.setFocusPainted(false);
        toggleBtn.setFont(toggleBtn.getFont().deriveFont(13f));
        toggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleBtn.setToolTipText("Outline ein-/ausblenden");

        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(20, 20, 20, 120));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(toggleBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Tree
        root = new DefaultMutableTreeNode("root");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setOpaque(false);
        tree.setForeground(new Color(220, 220, 220));
        tree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tree.setBorder(new EmptyBorder(4, 0, 4, 0));
        tree.setRowHeight(20);
        tree.setCellRenderer(new OutlineTreeCellRenderer());

        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                // Hier wurde 'e' hinzugefügt, um die Koordinaten zu übergeben
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    jumpToSelected(e); 
                }
            }
        });
        tree.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                // Nutzt die parameterlose Variante für Tastatursteuerung
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    jumpToSelected();
                }
            }
        });

        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scrollPane, BorderLayout.CENTER);

        // Toggle
        toggleBtn.addActionListener(e -> {
            collapsed = !collapsed;
            scrollPane.setVisible(!collapsed);
            toggleBtn.setText(collapsed ? "⊞" : "⊟");
            setPreferredSize(new Dimension(collapsed ? 24 : 200, 0));
            revalidate();
            repaint();
            Container p = getParent();
            if (p instanceof JSplitPane split) {
                split.setDividerLocation(collapsed ? 1.0 : 0.8);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(25, 25, 26, 80)); // niedrig = durchsichtiger
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    public void refresh(RSyntaxTextArea textArea, String fileName) {
        this.currentEditor = textArea;
        this.currentFileName = fileName;
        root.removeAllChildren();

        if (textArea == null || fileName == null) {
            model.reload();
            return;
        }

        titleLabel.setText("  " + fileName.toUpperCase());

        String code = textArea.getText();
        if (code == null || code.isBlank()) {
            model.reload();
            return;
        }

        boolean isJava   = fileName.endsWith(".java");
        boolean isPython = fileName.endsWith(".py");
        boolean isCpp    = fileName.endsWith(".cpp") || fileName.endsWith(".c") || fileName.endsWith(".h");

        if (isJava)        buildJavaOutline(code);
        else if (isPython) buildPythonOutline(code);
        else if (isCpp)    buildCppOutline(code);

        model.reload();
        expandAll();
    }

    private void buildJavaOutline(String code) {
        Matcher cm = CLASS_PATTERN.matcher(code);
        java.util.List<int[]> classRanges = new java.util.ArrayList<>();
        java.util.List<DefaultMutableTreeNode> classNodes = new java.util.ArrayList<>();

        while (cm.find()) {
            String kind = cm.group(2);
            String name = cm.group(3);
            String icon = switch (kind) {
                case "interface" -> "◇ ";
                case "enum"      -> "● ";
                case "record"    -> "▣ ";
                default          -> "◈ ";
            };
            OutlineNode node = new OutlineNode(icon + name, cm.start(), NodeType.CLASS);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            root.add(treeNode);
            classRanges.add(new int[]{cm.start()});
            classNodes.add(treeNode);
        }

        Matcher mm = METHOD_PATTERN.matcher(code);
        while (mm.find()) {
            String mName = extractMethodName(mm.group(0));
            if (mName == null || isJavaKeyword(mName)) continue;
            OutlineNode node = new OutlineNode("⊙ " + mName + "()", mm.start(), NodeType.METHOD);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            DefaultMutableTreeNode parent = findParentClass(classRanges, classNodes, mm.start());
            if (parent != null) parent.add(treeNode);
            else root.add(treeNode);
        }
    }

    private void buildPythonOutline(String code) {
        Pattern classP  = Pattern.compile("^class\\s+(\\w+)", Pattern.MULTILINE);
        Pattern methodP = Pattern.compile("^[ \\t]+def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
        Pattern funcP   = Pattern.compile("^def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);

        java.util.List<int[]> classRanges = new java.util.ArrayList<>();
        java.util.List<DefaultMutableTreeNode> classNodes = new java.util.ArrayList<>();

        Matcher cm = classP.matcher(code);
        while (cm.find()) {
            OutlineNode node = new OutlineNode("◈ " + cm.group(1), cm.start(), NodeType.CLASS);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            root.add(treeNode);
            classRanges.add(new int[]{cm.start()});
            classNodes.add(treeNode);
        }

        Matcher mm = methodP.matcher(code);
        while (mm.find()) {
            OutlineNode node = new OutlineNode("⊙ " + mm.group(1) + "()", mm.start(), NodeType.METHOD);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            DefaultMutableTreeNode parent = findParentClass(classRanges, classNodes, mm.start());
            if (parent != null) parent.add(treeNode);
            else root.add(treeNode);
        }

        Matcher fm = funcP.matcher(code);
        while (fm.find()) {
            OutlineNode node = new OutlineNode("⊙ " + fm.group(1) + "()", fm.start(), NodeType.METHOD);
            root.add(new DefaultMutableTreeNode(node));
        }
    }

    private void buildCppOutline(String code) {
        Pattern classP = Pattern.compile("^(class|struct|enum)\\s+(\\w+)", Pattern.MULTILINE);
        Pattern funcP  = Pattern.compile("^[\\w:*&<>]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(const)?\\s*\\{", Pattern.MULTILINE);

        java.util.List<int[]> classRanges = new java.util.ArrayList<>();
        java.util.List<DefaultMutableTreeNode> classNodes = new java.util.ArrayList<>();

        Matcher cm = classP.matcher(code);
        while (cm.find()) {
            OutlineNode node = new OutlineNode("◈ " + cm.group(2), cm.start(), NodeType.CLASS);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            root.add(treeNode);
            classRanges.add(new int[]{cm.start()});
            classNodes.add(treeNode);
        }

        Matcher fm = funcP.matcher(code);
        while (fm.find()) {
            String name = fm.group(1);
            if (name == null || isCppKeyword(name)) continue;
            OutlineNode node = new OutlineNode("⊙ " + name + "()", fm.start(), NodeType.METHOD);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            DefaultMutableTreeNode parent = findParentClass(classRanges, classNodes, fm.start());
            if (parent != null) parent.add(treeNode);
            else root.add(treeNode);
        }
    }

    private DefaultMutableTreeNode findParentClass(
            java.util.List<int[]> ranges,
            java.util.List<DefaultMutableTreeNode> nodes,
            int pos) {
        DefaultMutableTreeNode best = null;
        int bestPos = -1;
        for (int i = 0; i < ranges.size(); i++) {
            int cp = ranges.get(i)[0];
            if (cp < pos && cp > bestPos) {
                bestPos = cp;
                best = nodes.get(i);
            }
        }
        return best;
    }

    private String extractMethodName(String signature) {
        Pattern p = Pattern.compile("(\\w+)\\s*\\(");
        Matcher m = p.matcher(signature);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    private boolean isJavaKeyword(String s) {
        return switch (s) {
            case "if","else","while","for","switch","try","catch","return",
                 "new","void","int","long","double","float","boolean","char",
                 "byte","short","class","interface","enum","extends","implements",
                 "throws","throw","super","this","static","final","abstract" -> true;
            default -> false;
        };
    }

    private boolean isCppKeyword(String s) {
        return switch (s) {
            case "if","else","while","for","switch","return","new","delete",
                 "void","int","long","double","float","bool","char","struct",
                 "class","namespace","template","typename","public","private",
                 "protected","static","const","virtual","override" -> true;
            default -> false;
        };
    }

    // NEU: Die parameterlose Variante für Tastatur-Fokus (Enter-Taste)
    private void jumpToSelected() {
        if (currentEditor == null) return;

        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        Object oldUserObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();

        refresh(currentEditor, this.currentFileName);

        DefaultMutableTreeNode newTargetNode = findNodeByObject(root, oldUserObj);
        if (newTargetNode == null) return;

        TreePath newPath = new TreePath(newTargetNode.getPath());
        tree.setSelectionPath(newPath);
        
        if (oldUserObj instanceof OutlineNode node) {
            executeJump(node);
        }
    }

    // Für Mausklicks (Nutzt Koordinaten, um den Knoten nach dem Refresh zu fangen)
    private void jumpToSelected(MouseEvent e) {
        if (currentEditor == null) return;

        refresh(currentEditor, this.currentFileName);

        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;

        tree.setSelectionPath(path);

        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (obj instanceof OutlineNode node) {
            executeJump(node);
        }
    }

    // Der eigentliche Editor-Sprung, zentral für Maus und Tastatur
    private void executeJump(OutlineNode node) {
        String code = currentEditor.getText();
        int pos = node.offset();
        if (pos >= 0 && pos < code.length()) {
            currentEditor.setCaretPosition(pos);
            currentEditor.requestFocusInWindow();
            try {
                int line = currentEditor.getLineOfOffset(pos);
                Rectangle rect = currentEditor.modelToView2D(
                        currentEditor.getLineStartOffset(line)).getBounds();
                currentEditor.scrollRectToVisible(rect);
            } catch (Exception e) {
            	e.printStackTrace();
            	}
        }
    }

    // Hilfsmethode, um ein Element nach dem Refresh im Baum wiederzufinden (für ENTER)
    private DefaultMutableTreeNode findNodeByObject(DefaultMutableTreeNode parent, Object obj) {
        if (parent.getUserObject() != null && parent.getUserObject().toString().equals(obj.toString())) {
            return parent;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DefaultMutableTreeNode result = findNodeByObject(child, obj);
            if (result != null) return result;
        }
        return null;
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private enum NodeType { CLASS, METHOD }

    private record OutlineNode(String label, int offset, NodeType type) {
        @Override public String toString() { return label; }
    }

    private static class OutlineTreeCellRenderer extends DefaultTreeCellRenderer {

        private static final Color COLOR_CLASS  = new Color(78, 201, 176);
        private static final Color COLOR_METHOD = new Color(220, 220, 170);
        private static final Color COLOR_SEL    = new Color(55, 65, 85, 160);

        public OutlineTreeCellRenderer() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            Object userObj = ((DefaultMutableTreeNode) value).getUserObject();

            setOpaque(sel);
            setBackground(sel ? COLOR_SEL : new Color(0, 0, 0, 0));
            setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
            setBackgroundSelectionColor(COLOR_SEL);
            setBorderSelectionColor(new Color(0, 0, 0, 0));

            if (userObj instanceof OutlineNode node) {
                setText(node.label());
                setForeground(node.type() == NodeType.CLASS ? COLOR_CLASS : COLOR_METHOD);
            } else {
                setForeground(new Color(204, 204, 204));
            }

            setFont(new Font("Monospaced", Font.PLAIN, 12));
            setBorder(new EmptyBorder(1, 4, 1, 4));
            return this;
        }
    }
}