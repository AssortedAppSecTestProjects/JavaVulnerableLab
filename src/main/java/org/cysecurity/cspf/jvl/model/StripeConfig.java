package org.cysecurity.cspf.jvl.model;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads Stripe settings from environment variables first, then config.properties placeholders.
 */
public final class StripeConfig {

    private final String secretKey;
    private final String publishableKey;
    private final String priceId;
    private final String currency;

    private StripeConfig(String secretKey, String publishableKey, String priceId, String currency) {
        this.secretKey = secretKey;
        this.publishableKey = publishableKey;
        this.priceId = priceId;
        this.currency = currency;
    }

    public static StripeConfig load(String configPath) throws IOException {
        final Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPath)) {
            props.load(in);
        }
        final String secret = firstNonEmpty(
                System.getenv("STRIPE_SECRET_KEY"),
                props.getProperty("stripe.secret.key"));
        final String publishable = firstNonEmpty(
                System.getenv("STRIPE_PUBLISHABLE_KEY"),
                props.getProperty("stripe.publishable.key"));
        final String price = firstNonEmpty(
                System.getenv("STRIPE_PRICE_ID"),
                props.getProperty("stripe.price.id"));
        String currency = firstNonEmpty(
                System.getenv("STRIPE_CURRENCY"),
                props.getProperty("stripe.currency"));
        if (currency.isEmpty()) {
            currency = "usd";
        }
        return new StripeConfig(secret, publishable, price, currency);
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    public String getPriceId() {
        return priceId;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean hasPublishableKey() {
        return !publishableKey.isEmpty();
    }

    public boolean isBillingReady() {
        return !secretKey.isEmpty() && !priceId.isEmpty();
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return "";
    }
}
