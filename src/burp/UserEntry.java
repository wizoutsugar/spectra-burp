package burp;

import java.awt.Color;
import java.util.UUID;

public class UserEntry {

    public enum AuthType {
        NONE("No Auth (Anonymous)"),
        HEADER("Authorization Header"),
        COOKIE("Cookie");

        public final String label;
        AuthType(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    public enum ExpectedResult {
        UNKNOWN, ALLOW, DENY
    }

    private final String id;
    private String name;
    private AuthType authType;
    private String authValue;
    private boolean visible;
    private Color color;

    private static final Color[] PALETTE = {
        new Color(0x4C9BE8),
        new Color(0x2ECC71),
        new Color(0xE67E22),
        new Color(0x9B59B6),
        new Color(0xE74C3C),
        new Color(0x1ABC9C),
        new Color(0xF39C12),
        new Color(0x3498DB),
    };
    private static int colorIndex = 0;

    public UserEntry(String name, AuthType authType, String authValue) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.authType = authType;
        this.authValue = authValue;
        this.visible = true;
        this.color = PALETTE[colorIndex % PALETTE.length];
        colorIndex++;
    }

    public static UserEntry anonymous() {
        return new UserEntry("Anonymous", AuthType.NONE, "");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }
    public String getAuthValue() { return authValue; }
    public void setAuthValue(String authValue) { this.authValue = authValue; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    @Override
    public String toString() { return name; }
}
