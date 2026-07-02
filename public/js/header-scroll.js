(function () {
    const header = document.querySelector(".main-header");
    if (!header) {
        return;
    }

    function syncHeaderHeight() {
        const height = Math.round(header.getBoundingClientRect().height);
        if (height > 0) {
            document.documentElement.style.setProperty("--site-header-height", `${height}px`);
        }
    }

    function onScroll() {
        header.classList.toggle("is-scrolled", window.scrollY > 6);
    }

    syncHeaderHeight();
    onScroll();

    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", syncHeaderHeight);
    window.addEventListener("load", syncHeaderHeight);
})();
