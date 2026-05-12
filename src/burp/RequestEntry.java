package burp;

import java.util.UUID;

public class RequestEntry {

    private final String id;
    private final IHttpService service;
    private final byte[] originalRequest;
    private String method;
    private String path;
    private String displayUrl;

    public RequestEntry(IHttpService service, byte[] request, IExtensionHelpers helpers) {
        this.id = UUID.randomUUID().toString();
        this.service = service;
        this.originalRequest = request;

        try {
            IRequestInfo info = helpers.analyzeRequest(service, request);
            this.method = info.getMethod();
            java.net.URL url = info.getUrl();
            this.path = url.getPath() + (url.getQuery() != null ? "?" + url.getQuery() : "");
            this.displayUrl = service.getProtocol() + "://" + service.getHost()
                    + (isDefaultPort(service) ? "" : ":" + service.getPort())
                    + this.path;
        } catch (Exception e) {
            this.method = "?";
            this.path = "/";
            this.displayUrl = service.getHost() + "/";
        }
    }

    private boolean isDefaultPort(IHttpService svc) {
        return (svc.getProtocol().equals("https") && svc.getPort() == 443)
                || (svc.getProtocol().equals("http") && svc.getPort() == 80);
    }

    public String getId() { return id; }
    public IHttpService getService() { return service; }
    public byte[] getOriginalRequest() { return originalRequest; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getDisplayUrl() { return displayUrl; }

    @Override
    public String toString() { return method + " " + displayUrl; }
}
