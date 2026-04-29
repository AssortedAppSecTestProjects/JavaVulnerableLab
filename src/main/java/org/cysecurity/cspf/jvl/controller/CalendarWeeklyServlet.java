package org.cysecurity.cspf.jvl.controller;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.CalendarEventDataAccess;
import org.cysecurity.cspf.jvl.model.CalendarEventDataAccess.CalendarEventRecord;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Weekly Google Calendar sync/read endpoint.
 */
public class CalendarWeeklyServlet extends HttpServlet {

    private static final String SESSION_CSRF = "calendarCsrfToken";
    private static final String SESSION_LAST_SYNC_MS = "calendarLastSyncMs";
    private static final long MIN_SYNC_INTERVAL_MS = 15_000L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setSecurityHeaders(response);
        response.setContentType("application/json;charset=UTF-8");

        final HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        final Integer userId = getUserId(session);
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid user session");
            return;
        }

        final LocalDate weekStart = getWeekStart();
        final JSONObject body = new JSONObject();
        body.put("weekStart", weekStart.toString());
        body.put("csrfToken", ensureCsrf(session));

        final String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");
        try (Connection con = new DBConnect().connect(configPath)) {
            List<CalendarEventRecord> events = CalendarEventDataAccess.listWeekEvents(con, userId.intValue(), weekStart.toString());
            body.put("events", toJson(events));
            response.getWriter().print(body.toString());
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to load stored calendar events");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setSecurityHeaders(response);
        response.setContentType("application/json;charset=UTF-8");

        final HttpSession session = request.getSession(false);
        if (!isAuthenticated(session)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        final Integer userId = getUserId(session);
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid user session");
            return;
        }

        final String csrf = request.getParameter("csrfToken");
        if (!validateCsrf(session, csrf)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
            return;
        }
        if (!allowSyncNow(session)) {
            writeError(response, 429, "Please wait before syncing again");
            return;
        }

        final String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");
        final Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath)) {
            props.load(in);
        }

        final String accessToken = trimToEmpty(props.getProperty("google.calendar.access.token"));
        final String calendarId = trimToEmpty(props.getProperty("google.calendar.id"));
        if (accessToken.isEmpty()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Google Calendar token not configured");
            return;
        }

        final LocalDate weekStart = getWeekStart();
        final ZonedDateTime start = weekStart.atStartOfDay(ZoneOffset.UTC);
        final ZonedDateTime end = weekStart.plusDays(7).atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC);

        final List<CalendarEventRecord> events;
        try {
            events = fetchCalendarEvents(accessToken, calendarId.isEmpty() ? "primary" : calendarId, start, end);
        } catch (IOException e) {
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY, "Unable to fetch Google Calendar events");
            return;
        }

        try (Connection con = new DBConnect().connect(configPath)) {
            CalendarEventDataAccess.replaceWeekEvents(con, userId.intValue(), weekStart.toString(), events);
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to store weekly calendar events");
            return;
        }

        JSONObject body = new JSONObject();
        body.put("weekStart", weekStart.toString());
        body.put("eventCount", events.size());
        body.put("events", toJson(events));
        body.put("csrfToken", ensureCsrf(session));
        response.getWriter().print(body.toString());
    }

    private static JSONArray toJson(List<CalendarEventRecord> events) {
        JSONArray arr = new JSONArray();
        for (CalendarEventRecord event : events) {
            JSONObject obj = new JSONObject();
            obj.put("eventId", safe(event.eventId));
            obj.put("summary", safe(event.summary));
            obj.put("startUtc", event.startUtc == null ? JSONObject.NULL : event.startUtc.toInstant().toString());
            obj.put("endUtc", event.endUtc == null ? JSONObject.NULL : event.endUtc.toInstant().toString());
            obj.put("link", safe(event.link));
            arr.put(obj);
        }
        return arr;
    }

    private static List<CalendarEventRecord> fetchCalendarEvents(String accessToken, String calendarId, ZonedDateTime start, ZonedDateTime end)
            throws IOException {
        String encodedCalendarId = URLEncoder.encode(calendarId, "UTF-8");
        String url = "https://www.googleapis.com/calendar/v3/calendars/" + encodedCalendarId + "/events"
                + "?singleEvents=true&orderBy=startTime"
                + "&timeMin=" + URLEncoder.encode(start.toInstant().toString(), "UTF-8")
                + "&timeMax=" + URLEncoder.encode(end.toInstant().toString(), "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String payload = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("Google API error: " + code + " " + payload);
        }

        JSONObject root = new JSONObject(payload);
        JSONArray items = root.optJSONArray("items");
        List<CalendarEventRecord> out = new ArrayList<CalendarEventRecord>();
        if (items == null) {
            return out;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = trimToEmpty(item.optString("id"));
            if (id.isEmpty()) {
                continue;
            }

            Timestamp startTs = parseGoogleTime(item.optJSONObject("start"));
            Timestamp endTs = parseGoogleTime(item.optJSONObject("end"));
            String summary = trimToEmpty(item.optString("summary"));
            String link = trimToEmpty(item.optString("htmlLink"));
            out.add(new CalendarEventRecord(id, summary, startTs, endTs, link));
        }
        return out;
    }

    private static Timestamp parseGoogleTime(JSONObject timeContainer) {
        if (timeContainer == null) {
            return null;
        }
        String dateTime = trimToEmpty(timeContainer.optString("dateTime"));
        if (!dateTime.isEmpty()) {
            Instant instant = ZonedDateTime.parse(dateTime).toInstant();
            return Timestamp.from(instant);
        }
        String dateOnly = trimToEmpty(timeContainer.optString("date"));
        if (dateOnly.isEmpty()) {
            return null;
        }
        LocalDateTime dt = LocalDate.parse(dateOnly).atStartOfDay();
        return Timestamp.from(dt.toInstant(ZoneOffset.UTC));
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static LocalDate getWeekStart() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static boolean isAuthenticated(HttpSession session) {
        return session != null && session.getAttribute("isLoggedIn") != null;
    }

    private static Integer getUserId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object idObj = session.getAttribute("userid");
        if (idObj == null) {
            return null;
        }
        try {
            return Integer.valueOf(idObj.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String ensureCsrf(HttpSession session) {
        if (session == null) {
            return "";
        }
        Object token = session.getAttribute(SESSION_CSRF);
        if (token instanceof String && !((String) token).isEmpty()) {
            return (String) token;
        }
        String generated = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF, generated);
        return generated;
    }

    private static boolean validateCsrf(HttpSession session, String supplied) {
        if (session == null || supplied == null || supplied.trim().isEmpty()) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_CSRF);
        return expected instanceof String && supplied.equals(expected);
    }

    private static boolean allowSyncNow(HttpSession session) {
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Object prevObj = session.getAttribute(SESSION_LAST_SYNC_MS);
        if (prevObj instanceof Long) {
            long prev = ((Long) prevObj).longValue();
            if (now - prev < MIN_SYNC_INTERVAL_MS) {
                return false;
            }
        }
        session.setAttribute(SESSION_LAST_SYNC_MS, Long.valueOf(now));
        return true;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().print(new JSONObject().put("error", message).toString());
    }

    private static void setSecurityHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
    }
}
