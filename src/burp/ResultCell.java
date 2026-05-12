package burp;

import java.awt.Color;

public class ResultCell {

    public enum TestStatus { UNTESTED, RUNNING, DONE, ERROR }

    public enum ExpectedResult { UNKNOWN, ALLOW, DENY }

    private TestStatus status = TestStatus.UNTESTED;
    private ExpectedResult expected = ExpectedResult.UNKNOWN;
    private int statusCode = -1;
    private int responseLength = -1;
    private byte[] responseBytes;
    private byte[] modifiedRequest;
    private String errorMessage;

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public ExpectedResult getExpected() { return expected; }
    public void setExpected(ExpectedResult expected) { this.expected = expected; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public int getResponseLength() { return responseLength; }
    public void setResponseLength(int responseLength) { this.responseLength = responseLength; }

    public byte[] getResponseBytes() { return responseBytes; }
    public void setResponseBytes(byte[] responseBytes) { this.responseBytes = responseBytes; }

    public byte[] getModifiedRequest() { return modifiedRequest; }
    public void setModifiedRequest(byte[] modifiedRequest) { this.modifiedRequest = modifiedRequest; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void reset() {
        status = TestStatus.UNTESTED;
        statusCode = -1;
        responseLength = -1;
        responseBytes = null;
        modifiedRequest = null;
        errorMessage = null;
    }

    /** Short label shown inside the table cell. */
    public String getLabel() {
        switch (status) {
            case UNTESTED: return "";
            case RUNNING:  return "...";
            case ERROR:    return "ERR";
            case DONE:
                if (statusCode < 0) return "?";
                String analysis = getAnalysisTag();
                return statusCode + (analysis.isEmpty() ? "" : " " + analysis);
            default: return "";
        }
    }

    private String getAnalysisTag() {
        if (expected == ExpectedResult.UNKNOWN) return "";
        boolean allowed = statusCode >= 200 && statusCode < 300;
        if (expected == ExpectedResult.ALLOW)  return allowed ? "✓" : "✗";
        if (expected == ExpectedResult.DENY)   return allowed ? "BYPASS" : "✓";
        return "";
    }

    /** Background color for the table cell. */
    public Color getBackground() {
        switch (status) {
            case UNTESTED: return new Color(0x2B2B2B);
            case RUNNING:  return new Color(0x3C3F6E);
            case ERROR:    return new Color(0x6E3C3C);
            case DONE:
                return computeDoneColor();
            default: return new Color(0x2B2B2B);
        }
    }

    private Color computeDoneColor() {
        if (statusCode < 0) return new Color(0x4A4A4A);
        boolean allowed = statusCode >= 200 && statusCode < 300;
        boolean redirect = statusCode >= 300 && statusCode < 400;

        if (expected == ExpectedResult.UNKNOWN) {
            if (allowed)   return new Color(0x2D5A2D);
            if (redirect)  return new Color(0x5A5A1E);
            return new Color(0x5A2D2D);
        }
        if (expected == ExpectedResult.ALLOW) {
            return allowed ? new Color(0x1E6B1E) : new Color(0x6B1E1E);
        }
        if (expected == ExpectedResult.DENY) {
            if (allowed) return new Color(0x8B0000);   // bypass — bright red
            return new Color(0x1E6B1E);                 // correctly denied
        }
        return new Color(0x2B2B2B);
    }

    public Color getForeground() {
        if (status == TestStatus.DONE && expected == ExpectedResult.DENY) {
            boolean allowed = statusCode >= 200 && statusCode < 300;
            if (allowed) return Color.WHITE;
        }
        return new Color(0xE0E0E0);
    }
}
