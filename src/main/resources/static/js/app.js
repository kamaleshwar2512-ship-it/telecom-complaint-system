/* ==========================================================================
   Telecom Care - front-end behaviour
   --------------------------------------------------------------------------
   Purely presentational. Every number shown here is rendered by the server
   first; this script only keeps countdowns ticking, sizes the SLA meters and
   filters already-rendered rows. No business decision is made in the browser.
   ========================================================================== */
(function () {
    "use strict";

    /* ---------------------------------------------------------------- nav */
    function initNav() {
        var toggle = document.querySelector("[data-nav-toggle]");
        var target = document.querySelector("[data-nav-collapse]");
        if (!toggle || !target) return;
        toggle.addEventListener("click", function () {
            var open = target.classList.toggle("is-open");
            toggle.setAttribute("aria-expanded", open ? "true" : "false");
        });
    }

    /* --------------------------------------------------------- countdowns */
    function formatRemaining(msLeft) {
        if (msLeft <= 0) return null;
        var totalMinutes = Math.floor(msLeft / 60000);
        var days = Math.floor(totalMinutes / 1440);
        var hours = Math.floor((totalMinutes % 1440) / 60);
        var minutes = totalMinutes % 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        return hours + "h " + minutes + "m";
    }

    function tickCountdowns() {
        var now = Date.now();
        document.querySelectorAll("[data-sla-deadline]").forEach(function (el) {
            var deadline = new Date(el.getAttribute("data-sla-deadline")).getTime();
            if (isNaN(deadline)) return;

            // Resolved / closed complaints are no longer time-pressured.
            if (el.hasAttribute("data-sla-settled")) return;

            var remaining = formatRemaining(deadline - now);
            var suffix = el.getAttribute("data-sla-suffix") || "";
            if (remaining === null) {
                el.textContent = el.getAttribute("data-sla-breached-text") || "Deadline passed";
                el.classList.add("is-breached");
            } else {
                el.textContent = remaining + suffix;
                el.classList.remove("is-breached");
            }
        });
    }

    /* -------------------------------------------------------- SLA meters */
    function sizeMeters() {
        var now = Date.now();
        document.querySelectorAll("[data-meter]").forEach(function (meter) {
            var fill = meter.querySelector(".sla-meter__fill");
            if (!fill) return;

            var start = new Date(meter.getAttribute("data-start")).getTime();
            var end = new Date(meter.getAttribute("data-deadline")).getTime();
            var pct;

            if (isNaN(start) || isNaN(end) || end <= start) {
                pct = 100;
            } else {
                pct = ((now - start) / (end - start)) * 100;
            }
            if (meter.hasAttribute("data-settled")) pct = 100;
            pct = Math.max(2, Math.min(100, Math.round(pct)));

            fill.style.width = pct + "%";
            meter.setAttribute("aria-valuenow", String(pct));
        });
    }

    /* ------------------------------------------------------ table search */
    function initSearch() {
        document.querySelectorAll("[data-search-input]").forEach(function (input) {
            var scope = document.querySelector(input.getAttribute("data-search-input"));
            if (!scope) return;
            var counter = document.querySelector(input.getAttribute("data-search-count") || "");
            var emptyHint = document.querySelector(input.getAttribute("data-search-empty") || "");

            input.addEventListener("input", function () {
                var term = input.value.trim().toLowerCase();
                var visible = 0;
                scope.querySelectorAll("[data-search-row]").forEach(function (row) {
                    var hit = term === "" || row.textContent.toLowerCase().indexOf(term) !== -1;
                    row.hidden = !hit;
                    if (hit) visible++;
                });
                if (counter) counter.textContent = String(visible);
                if (emptyHint) emptyHint.hidden = visible !== 0;
            });
        });
    }

    /* ------------------------------- SLA target preview on complaint form */
    function initSlaPreview() {
        var host = document.querySelector("[data-sla-preview]");
        if (!host) return;

        var categoryEl = document.getElementById(host.getAttribute("data-category-field"));
        var priorityEl = document.getElementById(host.getAttribute("data-priority-field"));
        var output = host.querySelector("[data-sla-preview-value]");
        var hint = host.querySelector("[data-sla-preview-hint]");
        if (!categoryEl || !priorityEl || !output) return;

        var rules = {};
        document.querySelectorAll("[data-sla-rule]").forEach(function (node) {
            rules[node.getAttribute("data-category") + "|" + node.getAttribute("data-priority")] =
                parseInt(node.getAttribute("data-hours"), 10);
        });

        function describe(hours) {
            if (hours < 24) return hours + (hours === 1 ? " hour" : " hours");
            var days = Math.floor(hours / 24);
            var rest = hours % 24;
            var text = days + (days === 1 ? " day" : " days");
            if (rest) text += " " + rest + "h";
            return text + " (" + hours + " hours)";
        }

        function update() {
            var key = categoryEl.value + "|" + priorityEl.value;
            var hours = rules[key];
            if (!categoryEl.value || !priorityEl.value) {
                output.textContent = "Select a category and priority";
                if (hint) hint.textContent = "We'll show your guaranteed resolution target here.";
                return;
            }
            if (hours === undefined || isNaN(hours)) {
                output.textContent = "Confirmed after submission";
                if (hint) hint.textContent = "Your resolution target is set automatically when the complaint is registered.";
                return;
            }
            output.textContent = describe(hours);
            if (hint) hint.textContent = "This is the resolution target for this category and priority.";
        }

        categoryEl.addEventListener("change", update);
        priorityEl.addEventListener("change", update);
        update();
    }

    /* ------------------------------------------------- character counter */
    function initCounters() {
        document.querySelectorAll("[data-count-for]").forEach(function (out) {
            var field = document.getElementById(out.getAttribute("data-count-for"));
            if (!field) return;
            var max = field.getAttribute("maxlength");
            function render() {
                out.textContent = field.value.length + (max ? " / " + max : "") + " characters";
            }
            field.addEventListener("input", render);
            render();
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initNav();
        tickCountdowns();
        sizeMeters();
        initSearch();
        initSlaPreview();
        initCounters();
        setInterval(function () {
            tickCountdowns();
            sizeMeters();
        }, 60000);
    });
})();
