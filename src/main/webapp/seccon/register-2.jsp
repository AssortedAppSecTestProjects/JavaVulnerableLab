<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String pathCtx = request.getContextPath();
    if (session.getAttribute("seccon_name") == null) {
        response.sendRedirect(pathCtx + "/seccon/register-1.jsp?err="
            + java.net.URLEncoder.encode("Please start with your contact information.", "UTF-8"));
        return;
    }
%>
<%@ include file="../header.jsp" %>
<div class="registration-wrap">
    <h2>Apiiro SecCon 2026 — Ticket level</h2>
    <p class="registration-lead">Step 2 of 3: Choose your conference pass.</p>
    <% if (request.getParameter("err") != null) { %>
    <p class="fail"><%= request.getParameter("err") %></p>
    <% } %>
    <form action="<%=path%>/seccon/step2" method="post" class="registration-form">
        <table class="registration-table">
            <tr>
                <th scope="row"><label><input type="radio" name="tier" value="basic" required="required" /> Basic</label></th>
                <td>$25 — Standard conference access.</td>
            </tr>
            <tr>
                <th scope="row"><label><input type="radio" name="tier" value="preferred" /> Preferred</label></th>
                <td>$50 — Preferred seating and networking session.</td>
            </tr>
            <tr>
                <td></td>
                <td class="registration-actions">
                    <input type="submit" value="Continue to payment" />
                    <a href="<%=path%>/seccon/register-1.jsp" style="margin-left:1em;">Back</a>
                </td>
            </tr>
        </table>
    </form>
</div>
<%@ include file="../footer.jsp" %>
