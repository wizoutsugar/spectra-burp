package burp;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class ResultsPanel extends JPanel {

    private final AuthMatrixModel model;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;

    private final MatrixTableModel tableModel;
    private final JTable table;
    private RequestReplayWorker activeWorker;

    private boolean autoRunEnabled = true;
    private JToggleButton autoRunBtn;
    private final javax.swing.Timer autoRunTimer;

    private final JTextArea requestViewer  = new JTextArea();
    private final JTextArea responseViewer = new JTextArea();
    private final JLabel statusBar = new JLabel(" Ready");

    public ResultsPanel(AuthMatrixModel model,
                        IBurpExtenderCallbacks callbacks,
                        IExtensionHelpers helpers) {
        this.model = model;
        this.callbacks = callbacks;
        this.helpers = helpers;

        tableModel = new MatrixTableModel(model, helpers);
        table = buildTable();

        // Debounce: wait 700 ms after the last change before firing auto-run
        autoRunTimer = new javax.swing.Timer(700, e -> runUntested());
        autoRunTimer.setRepeats(false);

        setLayout(new BorderLayout());
        setBackground(new Color(0x2B2B2B));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTablePanel(), buildDetailPanel());
        split.setResizeWeight(0.65);
        split.setDividerSize(5);
        split.setBackground(new Color(0x2B2B2B));

        add(buildToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        model.addListener(new AuthMatrixModel.Listener() {
            @Override public void usersChanged()    { SwingUtilities.invokeLater(() -> { tableModel.structureChanged(); adjustColumnWidths(); if (!model.consumeSuppressNextAutoRun()) scheduleAutoRun(); }); }
            @Override public void requestsChanged() { SwingUtilities.invokeLater(() -> { tableModel.dataChanged();      scheduleAutoRun(); }); }
            @Override public void cellChanged(String r, String u) { SwingUtilities.invokeLater(() -> { tableModel.dataChanged(); updateStatusBar(); }); }
        });
    }

    // ── Auto-run ───────────────────────────────────────────────────────────

    private void scheduleAutoRun() {
        if (!autoRunEnabled) return;
        autoRunTimer.restart();
    }

    /** Runs only cells that haven't been tested yet (respects forceRerun=false). */
    private void runUntested() {
        List<UserEntry> users = model.getVisibleUsers();
        List<RequestEntry> allReqs = model.getRequests();
        if (allReqs.isEmpty() || users.isEmpty()) return;

        // Only queue requests that have at least one UNTESTED cell for a visible user
        List<RequestEntry> pending = new ArrayList<>();
        for (RequestEntry req : allReqs) {
            for (UserEntry user : users) {
                if (model.getCell(req, user).getStatus() == ResultCell.TestStatus.UNTESTED) {
                    pending.add(req);
                    break;
                }
            }
        }

        if (!pending.isEmpty()) {
            startWorker(pending, users, false);
        }
    }

    // ── Table ──────────────────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(tableModel);
        t.setBackground(new Color(0x2B2B2B));
        t.setForeground(new Color(0xE0E0E0));
        t.setGridColor(new Color(0x3D3D3D));
        t.setSelectionBackground(new Color(0x214283));
        t.setSelectionForeground(Color.WHITE);
        t.setRowHeight(26);
        t.setShowGrid(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTableHeader header = t.getTableHeader();
        header.setBackground(new Color(0x1E1E1E));
        header.setForeground(new Color(0xC0C0C0));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setReorderingAllowed(false);

        t.setDefaultRenderer(Object.class, new CellRenderer());

        t.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail();
        });
        t.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail();
        });

        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                int col = t.columnAtPoint(e.getPoint());
                if (row >= 0 && col > 1) {
                    t.setRowSelectionInterval(row, row);
                    t.setColumnSelectionInterval(col, col);
                    if (SwingUtilities.isRightMouseButton(e)) showCellContextMenu(e, row, col);
                }
            }
        });

        return t;
    }

    private JScrollPane buildTablePanel() {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(new Color(0x2B2B2B));
        scroll.getViewport().setBackground(new Color(0x2B2B2B));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Re-distribute column widths whenever the visible area changes (panel resize, divider drag)
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { adjustColumnWidths(); }
        });
        return scroll;
    }

    /**
     * Sizes Method and user columns to their content, then stretches the URL column
     * to fill whatever space remains in the viewport.
     */
    private void adjustColumnWidths() {
        int colCount = table.getColumnCount();
        if (colCount == 0) return;

        int viewportWidth = table.getParent() != null ? table.getParent().getWidth() : 0;
        if (viewportWidth <= 0) return;

        TableColumnModel cm = table.getColumnModel();

        // Method column — fixed, just wide enough for "DELETE"
        int methodW = 72;
        cm.getColumn(0).setMinWidth(52);
        cm.getColumn(0).setMaxWidth(90);
        cm.getColumn(0).setPreferredWidth(methodW);

        // User columns — sized to their header label with a sensible cap
        FontMetrics hfm = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int userTotalW = 0;
        for (int i = 2; i < colCount; i++) {
            String name = table.getColumnName(i);
            int w = Math.max(80, Math.min(150, hfm.stringWidth(name) + 30));
            cm.getColumn(i).setMinWidth(70);
            cm.getColumn(i).setMaxWidth(180);
            cm.getColumn(i).setPreferredWidth(w);
            userTotalW += w;
        }

        // URL column — absorbs all remaining space
        int urlW = Math.max(120, viewportWidth - methodW - userTotalW);
        cm.getColumn(1).setMinWidth(100);
        cm.getColumn(1).setMaxWidth(Integer.MAX_VALUE);
        cm.getColumn(1).setPreferredWidth(urlW);
    }

    // ── Toolbar ────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setBackground(new Color(0x1E1E1E));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x444444)));

        // Auto-run toggle (primary control, leftmost)
        autoRunBtn = new JToggleButton("● Auto-run: ON");
        autoRunBtn.setSelected(true);
        styleToggle(autoRunBtn, new Color(0x2ECC71));
        autoRunBtn.addActionListener(e -> {
            autoRunEnabled = autoRunBtn.isSelected();
            if (autoRunEnabled) {
                autoRunBtn.setText("● Auto-run: ON");
                autoRunBtn.setBackground(new Color(0x2ECC71));
                scheduleAutoRun();
            } else {
                autoRunBtn.setText("○ Auto-run: OFF");
                autoRunBtn.setBackground(new Color(0x555555));
                autoRunTimer.stop();
            }
        });
        bar.add(autoRunBtn);

        bar.add(separator());

        JButton stop    = toolBtn("■ Stop",          new Color(0xE74C3C));
        JButton runAll  = toolBtn("▶ Run All",        new Color(0x4C9BE8));
        JButton runSel  = toolBtn("▶ Run Selected",  new Color(0x3A7BD5));

        stop.setToolTipText("Cancel the running tests");
        runAll.setToolTipText("Reset all results and re-run everything");
        runSel.setToolTipText("Reset and re-run selected rows only");

        stop.addActionListener(e   -> stopTests());
        runAll.addActionListener(e -> {
            model.resetCells();
            startWorker(model.getRequests(), model.getVisibleUsers(), true);
        });
        runSel.addActionListener(e -> runSelected());

        bar.add(stop);
        bar.add(runAll);
        bar.add(runSel);

        bar.add(separator());

        JButton clear     = toolBtn("Clear Results",   new Color(0x444444));
        JButton clearReqs = toolBtn("Clear Requests",  new Color(0x444444));
        clear.addActionListener(e    -> model.resetCells());
        clearReqs.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Remove all requests?", "Clear", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) model.clearRequests();
        });
        bar.add(clear);
        bar.add(clearReqs);

        return bar;
    }

    // ── Detail panel ───────────────────────────────────────────────────────

    private JSplitPane buildDetailPanel() {
        JScrollPane reqScroll  = styledScroll(requestViewer,  "Request");
        JScrollPane respScroll = styledScroll(responseViewer, "Response");
        styleViewer(requestViewer);
        styleViewer(responseViewer);

        JSplitPane detail = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, reqScroll, respScroll);
        detail.setResizeWeight(0.5);
        detail.setDividerSize(5);
        detail.setBackground(new Color(0x2B2B2B));
        return detail;
    }

    private JScrollPane styledScroll(JTextArea area, String title) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x444444)), " " + title + " ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 11), new Color(0x888888)));
        scroll.setBackground(new Color(0x1E1E1E));
        scroll.getViewport().setBackground(new Color(0x1E1E1E));
        return scroll;
    }

    private void styleViewer(JTextArea area) {
        area.setBackground(new Color(0x1E1E1E));
        area.setForeground(new Color(0xD0D0D0));
        area.setCaretColor(Color.WHITE);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
    }

    // ── Status bar ─────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x1E1E1E));
        bar.setBorder(new EmptyBorder(3, 8, 3, 8));
        statusBar.setForeground(new Color(0x888888));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        bar.add(statusBar, BorderLayout.WEST);
        return bar;
    }

    private void updateStatusBar() {
        List<RequestEntry> reqs = model.getRequests();
        List<UserEntry> users = model.getVisibleUsers();
        int total = reqs.size() * users.size();
        int done = 0, bypassed = 0, running = 0;
        for (RequestEntry req : reqs) {
            for (UserEntry user : users) {
                ResultCell cell = model.getCell(req, user);
                if (cell.getStatus() == ResultCell.TestStatus.DONE)    done++;
                if (cell.getStatus() == ResultCell.TestStatus.RUNNING) running++;
                if (cell.getStatus() == ResultCell.TestStatus.DONE
                        && cell.getExpected() == ResultCell.ExpectedResult.DENY
                        && cell.getStatusCode() >= 200 && cell.getStatusCode() < 300) bypassed++;
            }
        }
        String msg = done + "/" + total + " tested";
        if (running > 0) msg += "  |  " + running + " running...";
        if (bypassed > 0) msg += "  |  ⚠ " + bypassed + " BYPASSED";
        statusBar.setText(" " + msg);
        statusBar.setForeground(bypassed > 0 ? new Color(0xFF5555) : new Color(0x888888));
    }

    // ── Run logic ──────────────────────────────────────────────────────────

    private void startWorker(List<RequestEntry> reqs, List<UserEntry> users, boolean force) {
        if (reqs.isEmpty() || users.isEmpty()) return;
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        updateStatusBar();
        activeWorker = new RequestReplayWorker(model, callbacks, helpers, reqs, users, force);
        activeWorker.execute();
    }

    private void runSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) { statusBar.setText(" No rows selected."); return; }

        List<RequestEntry> reqs = new ArrayList<>();
        List<RequestEntry> all  = model.getRequests();
        for (int r : rows) {
            int modelRow = table.convertRowIndexToModel(r);
            if (modelRow < all.size()) reqs.add(all.get(modelRow));
        }
        // Reset cells for these specific requests before force-running them
        for (RequestEntry req : reqs) {
            for (UserEntry user : model.getVisibleUsers()) {
                model.getCell(req, user).reset();
            }
        }
        startWorker(reqs, model.getVisibleUsers(), true);
    }

    private void stopTests() {
        autoRunTimer.stop();
        if (activeWorker != null) activeWorker.cancel(true);
        statusBar.setText(" Stopped.");
    }

    // ── Context menu ───────────────────────────────────────────────────────

    private void showCellContextMenu(MouseEvent e, int row, int col) {
        List<UserEntry> visible = model.getVisibleUsers();
        int userIdx = col - 2;
        if (userIdx < 0 || userIdx >= visible.size()) return;

        List<RequestEntry> requests = model.getRequests();
        if (row >= requests.size()) return;

        RequestEntry req  = requests.get(row);
        UserEntry    user = visible.get(userIdx);
        ResultCell   cell = model.getCell(req, user);

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(0x2B2B2B));

        JMenuItem allow   = styledMenuItem("Set Expected: Allow  ✓");
        JMenuItem deny    = styledMenuItem("Set Expected: Deny   ✗");
        JMenuItem unknown = styledMenuItem("Clear Expected");
        JMenuItem rerun   = styledMenuItem("Re-run this cell");

        allow.addActionListener(ev   -> { cell.setExpected(ResultCell.ExpectedResult.ALLOW);   model.fireCellChanged(req.getId(), user.getId()); });
        deny.addActionListener(ev    -> { cell.setExpected(ResultCell.ExpectedResult.DENY);    model.fireCellChanged(req.getId(), user.getId()); });
        unknown.addActionListener(ev -> { cell.setExpected(ResultCell.ExpectedResult.UNKNOWN); model.fireCellChanged(req.getId(), user.getId()); });
        rerun.addActionListener(ev   -> {
            cell.reset();
            startWorker(List.of(req), List.of(user), true);
        });

        menu.add(allow);
        menu.add(deny);
        menu.add(unknown);
        menu.addSeparator();
        menu.add(rerun);
        menu.show(table, e.getX(), e.getY());
    }

    private JMenuItem styledMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(new Color(0x2B2B2B));
        item.setForeground(new Color(0xE0E0E0));
        return item;
    }

    // ── Detail viewer ──────────────────────────────────────────────────────

    private void showDetail() {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 2) { requestViewer.setText(""); responseViewer.setText(""); return; }

        List<RequestEntry> requests = model.getRequests();
        List<UserEntry>    visible  = model.getVisibleUsers();
        int userIdx = col - 2;
        if (row >= requests.size() || userIdx >= visible.size()) return;

        RequestEntry req  = requests.get(row);
        UserEntry    user = visible.get(userIdx);
        ResultCell   cell = model.getCell(req, user);

        byte[] shownRequest = cell.getModifiedRequest() != null
                ? cell.getModifiedRequest()
                : req.getOriginalRequest();
        requestViewer.setText(new String(shownRequest));
        requestViewer.setCaretPosition(0);

        if (cell.getResponseBytes() != null) {
            responseViewer.setText(new String(cell.getResponseBytes()));
            responseViewer.setCaretPosition(0);
        } else {
            responseViewer.setText(cell.getStatus() == ResultCell.TestStatus.ERROR
                    ? "Error: " + cell.getErrorMessage() : "(not yet tested)");
        }
    }

    // ── Styling helpers ────────────────────────────────────────────────────

    private JButton toolBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(12f));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMargin(new Insets(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleToggle(JToggleButton btn, Color activeColor) {
        btn.setBackground(activeColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
        btn.setMargin(new Insets(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(new Color(0x444444));
        return sep;
    }

    // ── Table model ────────────────────────────────────────────────────────

    private static class MatrixTableModel extends AbstractTableModel {
        private final AuthMatrixModel model;
        private final IExtensionHelpers helpers;

        MatrixTableModel(AuthMatrixModel model, IExtensionHelpers helpers) {
            this.model = model;
            this.helpers = helpers;
        }

        void structureChanged() { fireTableStructureChanged(); }
        void dataChanged()      { fireTableDataChanged(); }

        @Override public int getRowCount() { return model.getRequests().size(); }

        @Override
        public int getColumnCount() { return 2 + model.getVisibleUsers().size(); }

        @Override
        public String getColumnName(int col) {
            if (col == 0) return "Method";
            if (col == 1) return "URL";
            List<UserEntry> visible = model.getVisibleUsers();
            int idx = col - 2;
            return idx < visible.size() ? visible.get(idx).getName() : "";
        }

        @Override
        public Object getValueAt(int row, int col) {
            List<RequestEntry> requests = model.getRequests();
            if (row >= requests.size()) return null;
            RequestEntry req = requests.get(row);
            if (col == 0) return req.getMethod();
            if (col == 1) return req.getDisplayUrl();

            List<UserEntry> visible = model.getVisibleUsers();
            int idx = col - 2;
            if (idx >= visible.size()) return null;
            return model.getCell(req, visible.get(idx));
        }

        @Override public boolean isCellEditable(int row, int col) { return false; }
    }

    // ── Cell renderer ──────────────────────────────────────────────────────

    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            label.setHorizontalAlignment(col < 2 ? LEFT : CENTER);
            label.setBorder(new EmptyBorder(0, 6, 0, 6));

            if (col == 0) {
                String method = value == null ? "" : value.toString();
                label.setText(method);
                label.setForeground(methodColor(method));
                label.setBackground(isSelected ? new Color(0x214283) : new Color(0x1E1E1E));
                label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
                return label;
            }

            if (col == 1) {
                label.setForeground(new Color(0xC0C0C0));
                label.setBackground(isSelected ? new Color(0x214283) : new Color(0x252525));
                return label;
            }

            if (value instanceof ResultCell) {
                ResultCell cell = (ResultCell) value;
                label.setText(cell.getLabel());
                label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
                if (isSelected) {
                    label.setBackground(new Color(0x214283));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(cell.getBackground());
                    label.setForeground(cell.getForeground());
                }
                if (cell.getStatus() == ResultCell.TestStatus.DONE) {
                    label.setToolTipText("Status: " + cell.getStatusCode()
                            + "  Length: " + cell.getResponseLength()
                            + "  Expected: " + cell.getExpected());
                } else {
                    label.setToolTipText(null);
                }
                return label;
            }

            label.setForeground(new Color(0xE0E0E0));
            label.setBackground(isSelected ? new Color(0x214283) : new Color(0x2B2B2B));
            return label;
        }

        private Color methodColor(String method) {
            if (method == null) return new Color(0xE0E0E0);
            switch (method.toUpperCase()) {
                case "GET":    return new Color(0x61AFEF);
                case "POST":   return new Color(0x98C379);
                case "PUT":    return new Color(0xE5C07B);
                case "PATCH":  return new Color(0xE5C07B);
                case "DELETE": return new Color(0xE06C75);
                default:       return new Color(0xABB2BF);
            }
        }
    }
}
