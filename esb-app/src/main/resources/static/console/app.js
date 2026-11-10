/* King ESB admin console — dependency-free SPA. Talks to /admin/api/v1 with a JWT. */
(function () {
    "use strict";

    var API = "/admin/api/v1";
    var TOKEN_KEY = "esb.token";
    var USER_KEY = "esb.user";
    var editingId = null;

    var $ = function (id) { return document.getElementById(id); };

    function token() { return localStorage.getItem(TOKEN_KEY); }

    function api(path, options) {
        options = options || {};
        var headers = { "Accept": "application/json" };
        if (options.body !== undefined) { headers["Content-Type"] = "application/json"; }
        if (token()) { headers["Authorization"] = "Bearer " + token(); }
        return fetch(API + path, {
            method: options.method || "GET",
            headers: headers,
            body: options.body === undefined ? undefined : JSON.stringify(options.body)
        }).then(function (response) {
            if (response.status === 204) { return null; }
            return response.text().then(function (text) {
                var data = text ? JSON.parse(text) : null;
                if (!response.ok) {
                    var message = data && data.error ? data.error.message : ("HTTP " + response.status);
                    var details = data && data.error && data.error.details;
                    if (details && details.length) {
                        message += " (" + details.map(function (d) { return d.field + ": " + d.message; }).join("; ") + ")";
                    }
                    throw new Error(message);
                }
                return data;
            });
        });
    }

    /* ---------- auth ---------- */

    $("login-form").addEventListener("submit", function (event) {
        event.preventDefault();
        var errorBox = $("login-error");
        errorBox.classList.add("hidden");
        api("/auth/login", {
            method: "POST",
            body: { username: $("login-username").value, password: $("login-password").value }
        }).then(function (data) {
            localStorage.setItem(TOKEN_KEY, data.token);
            localStorage.setItem(USER_KEY, $("login-username").value);
            enterApp();
        }).catch(function (error) {
            errorBox.textContent = "Sign in failed: " + error.message;
            errorBox.classList.remove("hidden");
        });
    });

    $("logout-btn").addEventListener("click", function () {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        location.reload();
    });

    function enterApp() {
        $("login-view").classList.add("hidden");
        $("main-view").classList.remove("hidden");
        $("user-box").classList.remove("hidden");
        $("user-name").textContent = localStorage.getItem(USER_KEY) || "user";
        showTab("dashboard");
        loadDashboard();
        loadRoutes();
    }

    /* ---------- tabs ---------- */

    Array.prototype.forEach.call(document.querySelectorAll(".tab"), function (tab) {
        tab.addEventListener("click", function () {
            showTab(tab.getAttribute("data-tab"));
            if (tab.getAttribute("data-tab") === "logs") { loadLogs(); }
        });
    });

    function showTab(name) {
        Array.prototype.forEach.call(document.querySelectorAll(".tab"), function (tab) {
            tab.classList.toggle("active", tab.getAttribute("data-tab") === name);
        });
        ["dashboard", "routes", "logs"].forEach(function (tab) {
            $("tab-" + tab).classList.toggle("hidden", tab !== name);
        });
    }

    /* ---------- dashboard ---------- */

    $("refresh-dashboard").addEventListener("click", loadDashboard);

    function loadDashboard() {
        api("/metrics/summary").then(function (summary) {
            $("stat-total").textContent = summary.totalCalls;
            $("stat-errors").textContent = summary.errorCalls;
            $("stat-rate").textContent = (summary.errorRate * 100).toFixed(2) + "%";
            $("stat-latency").textContent = summary.avgDurationMs.toFixed(1) + " ms";
            $("stat-routes").textContent = summary.publishedRoutes;
        }).catch(function (error) { console.error(error); });

        api("/routes?size=100").then(function (page) {
            var body = $("dashboard-routes");
            body.innerHTML = "";
            page.items.forEach(function (route) {
                var row = document.createElement("tr");
                row.innerHTML =
                    "<td>" + esc(route.name) + "</td>" +
                    "<td>" + esc(route.method) + "</td>" +
                    "<td>" + esc(route.path) + "</td>" +
                    "<td>" + route.priority + "</td>" +
                    "<td>" + enabledBadge(route.enabled) + "</td>" +
                    "<td>" + route.version + "</td>";
                body.appendChild(row);
            });
        }).catch(function (error) { console.error(error); });
    }

    /* ---------- routes ---------- */

    $("refresh-routes").addEventListener("click", loadRoutes);
    $("new-route").addEventListener("click", function () {
        editingId = null;
        $("editor-title").textContent = "New route (JSON)";
        $("route-editor").value = JSON.stringify({
            name: "",
            path: "/api/**",
            method: "*",
            priority: 100,
            enabled: true,
            pipeline: [
                { id: "log", type: "LOG", config: { message: "incoming ${method} ${path}" } }
            ]
        }, null, 2);
        setNotice("", null);
    });
    $("save-route").addEventListener("click", saveRoute);

    function loadRoutes() {
        api("/routes?size=100").then(function (page) {
            var body = $("routes-body");
            body.innerHTML = "";
            page.items.forEach(function (route) {
                var row = document.createElement("tr");
                row.innerHTML =
                    "<td>" + esc(route.name) + "</td>" +
                    "<td>" + esc(route.method) + "</td>" +
                    "<td>" + esc(route.path) + "</td>" +
                    "<td>" + enabledBadge(route.enabled) + "</td>";
                var actions = document.createElement("td");
                actions.appendChild(button("Edit", "small", function () { loadRoute(route.id); }));
                actions.appendChild(button(route.enabled ? "Disable" : "Enable", "small", function () {
                    setStatus(route.id, !route.enabled);
                }));
                actions.appendChild(button("Publish", "small", function () { publish(route.id); }));
                actions.appendChild(button("Delete", "small danger", function () { remove(route.id); }));
                row.appendChild(actions);
                body.appendChild(row);
            });
        }).catch(function (error) { setNotice(error.message, false); });
    }

    function loadRoute(id) {
        api("/routes/" + id + "/definition").then(function (definition) {
            editingId = id;
            $("editor-title").textContent = "Edit route: " + definition.name;
            $("route-editor").value = JSON.stringify(definition, null, 2);
            setNotice("Loaded route definition.", true);
        }).catch(function (error) { setNotice(error.message, false); });
    }

    function saveRoute() {
        var text = $("route-editor").value;
        var definition;
        try {
            definition = JSON.parse(text);
        } catch (error) {
            setNotice("Invalid JSON: " + error.message, false);
            return;
        }
        var request = editingId
            ? api("/routes/" + editingId, { method: "PUT", body: definition })
            : api("/routes", { method: "POST", body: definition });
        request.then(function () {
            setNotice("Route saved.", true);
            loadRoutes();
            loadDashboard();
        }).catch(function (error) { setNotice(error.message, false); });
    }

    function setStatus(id, enabled) {
        api("/routes/" + id + "/status", { method: "PUT", body: { enabled: enabled } })
            .then(function () { loadRoutes(); loadDashboard(); })
            .catch(function (error) { setNotice(error.message, false); });
    }

    function publish(id) {
        api("/routes/" + id + "/publish", { method: "POST" })
            .then(function () { setNotice("Route published to the runtime registry.", true); loadDashboard(); })
            .catch(function (error) { setNotice(error.message, false); });
    }

    function remove(id) {
        if (!window.confirm("Delete this route?")) { return; }
        api("/routes/" + id, { method: "DELETE" })
            .then(function () { setNotice("Route deleted.", true); loadRoutes(); loadDashboard(); })
            .catch(function (error) { setNotice(error.message, false); });
    }

    function setNotice(message, ok) {
        var box = $("route-message");
        box.textContent = message;
        box.className = "notice " + (message ? (ok ? "ok" : "err") : "hidden");
    }

    /* ---------- logs ---------- */

    $("refresh-logs").addEventListener("click", loadLogs);

    function loadLogs() {
        var params = new URLSearchParams();
        params.set("size", "100");
        var routeId = $("filter-route").value.trim();
        var status = $("filter-status").value;
        if (routeId) { params.set("routeId", routeId); }
        if (status) { params.set("status", status); }
        api("/logs?" + params.toString()).then(function (page) {
            var body = $("logs-body");
            body.innerHTML = "";
            page.items.forEach(function (log) {
                var row = document.createElement("tr");
                row.innerHTML =
                    "<td>" + esc(log.createdAt) + "</td>" +
                    "<td>" + esc(log.traceId) + "</td>" +
                    "<td>" + esc(log.routeId) + "</td>" +
                    "<td>" + esc(log.method) + "</td>" +
                    "<td>" + esc(log.path) + "</td>" +
                    "<td>" + statusBadge(log.status) + "</td>" +
                    "<td>" + log.httpStatus + "</td>" +
                    "<td>" + log.durationMs + "</td>";
                body.appendChild(row);
            });
        }).catch(function (error) { console.error(error); });
    }

    /* ---------- helpers ---------- */

    function button(label, className, onClick) {
        var element = document.createElement("button");
        element.className = "btn " + className;
        element.textContent = label;
        element.addEventListener("click", onClick);
        element.style.marginRight = "6px";
        return element;
    }

    function enabledBadge(enabled) {
        return enabled ? '<span class="badge ok">enabled</span>' : '<span class="badge muted">disabled</span>';
    }

    function statusBadge(status) {
        var cls = status === "OK" ? "ok" : (status === "ERROR" ? "err" : "warn");
        return '<span class="badge ' + cls + '">' + esc(status) + "</span>";
    }

    function esc(value) {
        if (value === null || value === undefined) { return ""; }
        return String(value).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    /* ---------- bootstrap ---------- */

    if (token()) {
        api("/auth/me").then(enterApp).catch(function () {
            localStorage.removeItem(TOKEN_KEY);
            $("login-view").classList.remove("hidden");
        });
    } else {
        $("login-view").classList.remove("hidden");
    }
})();
