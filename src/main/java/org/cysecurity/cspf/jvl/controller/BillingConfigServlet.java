package org.cysecurity.cspf.jvl.controller;

import java.io.IOException;
import java.sql.Connection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.BillingDataAccess;
import org.cysecurity.cspf.jvl.model.BillingDataAccess.BillingAccountSummary;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.cysecurity.cspf.jvl.model.StripeConfig;
import org.json.JSONObject;

/**
 * Authenticated billing configuration (publishable key only; no secrets).
 */
public class BillingConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!BillingApiSupport.requireLogin(request, response)) {
            return;
        }

        final String configPath = getServletContext().getRealPath("/WEB-INF/config.properties");
        final StripeConfig stripe;
        try {
            stripe = StripeConfig.load(configPath);
        } catch (IOException e) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load configuration");
            return;
        }

        final HttpSession session = request.getSession(false);
        final String userId = BillingApiSupport.currentUserId(session);

        final JSONObject body = new JSONObject();
        body.put("publishableKey", stripe.getPublishableKey());
        body.put("currency", stripe.getCurrency());
        body.put("pricingModel", "per_active_user");
        body.put("configured", stripe.hasPublishableKey() && stripe.isBillingReady());

        if (!stripe.hasPublishableKey() || !stripe.isBillingReady()) {
            body.put("error", "Stripe billing is not fully configured");
            BillingApiSupport.writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, body);
            return;
        }

        try (Connection con = new DBConnect().connect(configPath)) {
            if (con != null && !con.isClosed()) {
                final BillingAccountSummary summary = BillingDataAccess.findAccountSummary(con, userId);
                if (summary != null) {
                    final JSONObject account = new JSONObject();
                    if (summary.getLast4() != null) {
                        account.put("cardLast4", summary.getLast4());
                    }
                    if (summary.getBrand() != null) {
                        account.put("cardBrand", summary.getBrand());
                    }
                    if (summary.getStatus() != null) {
                        account.put("subscriptionStatus", summary.getStatus());
                    }
                    if (summary.getSeatQuantity() > 0) {
                        account.put("seatQuantity", summary.getSeatQuantity());
                    }
                    body.put("account", account);
                }
            }
        } catch (Exception e) {
            BillingApiSupport.auditFailure("config_account_lookup", userId, e);
        }

        BillingApiSupport.writeJson(response, HttpServletResponse.SC_OK, body);
    }
}
