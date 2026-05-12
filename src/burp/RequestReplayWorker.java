package burp;

import javax.swing.*;
import java.util.*;

public class RequestReplayWorker extends SwingWorker<Void, Object[]> {

    private final AuthMatrixModel model;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private final List<RequestEntry> requests;
    private final List<UserEntry> users;
    private final boolean forceRerun;

    public RequestReplayWorker(AuthMatrixModel model,
                               IBurpExtenderCallbacks callbacks,
                               IExtensionHelpers helpers,
                               List<RequestEntry> requests,
                               List<UserEntry> users,
                               boolean forceRerun) {
        this.model = model;
        this.callbacks = callbacks;
        this.helpers = helpers;
        this.requests = requests;
        this.users = users;
        this.forceRerun = forceRerun;
    }

    @Override
    protected Void doInBackground() {
        for (RequestEntry req : requests) {
            for (UserEntry user : users) {
                if (isCancelled()) return null;

                ResultCell cell = model.getCell(req, user);
                if (!forceRerun && cell.getStatus() != ResultCell.TestStatus.UNTESTED) continue;

                cell.setStatus(ResultCell.TestStatus.RUNNING);
                publish(new Object[]{req.getId(), user.getId()});

                try {
                    byte[] modifiedRequest = buildRequest(req, user);
                    cell.setModifiedRequest(modifiedRequest);
                    IHttpRequestResponse result = callbacks.makeHttpRequest(req.getService(), modifiedRequest);

                    byte[] response = result.getResponse();
                    if (response == null || response.length == 0) {
                        cell.setStatus(ResultCell.TestStatus.ERROR);
                        cell.setErrorMessage("No response");
                    } else {
                        IResponseInfo info = helpers.analyzeResponse(response);
                        cell.setStatusCode(info.getStatusCode());
                        cell.setResponseLength(response.length);
                        cell.setResponseBytes(response);
                        cell.setStatus(ResultCell.TestStatus.DONE);
                    }
                } catch (Exception e) {
                    cell.setStatus(ResultCell.TestStatus.ERROR);
                    cell.setErrorMessage(e.getMessage());
                    callbacks.printError("Spectra replay error: " + e.getMessage());
                }

                publish(new Object[]{req.getId(), user.getId()});
            }
        }
        return null;
    }

    @Override
    protected void process(List<Object[]> chunks) {
        for (Object[] chunk : chunks) {
            model.fireCellChanged((String) chunk[0], (String) chunk[1]);
        }
    }

    private byte[] buildRequest(RequestEntry req, UserEntry user) {
        IRequestInfo info = helpers.analyzeRequest(req.getService(), req.getOriginalRequest());
        List<String> headers = new ArrayList<>(info.getHeaders());

        switch (user.getAuthType()) {
            case NONE:
                removeHeaders(headers, "Authorization");
                removeHeaders(headers, "Cookie");
                break;

            case HEADER:
                removeHeaders(headers, "Authorization");
                if (!user.getAuthValue().isBlank()) {
                    String val = user.getAuthValue().trim();
                    // Allow bare token or full "Authorization: Bearer ..." form
                    if (val.toLowerCase().startsWith("authorization:")) {
                        headers.add(val);
                    } else {
                        headers.add("Authorization: " + val);
                    }
                }
                break;

            case COOKIE:
                removeHeaders(headers, "Cookie");
                if (!user.getAuthValue().isBlank()) {
                    headers.add("Cookie: " + user.getAuthValue().trim());
                }
                break;
        }

        byte[] body = Arrays.copyOfRange(
                req.getOriginalRequest(),
                info.getBodyOffset(),
                req.getOriginalRequest().length);

        return helpers.buildHttpMessage(headers, body);
    }

    private void removeHeaders(List<String> headers, String name) {
        headers.removeIf(h -> h.toLowerCase().startsWith(name.toLowerCase() + ":"));
    }
}
