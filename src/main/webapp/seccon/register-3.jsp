<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String pathCtx = request.getContextPath();
    if (session.getAttribute("seccon_amount_cents") == null
            || session.getAttribute("seccon_tier") == null
            || session.getAttribute("seccon_name") == null) {
        response.sendRedirect(pathCtx + "/seccon/register-1.jsp?err="
            + java.net.URLEncoder.encode("Please complete registration steps 1 and 2 first.", "UTF-8"));
        return;
    }
%>
<%@ include file="../header.jsp" %>
<div class="registration-wrap">
    <h2>Apiiro SecCon 2026 — Payment</h2>
    <p class="registration-lead">Step 3 of 3: Pay securely with Stripe. Card data is sent directly to Stripe; this application never receives your full card number.</p>

    <div id="seccon-summary" style="margin:1em 0;padding:.75em;border:1px solid #ccc;background:#fafafa;"></div>
    <div id="seccon-status" style="margin:1em 0;"></div>

    <div id="payment-section">
        <form id="payment-form">
            <div id="card-element" style="padding:.75em;border:1px solid #ccc;border-radius:4px;background:#fff;"></div>
            <div id="card-errors" style="color:#b00020;margin-top:.5em;"></div>
            <button id="submit-pay" type="submit" style="margin-top:1em;">Pay and complete registration</button>
        </form>
    </div>

    <div id="success-panel" style="display:none;margin-top:1.5em;padding:1em;border:1px solid #080;background:#efe;"></div>

    <p style="margin-top:1em;"><a href="<%=path%>/seccon/register-2.jsp">Back to ticket level</a></p>
</div>

<script src="https://js.stripe.com/v3/"></script>
<script type="text/javascript">
(function () {
    var ctx = "<%=path%>";
    var summaryEl = document.getElementById("seccon-summary");
    var statusEl = document.getElementById("seccon-status");
    var form = document.getElementById("payment-form");
    var submitBtn = document.getElementById("submit-pay");
    var cardErrors = document.getElementById("card-errors");
    var paymentSection = document.getElementById("payment-section");
    var successPanel = document.getElementById("success-panel");

    function money(cents, currency) {
        try {
            return (cents / 100).toLocaleString(undefined, { style: "currency", currency: (currency || "usd").toUpperCase() });
        } catch (e) {
            return (cents / 100) + " " + (currency || "usd").toUpperCase();
        }
    }

    function fetchJson(url, options) {
        return fetch(ctx + url, options || {}).then(function (res) {
            return res.text().then(function (text) {
                var data;
                try {
                    data = text ? JSON.parse(text) : {};
                } catch (e) {
                    throw new Error("Unexpected response from server");
                }
                if (!res.ok) {
                    throw new Error(data.error || ("HTTP " + res.status));
                }
                return data;
            });
        });
    }

    var stripe = null;
    var elements = null;
    var card = null;
    var publishableKey = null;
    var csrfToken = null;
    var currencyCode = "usd";

    fetchJson("/api/seccon/config")
        .then(function (cfg) {
            if (!cfg.configured) {
                summaryEl.textContent = cfg.error || "Stripe is not configured.";
                return null;
            }
            publishableKey = cfg.publishableKey;
            csrfToken = cfg.csrfToken;
            currencyCode = cfg.currency || "usd";
            stripe = Stripe(publishableKey);
            elements = stripe.elements();
            card = elements.create("card");
            var lines = [];
            lines.push("<strong>" + (cfg.conference || "Conference") + "</strong>");
            lines.push("Registrant: " + cfg.registrantName);
            lines.push("Email: " + cfg.email);
            lines.push("Level: " + cfg.tierLabel + " — " + money(cfg.amountCents, currencyCode));
            summaryEl.innerHTML = "<div style=\"white-space:pre-wrap;\">" + lines.join("\n") + "</div>";
            card.mount("#card-element");
            statusEl.textContent = "Enter your card details below.";
            return cfg;
        })
        .catch(function (err) {
            summaryEl.textContent = err.message || String(err);
        });

    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        cardErrors.textContent = "";
        if (!stripe || !csrfToken || !publishableKey) {
            statusEl.textContent = "Payment is not ready yet. Check Stripe configuration.";
            return;
        }
        submitBtn.disabled = true;
        statusEl.textContent = "Preparing secure payment…";

        fetchJson("/api/seccon/payment-intent", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ csrf: csrfToken })
        })
        .then(function (pi) {
            return stripe.confirmCardPayment(pi.clientSecret, {
                payment_method: { card: card }
            });
        })
        .then(function (result) {
            if (result.error) {
                throw new Error(result.error.message || "Payment failed");
            }
            var intent = result.paymentIntent;
            var pid = intent && intent.id;
            var st = intent && intent.status;
            paymentSection.style.display = "none";
            successPanel.style.display = "block";
            successPanel.innerHTML = "<strong>Registration complete.</strong><br/>Payment status: "
                + (st || "unknown") + (pid ? ("<br/>Reference: " + pid) : "");
            statusEl.textContent = "";
        })
        .catch(function (err) {
            cardErrors.textContent = err.message || String(err);
            statusEl.textContent = "Payment could not be completed.";
        })
        .then(function () {
            submitBtn.disabled = false;
        });
    });
})();
</script>
<%@ include file="../footer.jsp" %>
