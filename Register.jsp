<%--
    Document   : Register
    Created on : 2 Dec, 2014, 10:47:44 AM
    Author     : breakthesec
--%>
<%@ include file="header.jsp" %>
<script type="text/javascript">
$(document).ready(function(){
    var base = "<%=path%>";
    $("#username").change(function(){
        var username = $(this).val();
        $.getJSON(base + "/UsernameCheck.do", "username=" + encodeURIComponent(username), function(result)
        {
            if(result.available==0)
            {
                $("#status").html("<b style='color:green'>&#10004; Available</b>");
            }
            else
            {
                $("#status").html("<b style='color:red'>&#10006; Username is already taken</b>");
            }
        });
    });
    $("#email").change(function(){
        var email = $(this).val();
        $.getJSON(base + "/EmailCheck.do", "email=" + encodeURIComponent(email), function(result)
        {
            if(result.available==0)
            {
                $("#emailStatus").html("<b style='color:green'>&#10004;</b>");
            }
            else
            {
                $("#emailStatus").html("<b style='color:red'>&#10006; Email is already in use</b>");
            }
        });
    });
});
</script>

<div class="registration-wrap">
    <h2>Create an account</h2>
    <p class="registration-lead">Choose a username and password. You can check username and email availability before you submit.</p>
    <% if (request.getParameter("err") != null) { %>
    <p class="fail"><%= request.getParameter("err") %></p>
    <% } %>

    <form action="<%=path%>/AddUser" method="post" class="registration-form">
        <table class="registration-table">
            <tr>
                <th scope="row"><label for="username">Username</label></th>
                <td><input type="text" name="username" id="username" autocomplete="username" required="required" /></td>
                <td class="registration-status"><span id="status"></span></td>
            </tr>
            <tr>
                <th scope="row"><label for="email">Email</label></th>
                <td><input type="text" name="email" id="email" autocomplete="email" /></td>
                <td class="registration-status"><span id="emailStatus"></span></td>
            </tr>
            <tr>
                <th scope="row"><label for="about">Describe yourself</label></th>
                <td colspan="2"><input type="text" name="About" id="about" /></td>
            </tr>
            <tr>
                <th scope="row"><label for="secret">What is your pet's name?</label></th>
                <td colspan="2"><input type="text" name="secret" id="secret" /></td>
            </tr>
            <tr>
                <th scope="row"><label for="password">Password</label></th>
                <td colspan="2"><input type="password" name="password" id="password" autocomplete="new-password" required="required" /></td>
            </tr>
            <tr>
                <td></td>
                <td colspan="2" class="registration-actions">
                    <input type="submit" name="Register" value="Register" />
                </td>
            </tr>
        </table>
    </form>

    <p class="registration-alt">Already have an account? <a href="<%=path%>/login.jsp">Log in</a></p>
</div>

<%@ include file="footer.jsp" %>
