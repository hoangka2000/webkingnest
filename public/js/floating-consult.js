(function () {
    const STORAGE_KEY = "kingnestFloatingConsult";

    function toggleFloatingConsult(show) {
        const box = document.getElementById("floatingConsult");
        const openBtn = document.getElementById("floatingOpen");

        if (!box || !openBtn) {
            return;
        }

        if (show) {
            box.classList.remove("is-hidden");
            openBtn.classList.remove("is-show");
            document.body.classList.add("has-floating-consult");
            localStorage.setItem(STORAGE_KEY, "open");
        } else {
            box.classList.add("is-hidden");
            openBtn.classList.add("is-show");
            document.body.classList.remove("has-floating-consult");
            localStorage.setItem(STORAGE_KEY, "closed");
        }
    }

    window.toggleFloatingConsult = toggleFloatingConsult;

    function initFloatingConsult() {
        const savedState = localStorage.getItem(STORAGE_KEY);

        if (savedState === "closed") {
            toggleFloatingConsult(false);
        } else {
            toggleFloatingConsult(true);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initFloatingConsult);
    } else {
        initFloatingConsult();
    }
})();
