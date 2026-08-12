package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import java.io.IOException;
import java.sql.Connection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.BillingDataAccess;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.cysecurity.cspf.jvl.model.StripeConfig;
import org.json.JSONObject;

/**
 * Authenticated quote for per-seat pricing based on billable users in the database.
 */
public class BillingQuoteServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!BillingApiSupport.requireLogin(request, response)) {
            return;
        }

        final HttpSession session = request.getSession(false);
        final String userId = BillingApiSupport.currentUserId(session);
        final String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");

        final StripeConfig stripe;
        try {
            stripe = StripeConfig.load(configPath);
        } catch (IOException e) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load configuration");
            return;
        }

        if (!stripe.isBillingReady()) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Stripe billing is not fully configured");
            return;
        }

        int activeUsers;
        try (Connection con = new DBConnect().connect(configPath)) {
            if (con == null || con.isClosed()) {
                BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database unavailable");
                return;
            }
            activeUsers = BillingDataAccess.countBillableSeats(con);
        } catch (Exception e) {
            BillingApiSupport.auditFailure("quote_seat_count", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to compute seat count");
            return;
        }

        Stripe.apiKey = stripe.getSecretKey();
        long unitAmountCents;
        try {
            final Price price = Price.retrieve(stripe.getPriceId());
            final Long ua = price.getUnitAmount();
            if (ua == null) {
                BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Configured Stripe price is not a per-unit recurring price");
                return;
            }
            unitAmountCents = ua.longValue();
        } catch (StripeException e) {
            BillingApiSupport.auditFailure("quote_price_retrieve", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Unable to load pricing from Stripe");
            return;
        }

        final int billableSeatsForCharge = Math.max(1, activeUsers);
        final long estimatedMonthlyCents = unitAmountCents * (long) billableSeatsForCharge;
        final String csrf = BillingApiSupport.issueCsrf(session);

        final JSONObject body = new JSONObject();
        body.put("activeUsers", activeUsers);
        body.put("billableSeatsForCharge", billableSeatsForCharge);
        body.put("unitAmountCents", unitAmountCents);
        body.put("estimatedMonthlyCents", estimatedMonthlyCents);
        body.put("currency", stripe.getCurrency());
        body.put("csrfToken", csrf);

        BillingApiSupport.audit("quote", userId, "seats=" + billableSeatsForCharge);
        BillingApiSupport.writeJson(response, HttpServletResponse.SC_OK, body);
    }
}
