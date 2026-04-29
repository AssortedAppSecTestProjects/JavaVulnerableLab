package org.cysecurity.cspf.jvl.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

/**
 * Public billing configuration (publishable key only; no secrets).
 */
public class BillingConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

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
        final String cur = currency.isEmpty() ? "usd" : currency;

        final JSONObject body = new JSONObject();
        body.put("publishableKey", publishableKey);
        body.put("currency", cur);
        body.put("pricingModel", "per_active_user");

        if (publishableKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            body.put("configured", false);
            body.put("error", "Stripe publishable key is not configured");
        } else {
            body.put("configured", true);
        }

        response.getWriter().print(body.toString());
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
