<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="header.jsp" %>
<%
    if (session.getAttribute("isLoggedIn") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?err=Please login to manage billing");
        return;
    }
%>
<div class="billing-panel">
    <h2>Self-service billing</h2>
    <p>
        Pricing is based on the number of <strong>active standard users</strong> in this deployment
        (accounts with the normal <code>user</code> privilege; administrators are excluded from the seat count).
        Add a payment method once — card details go directly to Stripe and are never stored by this application.
    </p>

    <div id="billing-status" class="billing-status"></div>
    <div id="billing-account" class="billing-account"></div>
    <div id="billing-quote" class="billing-quote"></div>

    <form id="payment-form" class="billing-form" style="display:none;">
        <label for="card-element">Credit or debit card</label>
        <div id="card-element" class="card-element"></div>
        <div id="card-errors" class="card-errors" role="alert"></div>
        <button id="submit" type="submit">Save card and start subscription</button>
    </form>
</div>

<script src="https://js.stripe.com/v3/"></script>
<script type="text/javascript">
(function () {
    var ctx = "<%=path%>";
    var statusEl = document.getElementById("billing-status");
    var accountEl = document.getElementById("billing-account");
    var quoteEl = document.getElementById("billing-quote");
    var form = document.getElementById("payment-form");
    var submitBtn = document.getElementById("submit");
    var cardErrors = document.getElementById("card-errors");

    function setStatus(msg) {
        statusEl.textContent = msg;
    }

    function money(cents, currency) {
        try {
            return (cents / 100).toLocaleString(undefined, {
                style: "currency",
                currency: (currency || "usd").toUpperCase()
            });
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
    var card = null;
    var csrfToken = null;

    fetchJson("/api/billing/config")
        .then(function (cfg) {
            if (!cfg.configured) {
                setStatus(cfg.error || "Billing is not configured.");
                return null;
            }
            if (cfg.account) {
                var bits = [];
                if (cfg.account.subscriptionStatus) {
                    bits.push("Status: " + cfg.account.subscriptionStatus);
                }
                if (cfg.account.cardBrand || cfg.account.cardLast4) {
                    bits.push("Card on file: "
                        + (cfg.account.cardBrand || "card")
                        + (cfg.account.cardLast4 ? (" ending in " + cfg.account.cardLast4) : ""));
                }
                if (cfg.account.seatQuantity) {
                    bits.push("Seats: " + cfg.account.seatQuantity);
                }
                if (bits.length) {
                    accountEl.innerHTML = "<pre>" + bits.join("\n") + "</pre>";
                }
            }
            stripe = Stripe(cfg.publishableKey);
            var elements = stripe.elements();
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
            quoteEl.innerHTML = "<pre>" + lines.join("\n") + "</pre>";
            form.style.display = "block";
            card.mount("#card-element");
            setStatus("Enter your card below. Card data is sent only to Stripe; this app never receives the full card number.");
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
            var msg = "Subscription active (status: " + done.status + ", quantity: " + done.quantity + ").";
            if (done.cardLast4) {
                msg += " Card ending in " + done.cardLast4 + " saved.";
            }
            setStatus(msg);
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
