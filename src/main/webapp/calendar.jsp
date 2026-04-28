<%
response.setHeader("X-Content-Type-Options", "nosniff");
response.setHeader("X-Frame-Options", "DENY");
response.setHeader("Referrer-Policy", "same-origin");
response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self';");
%>
<%@ include file="/header.jsp" %>
<%
if (session.getAttribute("isLoggedIn") == null) {
    out.print("Please login to view your weekly calendar events.");
} else {
%>
<h2>Weekly Meetings</h2>
<p>Sync your current week's Google Calendar events and store them in the local database.</p>
<button id="syncBtn" type="button">Sync This Week</button>
<p id="statusLine"></p>
<table id="eventsTable" border="1" cellpadding="6" cellspacing="0" style="margin-top: 10px; width: 100%;">
    <thead>
        <tr>
            <th>Summary</th>
            <th>Start (UTC)</th>
            <th>End (UTC)</th>
            <th>Link</th>
        </tr>
    </thead>
    <tbody></tbody>
</table>
<script type="text/javascript">
    (function() {
        var csrfToken = "";
        var statusLine = document.getElementById("statusLine");
        var syncBtn = document.getElementById("syncBtn");
        var tableBody = document.querySelector("#eventsTable tbody");

        function setStatus(msg) {
            statusLine.textContent = msg;
        }

        function clearRows() {
            while (tableBody.firstChild) {
                tableBody.removeChild(tableBody.firstChild);
            }
        }

        function addCell(row, text) {
            var td = document.createElement("td");
            td.textContent = text;
            row.appendChild(td);
        }

        function renderEvents(events) {
            clearRows();
            if (!events || events.length === 0) {
                var empty = document.createElement("tr");
                var td = document.createElement("td");
                td.colSpan = 4;
                td.textContent = "No meetings stored for this week.";
                empty.appendChild(td);
                tableBody.appendChild(empty);
                return;
            }
            for (var i = 0; i < events.length; i++) {
                var ev = events[i];
                var tr = document.createElement("tr");
                addCell(tr, ev.summary || "(no title)");
                addCell(tr, ev.startUtc || "");
                addCell(tr, ev.endUtc || "");

                var linkTd = document.createElement("td");
                if (ev.link) {
                    var a = document.createElement("a");
                    a.href = ev.link;
                    a.target = "_blank";
                    a.rel = "noopener noreferrer";
                    a.textContent = "Open";
                    linkTd.appendChild(a);
                } else {
                    linkTd.textContent = "";
                }
                tr.appendChild(linkTd);
                tableBody.appendChild(tr);
            }
        }

        function loadStored() {
            fetch("<%=path%>/api/calendar/week")
                .then(function(resp) { return resp.json(); })
                .then(function(data) {
                    if (data.error) {
                        setStatus(data.error);
                        return;
                    }
                    csrfToken = data.csrfToken || "";
                    renderEvents(data.events || []);
                    setStatus("Week start: " + (data.weekStart || ""));
                })
                .catch(function() {
                    setStatus("Unable to load saved events.");
                });
        }

        syncBtn.addEventListener("click", function() {
            if (!csrfToken) {
                setStatus("Refresh page and try again.");
                return;
            }
            setStatus("Syncing...");
            var form = new URLSearchParams();
            form.append("csrfToken", csrfToken);

            fetch("<%=path%>/api/calendar/week", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
                },
                body: form.toString()
            })
            .then(function(resp) { return resp.json(); })
            .then(function(data) {
                if (data.error) {
                    setStatus(data.error);
                    return;
                }
                csrfToken = data.csrfToken || csrfToken;
                renderEvents(data.events || []);
                setStatus("Synced " + (data.eventCount || 0) + " event(s) for week " + (data.weekStart || ""));
            })
            .catch(function() {
                setStatus("Sync failed. Please try again.");
            });
        });

        loadStored();
    })();
</script>
<%
}
%>
<%@ include file="/footer.jsp" %>
