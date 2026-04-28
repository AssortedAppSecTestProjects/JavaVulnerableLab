package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.util.Properties;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.BillingDataAccess;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.json.JSONObject;

/**
 * Authenticated quote for per-seat pricing based on billable users in the database.
 */
public class BillingQuoteServlet extends HttpServlet {

    private static final String SESSION_CSRF = "billingCsrfToken";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        final HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("isLoggedIn") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print(new JSONObject().put("error", "Login required").toString());
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

        final String secretKey = trimToEmpty(props.getProperty("stripe.secret.key"));
        final String priceId = trimToEmpty(props.getProperty("stripe.price.id"));
        final String currency = trimToEmpty(props.getProperty("stripe.currency"));
        final String cur = currency.isEmpty() ? "usd" : currency;

        if (secretKey.isEmpty() || priceId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().print(new JSONObject()
                    .put("error", "Stripe billing is not fully configured (secret key and price id required)")
                    .toString());
            return;
        }

        int activeUsers;
        try (Connection con = new DBConnect().connect(configPath)) {
            if (con == null || con.isClosed()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().print(new JSONObject().put("error", "Database unavailable").toString());
                return;
            }
            activeUsers = BillingDataAccess.countBillableSeats(con);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(new JSONObject().put("error", "Unable to compute seat count").toString());
            return;
        }

        Stripe.apiKey = secretKey;
        long unitAmountCents;
        try {
            final Price price = Price.retrieve(priceId);
            final Long ua = price.getUnitAmount();
            if (ua == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().print(new JSONObject()
                        .put("error", "Configured Stripe price is not a per-unit recurring price")
                        .toString());
                return;
            }
            unitAmountCents = ua.longValue();
        } catch (StripeException e) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getWriter().print(new JSONObject()
                    .put("error", "Unable to load Stripe price: " + e.getMessage())
                    .toString());
            return;
        }

        final int billableSeatsForCharge = Math.max(1, activeUsers);
        final long estimatedMonthlyCents = unitAmountCents * (long) billableSeatsForCharge;

        final String csrf = UUID.randomUUID().toString();
        session.setAttribute(SESSION_CSRF, csrf);

        final JSONObject body = new JSONObject();
        body.put("activeUsers", activeUsers);
        body.put("billableSeatsForCharge", billableSeatsForCharge);
        body.put("unitAmountCents", unitAmountCents);
        body.put("estimatedMonthlyCents", estimatedMonthlyCents);
        body.put("currency", cur);
        body.put("stripePriceId", priceId);
        body.put("csrfToken", csrf);

        response.getWriter().print(body.toString());
    }

    static boolean validateCsrf(HttpSession session, String supplied) {
        if (session == null || supplied == null) {
            return false;
        }
        final Object expected = session.getAttribute(SESSION_CSRF);
        if (!(expected instanceof String)) {
            return false;
        }
        return supplied.equals(expected);
    }

    static void clearCsrf(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_CSRF);
        }
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
