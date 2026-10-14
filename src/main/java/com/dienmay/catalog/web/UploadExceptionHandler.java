package com.dienmay.catalog.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class UploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(MaxUploadSizeExceededException exception, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "Ảnh tối đa 2MB. Vui lòng mở lại form và chọn ảnh nhỏ hơn.");
        return "redirect:/admin/catalog/products";
    }
}
