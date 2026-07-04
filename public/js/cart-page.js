(function () {
    function initCartPage() {
        const cartApi = window.KingnestCart;
        if (!cartApi) {
            return;
        }

        const stepPanels = {
            1: document.getElementById("cartStep1"),
            2: document.getElementById("cartStep2")
        };

        const stepNodes = Array.from(document.querySelectorAll("[data-step-target]"));
        const cartItemsBox = document.getElementById("cartItemsBox");
        const cartStepTotals = document.getElementById("cartStepTotals");
        const cartStepSubtotal = document.getElementById("cartStepSubtotal");
        const cartStepCouponRow = document.getElementById("cartStepCouponRow");
        const cartStepCouponLabel = document.getElementById("cartStepCouponLabel");
        const cartStepDiscount = document.getElementById("cartStepDiscount");
        const cartStepGrandTotal = document.getElementById("cartStepGrandTotal");
        const cartEmptyBox = document.getElementById("cartEmptyBox");
        const checkoutSummaryBody = document.getElementById("checkoutSummaryBody");
        const checkoutTotals = document.getElementById("checkoutTotals");
        const orderCodeInline = document.getElementById("orderCodeInline");
        const checkoutForm = document.getElementById("checkoutForm");
        const paymentSection = document.getElementById("paymentSection");
        const checkoutLayout = document.getElementById("cartStep2");
        let checkoutMeta = {
            coupon: "",
            discount: 0,
            subtotal: 0,
            total: 0
        };
        let currentOrderCode = "";
        let isSubmitting = false;

        function getUrlParams() {
            return new URLSearchParams(window.location.search);
        }

        async function loadCheckoutFromUrl() {
            const params = getUrlParams();
            const productsParam = params.get("products");
            if (!productsParam) {
                return false;
            }

            const coupon = params.get("coupon") || "";
            const query = new URLSearchParams({ products: productsParam });
            if (coupon) {
                query.set("coupon", coupon);
            }

            try {
                const response = await fetch(`/checkout?${query.toString()}`, {
                    headers: { Accept: "application/json" }
                });
                const data = await response.json();
                if (!response.ok || !data.success || !Array.isArray(data.products) || data.products.length === 0) {
                    return false;
                }

                cartApi.replaceCart(data.products);
                checkoutMeta = {
                    coupon: data.coupon || "",
                    discount: Number(data.discount) || 0,
                    subtotal: Number(data.subtotal) || cartApi.getCartTotal(),
                    total: Number(data.total) || cartApi.getCartTotal()
                };
                return true;
            } catch (error) {
                return false;
            }
        }

        function getStepFromUrl() {
            const step = Number(new URLSearchParams(window.location.search).get("step"));
            return [1, 2, 3].includes(step) ? step : 1;
        }

        function updateStepIndicator(step) {
            stepNodes.forEach((node) => {
                const target = Number(node.dataset.stepTarget);
                node.classList.toggle("active", target === step);
                node.classList.toggle("done", target < step);
            });
        }

        function showPaymentSection(visible) {
            if (!paymentSection || !checkoutLayout) {
                return;
            }

            paymentSection.hidden = !visible;
            checkoutLayout.classList.toggle("is-payment-only", visible);
        }

        function showPanel(step) {
            const panelStep = step >= 2 ? 2 : 1;

            Object.entries(stepPanels).forEach(([key, panel]) => {
                if (panel) {
                    panel.hidden = Number(key) !== panelStep;
                }
            });

            updateStepIndicator(step);
        }

        function setStep(step, options) {
            const opts = options || {};
            const safeStep = [1, 2, 3].includes(step) ? step : 1;
            const items = cartApi.getCart();

            if (safeStep > 1 && items.length === 0) {
                showPaymentSection(false);
                showPanel(1);
                return;
            }

            if (safeStep === 1) {
                showPaymentSection(false);
            } else if (safeStep === 2 && !opts.keepPayment) {
                showPaymentSection(false);
            } else if (safeStep === 3) {
                showPaymentSection(true);
                if (orderCodeInline && !currentOrderCode) {
                    currentOrderCode = generateOrderCode();
                    orderCodeInline.textContent = currentOrderCode;
                }
            }

            showPanel(safeStep);

            const url = new URL(window.location.href);
            url.searchParams.set("step", String(safeStep));
            window.history.replaceState({}, "", url);

            if (safeStep === 3 && paymentSection && !paymentSection.hidden) {
                paymentSection.scrollIntoView({ behavior: "smooth", block: "start" });
            }
        }

        function escapeHtml(text) {
            return String(text)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;");
        }

        function renderCartItems() {
            const items = cartApi.getCart();

            if (items.length === 0) {
                cartItemsBox.innerHTML = "";
                cartEmptyBox.hidden = false;
                return;
            }

            cartEmptyBox.hidden = true;
            cartItemsBox.innerHTML = items.map((item) => `
                <article class="cart-item">
                    <div class="cart-item-image">
                        <img src="${escapeHtml(item.image)}" alt="${escapeHtml(item.title)}" onerror="this.style.display='none';">
                    </div>
                    <div class="cart-item-info">
                        <h3 class="cart-item-title">${escapeHtml(item.title)}</h3>
                        <p class="cart-item-desc">${escapeHtml(item.desc)}</p>
                        <div class="cart-item-price">${cartApi.formatPrice(item.price)}</div>
                    </div>
                    <div class="qty-control" aria-label="Số lượng">
                        <button type="button" data-qty-action="minus" data-id="${item.id}" aria-label="Giảm">−</button>
                        <span>${item.quantity}</span>
                        <button type="button" data-qty-action="plus" data-id="${item.id}" aria-label="Tăng">+</button>
                    </div>
                    <button class="cart-remove" type="button" data-remove-id="${item.id}" aria-label="Xóa">🗑</button>
                </article>
            `).join("");
        }

        function renderCartTotals() {
            const items = cartApi.getCart();
            if (!cartStepTotals) {
                return;
            }

            if (items.length === 0) {
                cartStepTotals.hidden = true;
                return;
            }

            const subtotal = cartApi.getCartTotal();
            const discount = checkoutMeta.discount || 0;
            const coupon = checkoutMeta.coupon;
            const hasCoupon = Boolean(coupon && coupon !== "No coupon applied" && discount > 0);
            const total = Math.max(0, subtotal - discount);

            cartStepTotals.hidden = false;
            if (cartStepSubtotal) {
                cartStepSubtotal.textContent = cartApi.formatPrice(subtotal);
            }
            if (cartStepCouponRow) {
                cartStepCouponRow.hidden = !hasCoupon;
            }
            if (hasCoupon && cartStepCouponLabel) {
                cartStepCouponLabel.textContent = `Mã giảm giá (${coupon})`;
            }
            if (cartStepDiscount) {
                cartStepDiscount.textContent = `- ${cartApi.formatPrice(discount)}`;
            }
            if (cartStepGrandTotal) {
                cartStepGrandTotal.textContent = cartApi.formatPrice(total);
            }
        }

        function renderSummary() {
            const items = cartApi.getCart();
            const subtotal = checkoutMeta.subtotal || cartApi.getCartTotal();
            const discount = checkoutMeta.discount || 0;
            const total = checkoutMeta.total || Math.max(0, subtotal - discount);

            checkoutSummaryBody.innerHTML = items.map((item) => `
                <tr>
                    <td>
                        <strong>${escapeHtml(item.title)}</strong><br>
                        <span>Giá: ${cartApi.formatPrice(item.price)}</span>
                    </td>
                    <td>${item.quantity}</td>
                    <td>${cartApi.formatPrice(item.price * item.quantity)}</td>
                </tr>
            `).join("");

            const couponRow = checkoutMeta.coupon && checkoutMeta.coupon !== "No coupon applied"
                ? `<div><span>Mã giảm giá (${escapeHtml(checkoutMeta.coupon)})</span><strong>- ${cartApi.formatPrice(discount)}</strong></div>`
                : "";

            checkoutTotals.innerHTML = `
                <div><span>Thành tiền</span><strong>${cartApi.formatPrice(subtotal)}</strong></div>
                ${couponRow}
                <div><span>Phí vận chuyển</span><strong class="shipping-free">0 đ</strong></div>
                <div class="grand-total"><span>Tổng tiền thanh toán</span><span>${cartApi.formatPrice(total)}</span></div>
            `;
        }

        function renderAll() {
            renderCartItems();
            renderCartTotals();
            renderSummary();
        }

        function validateCustomerForm() {
            return checkoutForm.reportValidity();
        }

        function validatePayment() {
            return validateCustomerForm();
        }

        function getCouponValue() {
            const coupon = checkoutMeta.coupon;
            if (!coupon || coupon === "No coupon applied") {
                return "";
            }
            return coupon;
        }

        function buildOrderPayload(paymentMethod) {
            return {
                orderCode: currentOrderCode,
                customerName: document.getElementById("customerName").value.trim(),
                customerEmail: document.getElementById("customerEmail").value.trim(),
                customerPhone: document.getElementById("customerPhone").value.trim(),
                customerAddress: document.getElementById("customerAddress").value.trim(),
                customerNote: document.getElementById("customerNote").value.trim(),
                coupon: getCouponValue(),
                paymentMethod,
                items: cartApi.getCart().map((item) => ({
                    id: item.id,
                    productId: item.productId || String(item.id).split("-")[0],
                    variantId: item.variantId || "",
                    variantLabel: item.variantLabel || "",
                    quantity: item.quantity
                }))
            };
        }

        async function submitOrder(paymentMethod) {
            if (!validatePayment() || isSubmitting) {
                return false;
            }

            if (!currentOrderCode) {
                currentOrderCode = generateOrderCode();
                if (orderCodeInline) {
                    orderCodeInline.textContent = currentOrderCode;
                }
            }

            isSubmitting = true;

            try {
                const response = await fetch("/api/orders", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json"
                    },
                    body: JSON.stringify(buildOrderPayload(paymentMethod))
                });
                const data = await response.json();
                if (!response.ok || !data.success) {
                    let message = data.message;
                    if (!message && Array.isArray(data.detail)) {
                        message = data.detail.map((entry) => entry.msg).join(", ");
                    }
                    throw new Error(message || "Không thể ghi nhận đơn hàng");
                }
                return true;
            } catch (error) {
                alert(error.message || "Có lỗi xảy ra khi gửi đơn hàng");
                return false;
            } finally {
                isSubmitting = false;
            }
        }

        function generateOrderCode() {
            const now = new Date();
            const y = String(now.getFullYear()).slice(-2);
            const m = String(now.getMonth() + 1).padStart(2, "0");
            const d = String(now.getDate()).padStart(2, "0");
            const suffix = String(Math.floor(Math.random() * 9000) + 1000);
            return `ORC-${y}${m}${d}-${suffix}`;
        }

        document.getElementById("goToCheckoutBtn")?.addEventListener("click", () => {
            if (cartApi.getCart().length === 0) {
                return;
            }
            setStep(2);
        });

        document.getElementById("backToCartBtn")?.addEventListener("click", () => setStep(1));

        document.getElementById("backToFormBtn")?.addEventListener("click", () => {
            showPaymentSection(false);
            setStep(2);
            checkoutForm.scrollIntoView({ behavior: "smooth", block: "start" });
        });

        document.getElementById("confirmCheckoutBtn")?.addEventListener("click", () => {
            if (!validateCustomerForm()) {
                return;
            }
            setStep(3);
        });

        document.getElementById("confirmPaidBtn")?.addEventListener("click", async () => {
            const success = await submitOrder("BANK_TRANSFER");
            if (!success) {
                return;
            }
            alert("Cảm ơn bạn! Đơn hàng đã được ghi nhận và gửi tới cửa hàng. Chúng tôi sẽ liên hệ xác nhận trong thời gian sớm nhất.");
            cartApi.clearCart();
            window.location.href = "/gio-hang?step=1";
        });

        document.getElementById("codBtn")?.addEventListener("click", async () => {
            const success = await submitOrder("COD");
            if (!success) {
                return;
            }
            alert("Đã ghi nhận đơn hàng thanh toán khi nhận hàng. Cảm ơn bạn!");
            cartApi.clearCart();
            window.location.href = "/san-pham";
        });

        cartItemsBox?.addEventListener("click", (event) => {
            const minus = event.target.closest("[data-qty-action='minus']");
            const plus = event.target.closest("[data-qty-action='plus']");
            const removeBtn = event.target.closest("[data-remove-id]");

            if (minus) {
                const item = cartApi.getCart().find((entry) => entry.id === minus.dataset.id);
                if (item) {
                    cartApi.updateQuantity(item.id, item.quantity - 1);
                    renderAll();
                }
            }

            if (plus) {
                const item = cartApi.getCart().find((entry) => entry.id === plus.dataset.id);
                if (item) {
                    cartApi.updateQuantity(item.id, item.quantity + 1);
                    renderAll();
                }
            }

            if (removeBtn) {
                cartApi.removeFromCart(removeBtn.dataset.removeId);
                renderAll();
                if (cartApi.getCart().length === 0) {
                    setStep(1);
                }
            }
        });

        renderAll();

        loadCheckoutFromUrl().finally(() => {
            renderAll();

            const initialStep = getStepFromUrl();
            if (initialStep === 3 && validateCustomerForm()) {
                setStep(3);
            } else {
                setStep(initialStep >= 2 ? 2 : 1);
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initCartPage);
    } else {
        initCartPage();
    }
})();
