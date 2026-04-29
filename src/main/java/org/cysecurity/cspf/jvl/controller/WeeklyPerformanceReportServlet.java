package org.cysecurity.cspf.jvl.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.json.JSONObject;

/**
 * Admin-only endpoint that sends weekly performance reports to configured team leads.
 */
public class WeeklyPerformanceReportServlet extends HttpServlet {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setSecurityHeaders(response);
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("isLoggedIn") == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath)) {
            props.load(in);
        }

        try (Connection con = new DBConnect().connect(configPath)) {
            Integer userId = parseInt(session.getAttribute("userid"));
            if (userId == null || !isAdmin(con, userId.intValue())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Admin access required");
                return;
            }

            List<String> recipients = parseRecipients(props.getProperty("reports.team.leads.emails"));
            if (recipients.isEmpty()) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No team lead recipients configured");
                return;
            }

            String reportBody = buildReport(con);
            sendEmail(props, recipients, reportBody);

            JSONObject ok = new JSONObject();
            ok.put("status", "sent");
            ok.put("recipientCount", recipients.size());
            ok.put("period", "weekly");
            response.getWriter().print(ok.toString());
        } catch (MessagingException e) {
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY, "Unable to send report email");
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to build weekly report");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(new JSONObject().put("error", "POST required").toString());
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isAdmin(Connection con, int userId) throws Exception {
        String sql = "select privilege from users where id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String privilege = rs.getString("privilege");
                return privilege != null && "admin".equalsIgnoreCase(privilege.trim());
            }
        }
    }

    private static List<String> parseRecipients(String configured) {
        List<String> out = new ArrayList<String>();
        if (configured == null) {
            return out;
        }
        String[] parts = configured.split(",");
        for (String raw : parts) {
            String email = raw == null ? "" : raw.trim();
            if (email.isEmpty()) {
                continue;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                continue;
            }
            out.add(email);
        }
        return out;
    }

    private static String buildReport(Connection con) throws Exception {
        int activeUsers = scalar(con, "select count(*) from users");
        int totalPosts = scalar(con, "select count(*) from posts");
        int contactMessages = scalar(con, "select count(*) from messages");
        int directMessages = scalar(con, "select count(*) from usermessages");

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(7);
        StringBuilder body = new StringBuilder();
        body.append("Weekly Performance Report\n");
        body.append("Window: ").append(start).append(" to ").append(end).append("\n\n");
        body.append("Current Platform Activity:\n");
        body.append("- Registered users: ").append(activeUsers).append("\n");
        body.append("- Total posts: ").append(totalPosts).append("\n");
        body.append("- Contact messages: ").append(contactMessages).append("\n");
        body.append("- Direct messages: ").append(directMessages).append("\n");
        return body.toString();
    }

    private static int scalar(Connection con, String sql) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void sendEmail(Properties props, List<String> recipients, String body)
            throws MessagingException {
        final String host = trimToEmpty(props.getProperty("reports.smtp.host"));
        final String username = trimToEmpty(props.getProperty("reports.smtp.username"));
        final String password = trimToEmpty(props.getProperty("reports.smtp.password"));
        final String from = trimToEmpty(props.getProperty("reports.smtp.from"));
        final String port = trimToEmpty(props.getProperty("reports.smtp.port"));

        if (host.isEmpty() || username.isEmpty() || password.isEmpty() || from.isEmpty()) {
            throw new MessagingException("SMTP configuration is incomplete");
        }

        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.smtp.host", host);
        mailProps.put("mail.smtp.port", port.isEmpty() ? "587" : port);
        mailProps.put("mail.smtp.connectiontimeout", "10000");
        mailProps.put("mail.smtp.timeout", "10000");
        mailProps.put("mail.smtp.writetimeout", "10000");

        Session mailSession = Session.getInstance(mailProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress(from));
        for (String recipient : recipients) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }
        message.setSubject("Weekly Performance Report");
        message.setText(body);
        Transport.send(message);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
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
