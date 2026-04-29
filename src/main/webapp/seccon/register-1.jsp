<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="../header.jsp" %>
<div class="registration-wrap">
    <h2>Apiiro SecCon 2026 — Register</h2>
    <p class="registration-lead">Step 1 of 3: Your contact information.</p>
    <% if (request.getParameter("err") != null) { %>
    <p class="fail"><%= request.getParameter("err") %></p>
    <% } %>
    <form action="<%=path%>/seccon/step1" method="post" class="registration-form">
        <table class="registration-table">
            <tr>
                <th scope="row"><label for="name">Full name</label></th>
                <td><input type="text" name="name" id="name" autocomplete="name" required="required" /></td>
            </tr>
            <tr>
                <th scope="row"><label for="address">Address</label></th>
                <td><input type="text" name="address" id="address" autocomplete="street-address" required="required" /></td>
            </tr>
            <tr>
                <th scope="row"><label for="telephone">Telephone</label></th>
                <td><input type="text" name="telephone" id="telephone" autocomplete="tel" required="required" /></td>
            </tr>
            <tr>
                <th scope="row"><label for="email">Email</label></th>
                <td><input type="email" name="email" id="email" autocomplete="email" required="required" /></td>
            </tr>
            <tr>
                <td></td>
                <td class="registration-actions">
                    <input type="submit" value="Continue to ticket level" />
                </td>
            </tr>
        </table>
    </form>
</div>
<%@ include file="../footer.jsp" %>
