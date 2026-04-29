<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="header.jsp" %>
<%
    if (session.getAttribute("isLoggedIn") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?err=Please login to manage billing");
        return;
    }
%>
<div style="max-width:720px;margin:1em auto;">
    <h2>Self-service billing</h2>
    <p>
        Pricing is based on the number of <strong>active standard users</strong> in this deployment (users with the
        normal <code>user</code> privilege; administrators are excluded from the seat count).
    </p>

    <div id="billing-status" style="margin:1em 0;padding:.75em;border:1px solid #ccc;background:#fafafa;"></div>

    <div id="billing-quote" style="margin:1em 0;"></div>

    <form id="payment-form" style="display:none;margin-top:1.5em;">
        <div id="card-element" style="padding:.75em;border:1px solid #ccc;border-radius:4px;background:#fff;"></div>
        <div id="card-errors" style="color:#b00020;margin-top:.5em;"></div>
        <button id="submit" type="submit" style="margin-top:1em;">Save card and start subscription</button>
    </form>
</div>

<script src="https://js.stripe.com/v3/"></script>
<script type="text/javascript">
(function () {
    var ctx = "<%=path%>";
    var statusEl = document.getElementById("billing-status");
    var quoteEl = document.getElementById("billing-quote");
    var form = document.getElementById("payment-form");
    var submitBtn = document.getElementById("submit");
    var cardErrors = document.getElementById("card-errors");

    function setStatus(msg) {
        statusEl.textContent = msg;
    }

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

    fetchJson("/api/billing/config")
        .then(function (cfg) {
            if (!cfg.configured) {
                setStatus(cfg.error || "Billing is not configured.");
                return null;
            }
            publishableKey = cfg.publishableKey;
            stripe = Stripe(publishableKey);
            elements = stripe.elements();
            card = elements.create("card");
            return fetchJson("/api/billing/quote");
        })
        .then(function (quote) {
            if (!quote) {
                return;
            }
            csrfToken = quote.csrfToken;
            var lines = [];
            lines.push("Active standard users: " + quote.activeUsers);
            lines.push("Seats billed this cycle: " + quote.billableSeatsForCharge + " (minimum 1)");
            lines.push("Per-seat amount: " + money(quote.unitAmountCents, quote.currency) + " / month");
            lines.push("Estimated monthly total: " + money(quote.estimatedMonthlyCents, quote.currency));
            quoteEl.innerHTML = "<pre style=\"white-space:pre-wrap;\">" + lines.join("\n") + "</pre>";
            form.style.display = "block";
            card.mount("#card-element");
            setStatus("Enter your card details below. Card data is sent directly to Stripe (PCI SAQ A style); this application never receives your full card number.");
        })
        .catch(function (err) {
            setStatus(err.message || String(err));
        });

    form.addEventListener("submit", function (ev) {
        ev.preventDefault();
        if (!stripe || !csrfToken) {
            setStatus("Billing is not ready yet.");
            return;
        }
        cardErrors.textContent = "";
        submitBtn.disabled = true;
        setStatus("Preparing secure card capture…");

        fetchJson("/api/billing/setup-intent", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ csrf: csrfToken })
        })
        .then(function (si) {
            return stripe.confirmCardSetup(si.clientSecret, {
                payment_method: { card: card }
            });
        })
        .then(function (result) {
            if (result.error) {
                throw new Error(result.error.message || "Card setup failed");
            }
            var pm = result.setupIntent && result.setupIntent.payment_method;
            var pmId = pm && (typeof pm === "string" ? pm : pm.id);
            if (!pmId) {
                throw new Error("Missing payment method from Stripe");
            }
            setStatus("Activating subscription…");
            return fetchJson("/api/billing/complete", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ csrf: csrfToken, paymentMethodId: pmId })
            });
        })
        .then(function (done) {
            setStatus("Subscription created: " + done.subscriptionId + " (status: " + done.status + ", quantity: " + done.quantity + ").");
            form.style.display = "none";
        })
        .catch(function (err) {
            cardErrors.textContent = err.message || String(err);
            setStatus("Could not complete billing.");
        })
        .then(function () {
            submitBtn.disabled = false;
        });
    });
})();
</script>
<%@ include file="footer.jsp" %>
