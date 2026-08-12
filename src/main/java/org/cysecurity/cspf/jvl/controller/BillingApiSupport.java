package org.cysecurity.cspf.jvl.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.json.JSONObject;

/**
 * Shared auth, CSRF, and JSON helpers for billing APIs.
 */
final class BillingApiSupport {

    private static final Logger LOG = Logger.getLogger(BillingApiSupport.class.getName());
    private static final String SESSION_CSRF = "billingCsrfToken";
    private static final Pattern PAYMENT_METHOD_ID = Pattern.compile("^pm_[A-Za-z0-9]+$");

    private BillingApiSupport() {
    }

    static boolean requireLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("isLoggedIn") == null
                || session.getAttribute("userid") == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return false;
        }
        return true;
    }

    static String currentUserId(HttpSession session) {
        return String.valueOf(session.getAttribute("userid"));
    }

    static String issueCsrf(HttpSession session) {
        final String csrf = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF, csrf);
        return csrf;
    }

    static boolean validateCsrf(HttpSession session, String supplied) {
        if (session == null || supplied == null) {
            return false;
        }
        final Object expected = session.getAttribute(SESSION_CSRF);
        if (!(expected instanceof String)) {
            return false;
        }
        final byte[] a = ((String) expected).getBytes(StandardCharsets.UTF_8);
        final byte[] b = supplied.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    static void clearCsrf(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_CSRF);
        }
    }

    static boolean isValidPaymentMethodId(String paymentMethodId) {
        return paymentMethodId != null && PAYMENT_METHOD_ID.matcher(paymentMethodId).matches();
    }

    static JSONObject readJsonObject(HttpServletRequest request) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        final String raw = sb.toString().trim();
        if (raw.isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(raw);
    }

    static void writeJson(HttpServletResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().print(body.toString());
    }

    static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        writeJson(response, status, new JSONObject().put("error", message));
    }

    static void audit(String action, String userId, String detail) {
        LOG.log(Level.INFO, "billing_audit action={0} userId={1} detail={2}",
                new Object[]{action, userId, detail});
    }

    static void auditFailure(String action, String userId, Exception e) {
        LOG.log(Level.WARNING, "billing_audit action=" + action + " userId=" + userId + " failed", e);
    }
}
