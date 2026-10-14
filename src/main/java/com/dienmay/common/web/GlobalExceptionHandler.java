package com.dienmay.common.web;

import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/404";
    }

    // Không bắt BusinessException toàn cục: controller catalog ở bước sau
    // cần catch lỗi để giữ dữ liệu nhập, BindingResult và danh sách chọn của form.
}
