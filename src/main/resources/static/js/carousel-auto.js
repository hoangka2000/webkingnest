(function () {
    const AUTO_MS = 1000;
    const TOUCH_MAX = 1180;

    const controllers = [];

    function isTouchCarousel() {
        return window.innerWidth <= TOUCH_MAX;
    }

    function getScrollStep(track) {
        const card = track.querySelector(".best-product-card, .category-card, .choose-card");
        if (!card) {
            return track.clientWidth * 0.85;
        }

        const gap = parseFloat(getComputedStyle(track).gap) || 0;
        return card.offsetWidth + gap;
    }

    function createAutoScroll(track) {
        if (!track) {
            return null;
        }

        let timer = null;
        let paused = false;

        function clearTimer() {
            if (timer) {
                clearTimeout(timer);
                timer = null;
            }
        }

        function scrollNext() {
            if (!isTouchCarousel() || paused) {
                return;
            }

            const maxScroll = track.scrollWidth - track.clientWidth;
            if (maxScroll <= 4) {
                schedule();
                return;
            }

            const step = getScrollStep(track);
            let next = track.scrollLeft + step;

            if (next >= maxScroll - 4) {
                next = 0;
            }

            track.scrollTo({ left: next, behavior: "smooth" });
            schedule();
        }

        function schedule() {
            clearTimer();
            if (!isTouchCarousel() || paused) {
                return;
            }
            timer = setTimeout(scrollNext, AUTO_MS);
        }

        function pause() {
            paused = true;
            clearTimer();
        }

        function resumeLater() {
            clearTimer();
            timer = setTimeout(function () {
                paused = false;
                schedule();
            }, AUTO_MS);
        }

        track.addEventListener("pointerdown", pause);
        track.addEventListener("touchstart", pause, { passive: true });
        track.addEventListener("wheel", pause, { passive: true });
        track.addEventListener("pointerup", resumeLater);
        track.addEventListener("touchend", resumeLater);
        track.addEventListener("scroll", function () {
            if (!paused) {
                clearTimer();
                timer = setTimeout(schedule, AUTO_MS);
            }
        }, { passive: true });

        schedule();

        return {
            destroy: function () {
                clearTimer();
            },
            restart: function () {
                paused = false;
                schedule();
            },
            stop: function () {
                paused = true;
                clearTimer();
            }
        };
    }

    function setupAll() {
        controllers.forEach(function (ctrl) {
            if (ctrl) {
                ctrl.destroy();
            }
        });
        controllers.length = 0;

        if (!isTouchCarousel()) {
            return;
        }

        const bestTrack = document.getElementById("bestSellerTrack");
        const chooseTrack = document.getElementById("chooseGrid");

        controllers.push(createAutoScroll(bestTrack));
        controllers.push(createAutoScroll(chooseTrack));
    }

    window.kingnestSetupCarouselAuto = setupAll;
    window.kingnestIsTouchCarousel = isTouchCarousel;

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", setupAll);
    } else {
        setupAll();
    }

    let resizeTimer;
    window.addEventListener("resize", function () {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(setupAll, 200);
    });
})();
