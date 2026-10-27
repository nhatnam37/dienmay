"use strict";
document.addEventListener("submit", (event) => {
    const form = event.target;
    if (form.matches("form[data-confirm]") && !window.confirm(form.dataset.confirm)) {
        event.preventDefault();
    }
});
document.querySelectorAll("[data-warranty-print]").forEach(button => {
    button.addEventListener("click", () => window.print());
});
