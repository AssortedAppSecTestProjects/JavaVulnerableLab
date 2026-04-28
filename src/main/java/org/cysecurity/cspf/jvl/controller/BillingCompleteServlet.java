package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SubscriptionCreateParams;
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
 * Attaches the confirmed payment method to the session's Stripe customer and creates a subscription
 * with quantity derived from active billable users.
 */
public class BillingCompleteServlet extends HttpServlet {

    private static final String SESSION_SUBSCRIPTION = "stripeSubscriptionId";

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

        final String paymentMethodId = bodyIn == null || !bodyIn.has("paymentMethodId")
                ? ""
                : trimToEmpty(bodyIn.getString("paymentMethodId"));
        if (paymentMethodId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print(new JSONObject().put("error", "paymentMethodId is required").toString());
            return;
        }

        final String customerId = BillingSetupIntentServlet.getStripeCustomerId(session);
        if (customerId == null || customerId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print(new JSONObject().put("error", "Missing Stripe customer; call setup-intent first").toString());
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
        if (secretKey.isEmpty() || priceId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().print(new JSONObject().put("error", "Stripe billing is not fully configured").toString());
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

        final long quantity = Math.max(1L, (long) activeUsers);

        Stripe.apiKey = secretKey;
        try {
            final PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
            pm.attach(PaymentMethodAttachParams.builder().setCustomer(customerId).build());

            final Customer customer = Customer.retrieve(customerId);
            customer.update(CustomerUpdateParams.builder()
                    .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                            .setDefaultPaymentMethod(paymentMethodId)
                            .build())
                    .build());

            final Subscription subscription = Subscription.create(SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .setQuantity(quantity)
                            .build())
                    .build());

            session.setAttribute(SESSION_SUBSCRIPTION, subscription.getId());
            BillingQuoteServlet.clearCsrf(session);

            final JSONObject out = new JSONObject();
            out.put("subscriptionId", subscription.getId());
            out.put("status", subscription.getStatus());
            out.put("quantity", quantity);
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

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
