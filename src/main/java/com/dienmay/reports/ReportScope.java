package com.dienmay.reports;
// Chỉ là yêu cầu chọn phạm vi. Service luôn xác thực lại với Actor/DB; null là toàn chuỗi chỉ cho ADMIN.
public record ReportScope(Long branchId) {
    public static ReportScope chain(){return new ReportScope(null);}
    public static ReportScope branch(Long id){return new ReportScope(id);}
}
