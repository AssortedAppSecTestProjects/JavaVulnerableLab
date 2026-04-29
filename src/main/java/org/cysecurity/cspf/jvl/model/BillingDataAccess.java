package org.cysecurity.cspf.jvl.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Read-only billing queries using parameterized SQL.
 */
public final class BillingDataAccess {

    private BillingDataAccess() {
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
}
