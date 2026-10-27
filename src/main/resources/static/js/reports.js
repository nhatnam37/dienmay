"use strict";
(() => {
    const root = document.querySelector(".report-shell");
    if (!root) return;
    const money = value => new Intl.NumberFormat("vi-VN", {maximumFractionDigits: 0}).format(value) + " đ";
    const day = value => new Intl.DateTimeFormat("vi-VN").format(new Date(value + "T00:00:00"));
    const css = getComputedStyle(document.documentElement);
    const color = name => css.getPropertyValue(name).trim();
    const filters = new URLSearchParams();
    for (const key of ["from", "to", "branch", "granularity", "order"]) {
        if (root.dataset[key]) filters.set(({branch: "branchId", order: "orderBy"})[key] || key, root.dataset[key]);
    }
    document.querySelectorAll(".report-filters").forEach(form => form.addEventListener("submit", event => {
        const from = form.querySelector("[name=from]"); const to = form.querySelector("[name=to]");
        if (from.disabled || !from.value || !to.value) return;
        const length = (Date.parse(to.value + "T00:00:00Z") - Date.parse(from.value + "T00:00:00Z")) / 86400000 + 1;
        if (length < 1 || length > 366) { event.preventDefault(); form.querySelector(".report-date-error").textContent = "Chọn kỳ hợp lệ: từ không sau đến, tối đa 366 ngày."; }
    }));
    async function responseJson(response) {
        if (response.redirected) throw new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        if (!response.ok) {
            const body = await response.json().catch(() => null);
            throw new Error(body?.message || "Không tải được báo cáo. Vui lòng thử lại.");
        }
        return response.json();
    }
    document.querySelectorAll("[data-report-chart]").forEach(async figure => {
        try {
            const kind = figure.dataset.reportChart;
            const values = await responseJson(await fetch("/reports/api/" + kind + "?" + filters, {headers: {Accept: "application/json"}}));
            let labels, series, headers, rows;
            if (kind === "revenue-series") {
                labels = values.map(v => day(v.period)); headers = ["Ngày", "Gộp", "Hoàn trả", "Thuần"];
                rows = values.map(v => [day(v.period), money(v.totals.gross), money(v.totals.refunds), money(v.totals.net)]);
                series = [{label: "Gộp", data: values.map(v => v.totals.gross), backgroundColor: color("--color-primary")}, {label: "Hoàn trả", data: values.map(v => v.totals.refunds), backgroundColor: color("--color-danger")}, {type: "line", label: "Thuần", data: values.map(v => v.totals.net), borderColor: color("--color-accent"), backgroundColor: color("--color-accent")}];
            } else {
                const product = kind === "top-products";
                labels = values.map(v => product ? v.productName : kind === "by-channel" ? (v.channel === "POS" ? "Tại quầy" : "Online") : v.branchName);
                const data = values.map(v => product ? v.quantity : v.totals.net);
                headers = [product ? "Sản phẩm" : "Kênh / chi nhánh", product ? "Số lượng" : "Thuần"];
                rows = labels.map((label, index) => [label, product ? data[index] : money(data[index])]);
                series = [{label: product ? "Số lượng" : "Thuần", data, backgroundColor: data.map(v => color(v < 0 ? "--color-danger" : "--color-primary"))}];
            }
            const table = figure.querySelector("table"); const tr = document.createElement("tr");
            headers.forEach(text => {const th = document.createElement("th"); th.textContent = text; tr.append(th);}); table.tHead.append(tr);
            rows.forEach(values => {const row = document.createElement("tr"); values.forEach(value => {const td = document.createElement("td"); td.textContent = value; row.append(td);}); table.tBodies[0].append(row);});
            if (!window.Chart) throw new Error("Không tải được thư viện biểu đồ. Bạn vẫn có thể xem bảng dữ liệu bên dưới.");
            const product = kind === "top-products";
            new Chart(figure.querySelector("canvas"), {type: "bar", data: {labels, datasets: series}, options: {responsive: true, maintainAspectRatio: false, scales: {y: {ticks: {precision: 0, callback: value => product ? new Intl.NumberFormat("vi-VN").format(value) : money(value)}}}, plugins: {tooltip: {callbacks: {label: item => item.dataset.label + ": " + (product ? new Intl.NumberFormat("vi-VN").format(item.parsed.y) : money(item.parsed.y))}}}}});
        } catch (error) { figure.querySelector(".report-chart-error").textContent = error.message; figure.querySelector("details").open = true; }
    });
    document.querySelectorAll(".report-export").forEach(link => link.addEventListener("click", async event => {
        event.preventDefault(); const output = document.getElementById("report-export-error"); output.textContent = "Đang tạo tệp Excel…";
        try {
            const response = await fetch(link.href);
            if (response.redirected) throw new Error("Vui lòng đăng nhập lại.");
            if (!response.ok) {const error = await response.json().catch(() => null); throw new Error(error?.message || "Không thể xuất Excel.");}
            if (!response.headers.get("Content-Type")?.includes("spreadsheetml")) throw new Error("Phản hồi không phải tệp Excel.");
            const blob = await response.blob(); const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url;
            a.download = /filename="?([^";]+)/i.exec(response.headers.get("Content-Disposition") || "")?.[1] || "bao-cao.xlsx";
            a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); output.textContent = "Đã tạo tệp Excel.";
        } catch (error) { output.textContent = error.message; }
    }));
})();
