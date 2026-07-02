(function () {
    function closeMobileNav() {
        const nav = document.getElementById("mainNav");
        const toggle = document.querySelector(".mobile-nav-toggle");
        const backdrop = document.querySelector(".mobile-nav-backdrop");

        if (nav) {
            nav.classList.remove("is-open");
        }
        if (toggle) {
            toggle.setAttribute("aria-expanded", "false");
        }
        if (backdrop) {
            backdrop.classList.remove("is-open");
            backdrop.setAttribute("aria-hidden", "true");
        }
        document.body.classList.remove("nav-open");
    }

    function openMobileNav() {
        const nav = document.getElementById("mainNav");
        const toggle = document.querySelector(".mobile-nav-toggle");
        const backdrop = document.querySelector(".mobile-nav-backdrop");

        if (nav) {
            nav.classList.add("is-open");
        }
        if (toggle) {
            toggle.setAttribute("aria-expanded", "true");
        }
        if (backdrop) {
            backdrop.classList.add("is-open");
            backdrop.setAttribute("aria-hidden", "false");
        }
        document.body.classList.add("nav-open");
    }

    function isMobileNav() {
        return window.innerWidth <= 1180;
    }

    function setupMobileNav() {
        const toggle = document.querySelector(".mobile-nav-toggle");
        const backdrop = document.querySelector(".mobile-nav-backdrop");
        const nav = document.getElementById("mainNav");

        if (!toggle || !nav) {
            return;
        }

        toggle.addEventListener("click", function (event) {
            event.preventDefault();
            event.stopPropagation();

            if (!isMobileNav()) {
                return;
            }

            if (nav.classList.contains("is-open")) {
                closeMobileNav();
            } else {
                openMobileNav();
            }
        });

        if (backdrop) {
            backdrop.addEventListener("click", closeMobileNav);
        }

        const closeBtn = document.querySelector(".mobile-nav-close");
        if (closeBtn) {
            closeBtn.addEventListener("click", function (event) {
                event.preventDefault();
                event.stopPropagation();
                closeMobileNav();
            });
        }

        nav.addEventListener("click", function (event) {
            if (!isMobileNav()) {
                return;
            }

            const link = event.target.closest("a");
            if (!link || !nav.contains(link)) {
                return;
            }

            const isDropdownToggle = link.parentElement && link.parentElement.classList.contains("nav-dropdown");
            if (isDropdownToggle) {
                event.preventDefault();
                event.stopPropagation();
                link.parentElement.classList.toggle("is-open");
                return;
            }

            closeMobileNav();
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeMobileNav();
            }
        });

        window.addEventListener("resize", function () {
            if (!isMobileNav()) {
                closeMobileNav();
                nav.querySelectorAll(".nav-dropdown").forEach(function (dropdown) {
                    dropdown.classList.remove("is-open");
                });
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", setupMobileNav);
    } else {
        setupMobileNav();
    }
})();
