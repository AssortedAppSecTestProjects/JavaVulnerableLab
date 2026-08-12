package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SubscriptionCreateParams;
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
 * Attaches the confirmed payment method for the logged-in user's Stripe customer and creates a
 * subscription with quantity derived from active billable users.
 */
public class BillingCompleteServlet extends HttpServlet {

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

        final String paymentMethodId = bodyIn.has("paymentMethodId")
                ? bodyIn.getString("paymentMethodId").trim()
                : "";
        if (!BillingApiSupport.isValidPaymentMethodId(paymentMethodId)) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "paymentMethodId is required");
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

        if (!stripe.isBillingReady()) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Stripe billing is not fully configured");
            return;
        }

        String customerId;
        int activeUsers;
        try (Connection con = new DBConnect().connect(configPath)) {
            if (con == null || con.isClosed()) {
                BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database unavailable");
                return;
            }
            customerId = BillingDataAccess.findStripeCustomerId(con, userId);
            activeUsers = BillingDataAccess.countBillableSeats(con);
        } catch (Exception e) {
            BillingApiSupport.auditFailure("complete_lookup", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load billing account");
            return;
        }

        if (customerId == null || customerId.trim().isEmpty()) {
            BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing Stripe customer; call setup-intent first");
            return;
        }

        final long quantity = Math.max(1L, (long) activeUsers);
        Stripe.apiKey = stripe.getSecretKey();

        try {
            final PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
            final String pmCustomer = pm.getCustomer();
            if (pmCustomer != null && !pmCustomer.isEmpty() && !customerId.equals(pmCustomer)) {
                BillingApiSupport.audit("complete_idor_blocked", userId, "payment_method_mismatch");
                BillingApiSupport.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Payment method does not belong to this account");
                return;
            }

            if (pmCustomer == null || pmCustomer.isEmpty()) {
                pm.attach(PaymentMethodAttachParams.builder().setCustomer(customerId).build());
            }

            final Customer customer = Customer.retrieve(customerId);
            final String metaUser = customer.getMetadata() != null
                    ? customer.getMetadata().get("app_user_id")
                    : null;
            if (metaUser != null && !metaUser.isEmpty() && !userId.equals(metaUser)) {
                BillingApiSupport.audit("complete_idor_blocked", userId, "customer_metadata_mismatch");
                BillingApiSupport.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Billing account mismatch");
                return;
            }

            customer.update(CustomerUpdateParams.builder()
                    .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                            .setDefaultPaymentMethod(paymentMethodId)
                            .build())
                    .build());

            final Subscription subscription = Subscription.create(SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(stripe.getPriceId())
                            .setQuantity(quantity)
                            .build())
                    .putMetadata("app_user_id", userId)
                    .build());

            String last4 = null;
            String brand = null;
            if (pm.getCard() != null) {
                last4 = pm.getCard().getLast4();
                brand = pm.getCard().getBrand();
            }

            try (Connection con = new DBConnect().connect(configPath)) {
                BillingDataAccess.updateSubscription(
                        con,
                        userId,
                        subscription.getId(),
                        subscription.getStatus(),
                        (int) quantity,
                        last4,
                        brand);
            }

            BillingApiSupport.clearCsrf(session);
            BillingApiSupport.audit("complete", userId,
                    "subscription=" + subscription.getId() + " seats=" + quantity);

            final JSONObject out = new JSONObject();
            out.put("subscriptionId", subscription.getId());
            out.put("status", subscription.getStatus());
            out.put("quantity", quantity);
            if (last4 != null) {
                out.put("cardLast4", last4);
            }
            if (brand != null) {
                out.put("cardBrand", brand);
            }
            BillingApiSupport.writeJson(response, HttpServletResponse.SC_OK, out);
        } catch (StripeException e) {
            BillingApiSupport.auditFailure("complete_stripe", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Unable to complete billing");
        } catch (Exception e) {
            BillingApiSupport.auditFailure("complete", userId, e);
            BillingApiSupport.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to complete billing");
        }
    }
}
