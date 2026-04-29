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
 * Persists ticket tier and amount (step 2).
 */
public class SecConStep2Servlet extends HttpServlet {

    private static final String CHARSET = "UTF-8";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(CHARSET);
        final String ctx = request.getContextPath();

        final HttpSession session = request.getSession(false);
        if (session == null || !SecConRegistration.hasContact(session)) {
            response.sendRedirect(ctx + "/seccon/register-1.jsp?err="
                    + URLEncoder.encode("Please start with your contact information.", CHARSET));
            return;
        }

        final String tierParam = request.getParameter("tier");
        final int amount = SecConRegistration.amountForTier(tierParam);
        if (amount < 0) {
            response.sendRedirect(ctx + "/seccon/register-2.jsp?err="
                    + URLEncoder.encode("Please select Basic or Preferred.", CHARSET));
            return;
        }

        session.setAttribute(SecConRegistration.ATTR_TIER, tierParam.trim().toLowerCase());
        session.setAttribute(SecConRegistration.ATTR_AMOUNT_CENTS, Long.valueOf(amount));
        session.removeAttribute(SecConRegistration.SESSION_CSRF);

        response.sendRedirect(ctx + "/seccon/register-3.jsp");
    }
}
