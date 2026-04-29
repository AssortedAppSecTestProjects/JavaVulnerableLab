package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.BillingDataAccess;
import org.cysecurity.cspf.jvl.model.DBConnect;
import org.json.JSONObject;

/**
 * Creates (or reuses) a Stripe customer for the logged-in user and returns a SetupIntent client secret.
 */
public class BillingSetupIntentServlet extends HttpServlet {

    private static final String SESSION_STRIPE_CUSTOMER = "stripeCustomerId";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        final HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("isLoggedIn") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print(new JSONObject().put("error", "Login required").toString());
            return;
        }

        final JSONObject bodyIn = readJsonObject(request);
        final String csrf = bodyIn == null || !bodyIn.has("csrf") ? null : bodyIn.getString("csrf");
        if (!BillingQuoteServlet.validateCsrf(session, csrf)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().print(new JSONObject().put("error", "Invalid or missing CSRF token").toString());
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
        if (secretKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().print(new JSONObject().put("error", "Stripe secret key is not configured").toString());
            return;
        }

        final String userId = String.valueOf(session.getAttribute("userid"));
        String email;
        try (Connection con = new DBConnect().connect(configPath)) {
            if (con == null || con.isClosed()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().print(new JSONObject().put("error", "Database unavailable").toString());
                return;
            }
            email = BillingDataAccess.findEmailForUserId(con, userId);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(new JSONObject().put("error", "Unable to load user profile").toString());
            return;
        }

        Stripe.apiKey = secretKey;

        String customerId = (String) session.getAttribute(SESSION_STRIPE_CUSTOMER);
        try {
            if (customerId == null || customerId.trim().isEmpty()) {
                final CustomerCreateParams.Builder cb = CustomerCreateParams.builder()
                        .putMetadata("app_user_id", userId);
                if (email != null && !email.trim().isEmpty()) {
                    cb.setEmail(email.trim());
                }
                final Customer customer = Customer.create(cb.build());
                customerId = customer.getId();
                session.setAttribute(SESSION_STRIPE_CUSTOMER, customerId);
            }

            final SetupIntentCreateParams sip = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .addPaymentMethodType("card")
                    .build();
            final SetupIntent setupIntent = SetupIntent.create(sip);

            final JSONObject out = new JSONObject();
            out.put("clientSecret", setupIntent.getClientSecret());
            out.put("customerId", customerId);
            response.getWriter().print(out.toString());
        } catch (StripeException e) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getWriter().print(new JSONObject().put("error", e.getMessage()).toString());
        }
    }

    private static JSONObject readJsonObject(HttpServletRequest request) throws IOException {
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

    static String getStripeCustomerId(HttpSession session) {
        if (session == null) {
            return null;
        }
        final Object v = session.getAttribute(SESSION_STRIPE_CUSTOMER);
        return v instanceof String ? (String) v : null;
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
