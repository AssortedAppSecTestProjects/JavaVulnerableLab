package org.cysecurity.cspf.jvl.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Billing persistence using parameterized SQL. Stores Stripe tokenized references only — never PAN/CVV.
 */
public final class BillingDataAccess {

    private BillingDataAccess() {
    }

    public static void ensureSchema(Connection con) throws SQLException {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS billing_accounts ("
                            + "user_id INT NOT NULL PRIMARY KEY,"
                            + "stripe_customer_id VARCHAR(64) NOT NULL,"
                            + "stripe_subscription_id VARCHAR(64),"
                            + "payment_method_last4 VARCHAR(4),"
                            + "payment_method_brand VARCHAR(32),"
                            + "subscription_status VARCHAR(32),"
                            + "seat_quantity INT,"
                            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                            + ")");
        }
    }

    /**
     * Billable seats: registered users with the standard {@code user} privilege (excludes admins).
     */
    public static int countBillableSeats(Connection con) throws SQLException {
        final String sql = "SELECT COUNT(*) AS c FROM users WHERE LOWER(privilege) = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "user");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs != null && rs.next()) {
                    return rs.getInt("c");
                }
            }
        }
        return 0;
    }

    public static String findEmailForUserId(Connection con, String userId) throws SQLException {
        final String sql = "SELECT email FROM users WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs != null && rs.next()) {
                    return rs.getString("email");
                }
            }
        }
        return null;
    }

    public static String findStripeCustomerId(Connection con, String userId) throws SQLException {
        ensureSchema(con);
        final String sql = "SELECT stripe_customer_id FROM billing_accounts WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(userId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs != null && rs.next()) {
                    return rs.getString("stripe_customer_id");
                }
            }
        }
        return null;
    }

    public static void upsertStripeCustomer(Connection con, String userId, String stripeCustomerId)
            throws SQLException {
        ensureSchema(con);
        final String sql =
                "INSERT INTO billing_accounts (user_id, stripe_customer_id) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE stripe_customer_id = VALUES(stripe_customer_id)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(userId));
            ps.setString(2, stripeCustomerId);
            ps.executeUpdate();
        }
    }

    public static void updateSubscription(
            Connection con,
            String userId,
            String subscriptionId,
            String status,
            int seatQuantity,
            String last4,
            String brand)
            throws SQLException {
        ensureSchema(con);
        final String sql =
                "UPDATE billing_accounts SET stripe_subscription_id = ?, subscription_status = ?, "
                        + "seat_quantity = ?, payment_method_last4 = ?, payment_method_brand = ? "
                        + "WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, subscriptionId);
            ps.setString(2, status);
            ps.setInt(3, seatQuantity);
            ps.setString(4, last4);
            ps.setString(5, brand);
            ps.setInt(6, Integer.parseInt(userId));
            ps.executeUpdate();
        }
    }

    public static BillingAccountSummary findAccountSummary(Connection con, String userId)
            throws SQLException {
        ensureSchema(con);
        final String sql =
                "SELECT stripe_customer_id, stripe_subscription_id, payment_method_last4, "
                        + "payment_method_brand, subscription_status, seat_quantity "
                        + "FROM billing_accounts WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(userId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs != null && rs.next()) {
                    return new BillingAccountSummary(
                            rs.getString("stripe_customer_id"),
                            rs.getString("stripe_subscription_id"),
                            rs.getString("payment_method_last4"),
                            rs.getString("payment_method_brand"),
                            rs.getString("subscription_status"),
                            rs.getInt("seat_quantity"));
                }
            }
        }
        return null;
    }

    public static final class BillingAccountSummary {
        private final String stripeCustomerId;
        private final String stripeSubscriptionId;
        private final String last4;
        private final String brand;
        private final String status;
        private final int seatQuantity;

        public BillingAccountSummary(
                String stripeCustomerId,
                String stripeSubscriptionId,
                String last4,
                String brand,
                String status,
                int seatQuantity) {
            this.stripeCustomerId = stripeCustomerId;
            this.stripeSubscriptionId = stripeSubscriptionId;
            this.last4 = last4;
            this.brand = brand;
            this.status = status;
            this.seatQuantity = seatQuantity;
        }

        public String getStripeCustomerId() {
            return stripeCustomerId;
        }

        public String getStripeSubscriptionId() {
            return stripeSubscriptionId;
        }

        public String getLast4() {
            return last4;
        }

        public String getBrand() {
            return brand;
        }

        public String getStatus() {
            return status;
        }

        public int getSeatQuantity() {
            return seatQuantity;
        }
    }
}
