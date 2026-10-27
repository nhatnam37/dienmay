(() => {
    'use strict';
    document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', event => {
            if (form.dataset.submitting === 'true' || !window.confirm(form.dataset.confirm)) { event.preventDefault(); return; }
            form.dataset.submitting = 'true'; form.querySelectorAll('button[type="submit"]').forEach(button => button.disabled = true);
        });
    });
    const $ = id => document.getElementById(id);
    const invoice = $('invoice-page');
    const storageKey = owner => 'dienmay.pos.v1.' + owner;
    if (invoice) {
        $('print-invoice').addEventListener('click', () => window.print());
        // Chỉ xóa đúng giỏ vừa thanh toán, không xóa giỏ mới khi mở lại hóa đơn cũ.
        if (invoice.dataset.completedToken) {
            try {
                const key = storageKey(invoice.dataset.owner);
                const saved = JSON.parse(sessionStorage.getItem(key));
                if (saved && saved.token === invoice.dataset.completedToken) sessionStorage.removeItem(key);
            } catch (_) { /* In hóa đơn vẫn hoạt động nếu trình duyệt chặn storage. */ }
        }
        return;
    }
    const root = $('pos-counter');
    if (!root) return;
    const key = storageKey(root.dataset.owner);
    let state = {token: root.dataset.token, items: [], payments: [{method: 'CASH', amount: '', cashReceived: '', referenceCode: ''}], customerName: '', customerPhone: '', discount: '0', note: ''};
    let submitting = false, serialBusy = false, timer, searchSequence = 0;
    const maxMoney = 999999999999999n;
    function cents(value) {
        const text = String(value ?? '').trim();
        if (!/^\d{1,13}(\.0{1,2})?$/.test(text)) return null;
        const [whole, fraction = ''] = text.split('.');
        const amount = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
        return amount <= maxMoney ? amount : null;
    }
    function decimal(value) { return (value / 100n).toString() + '.' + (value % 100n).toString().padStart(2, '0'); }
    function money(value) {
        const negative = value < 0n; if (negative) value = -value;
        const integer = (value / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
        const fraction = value % 100n;
        return (negative ? '−' : '') + integer + (fraction ? ',' + fraction.toString().padStart(2, '0') : '') + ' đ';
    }
    function storageWarning() { $('storage-warning').hidden = false; $('storage-warning').textContent = 'Trình duyệt không lưu được giỏ tạm. Không tải lại trang khi đang nhập đơn.'; }
    try {
        const saved = JSON.parse(sessionStorage.getItem(key));
        if (saved && typeof saved.token === 'string' && Array.isArray(saved.items) && Array.isArray(saved.payments)) {
            state = {...state, ...saved};
            state.items = saved.items.filter(item => Number.isSafeInteger(item.productId) && cents(item.price) !== null && Number.isInteger(item.quantity) && item.quantity > 0);
            if (!state.payments.length) state.payments.push({method: 'CASH', amount: '', cashReceived: '', referenceCode: ''});
        }
    } catch (_) { storageWarning(); }
    function save() { try { sessionStorage.setItem(key, JSON.stringify(state)); } catch (_) { storageWarning(); } }
    function node(tag, className, text) { const el = document.createElement(tag); if (className) el.className = className; if (text !== undefined) el.textContent = text; return el; }
    function focusSerial() { $('serial-input').focus(); }
    async function getJson(url) {
        const response = await fetch(url, {headers: {'Accept': 'application/json'}, credentials: 'same-origin'});
        if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Phiên đăng nhập đã hết hoặc phản hồi không hợp lệ. Hãy đăng nhập lại; giỏ vẫn được lưu trong tab này.');
        if (!response.ok) throw new Error('Không thể tra cứu. Kiểm tra quyền truy cập và thử lại.');
        return response.json();
    }
    function renderCart() {
        $('cart-lines').replaceChildren();
        state.items.forEach((item, index) => {
            const row = node('tr'); const product = node('td');
            product.append(node('strong', '', item.name), node('div', 'small text-secondary', item.sku));
            if (item.serialNo) {
                product.append(node('div', 'small', 'Serial: ' + item.serialNo));
                const badge = $('serial-badges').content.querySelector('[data-status="IN_STOCK"]').cloneNode(true); product.append(badge);
            }
            const quantity = node('td');
            if (item.serialNo) quantity.textContent = '1';
            else {
                const input = node('input', 'form-control form-control-sm cart-quantity'); input.type = 'number'; input.min = '1'; input.max = String(item.availableQuantity); input.step = '1'; input.value = item.quantity; input.setAttribute('aria-label', 'Số lượng ' + item.name);
                input.addEventListener('change', () => {
                    const value = Number(input.value);
                    if (!Number.isInteger(value) || value < 1 || value > item.availableQuantity) { $('accessory-message').textContent = 'Số lượng phải từ 1 đến tồn khả dụng: ' + item.availableQuantity; input.value = item.quantity; return; }
                    item.quantity = value; renderCart(); update();
                }); quantity.append(input);
            }
            row.append(product, quantity, node('td', 'text-end text-nowrap', money(cents(item.price))), node('td', 'text-end text-nowrap', money(cents(item.price) * BigInt(item.quantity))));
            const action = node('td'); const remove = node('button', 'btn btn-sm btn-outline-danger', 'Xóa'); remove.type = 'button'; remove.setAttribute('aria-label', 'Xóa ' + (item.serialNo || item.name));
            remove.addEventListener('click', () => { state.items.splice(index, 1); renderCart(); update(); focusSerial(); }); action.append(remove); row.append(action); $('cart-lines').append(row);
        });
        $('empty-cart').hidden = state.items.length > 0; $('cart-count').textContent = state.items.length + ' dòng';
    }
    function renderPayments() {
        $('payment-lines').replaceChildren();
        state.payments.forEach((payment, index) => {
            const fragment = $('payment-template').content.cloneNode(true); const row = fragment.firstElementChild;
            const method = row.querySelector('.payment-method'), amount = row.querySelector('.payment-amount'), received = row.querySelector('.payment-received'), reference = row.querySelector('.payment-reference');
            method.value = payment.method; amount.value = payment.amount; received.value = payment.cashReceived || ''; reference.value = payment.referenceCode || '';
            function mode() { const cash = method.value === 'CASH'; row.querySelector('.cash-group').hidden = !cash; row.querySelector('.reference-group').hidden = cash; received.required = cash; received.disabled = !cash; }
            method.addEventListener('change', () => { payment.method = method.value; payment.cashReceived = ''; received.value = ''; mode(); update(); });
            amount.addEventListener('input', () => { payment.amount = amount.value; update(); }); received.addEventListener('input', () => { payment.cashReceived = received.value; update(); }); reference.addEventListener('input', () => { payment.referenceCode = reference.value; save(); });
            row.querySelector('.remove-payment').addEventListener('click', () => { state.payments.splice(index, 1); renderPayments(); update(); }); mode(); $('payment-lines').append(fragment);
        });
    }
    function update() {
        const subtotal = state.items.reduce((sum, item) => sum + cents(item.price) * BigInt(item.quantity), 0n);
        const discount = cents(state.discount); const total = discount === null ? subtotal : subtotal - discount;
        const hasSerial = state.items.some(item => item.serialNo); const rawPhone = state.customerPhone.trim(); const phone = rawPhone.startsWith('+84') ? '0' + rawPhone.slice(3) : rawPhone;
        const validPhone = (!rawPhone && !hasSerial) || /^0[35789][0-9]{8}$/.test(phone);
        $('phone-required').hidden = !hasSerial; $('customer-phone').required = hasSerial;
        $('phone-error').textContent = validPhone ? '' : (hasSerial && !rawPhone ? 'Hàng Serial bắt buộc số điện thoại khách.' : 'Nhập số di động 03/05/07/08/09 hoặc +84 tương ứng.');
        let discountError = '';
        if (discount === null || discount >= subtotal) discountError = 'Giảm giá phải không âm và nhỏ hơn tiền hàng.';
        else if (Number(root.dataset.discountLimit) < 100 && discount * 10000n > subtotal * BigInt(Math.round(Number(root.dataset.discountLimit) * 100))) discountError = 'Thu ngân chỉ được giảm tối đa ' + root.dataset.discountLimit + '%.';
        $('discount-error').textContent = state.items.length ? discountError : '';
        $('subtotal').textContent = money(subtotal); $('total').textContent = money(total);
        let paid = 0n, validPayments = state.payments.length > 0;
        [...$('payment-lines').children].forEach((row, index) => {
            const payment = state.payments[index], amount = cents(payment.amount), received = cents(payment.cashReceived);
            if (amount === null || amount <= 0n) validPayments = false; else paid += amount;
            let change = '';
            if (payment.method === 'CASH') {
                if (received === null || amount === null || received < amount) { validPayments = false; change = 'Khách đưa phải đủ phần tiền mặt.'; }
                else change = 'Tiền thối: ' + money(received - amount);
            }
            row.querySelector('.payment-change').textContent = change;
        });
        const difference = total - paid;
        $('payment-balance').textContent = difference === 0n ? 'Đã đủ tiền thanh toán' : (difference > 0n ? 'Còn thiếu: ' + money(difference) : 'Đang thừa: ' + money(-difference));
        $('checkout-button').disabled = submitting || !state.items.length || !validPhone || !!discountError || !validPayments || difference !== 0n || total <= 0n || subtotal > maxMoney;
        save();
    }
    $('serial-input').addEventListener('keydown', async event => {
        if (event.key !== 'Enter') return; event.preventDefault(); if (serialBusy || submitting) return;
        const number = event.target.value.trim().toUpperCase(); if (!number) return;
        if (state.items.some(item => item.serialNo === number)) { $('serial-message').textContent = 'Serial đã có trong giỏ: ' + number; event.target.select(); return; }
        serialBusy = true; $('serial-message').textContent = 'Đang tra Serial…';
        try {
            const url = new URL(root.dataset.serialUrl, location.origin); url.searchParams.set('serialNo', number); const result = await getJson(url);
            if (!result.sellable) { $('serial-message').textContent = result.reason; return; }
            if (state.items.some(item => item.serialNo === result.serialNo)) { $('serial-message').textContent = 'Serial đã có trong giỏ.'; return; }
            state.items.push({productId: result.productId, sku: result.sku, name: result.productName, serialNo: result.serialNo, price: String(result.price), quantity: 1});
            event.target.value = ''; $('serial-message').textContent = ''; renderCart(); update();
        } catch (error) { $('serial-message').textContent = error.message; }
        finally { serialBusy = false; focusSerial(); }
    });
    $('accessory-input').addEventListener('input', () => {
        clearTimeout(timer); const sequence = ++searchSequence; const keyword = $('accessory-input').value.trim(); $('accessory-results').replaceChildren(); if (!keyword) return;
        timer = setTimeout(async () => {
            try {
                const url = new URL(root.dataset.accessoryUrl, location.origin); url.searchParams.set('keyword', keyword); const results = await getJson(url); if (sequence !== searchSequence) return;
                $('accessory-results').replaceChildren(); $('accessory-message').textContent = results.length ? '' : 'Không tìm thấy phụ kiện phù hợp.';
                results.forEach(item => {
                    const choice = node('button', 'list-group-item list-group-item-action', item.sku + ' · ' + item.name + ' · ' + money(cents(item.price)) + ' · Có thể bán: ' + item.availableQuantity); choice.type = 'button';
                    choice.addEventListener('click', () => {
                        const quantity = Number($('accessory-quantity').value); const existing = state.items.find(line => !line.serialNo && line.productId === item.productId); const combined = quantity + (existing?.quantity || 0);
                        if (!Number.isInteger(quantity) || quantity <= 0 || combined > item.availableQuantity || combined > 2147483647) { $('accessory-message').textContent = 'Số lượng không hợp lệ hoặc vượt tồn khả dụng: ' + item.availableQuantity; return; }
                        if (existing) { existing.quantity = combined; existing.availableQuantity = item.availableQuantity; existing.price = String(item.price); }
                        else state.items.push({productId: item.productId, sku: item.sku, name: item.name, serialNo: null, price: String(item.price), quantity, availableQuantity: item.availableQuantity});
                        $('accessory-input').value = ''; $('accessory-results').replaceChildren(); $('accessory-message').textContent = ''; renderCart(); update(); focusSerial();
                    }); $('accessory-results').append(choice);
                });
            } catch (error) { if (sequence === searchSequence) $('accessory-message').textContent = error.message; }
        }, 200);
    });
    for (const [id, field] of [['customer-name', 'customerName'], ['customer-phone', 'customerPhone'], ['discount', 'discount'], ['note', 'note']]) {
        $(id).value = state[field]; $(id).addEventListener('input', () => { state[field] = $(id).value; update(); });
    }
    $('add-payment').addEventListener('click', () => { state.payments.push({method: 'CASH', amount: '', cashReceived: '', referenceCode: ''}); renderPayments(); update(); });
    $('checkout-form').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.target.matches('textarea,button')) event.preventDefault(); });
    $('checkout-form').addEventListener('submit', event => {
        update(); if (submitting || $('checkout-button').disabled || serialBusy) { event.preventDefault(); return; }
        const payments = state.payments.map(payment => ({method: payment.method, amount: payment.amount, cashReceived: payment.method === 'CASH' ? payment.cashReceived : null, referenceCode: payment.referenceCode || null}));
        $('checkout-payload').value = JSON.stringify({checkoutToken: state.token, branchId: Number(root.dataset.branch), customerName: state.customerName, customerPhone: state.customerPhone, discountAmount: state.discount, note: state.note, items: state.items.map(item => ({productId: item.productId, serialNo: item.serialNo, quantity: item.quantity})), payments});
        save(); submitting = true; $('checkout-button').disabled = true; $('checkout-button').textContent = 'Đang thanh toán…';
    });
    window.addEventListener('pageshow', () => { submitting = false; $('checkout-button').textContent = 'Thanh toán'; update(); });
    renderCart(); renderPayments(); update(); focusSerial();
})();
