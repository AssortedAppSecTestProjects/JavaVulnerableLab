package org.cysecurity.cspf.jvl.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.SecConRegistration;
import org.json.JSONObject;

/**
 * Public JSON for SecCon checkout: publishable key and CSRF after steps 1–2 are complete.
 */
public class SecConConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        final HttpSession session = request.getSession(false);
        if (session == null || !SecConRegistration.hasTier(session)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print(new JSONObject()
                    .put("error", "Registration session is incomplete. Complete steps 1 and 2 first.")
                    .toString());
            return;
        }

        final String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");
        final Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(new JSONObject().put("error", "Unable to load configuration").toString());
            return;
        }

        final String publishableKey = trimToEmpty(props.getProperty("stripe.publishable.key"));
        final String currency = trimToEmpty(props.getProperty("stripe.currency"));
        final String cur = currency.isEmpty() ? "usd" : currency.toLowerCase();

        final JSONObject body = new JSONObject();
        body.put("publishableKey", publishableKey);
        body.put("currency", cur);
        body.put("conference", SecConRegistration.CONFERENCE_DISPLAY);

        final String tier = String.valueOf(session.getAttribute(SecConRegistration.ATTR_TIER));
        final long amountCents = ((Long) session.getAttribute(SecConRegistration.ATTR_AMOUNT_CENTS)).longValue();
        body.put("tier", tier);
        body.put("tierLabel", SecConRegistration.tierLabel(tier));
        body.put("amountCents", amountCents);
        body.put("registrantName", String.valueOf(session.getAttribute(SecConRegistration.ATTR_NAME)));
        body.put("email", String.valueOf(session.getAttribute(SecConRegistration.ATTR_EMAIL)));

        if (publishableKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            body.put("configured", false);
            body.put("error", "Stripe publishable key is not configured");
            response.getWriter().print(body.toString());
            return;
        }

        body.put("configured", true);

        final String csrf = UUID.randomUUID().toString();
        session.setAttribute(SecConRegistration.SESSION_CSRF, csrf);
        body.put("csrfToken", csrf);

        response.getWriter().print(body.toString());
    }

    static boolean validateCsrf(HttpSession session, String supplied) {
        if (session == null || supplied == null) {
            return false;
        }
        final Object expected = session.getAttribute(SecConRegistration.SESSION_CSRF);
        if (!(expected instanceof String)) {
            return false;
        }
        return supplied.equals(expected);
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
