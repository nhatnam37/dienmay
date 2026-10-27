# Test đổi tên trong 9A

## Lớp tích hợp

| File cũ | File mới |
|---|---|
| src/test/java/com/dienmay/DienmayApplicationTests.java | src/test/java/com/dienmay/DienmayApplicationIT.java |
| src/test/java/com/dienmay/organization/BranchWorkflowTest.java | src/test/java/com/dienmay/organization/BranchWorkflowIT.java |
| src/test/java/com/dienmay/sales/ReturnWorkflowTest.java | src/test/java/com/dienmay/sales/ReturnWorkflowIT.java |
| src/test/java/com/dienmay/sales/CheckoutWorkflowTest.java | src/test/java/com/dienmay/sales/CheckoutWorkflowIT.java |
| src/test/java/com/dienmay/sales/PosReadIntegrationTest.java | src/test/java/com/dienmay/sales/PosReadIntegrationIT.java |
| src/test/java/com/dienmay/catalog/CatalogPersistenceTest.java | src/test/java/com/dienmay/catalog/CatalogPersistenceIT.java |
| src/test/java/com/dienmay/inventory/StocktakeWorkflowTest.java | src/test/java/com/dienmay/inventory/StocktakeWorkflowIT.java |
| src/test/java/com/dienmay/inventory/InventoryPersistenceTest.java | src/test/java/com/dienmay/inventory/InventoryPersistenceIT.java |
| src/test/java/com/dienmay/inventory/GoodsReceiptWorkflowTest.java | src/test/java/com/dienmay/inventory/GoodsReceiptWorkflowIT.java |
| src/test/java/com/dienmay/inventory/TransferWorkflowTest.java | src/test/java/com/dienmay/inventory/TransferWorkflowIT.java |
| src/test/java/com/dienmay/orders/OrderWorkflowTest.java | src/test/java/com/dienmay/orders/OrderWorkflowIT.java |
| src/test/java/com/dienmay/orders/OrderFulfillmentTest.java | src/test/java/com/dienmay/orders/OrderFulfillmentIT.java |
| src/test/java/com/dienmay/customer/CustomerSecurityIntegrationTest.java | src/test/java/com/dienmay/customer/CustomerSecurityIntegrationIT.java |
| src/test/java/com/dienmay/reports/ReportQueryIntegrationTest.java | src/test/java/com/dienmay/reports/ReportQueryIntegrationIT.java |
| src/test/java/com/dienmay/warranty/WarrantyWorkflowTest.java | src/test/java/com/dienmay/warranty/WarrantyWorkflowIT.java |

## Method chuẩn hóa

| File | Tên cũ | Tên mới |
|---|---|---|
| src/test/java/com/dienmay/DienmayApplicationIT.java | contextLoads | context_applicationStarts_requiredServicesAvailable |
| src/test/java/com/dienmay/config/DevDataInitializerTest.java | createsMissingAccountsWithBcryptAndPreservesExistingAccounts | initialize_missingAccounts_createsBcryptAndPreservesExisting |
| src/test/java/com/dienmay/config/DevDataInitializerTest.java | missingCn2_createsBranchAndManagerWithoutChangingExistingAccounts | initialize_missingCn2_createsBranchAndManager |
| src/test/java/com/dienmay/reports/ReportRulesTest.java | scope_warehouseInventoryAllowedFinancialDenied | scope_warehouse_allowsInventoryDeniesFinancial |
| src/test/java/com/dienmay/reports/ReportRulesTest.java | dto_noCustomerPiiOrCost | dto_publicReport_excludesCustomerPiiAndCost |
| src/test/java/com/dienmay/reports/ReportWebTest.java | warehouse_inventoryAllowedRevenueDeniedAndExportsProtected | reports_warehouse_allowsInventoryDeniesRevenueAndExports |
| src/test/java/com/dienmay/reports/ReportWebTest.java | customerContext_employeeLoginRequired | reports_customerContext_requiresEmployeeLogin |
| src/test/java/com/dienmay/reports/ReportWebTest.java | jsonAndExcel_cashierBlocked | reports_cashier_deniesJsonAndExcel |
| src/test/java/com/dienmay/reports/ReportWebTest.java | excelWarehouseInventory_allowedHeadersAndTemporaryFileRemoved | export_warehouseInventory_returnsHeadersAndRemovesTempFile |
| src/test/java/com/dienmay/reports/ReportWebTest.java | excelTooManyRows_friendlyJsonAndNoXlsx | export_tooManyRows_returnsFriendlyJsonWithoutXlsx |
| src/test/java/com/dienmay/reports/ReportExcelTest.java | revenue_numericMoneyDateAndSummaryMatch | export_revenue_preservesNumericMoneyDateAndSummary |
| src/test/java/com/dienmay/reports/ReportExcelTest.java | productFormulaLikeName_isStringNeverFormula | export_formulaLikeName_keepsStringNeverFormula |
| src/test/java/com/dienmay/reports/ReportExcelTest.java | inventoryLimit_preflightRefusesBeforeStreaming | export_inventoryOverLimit_refusesBeforeStreaming |
| src/test/java/com/dienmay/reports/ReportQueryIntegrationIT.java | products_quantityRevenueDifferentAndDiscontinuedIncluded | products_differentRanking_includesDiscontinuedSales |
| src/test/java/com/dienmay/reports/ReportQueryIntegrationIT.java | lowStock_transferredOutStillVisibleNeverHeldExcludedReservedUnavailable | lowStock_branchHoldingHistory_includesTransferredOutExcludesNeverHeldAndReserved |
| src/test/java/com/dienmay/reports/ReportQueryIntegrationIT.java | scope_managerOwnBranchAndWarehouseNoRevenue | scope_staffRoles_restrictsBranchAndFinancialAccess |
| src/test/java/com/dienmay/reports/ReportQueryIntegrationIT.java | ordinaryTestProfile_benchmarkLoaderAndRunnerNotRegistered | profile_ordinaryTest_excludesBenchmarkLoaderAndRunner |
| src/test/java/com/dienmay/warranty/WarrantyWebTest.java | customerPage_usesPrincipalIdOnly | customerPage_authenticatedCustomer_usesPrincipalIdOnly |
