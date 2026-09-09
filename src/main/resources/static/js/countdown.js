// Live-updates any element with a [data-sla-deadline] attribute (an ISO-8601 timestamp) so the
// countdown keeps ticking without a page reload. The server still renders the correct initial
// value and drives all business decisions (breach/escalation) - this is purely cosmetic.
(function () {
    function formatRemaining(deadline) {
        const now = new Date();
        const diffMs = deadline.getTime() - now.getTime();
        if (diffMs <= 0) {
            return { text: "BREACHED", breached: true };
        }
        const totalMinutes = Math.floor(diffMs / 60000);
        const hours = Math.floor(totalMinutes / 60);
        const minutes = totalMinutes % 60;
        return { text: hours + "h " + minutes + "m remaining", breached: false };
    }

    function tick() {
        document.querySelectorAll("[data-sla-deadline]").forEach(function (el) {
            const deadline = new Date(el.getAttribute("data-sla-deadline"));
            if (isNaN(deadline.getTime())) {
                return;
            }
            const result = formatRemaining(deadline);
            el.textContent = result.text;
            el.classList.toggle("text-danger", result.breached);
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        tick();
        setInterval(tick, 60000);
    });
})();
