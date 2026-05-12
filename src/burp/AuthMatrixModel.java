package burp;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuthMatrixModel {

    private final List<UserEntry> users = new CopyOnWriteArrayList<>();
    private final List<RequestEntry> requests = new CopyOnWriteArrayList<>();
    private final Map<String, ResultCell> cells = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean deduplicateRequests = true;
    private volatile boolean suppressNextAutoRun = false;

    public interface Listener {
        void usersChanged();
        void requestsChanged();
        void cellChanged(String requestId, String userId);
    }

    // ── Users ──────────────────────────────────────────────────────────────

    public void addUser(UserEntry user) {
        users.add(user);
        for (RequestEntry req : requests) {
            cells.put(cellKey(req.getId(), user.getId()), new ResultCell());
        }
        fireUsersChanged();
    }

    public void removeUser(UserEntry user) {
        users.remove(user);
        for (RequestEntry req : requests) {
            cells.remove(cellKey(req.getId(), user.getId()));
        }
        fireUsersChanged();
    }

    public List<UserEntry> getUsers() { return Collections.unmodifiableList(users); }

    public List<UserEntry> getVisibleUsers() {
        List<UserEntry> visible = new ArrayList<>();
        for (UserEntry u : users) {
            if (u.isVisible()) visible.add(u);
        }
        return visible;
    }

    public void toggleUserVisibility(UserEntry user) {
        user.setVisible(!user.isVisible());
        fireUsersChanged();
    }

    // ── Requests ───────────────────────────────────────────────────────────

    public void addRequest(RequestEntry request) {
        if (isDuplicate(request)) return;
        requests.add(request);
        for (UserEntry user : users) {
            cells.put(cellKey(request.getId(), user.getId()), new ResultCell());
        }
        fireRequestsChanged();
    }

    public void setDeduplicateRequests(boolean dedup) { this.deduplicateRequests = dedup; }
    public boolean isDeduplicateRequests() { return deduplicateRequests; }

    private boolean isDuplicate(RequestEntry incoming) {
        if (!deduplicateRequests) return false;
        for (RequestEntry existing : requests) {
            if (existing.getDisplayUrl().equals(incoming.getDisplayUrl())
                    && existing.getMethod().equals(incoming.getMethod())) {
                return true;
            }
        }
        return false;
    }

    public void setSuppressNextAutoRun(boolean suppress) { this.suppressNextAutoRun = suppress; }

    /** Returns the flag value and resets it to false atomically. */
    public boolean consumeSuppressNextAutoRun() {
        boolean val = suppressNextAutoRun;
        suppressNextAutoRun = false;
        return val;
    }

    public void removeRequest(RequestEntry request) {
        requests.remove(request);
        for (UserEntry user : users) {
            cells.remove(cellKey(request.getId(), user.getId()));
        }
        fireRequestsChanged();
    }

    public void clearRequests() {
        requests.clear();
        cells.clear();
        fireRequestsChanged();
    }

    public List<RequestEntry> getRequests() { return Collections.unmodifiableList(requests); }

    // ── Cells ──────────────────────────────────────────────────────────────

    public ResultCell getCell(RequestEntry request, UserEntry user) {
        return cells.computeIfAbsent(cellKey(request.getId(), user.getId()), k -> new ResultCell());
    }

    public void resetUserCells(UserEntry user) {
        for (RequestEntry req : requests) {
            String key = cellKey(req.getId(), user.getId());
            ResultCell cell = cells.get(key);
            if (cell != null) {
                cell.reset();
                fireCellChanged(req.getId(), user.getId());
            }
        }
    }

    public void resetCells() {
        for (ResultCell cell : cells.values()) cell.reset();
        for (RequestEntry req : requests) {
            for (UserEntry user : users) {
                fireCellChanged(req.getId(), user.getId());
            }
        }
    }

    private static String cellKey(String requestId, String userId) {
        return requestId + "|" + userId;
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    public void addListener(Listener listener) { listeners.add(listener); }

    public void fireUsersChanged() {
        for (Listener l : listeners) l.usersChanged();
    }

    public void fireRequestsChanged() {
        for (Listener l : listeners) l.requestsChanged();
    }

    public void fireCellChanged(String requestId, String userId) {
        for (Listener l : listeners) l.cellChanged(requestId, userId);
    }
}
