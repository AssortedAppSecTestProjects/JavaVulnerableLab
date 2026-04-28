package org.cysecurity.cspf.jvl.controller;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.SecConRegistration;
import org.json.JSONObject;

/**
 * Creates a Stripe PaymentIntent for SecCon registration (one-time charge).
 */
public class SecConPaymentIntentServlet extends HttpServlet {

    private static final int METADATA_MAX = 500;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        final HttpSession session = request.getSession(false);
        if (session == null || !SecConRegistration.hasTier(session)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print(new JSONObject()
                    .put("error", "Registration session is incomplete.")
                    .toString());
            return;
        }

        final JSONObject bodyIn = readJsonObject(request);
        final String csrf = bodyIn == null || !bodyIn.has("csrf") ? null : bodyIn.getString("csrf");
        if (!SecConConfigServlet.validateCsrf(session, csrf)) {
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
        final String currency = trimToEmpty(props.getProperty("stripe.currency"));
        final String cur = currency.isEmpty() ? "usd" : currency.toLowerCase();

        if (secretKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().print(new JSONObject().put("error", "Stripe secret key is not configured").toString());
            return;
        }

        final long amountCents = ((Long) session.getAttribute(SecConRegistration.ATTR_AMOUNT_CENTS)).longValue();
        final String email = String.valueOf(session.getAttribute(SecConRegistration.ATTR_EMAIL)).trim();
        final String tier = String.valueOf(session.getAttribute(SecConRegistration.ATTR_TIER)).trim();
        final String registrantName = String.valueOf(session.getAttribute(SecConRegistration.ATTR_NAME)).trim();

        Stripe.apiKey = secretKey;

        try {
            final PaymentIntentCreateParams.Builder pb = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(cur)
                    .addPaymentMethodType("card")
                    .putMetadata("conference", truncate(SecConRegistration.CONFERENCE_DISPLAY, METADATA_MAX))
                    .putMetadata("tier", truncate(tier, METADATA_MAX));

            if (!email.isEmpty()) {
                pb.setReceiptEmail(email);
            }
            if (!registrantName.isEmpty()) {
                pb.putMetadata("registrant_name", truncate(registrantName, METADATA_MAX));
            }

            final PaymentIntent pi = PaymentIntent.create(pb.build());

            final JSONObject out = new JSONObject();
            out.put("clientSecret", pi.getClientSecret());
            out.put("paymentIntentId", pi.getId());
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
