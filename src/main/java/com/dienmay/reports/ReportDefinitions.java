package com.dienmay.reports;
import java.time.ZoneId;
/**
 * Nguồn sự thật của báo cáo: thời gian DATETIME(6) trong DB là giờ địa phương Asia/Ho_Chi_Minh.
 * Kỳ là [from 00:00, to+1 ngày 00:00), không DATE/YEAR cột trong WHERE.
 * Gộp = invoice.total_amount sau giảm giá, mọi status kể cả CANCELLED, theo ngày tạo hóa đơn.
 * Hoàn = sales_return.refund_total gồm FULL_CANCEL theo ngày tạo phiếu trả. Thuần = gộp - hoàn, có thể âm.
 * Số hóa đơn theo ngày bán; số đã hủy là status hiện tại CANCELLED của các hóa đơn đó.
 * Số lượng = lượng bán theo ngày hóa đơn - lượng trả theo NGÀY PHÁT SINH PHIẾU TRẢ,
 * kể cả bán kỳ trước: không sửa ngược ngày bán, nhất quán dòng sự kiện tiền.
 * Doanh số sản phẩm = line_total - quantity trả * unit_price gốc, TRƯỚC giảm giá cấp hóa đơn.
 * Không cộng payment/order_payment/payment_refund: tránh cộng tiền hai lần hoặc khấu trừ FULL_CANCEL hai lần.
 * Kênh POS khi invoice.order_id NULL, ONLINE khi khác NULL; trả hàng giữ kênh hóa đơn gốc.
 * Hàng sắp hết: product ACTIVE, branch active, khả dụng <= ngưỡng kể cả 0;
 * Serial khả dụng chỉ IN_STOCK; phụ kiện quantity-reserved_qty.
 * Từng có hàng: phụ kiện có stock_quantity; Serial có serial_item hiện tại ở chi nhánh
 * hoặc serial_history.to_branch_id tại đó. Máy đã chuyển đi vẫn phải cảnh báo.
 * Báo cáo tiền/tồn chỉ dùng projection, không cộng entity hoặc loại hóa đơn CANCELLED khỏi doanh thu gộp.
 */
public final class ReportDefinitions {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String PRODUCT_NOTE="Doanh số sản phẩm trước giảm giá cấp hóa đơn; hàng trả được trừ theo ngày phát sinh phiếu trả.";
    private ReportDefinitions() { }
}
