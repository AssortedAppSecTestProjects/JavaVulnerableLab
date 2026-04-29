package org.cysecurity.cspf.jvl.controller;

import java.io.IOException;
import java.net.URLEncoder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.cysecurity.cspf.jvl.model.SecConRegistration;

/**
 * Persists conference registrant contact information in the HTTP session (step 1).
 */
public class SecConStep1Servlet extends HttpServlet {

    private static final String CHARSET = "UTF-8";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(CHARSET);

        final String name = trimToNull(request.getParameter("name"));
        final String address = trimToNull(request.getParameter("address"));
        final String phone = trimToNull(request.getParameter("telephone"));
        final String email = trimToNull(request.getParameter("email"));

        final String ctx = request.getContextPath();

        if (name == null || address == null || phone == null || email == null) {
            response.sendRedirect(ctx + "/seccon/register-1.jsp?err="
                    + URLEncoder.encode("Please fill in all fields.", CHARSET));
            return;
        }

        final HttpSession session = request.getSession(true);
        session.setAttribute(SecConRegistration.ATTR_NAME, name);
        session.setAttribute(SecConRegistration.ATTR_ADDRESS, address);
        session.setAttribute(SecConRegistration.ATTR_PHONE, phone);
        session.setAttribute(SecConRegistration.ATTR_EMAIL, email);

        session.removeAttribute(SecConRegistration.ATTR_TIER);
        session.removeAttribute(SecConRegistration.ATTR_AMOUNT_CENTS);
        session.removeAttribute(SecConRegistration.SESSION_CSRF);

        response.sendRedirect(ctx + "/seccon/register-2.jsp");
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        final String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
