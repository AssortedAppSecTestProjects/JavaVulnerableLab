package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
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
 * Creates (or reuses) a Stripe customer for the logged-in user and returns a SetupIntent client secret.
 */
public class BillingSetupIntentServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!BillingApiSupport.requireLogin(request, response)) {
            return;
        }

        final HttpSession session = request.getSession(false);
        final String userId = BillingApiSupport.currentUserId(session);

        final JSONObject bodyIn = BillingApiSupport.readJsonObject(request);
        final String csrf = bodyIn.has("csrf") ? bodyIn.getString("csrf") : null;
        if (!BillingApiSupport.validateCsrf(session, csrf)) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token");
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

        if (stripe.getSecretKey().isEmpty()) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Stripe secret key is not configured");
            return;
        }

        String email;
        String existingCustomerId;
        try (Connection con = new DBConnect().connect(configPath)) {
            if (con == null || con.isClosed()) {
                BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database unavailable");
                return;
            }
            email = BillingDataAccess.findEmailForUserId(con, userId);
            existingCustomerId = BillingDataAccess.findStripeCustomerId(con, userId);
        } catch (Exception e) {
            BillingApiSupport.auditFailure("setup_intent_user_lookup", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load user profile");
            return;
        }

        Stripe.apiKey = stripe.getSecretKey();

        try {
            String customerId = existingCustomerId;
            if (customerId == null || customerId.trim().isEmpty()) {
                final CustomerCreateParams.Builder cb = CustomerCreateParams.builder()
                        .putMetadata("app_user_id", userId);
                if (email != null && !email.trim().isEmpty()) {
                    cb.setEmail(email.trim());
                }
                final Customer customer = Customer.create(cb.build());
                customerId = customer.getId();
                try (Connection con = new DBConnect().connect(configPath)) {
                    BillingDataAccess.upsertStripeCustomer(con, userId, customerId);
                }
            }

            final SetupIntentCreateParams sip = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .addPaymentMethodType("card")
                    .putMetadata("app_user_id", userId)
                    .build();
            final SetupIntent setupIntent = SetupIntent.create(sip);

            BillingApiSupport.audit("setup_intent", userId, "created");
            final JSONObject out = new JSONObject();
            out.put("clientSecret", setupIntent.getClientSecret());
            BillingApiSupport.writeJson(response, HttpServletResponse.SC_OK, out);
        } catch (StripeException e) {
            BillingApiSupport.auditFailure("setup_intent_stripe", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Unable to start secure card setup");
        } catch (Exception e) {
            BillingApiSupport.auditFailure("setup_intent", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to start secure card setup");
        }
    }
}
