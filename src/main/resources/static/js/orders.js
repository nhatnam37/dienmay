(() => {
    'use strict';
    const cash = document.querySelector('[data-order-cash]');
    if (cash) {
        const change = document.querySelector('[data-order-change]');
        const updateCash = () => { const received = Number(cash.value), total = Number(cash.dataset.total); change.textContent = cash.value && received < total ? 'Chưa đủ tiền' : new Intl.NumberFormat('vi-VN').format(Math.max(0, received - total)) + ' đ'; };
        cash.addEventListener('input', updateCash); updateCash();
    }
    const checkout = document.querySelector('[data-checkout-form]');
    if (checkout) {
        const delivery = checkout.querySelector('#deliveryType');
        const payment = checkout.querySelector('#paymentMethod');
        const address = checkout.querySelector('#deliveryAddress');
        const update = () => {
            const shipping = delivery.value === 'DELIVERY';
            checkout.querySelector('[data-address-group]').hidden = !shipping;
            address.required = shipping;
            Array.from(payment.options).forEach(option => {
                const allowed = option.value === 'ONLINE' || option.value === (shipping ? 'COD' : 'PAY_AT_STORE');
                option.hidden = !allowed;
                option.disabled = !allowed;
            });
            if (payment.selectedOptions[0]?.disabled) payment.value = shipping ? 'COD' : 'PAY_AT_STORE';
        };
        delivery.addEventListener('change', update);
        update();
    }
    document.querySelectorAll('[data-lock-submit]').forEach(form => form.addEventListener('submit', event => {
        if (!form.checkValidity()) return;
        if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
        form.dataset.submitting = 'true';
        form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = true; });
    }));
    const countdowns = document.querySelectorAll('[data-countdown]');
    const tick = () => countdowns.forEach(element => {
        const seconds = Math.max(0, Math.ceil((Number(element.dataset.expiresMillis) - Date.now()) / 1000));
        const hours = Math.floor(seconds / 3600), minutes = Math.floor(seconds % 3600 / 60);
        element.textContent = seconds ? `Còn ${hours} giờ ${minutes} phút ${seconds % 60} giây` : 'Đã đến hạn giữ hàng';
        if (!seconds) document.querySelectorAll('[data-pay-button]').forEach(button => { button.disabled = true; });
    });
    tick();
    if (countdowns.length) setInterval(tick, 1000);
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
})();
