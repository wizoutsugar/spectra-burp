package burp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AuthMatrixPanel extends JPanel {

    private final AuthMatrixModel model;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;

    private final UserListPanel userListPanel;
    private final ResultsPanel  resultsPanel;

    private volatile boolean autoCaptureEnabled = false;
    private JToggleButton autoCaptureBtn;
    private JCheckBox scopeOnlyBox;
    private JCheckBox skipStaticBox;
    private JLabel captureCountLabel;

    private static final Set<String> STATIC_EXT = new HashSet<>(Arrays.asList(
        "js", "css", "png", "jpg", "jpeg", "gif", "svg", "ico",
        "woff", "woff2", "ttf", "eot", "otf", "map",
        "mp4", "mp3", "pdf", "zip", "gz", "bmp", "webp"
    ));

    public AuthMatrixPanel(IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers) {
        this.callbacks = callbacks;
        this.helpers   = helpers;
        this.model     = new AuthMatrixModel();

        setLayout(new BorderLayout());
        setBackground(new Color(0x2B2B2B));

        userListPanel = new UserListPanel(model, callbacks, helpers);
        resultsPanel  = new ResultsPanel(model, callbacks, helpers);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, userListPanel, resultsPanel);
        split.setDividerLocation(240);
        split.setDividerSize(5);
        split.setBackground(new Color(0x2B2B2B));

        JPanel top = new JPanel(new BorderLayout());
        top.add(buildHeader(), BorderLayout.NORTH);
        top.add(buildCaptureBar(), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        model.addListener(new AuthMatrixModel.Listener() {
            @Override public void usersChanged() {}
            @Override public void requestsChanged() {
                SwingUtilities.invokeLater(() -> updateCaptureCount());
            }
            @Override public void cellChanged(String r, String u) {}
        });

        model.addUser(UserEntry.anonymous());
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Called from BurpExtender on every proxy response (Burp thread). */
    public void onProxyMessage(IHttpRequestResponse message) {
        if (!autoCaptureEnabled) return;
        if (!passesFilter(message)) return;
        SwingUtilities.invokeLater(() -> addRequest(message));
    }

    public void addRequest(IHttpRequestResponse message) {
        if (message.getRequest() == null) return;
        IHttpService service = message.getHttpService();
        if (service == null) return;
        model.addRequest(new RequestEntry(service, message.getRequest(), helpers));
    }

    // ── Capture bar ────────────────────────────────────────────────────────

    private JPanel buildCaptureBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x232527));
        bar.setBorder(new EmptyBorder(5, 10, 5, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        autoCaptureBtn = new JToggleButton("Auto-capture: OFF");
        styleToggle(autoCaptureBtn);
        autoCaptureBtn.addActionListener(e -> {
            autoCaptureEnabled = autoCaptureBtn.isSelected();
            autoCaptureBtn.setText(autoCaptureEnabled ? "Auto-capture: ON" : "Auto-capture: OFF");
            autoCaptureBtn.setBackground(autoCaptureEnabled ? new Color(0x2ECC71) : new Color(0x555555));
        });
        left.add(autoCaptureBtn);

        JButton loadHistory = captureBtn("Load from History");
        loadHistory.setToolTipText("Import matching requests from the current Proxy history");
        loadHistory.addActionListener(e -> loadFromHistory());
        left.add(loadHistory);

        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        scopeOnlyBox = styledCheck("In-scope only", false);
        skipStaticBox = styledCheck("Skip static files", true);
        JCheckBox uniqueUrlsBox = styledCheck("Unique URLs only", true);
        uniqueUrlsBox.setToolTipText("When checked, duplicate method+URL pairs are ignored. Uncheck to capture every occurrence.");
        uniqueUrlsBox.addActionListener(e -> model.setDeduplicateRequests(uniqueUrlsBox.isSelected()));
        right.add(scopeOnlyBox);
        right.add(skipStaticBox);
        right.add(uniqueUrlsBox);

        captureCountLabel = new JLabel("0 requests");
        captureCountLabel.setForeground(new Color(0x666666));
        captureCountLabel.setFont(captureCountLabel.getFont().deriveFont(Font.ITALIC, 11f));
        right.add(captureCountLabel);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void loadFromHistory() {
        JButton src = null; // purely cosmetic
        new SwingWorker<Void, IHttpRequestResponse>() {
            @Override protected Void doInBackground() {
                IHttpRequestResponse[] history = callbacks.getProxyHistory();
                for (IHttpRequestResponse msg : history) {
                    if (passesFilter(msg)) publish(msg);
                }
                return null;
            }
            @Override protected void process(java.util.List<IHttpRequestResponse> chunks) {
                for (IHttpRequestResponse msg : chunks) addRequest(msg);
            }
            @Override protected void done() {
                updateCaptureCount();
            }
        }.execute();
    }

    private boolean passesFilter(IHttpRequestResponse msg) {
        if (msg.getRequest() == null || msg.getHttpService() == null) return false;
        try {
            IRequestInfo info = helpers.analyzeRequest(msg.getHttpService(), msg.getRequest());
            java.net.URL url = info.getUrl();
            if (scopeOnlyBox.isSelected() && !callbacks.isInScope(url)) return false;
            if (skipStaticBox.isSelected()) {
                String path = url.getPath().toLowerCase();
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && STATIC_EXT.contains(path.substring(dot + 1))) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateCaptureCount() {
        int n = model.getRequests().size();
        captureCountLabel.setText(n + " request" + (n == 1 ? "" : "s"));
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x181A1B));
        header.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel title = new JLabel("Spectra  –  Multi-Role Authorization Tester");
        title.setForeground(new Color(0xE0E0E0));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        header.add(title, BorderLayout.WEST);

        JLabel hint = new JLabel("Enable auto-capture or right-click a request in Burp → Send to Spectra");
        hint.setForeground(new Color(0x555555));
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        header.add(hint, BorderLayout.EAST);

        return header;
    }

    // ── Styling helpers ────────────────────────────────────────────────────

    private void styleToggle(JToggleButton btn) {
        btn.setBackground(new Color(0x555555));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setMargin(new Insets(3, 10, 3, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JButton captureBtn(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(0x3C3F41));
        btn.setForeground(new Color(0xC0C0C0));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setMargin(new Insets(3, 10, 3, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JCheckBox styledCheck(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setForeground(new Color(0xA0A0A0));
        box.setFont(box.getFont().deriveFont(11f));
        box.setFocusPainted(false);
        return box;
    }
}
