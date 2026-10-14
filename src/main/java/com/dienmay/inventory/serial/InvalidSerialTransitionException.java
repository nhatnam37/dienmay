package com.dienmay.inventory.serial;

import com.dienmay.common.exception.BusinessException;

public class InvalidSerialTransitionException extends BusinessException {
    public InvalidSerialTransitionException(SerialStatus from, SerialStatus to) {
        super("Không được chuyển Serial từ " + from + " sang " + to + ".");
    }
}
