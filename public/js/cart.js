(function () {
    const STORAGE_KEY = "kingnestCart";

    function readCart() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            const parsed = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
            return [];
        }
    }

    function writeCart(items) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        updateHeaderBadge();
    }

    function normalizeProduct(product) {
        return {
            id: String(product.id),
            productId: product.productId ? String(product.productId) : String(product.id).split("-")[0],
            variantId: product.variantId || "",
            variantLabel: product.variantLabel || "",
            slug: product.slug || "",
            title: product.title || product.name || "Sản phẩm",
            desc: product.desc || product.shortDesc || "Sản phẩm yến sào cao cấp Kingnest.",
            price: Number(product.price) || 0,
            image: product.image || "https://res.cloudinary.com/ln22f4im/image/upload/f_auto,q_auto,w_600/v1783077777/trungbay_n9dca4.jpg"
        };
    }

    function getCart() {
        return readCart();
    }

    function getCartCount() {
        return readCart().reduce((sum, item) => sum + (item.quantity || 0), 0);
    }

    function getCartTotal() {
        return readCart().reduce((sum, item) => sum + (item.price * (item.quantity || 0)), 0);
    }

    function addToCart(product, quantity) {
        const qty = Math.max(1, Number(quantity) || 1);
        const normalized = normalizeProduct(product);
        const items = readCart();
        const existing = items.find((item) => item.id === normalized.id);

        if (existing) {
            existing.quantity += qty;
        } else {
            items.push({ ...normalized, quantity: qty });
        }

        writeCart(items);
        return items;
    }

    function buyNow(product, quantity) {
        const qty = Math.max(1, Number(quantity) || 1);
        const normalized = normalizeProduct(product);
        writeCart([{ ...normalized, quantity: qty }]);
        window.location.href = "/gio-hang?step=2";
    }

    function updateQuantity(id, quantity) {
        const qty = Number(quantity);
        const items = readCart().map((item) => {
            if (item.id !== String(id)) {
                return item;
            }
            return { ...item, quantity: Math.max(1, qty) };
        });
        writeCart(items);
        return items;
    }

    function removeFromCart(id) {
        const items = readCart().filter((item) => item.id !== String(id));
        writeCart(items);
        return items;
    }

    function clearCart() {
        writeCart([]);
    }

    function replaceCart(items) {
        const normalizedItems = (items || []).map((item) => ({
            ...normalizeProduct(item),
            quantity: Math.max(1, Number(item.quantity) || 1)
        }));
        writeCart(normalizedItems);
        return normalizedItems;
    }

    function formatPrice(number) {
        return new Intl.NumberFormat("vi-VN").format(number) + " đ";
    }

    function updateHeaderBadge() {
        const count = getCartCount();
        document.querySelectorAll("[data-cart-count]").forEach((element) => {
            element.textContent = String(count);
            element.hidden = count <= 0;
        });
    }

    function cartIconSvg() {
        return `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="9" cy="20" r="1.5"></circle><circle cx="18" cy="20" r="1.5"></circle><path d="M2 3h2l2.2 12.3a2 2 0 0 0 2 1.7h8.7a2 2 0 0 0 2-1.6L22 6H6"></path></svg>`;
    }

    window.KingnestCart = {
        getCart,
        addToCart,
        buyNow,
        updateQuantity,
        removeFromCart,
        clearCart,
        replaceCart,
        getCartCount,
        getCartTotal,
        formatPrice,
        updateHeaderBadge,
        cartIconSvg
    };

    document.addEventListener("DOMContentLoaded", updateHeaderBadge);
})();
