package org.cysecurity.cspf.jvl.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and reads Google Calendar events for each user/week.
 */
public final class CalendarEventDataAccess {

    private CalendarEventDataAccess() {
    }

    public static void ensureTable(Connection con) throws SQLException {
        final String ddl = "CREATE TABLE IF NOT EXISTS calendar_events ("
                + "id INT NOT NULL AUTO_INCREMENT,"
                + "user_id INT NOT NULL,"
                + "week_start DATE NOT NULL,"
                + "google_event_id VARCHAR(255) NOT NULL,"
                + "summary VARCHAR(500),"
                + "start_time_utc DATETIME,"
                + "end_time_utc DATETIME,"
                + "event_link TEXT,"
                + "updated_at DATETIME NOT NULL,"
                + "PRIMARY KEY (id),"
                + "UNIQUE KEY uq_user_week_event (user_id, week_start, google_event_id)"
                + ")";
        try (PreparedStatement ps = con.prepareStatement(ddl)) {
            ps.executeUpdate();
        }
    }

    public static void replaceWeekEvents(Connection con, int userId, String weekStartIso, List<CalendarEventRecord> events)
            throws SQLException {
        ensureTable(con);
        final String deleteSql = "DELETE FROM calendar_events WHERE user_id = ? AND week_start = ?";
        try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
            ps.setInt(1, userId);
            ps.setString(2, weekStartIso);
            ps.executeUpdate();
        }

        final String insertSql = "INSERT INTO calendar_events "
                + "(user_id, week_start, google_event_id, summary, start_time_utc, end_time_utc, event_link, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(insertSql)) {
            final Timestamp now = Timestamp.from(Instant.now());
            for (CalendarEventRecord event : events) {
                ps.setInt(1, userId);
                ps.setString(2, weekStartIso);
                ps.setString(3, event.eventId);
                ps.setString(4, event.summary);
                ps.setTimestamp(5, event.startUtc);
                ps.setTimestamp(6, event.endUtc);
                ps.setString(7, event.link);
                ps.setTimestamp(8, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static List<CalendarEventRecord> listWeekEvents(Connection con, int userId, String weekStartIso) throws SQLException {
        ensureTable(con);
        final List<CalendarEventRecord> out = new ArrayList<CalendarEventRecord>();
        final String sql = "SELECT google_event_id, summary, start_time_utc, end_time_utc, event_link "
                + "FROM calendar_events WHERE user_id = ? AND week_start = ? ORDER BY start_time_utc ASC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, weekStartIso);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CalendarEventRecord r = new CalendarEventRecord(
                            rs.getString("google_event_id"),
                            rs.getString("summary"),
                            rs.getTimestamp("start_time_utc"),
                            rs.getTimestamp("end_time_utc"),
                            rs.getString("event_link"));
                    out.add(r);
                }
            }
        }
        return out;
    }

    public static final class CalendarEventRecord {
        public final String eventId;
        public final String summary;
        public final Timestamp startUtc;
        public final Timestamp endUtc;
        public final String link;

        public CalendarEventRecord(String eventId, String summary, Timestamp startUtc, Timestamp endUtc, String link) {
            this.eventId = eventId;
            this.summary = summary;
            this.startUtc = startUtc;
            this.endUtc = endUtc;
            this.link = link;
        }
    }
}
