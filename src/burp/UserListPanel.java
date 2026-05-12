package burp;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class UserListPanel extends JPanel {

    private final AuthMatrixModel model;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private final JPanel userListContainer = new JPanel();

    public UserListPanel(AuthMatrixModel model, IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers) {
        this.model     = model;
        this.callbacks = callbacks;
        this.helpers   = helpers;
        setLayout(new BorderLayout());
        setBackground(new Color(0x2B2B2B));
        setPreferredSize(new Dimension(240, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        model.addListener(new AuthMatrixModel.Listener() {
            @Override public void usersChanged() { SwingUtilities.invokeLater(() -> refreshList()); }
            @Override public void requestsChanged() {}
            @Override public void cellChanged(String r, String u) {}
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x1E1E1E));
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Roles / Users");
        title.setForeground(new Color(0xE0E0E0));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(title, BorderLayout.WEST);

        JButton addBtn = iconButton("+", new Color(0x2ECC71));
        addBtn.setToolTipText("Add user");
        addBtn.addActionListener(e -> addUser());
        header.add(addBtn, BorderLayout.EAST);

        return header;
    }

    private JScrollPane buildList() {
        userListContainer.setLayout(new BoxLayout(userListContainer, BoxLayout.Y_AXIS));
        userListContainer.setBackground(new Color(0x2B2B2B));

        JScrollPane scroll = new JScrollPane(userListContainer);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(new Color(0x2B2B2B));
        scroll.getViewport().setBackground(new Color(0x2B2B2B));
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        footer.setBackground(new Color(0x1E1E1E));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x444444)));

        JButton anonBtn = smallButton("+ Anonymous", new Color(0x555555));
        anonBtn.setToolTipText("Add an anonymous (no-auth) user");
        anonBtn.addActionListener(e -> addAnonymous());
        footer.add(anonBtn);

        return footer;
    }

    private void refreshList() {
        userListContainer.removeAll();
        for (UserEntry user : model.getUsers()) {
            userListContainer.add(buildUserRow(user));
            userListContainer.add(Box.createVerticalStrut(1));
        }
        userListContainer.revalidate();
        userListContainer.repaint();
    }

    private JPanel buildUserRow(UserEntry user) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        row.setBorder(new EmptyBorder(6, 10, 6, 6));
        row.setBackground(new Color(0x313335));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        JPanel left = new JPanel(new BorderLayout(6, 0));
        left.setOpaque(false);

        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(4, 0));
        swatch.setBackground(user.getColor());
        left.add(swatch, BorderLayout.WEST);

        JCheckBox vis = new JCheckBox();
        vis.setSelected(user.isVisible());
        vis.setOpaque(false);
        vis.setToolTipText("Show/hide this column");
        vis.addActionListener(e -> model.toggleUserVisibility(user));
        left.add(vis, BorderLayout.CENTER);
        row.add(left, BorderLayout.WEST);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
        info.setOpaque(false);

        JLabel nameLabel = new JLabel(user.getName());
        nameLabel.setForeground(new Color(0xE0E0E0));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));

        JLabel typeLabel = new JLabel(user.getAuthType().label);
        typeLabel.setForeground(new Color(0x888888));
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.PLAIN, 10f));

        info.add(nameLabel);
        info.add(typeLabel);
        row.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);

        JButton edit = iconButton("✎", new Color(0x4C9BE8));
        edit.setToolTipText("Edit");
        edit.addActionListener(e -> editUser(user));
        actions.add(edit);

        JButton del = iconButton("✕", new Color(0xE74C3C));
        del.setToolTipText("Remove");
        del.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Remove user '" + user.getName() + "'?",
                    "Remove User", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) model.removeUser(user);
        });
        actions.add(del);
        row.add(actions, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(new Color(0x3C3F41)); }
            @Override public void mouseExited(MouseEvent e)  { row.setBackground(new Color(0x313335)); }
        });

        return row;
    }

    private void addUser() {
        UserEditDialog dlg = new UserEditDialog(
                SwingUtilities.getWindowAncestor(this), "Add User", null, callbacks, helpers);
        dlg.setVisible(true);
        if (!dlg.isConfirmed()) return;

        String name = dlg.getUserName();
        if (name.isEmpty()) name = "User" + (model.getUsers().size() + 1);

        int reqCount = model.getRequests().size();
        if (reqCount > 0) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "<html>Run <b>" + name + "</b> against the " + reqCount
                    + " already-captured request" + (reqCount == 1 ? "" : "s") + "?</html>",
                    "Test New User", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                model.setSuppressNextAutoRun(true);
            }
        }

        model.addUser(new UserEntry(name, dlg.getAuthType(), dlg.getAuthValue()));
    }

    private void addAnonymous() {
        for (UserEntry u : model.getUsers()) {
            if (u.getAuthType() == UserEntry.AuthType.NONE) {
                JOptionPane.showMessageDialog(this,
                        "An anonymous user already exists (" + u.getName() + ").",
                        "Duplicate", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        model.addUser(UserEntry.anonymous());
    }

    private void editUser(UserEntry user) {
        UserEditDialog dlg = new UserEditDialog(
                SwingUtilities.getWindowAncestor(this), "Edit User", user, callbacks, helpers);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            user.setName(dlg.getUserName().isEmpty() ? user.getName() : dlg.getUserName());
            user.setAuthType(dlg.getAuthType());
            user.setAuthValue(dlg.getAuthValue());
            // Reset this user's results so auto-run re-tests with the new credentials
            model.resetUserCells(user);
            model.fireUsersChanged();
        }
    }

    private JButton iconButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton smallButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setBackground(bg);
        btn.setForeground(new Color(0xE0E0E0));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMargin(new Insets(3, 8, 3, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
