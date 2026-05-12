package burp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class UserEditDialog extends JDialog {

    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;

    private boolean confirmed = false;
    private final JTextField nameField = new JTextField(20);
    private final JComboBox<UserEntry.AuthType> authTypeCombo = new JComboBox<>(UserEntry.AuthType.values());
    private final JTextArea authValueArea = new JTextArea(8, 40);
    private final JLabel authValueLabel = new JLabel("Value:");
    private final JLabel hintLabel = new JLabel();
    private final JLabel fetchStatus = new JLabel(" ");

    public UserEditDialog(Window parent, String title, UserEntry prefill,
                          IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.callbacks = callbacks;
        this.helpers   = helpers;
        buildUi();
        if (prefill != null) populate(prefill);
        updateHint();
        pack();
        // Ensure the dialog is wide enough to display long tokens without truncation
        setMinimumSize(new Dimension(560, getPreferredSize().height));
        setSize(Math.max(560, getPreferredSize().width), getPreferredSize().height);
        setLocationRelativeTo(parent);
    }

    private void buildUi() {
        setBackground(new Color(0x2B2B2B));
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(0x2B2B2B));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(0x2B2B2B));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 8);
        lc.gridx = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 0);
        fc.gridx = 1;

        // Row 0: Name
        lc.gridy = 0; fc.gridy = 0;
        form.add(styled(new JLabel("Name:")), lc);
        form.add(styledField(nameField), fc);

        // Row 1: Auth type + Fetch button side by side
        lc.gridy = 1; fc.gridy = 1;
        form.add(styled(new JLabel("Auth type:")), lc);
        styledCombo(authTypeCombo);
        authTypeCombo.addActionListener(e -> updateHint());

        JPanel typeRow = new JPanel(new BorderLayout(8, 0));
        typeRow.setOpaque(false);
        typeRow.add(authTypeCombo, BorderLayout.CENTER);
        JButton fetchBtn = fetchButton("Fetch from last request");
        fetchBtn.addActionListener(e -> fetchFromLastRequest());
        typeRow.add(fetchBtn, BorderLayout.EAST);
        form.add(typeRow, fc);

        // Row 2: Fetch status
        GridBagConstraints sc = new GridBagConstraints();
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.weightx = 1.0;
        sc.gridx = 1; sc.gridy = 2;
        sc.insets = new Insets(0, 0, 2, 0);
        fetchStatus.setForeground(new Color(0x888888));
        fetchStatus.setFont(fetchStatus.getFont().deriveFont(Font.ITALIC, 10f));
        form.add(fetchStatus, sc);

        // Row 3: Value
        lc.gridy = 3; fc.gridy = 3;
        form.add(styled(authValueLabel), lc);
        JScrollPane scroll = new JScrollPane(styledTextArea(authValueArea));
        scroll.setBackground(new Color(0x1E1E1E));
        form.add(scroll, fc);

        // Row 4: Hint
        GridBagConstraints hc = new GridBagConstraints();
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.weightx = 1.0;
        hc.gridx = 0; hc.gridy = 4;
        hc.gridwidth = 2;
        hc.insets = new Insets(2, 0, 4, 0);
        hintLabel.setForeground(new Color(0x888888));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        form.add(hintLabel, hc);

        root.add(form, BorderLayout.CENTER);

        // Save / Cancel
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setBackground(new Color(0x2B2B2B));
        JButton ok = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        styleButton(ok, new Color(0x4C9BE8));
        styleButton(cancel, new Color(0x555555));
        ok.addActionListener(e -> { confirmed = true; dispose(); });
        cancel.addActionListener(e -> dispose());
        btns.add(cancel);
        btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(ok);
        setContentPane(root);
    }

    // ── Fetch from last proxy request ──────────────────────────────────────

    private void fetchFromLastRequest() {
        IHttpRequestResponse[] history = callbacks.getProxyHistory();
        if (history == null || history.length == 0) {
            fetchStatus.setForeground(new Color(0xE74C3C));
            fetchStatus.setText("No proxy history found.");
            return;
        }

        IHttpRequestResponse last = history[history.length - 1];
        if (last.getRequest() == null) {
            fetchStatus.setForeground(new Color(0xE74C3C));
            fetchStatus.setText("Last request has no data.");
            return;
        }

        IRequestInfo info = helpers.analyzeRequest(last.getHttpService(), last.getRequest());
        List<String> headers = info.getHeaders();

        String authHeader  = null;
        String cookieHeader = null;

        for (String h : headers) {
            String lower = h.toLowerCase();
            if (lower.startsWith("authorization:") && authHeader == null) {
                authHeader = h.substring(h.indexOf(':') + 1).trim();
            }
            if (lower.startsWith("cookie:") && cookieHeader == null) {
                cookieHeader = h.substring(h.indexOf(':') + 1).trim();
            }
        }

        if (authHeader != null) {
            authTypeCombo.setSelectedItem(UserEntry.AuthType.HEADER);
            authValueArea.setText(authHeader);
            fetchStatus.setForeground(new Color(0x2ECC71));
            fetchStatus.setText("Fetched Authorization header from last request.");
        } else if (cookieHeader != null) {
            authTypeCombo.setSelectedItem(UserEntry.AuthType.COOKIE);
            authValueArea.setText(cookieHeader);
            fetchStatus.setForeground(new Color(0x2ECC71));
            fetchStatus.setText("Fetched Cookie header from last request.");
        } else {
            fetchStatus.setForeground(new Color(0xE5C07B));
            fetchStatus.setText("No Authorization or Cookie header found in last request.");
        }

        updateHint();
    }

    // ── Misc ───────────────────────────────────────────────────────────────

    private void updateHint() {
        UserEntry.AuthType type = (UserEntry.AuthType) authTypeCombo.getSelectedItem();
        boolean hasValue = type != UserEntry.AuthType.NONE;
        authValueArea.setEnabled(hasValue);
        authValueLabel.setEnabled(hasValue);
        if (type == UserEntry.AuthType.NONE) {
            hintLabel.setText("All auth headers will be stripped for this user.");
        } else if (type == UserEntry.AuthType.HEADER) {
            hintLabel.setText("e.g.  Bearer eyJhbG...   or   Authorization: Bearer eyJ...");
        } else {
            hintLabel.setText("e.g.  session=abc123; role=admin");
        }
    }

    private void populate(UserEntry u) {
        nameField.setText(u.getName());
        authTypeCombo.setSelectedItem(u.getAuthType());
        authValueArea.setText(u.getAuthValue());
    }

    public boolean isConfirmed() { return confirmed; }
    public String getUserName() { return nameField.getText().trim(); }
    public UserEntry.AuthType getAuthType() { return (UserEntry.AuthType) authTypeCombo.getSelectedItem(); }
    public String getAuthValue() { return authValueArea.getText().trim(); }

    // ── Styling helpers ────────────────────────────────────────────────────

    private <T extends JLabel> T styled(T label) {
        label.setForeground(new Color(0xC0C0C0));
        return label;
    }

    private JTextField styledField(JTextField f) {
        f.setBackground(new Color(0x1E1E1E));
        f.setForeground(new Color(0xE0E0E0));
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x555555)),
                new EmptyBorder(3, 6, 3, 6)));
        return f;
    }

    private JTextArea styledTextArea(JTextArea a) {
        a.setBackground(new Color(0x1E1E1E));
        a.setForeground(new Color(0xE0E0E0));
        a.setCaretColor(Color.WHITE);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(4, 6, 4, 6));
        return a;
    }

    private void styledCombo(JComboBox<?> combo) {
        combo.setBackground(new Color(0x1E1E1E));
        combo.setForeground(new Color(0xE0E0E0));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JButton fetchButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setBackground(new Color(0x3C3F41));
        btn.setForeground(new Color(0xC0C0C0));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMargin(new Insets(3, 8, 3, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Auto-fill from the most recent request in Proxy history");
        return btn;
    }
}
