package burp;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BurpExtender implements IBurpExtender, ITab, IContextMenuFactory, IProxyListener {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private AuthMatrixPanel authMatrixPanel;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers   = callbacks.getHelpers();

        callbacks.setExtensionName("Spectra");

        SwingUtilities.invokeLater(() -> {
            authMatrixPanel = new AuthMatrixPanel(callbacks, helpers);
            callbacks.addSuiteTab(this);
            callbacks.registerContextMenuFactory(this);
            callbacks.registerProxyListener(this);
            callbacks.printOutput("Spectra loaded.");
        });
    }

    @Override
    public void processProxyMessage(boolean messageIsRequest, IInterceptedProxyMessage message) {
        // Capture on response so the request is known to have completed
        if (!messageIsRequest && authMatrixPanel != null) {
            authMatrixPanel.onProxyMessage(message.getMessageInfo());
        }
    }

    @Override
    public String getTabCaption() { return "Spectra"; }

    @Override
    public Component getUiComponent() { return authMatrixPanel; }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        List<JMenuItem> items = new ArrayList<>();
        IHttpRequestResponse[] messages = invocation.getSelectedMessages();
        if (messages == null || messages.length == 0) return items;

        JMenuItem send = new JMenuItem("Send to Spectra");
        send.addActionListener(e -> {
            for (IHttpRequestResponse msg : messages) {
                authMatrixPanel.addRequest(msg);
            }
        });
        items.add(send);
        return items;
    }
}
