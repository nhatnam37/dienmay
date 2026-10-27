package com.dienmay.common.pagination;

/** Message chỉ do code tạo, không chứa input, tên lớp hoặc câu SQL. */
public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException(String message){super(message);}
}
