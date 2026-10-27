# Đối chiếu mã ca với assertion thực tế

Nguồn duy nhất: [test-cases.md](test-cases.md), do người dùng cung cấp và bổ sung theo nhiệm vụ 9B. 162 mã đang dùng (150 mã 9A và SEC-02..13 của 9B). TC-WEB-26 đã thay bằng TC-WEB-31–35, không gắn annotation và không tính là ca thiếu. TC-AUTH-04 gọi /staff; TC-WEB-36 cho phép giỏ ẩn danh; TC-WEB-37 gồm hai context, đăng xuất độc lập và CSRF tách riêng.

## Quy tắc đối chiếu

Chỉ gắn khi test kiểm tra điều kiện/kết quả tương ứng; không gắn một mã cho toàn bộ class chỉ vì cùng module. Một ca có thể được kiểm tra bằng nhiều unit/web/integration test; một method kiểm tra nhiều ca có nhiều annotation. Method tham số hóa/repeated được gắn ở mức method nên mọi invocation cùng tham gia kết quả của các mã đã gắn.

Các giá trị tiền, tên, dữ liệu biên tương đương có thể khác ví dụ trong bảng: giá âm -0,01 thay -1; bảo hành 61 thay 100; upload .svg thay .txt; tiền thối 100/200 thay 800.000/1.000.000. Các test vẫn kiểm tra đúng quy tắc và kết quả tương ứng; không khẳng định đã chạy nguyên xi mọi dữ liệu mẫu. Đặc biệt INV-09 yêu cầu kiểm số lượng cụ thể 5 máy nhưng fixture hiện chỉ có 2: giữ Chưa có test để người dùng chốt, không tự gắn mã.

## Các ca chưa có test được gắn mã

- TC-AUTH-07: Đăng nhập sai mật khẩu.
- TC-AUTH-08: POST không có CSRF token.
- TC-AUTH-09: Đường dẫn không tồn tại.
- TC-INV-09: Nhập kho hợp lệ.
- TC-RPT-22: Hiệu năng (NFR-04).
- TC-WAR-14: Từ chối bảo hành.

## Bảng ánh xạ

| ID | Method đã rà và gắn mã |
|---|---|
| TC-AUTH-01 | AppUserDetailsServiceTest#loadUser_whenExists_returnsUserWithRoleAuthority |
| TC-AUTH-02 | AppUserDetailsServiceTest#loadUser_whenNotFound_throwsUsernameNotFound |
| TC-AUTH-03 | AppUserDetailsServiceTest#loadUser_whenInactive_returnsDisabledUser |
| TC-AUTH-04 | SecurityRulesTest#staff_whenAnonymous_redirectsToEmployeeLogin |
| TC-AUTH-05 | SecurityRulesTest#admin_whenCashier_isForbidden |
| TC-AUTH-06 | SecurityRulesTest#admin_whenAdmin_isOk |
| TC-AUTH-07 | Chưa có test |
| TC-AUTH-08 | Chưa có test |
| TC-AUTH-09 | Chưa có test |
| TC-AUTH-10 | DevDataInitializerTest#initialize_missingAccounts_createsBcryptAndPreservesExisting |
| TC-BRD-01 | BrandServiceTest#delete_brandHasProducts_rejectsRequest<br>CatalogWebTest#deleteBrand_hasProducts_redirectsWithFailureFlash |
| TC-CAT-01 | CategoryServiceTest#create_validParent_returnsViewWithParentName |
| TC-CAT-02 | CategoryServiceTest#create_duplicateName_rejectsRequest<br>CatalogPersistenceIT#categoryName_caseInsensitiveAccentSensitive_enforcesUniqueIndex<br>CatalogWebTest#createCategory_duplicateName_redirectsWithBusinessError |
| TC-CAT-03 | CategoryServiceTest#update_selfAsParent_rejectsRequest |
| TC-CAT-04 | CategoryServiceTest#update_descendantAsParent_rejectsRequest<br>CatalogPersistenceIT#updateCategory_descendantAsParent_rejectsCycleOnRealTree |
| TC-CAT-05 | CategoryServiceTest#delete_categoryHasProducts_rejectsRequest |
| TC-CAT-06 | CategoryServiceTest#delete_categoryHasChildren_rejectsRequest |
| TC-CAT-07 | CategoryServiceTest#delete_emptyCategory_deletesSuccessfully |
| TC-INV-01 | CheckoutWorkflowIT#checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock |
| TC-INV-02 | SerialStateServiceTest#transition_damagedSerial_cannotLeaveFinalState |
| TC-INV-03 | SerialNumberRulesTest#validate_knownValidImei_passesLuhnAndValidation |
| TC-INV-04 | SerialNumberRulesTest#validate_wrongCheckDigit_reportsLuhnError<br>GoodsReceiptServiceTest#create_invalidImei_reportsLineAndDoesNotSave |
| TC-INV-05 | SerialNumberRulesTest#validate_wrongImeiLengthOrLetters_rejectsInput<br>GoodsReceiptServiceTest#create_invalidImei_reportsLineAndDoesNotSave |
| TC-INV-06 | GoodsReceiptServiceTest#create_duplicateSerialAcrossLines_reportsOriginalLine<br>GoodsReceiptServiceTest#create_duplicateSerialWithinLine_rejectsBatch |
| TC-INV-07 | SerialStateServiceTest#register_existingNumber_rejectsBeforeSave<br>GoodsReceiptServiceTest#create_serialExistsInDatabase_checksWholeBatchWithOneQuery<br>InventoryPersistenceIT#insert_duplicateGlobalSerial_databaseRejectsDuplicate |
| TC-INV-08 | GoodsReceiptServiceTest#create_multipleErrors_reportsAllRowsTogether |
| TC-INV-09 | Chưa có test |
| TC-INV-10 | GoodsReceiptWorkflowIT#create_oneInvalidRow_savesNothing<br>GoodsReceiptWorkflowIT#create_databaseFailureAfterWrites_rollsBackEveryTableIncludingNewStockRow |
| TC-INV-11 | InventoryPersistenceIT#decrease_insufficientStock_rollsBackWithoutExtraMovement<br>StockServiceTest#decrease_moreThanAvailable_rejectsWithoutMovement |
| TC-INV-12 | ProductServiceTest#update_trackingChangedWithInventory_rejectsRequest<br>ProductServiceTest#update_serialTypeChangedWithInventory_rejectsRequest<br>InventoryPersistenceIT#update_productHasStock_blocksChangingTrackingMode |
| TC-INV-13 | SerialStateServiceTest#transition_transferCompleted_changesBranchAndRecordsBothBranches<br>TransferWorkflowIT#lifecycle_mixedTransfer_changesBranchesAndHistoryAtomically |
| TC-INV-14 | TransferServiceTest#receive_wrongBranch_returnsNotFound<br>TransferWorkflowIT#actions_wrongBranchAndRepeatedReceive_doNotChangeData |
| TC-INV-15 | TransferWorkflowIT#ship_twoConcurrentDraftsSharingSerials_exactlyOneCommits |
| TC-INV-16 | GoodsReceiptServiceTest#getById_receiptFromAnotherBranch_returnsNotFound<br>GoodsReceiptWorkflowIT#receipt_otherBranchThroughRealService_returns404<br>InventoryWebTest#detail_otherBranch_returnsNotFound |
| TC-INV-17 | TransferWorkflowIT#lifecycle_mixedTransfer_changesBranchesAndHistoryAtomically |
| TC-POS-01 | CheckoutWorkflowIT#checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock |
| TC-POS-02 | CheckoutServiceTest#checkout_emptyCart_rejects |
| TC-POS-03 | CheckoutServiceTest#checkout_serialWithoutPhone_rejects |
| TC-POS-04 | CheckoutServiceTest#checkout_duplicateSerial_rejectsBeforeLocking |
| TC-POS-05 | CheckoutServiceTest#checkout_serialOtherBranch_rejects<br>CheckoutServiceTest#checkout_reservedSerial_rejects<br>CheckoutWorkflowIT#checkout_oneValidOneReservedSerial_savesNothing<br>SerialLookupForSaleTest#lookup_reservedSerial_returnsOnlineReservationReason<br>PosWebTest#serial_eachStatus_returnsCorrectSellability<br>PosWebTest#serial_otherBranch_returnsReason |
| TC-POS-06 | CheckoutServiceTest#checkout_discontinuedProduct_rejects<br>PosWebTest#serial_discontinued_returnsReason |
| TC-POS-07 | CheckoutServiceTest#checkout_cashierDiscountAboveFivePercent_rejects |
| TC-POS-08 | CheckoutServiceTest#checkout_managerDiscountAboveCashierLimit_accepts |
| TC-POS-09 | CheckoutServiceTest#checkout_paymentBelowTotal_rejects |
| TC-POS-10 | CheckoutServiceTest#checkout_cashOverpayment_calculatesChangeOnServer<br>CheckoutWorkflowIT#checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock<br>CheckoutServiceTest#checkout_splitPayments_calculatesOnlyCashChange |
| TC-POS-11 | CheckoutWorkflowIT#checkout_oneValidOneReservedSerial_savesNothing |
| TC-POS-12 | CheckoutWorkflowIT#checkout_sameToken_replaysWithoutDuplicateEffects<br>CheckoutWorkflowIT#checkout_concurrentSameToken_returnsSameInvoiceToBoth |
| TC-POS-13 | CheckoutWorkflowIT#checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock<br>OrderFulfillmentIT#complete_delivery_usesOrderPricesAndCreatesWarrantyInvoiceAndStock |
| TC-POS-14 | CheckoutWorkflowIT#checkout_twoSalesSameSerial_exactlyOneCommits |
| TC-POS-15 | CheckoutWorkflowIT#checkout_overlappingSerialsReversedOrder_noPartialSaleOrDeadlock |
| TC-POS-16 | CheckoutWorkflowIT#checkout_saleVersusTransfer_exactlyOneOwnsSerial |
| TC-POS-17 | PosWebTest#counter_warehouse_returns403 |
| TC-POS-18 | PosWebTest#invoice_otherBranch_returns404<br>PosQueryServiceTest#detail_managerOtherBranch_throws404<br>PosReadIntegrationIT#detail_roleAndBranchRestrictions_areEnforcedByRealService |
| TC-POS-19 | ReturnWorkflowIT#returnItems_serialReturnedThenResold_preservesOldInvoiceAndVoidsOldWarranty |
| TC-POS-20 | ReturnWorkflowIT#returnItems_seventhDayAllowed_eighthDayRejected<br>ReturnServiceTest#returnItems_afterWindow_isRejected<br>ReturnServiceTest#returnItems_missingReason_isRejected<br>ReturnServiceTest#cancelInvoice_missingReason_rejectsBeforeInvoiceLock |
| TC-POS-21 | ReturnWorkflowIT#returnItems_serialInWarranty_rejectsWithoutAnyReturn<br>ReturnServiceTest#returnItems_serialInWarranty_isRejected<br>WarrantyWorkflowIT#salesReturn_whileRepairing_rejected |
| TC-POS-22 | ReturnWorkflowIT#returnItems_serialReturnedThenResold_preservesOldInvoiceAndVoidsOldWarranty<br>WarrantyWorkflowIT#lookup_afterReturnAndResale_usesNewActiveWarranty |
| TC-POS-23 | ReturnWorkflowIT#returnItems_twoThreadsSameSerial_onlyOneCommits<br>ReturnWorkflowIT#returnItems_twoThreadsSameQuantityLine_onlyOneFullReturnCommits<br>ReturnWorkflowIT#returnItems_twoStalePartialQuantityForms_onlyOneCommits |
| TC-PRD-01 | ProductServiceTest#create_lowercasePaddedSku_savesUppercaseAndReturnsNames<br>CatalogWebTest#createProduct_validForm_redirectsWithSuccess |
| TC-PRD-02 | ProductServiceTest#create_duplicateNormalizedSku_rejectsRequest |
| TC-PRD-03 | ProductServiceTest#create_negativePrice_rejectsRequest |
| TC-PRD-04 | ProductServiceTest#create_warrantyOverSixty_rejectsRequest |
| TC-PRD-05 | ProductServiceTest#discontinue_existingProduct_onlyChangesStatusWithoutDelete |
| TC-PRD-06 | CatalogPersistenceIT#search_filtersAndPagination_fetchesNamesWithoutNPlusOne<br>ProductServiceTest#search_optionalFilters_mapsPageAndEscapesLikeCharacters<br>CatalogWebTest#products_admin_rendersPageWithFiltersAndPrice |
| TC-RPT-01 | ReportQueryIntegrationIT#revenue_halfOpenDiscountRefundDatesCancellationAndNegativeDay_correct |
| TC-RPT-02 | ReportQueryIntegrationIT#revenue_monthBoundaryAndEmptyDays_correct |
| TC-RPT-03 | ReportQueryIntegrationIT#revenue_halfOpenDiscountRefundDatesCancellationAndNegativeDay_correct |
| TC-RPT-04 | ReportQueryIntegrationIT#revenue_halfOpenDiscountRefundDatesCancellationAndNegativeDay_correct<br>ReportWebTest#revenue_negativeWarningAndMoneyFormatting_render |
| TC-RPT-05 | ReportQueryIntegrationIT#revenue_halfOpenDiscountRefundDatesCancellationAndNegativeDay_correct |
| TC-RPT-06 | ReportQueryIntegrationIT#revenue_monthBoundaryAndEmptyDays_correct<br>ReportRulesTest#periods_missingDaysAndMonths_filledWithZero |
| TC-RPT-07 | ReportQueryIntegrationIT#revenue_crossCheckDayBranchChannelSummary_equal |
| TC-RPT-08 | ReportQueryIntegrationIT#products_differentRanking_includesDiscontinuedSales |
| TC-RPT-09 | ReportQueryIntegrationIT#lowStock_branchHoldingHistory_includesTransferredOutExcludesNeverHeldAndReserved |
| TC-RPT-10 | ReportQueryIntegrationIT#revenue_crossCheckDayBranchChannelSummary_equal |
| TC-RPT-11 | ReportRulesTest#comparison_zeroPrevious_percentNullAndNegativePreviousDirectionPreserved<br>ReportWebTest#dashboard_adminBranchSelectorAndChart_present |
| TC-RPT-12 | ReportRulesTest#range_reversed_rejected<br>ReportRulesTest#range_moreThan366_rejected<br>ReportWebTest#period_invalidOrLong_friendlyServerError |
| TC-RPT-13 | ReportQueryIntegrationIT#scope_staffRoles_restrictsBranchAndFinancialAccess<br>ReportRulesTest#scope_managerOtherBranch_notFound<br>ReportWebTest#json_managerOtherBranch_returns404 |
| TC-RPT-14 | ReportQueryIntegrationIT#scope_staffRoles_restrictsBranchAndFinancialAccess<br>ReportRulesTest#scope_warehouse_allowsInventoryDeniesFinancial<br>ReportRulesTest#scope_cashierAndForgedRole_denied<br>ReportWebTest#pages_cashierAndWarrantyStaff_allForbidden<br>ReportWebTest#reports_warehouse_allowsInventoryDeniesRevenueAndExports |
| TC-RPT-15 | ReportQueryIntegrationIT#inventory_countsStatusAndAvailable_noPii<br>ReportRulesTest#scope_warehouse_allowsInventoryDeniesFinancial<br>ReportWebTest#reports_warehouse_allowsInventoryDeniesRevenueAndExports<br>ReportWebTest#export_warehouseInventory_returnsHeadersAndRemovesTempFile |
| TC-RPT-16 | ReportWebTest#reports_warehouse_allowsInventoryDeniesRevenueAndExports<br>ReportWebTest#reports_cashier_deniesJsonAndExcel<br>ReportWebTest#json_byBranchManager_forbidden |
| TC-RPT-17 | ReportExcelTest#export_revenue_preservesNumericMoneyDateAndSummary |
| TC-RPT-18 | ReportExcelTest#export_formulaLikeName_keepsStringNeverFormula |
| TC-RPT-19 | ReportExcelTest#detailLimit_exceeded_refusedNoTruncation<br>ReportExcelTest#export_inventoryOverLimit_refusesBeforeStreaming<br>ReportWebTest#export_tooManyRows_returnsFriendlyJsonWithoutXlsx |
| TC-RPT-20 | ReportExcelTest#export_formulaLikeName_keepsStringNeverFormula |
| TC-RPT-21 | ReportQueryIntegrationIT#queries_rowCountGrows_statementCountStaysConstant |
| TC-RPT-22 | Chưa có test |
| TC-RPT-23 | ReportQueryIntegrationIT#lowStock_branchHoldingHistory_includesTransferredOutExcludesNeverHeldAndReserved |
| TC-SEC-01 | CatalogWebTest#products_cashier_isForbidden |
| TC-UPL-01 | FileStorageServiceTest#store_invalidExtension_rejectsFile |
| TC-UPL-02 | FileStorageServiceTest#store_fileAboveTwoMegabytes_rejectsFile |
| TC-UPL-03 | FileStorageServiceTest#store_originalName_usesUuidInsteadOfOriginalName<br>FileStorageServiceTest#store_traversalOriginalName_staysInsideUploadDirectory |
| TC-WAR-01 | CheckoutWorkflowIT#checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock<br>OrderFulfillmentIT#complete_delivery_usesOrderPricesAndCreatesWarrantyInvoiceAndStock |
| TC-WAR-02 | WarrantyStatusPolicyTest#evaluate_lastDay_validIncludingLastSecond<br>WarrantyStatusPolicyTest#evaluate_nextDay_expiredInVietnamDespiteUtcDate |
| TC-WAR-03 | WarrantyQueryServiceTest#publicDtos_structure_hasOnlyApprovedFields<br>WarrantyWebTest#lookup_success_noPiiAndNoSerialInResponseUrl |
| TC-WAR-04 | WarrantyQueryServiceTest#publicLookup_unknownUnsoldVoided_sameEmptyResult<br>WarrantyWorkflowIT#publicHtml_unknownAndActualUnsold_renderSameVisibleContent<br>WarrantyWebTest#lookup_unknownAndUnsold_renderExactlySameVisibleContent |
| TC-WAR-05 | WarrantyLookupRateLimiterTest#check_twentyFirstRequest_429AndIndependentIpAndReset<br>WarrantyWebTest#lookup_twentyFirstSharedRequest_friendly429 |
| TC-WAR-06 | WarrantyQueryServiceTest#publicTrack_missingOnePart_returnsNothing |
| TC-WAR-07 | WarrantyWorkflowIT#repair_fullLifecycle_recordsSerialAndTicketHistoriesAndAllowsNewTicket<br>WarrantyWebTest#receive_success_postRedirectGetAndFlash |
| TC-WAR-08 | WarrantyTicketServiceTest#receive_notSold_rejected<br>WarrantyTicketServiceTest#receive_voided_rejected<br>WarrantyTicketServiceTest#receive_openTicket_rejected<br>WarrantyTicketServiceTest#receive_missingIssue_rejected<br>WarrantyWebTest#receive_validation_keepsFormInFlash |
| TC-WAR-09 | WarrantyTicketServiceTest#receive_expiredStaff_rejected<br>WarrantyWebTest#new_expiredStaff_needsManagerAndNoOverrideInput |
| TC-WAR-10 | WarrantyTicketServiceTest#receive_expiredManagerWithReason_accepted |
| TC-WAR-11 | WarrantyTicketStatusTest#canTransitionTo_all36Pairs_matchesBusinessMatrix |
| TC-WAR-12 | WarrantyWorkflowIT#repair_fullLifecycle_recordsSerialAndTicketHistoriesAndAllowsNewTicket |
| TC-WAR-13 | WarrantyWorkflowIT#cancel_receivedTicket_returnsSerialToSold |
| TC-WAR-14 | Chưa có test |
| TC-WAR-15 | ReturnWorkflowIT#returnItems_serialInWarranty_rejectsWithoutAnyReturn<br>ReturnServiceTest#returnItems_serialInWarranty_isRejected<br>WarrantyWorkflowIT#salesReturn_whileRepairing_rejected |
| TC-WAR-16 | WarrantyWorkflowIT#lookup_afterReturnAndResale_usesNewActiveWarranty |
| TC-WAR-17 | WarrantyTicketServiceTest#receive_openTicket_rejected<br>WarrantyWorkflowIT#constraints_duplicateOpenTicketAndActiveWarranty_rejectedButClosedTicketPreserved |
| TC-WAR-18 | WarrantyWorkflowIT#receive_twoEmployees_onlyOneTicketRepeated20Times |
| TC-WAR-19 | WarrantyWorkflowIT#receive_vsSalesReturn_oneConsistentWinnerRepeated20Times |
| TC-WAR-20 | WarrantyWorkflowIT#change_sameVersion_onlyOneWinsWithReloadMessageRepeated20Times<br>WarrantyWebTest#transition_staleVersion_flashAndRedirect |
| TC-WAR-21 | WarrantyTicketServiceTest#startRepair_wrongBranch_notFound<br>WarrantyWorkflowIT#startRepair_managerOtherBranch_returns404<br>WarrantyWorkflowIT#screen_pagingOpenFirst_filtersAndServiceBranchScope<br>WarrantyWebTest#detail_otherBranch_returns404 |
| TC-WAR-22 | WarrantyWebTest#staff_cashierAndWarehouse_forbidden |
| TC-WAR-23 | WarrantyWorkflowIT#customerWarranties_onlineOwnerOnly_neverPhoneMatch<br>WarrantyWebTest#customerPage_authenticatedCustomer_usesPrincipalIdOnly |
| TC-WEB-01 | CustomerServiceTest#register_validInput_normalizedAndHashed<br>CustomerSecurityIntegrationIT#register_valid_autoLoginKeepsEmployeeAndUsesBcrypt |
| TC-WEB-02 | CustomerServiceTest#register_duplicateEmail_rejected<br>CustomerServiceTest#register_duplicatePhone_rejected<br>CustomerWebTest#register_duplicateEmail_showsFormWithoutEchoingPassword |
| TC-WEB-03 | CustomerServiceTest#register_shortPassword_rejected<br>CustomerWebTest#register_invalidFields_showsErrorsWithoutSaving |
| TC-WEB-04 | StorefrontWebTest#products_anonymous_public<br>StorefrontWebTest#detail_available_rendersOnlyPublicInformation |
| TC-WEB-05 | CustomerSecurityIntegrationIT#checkout_anonymousCart_thenUppercaseEmailLoginPreservesCart<br>StorefrontWebTest#checkout_anonymous_customerLogin<br>OrderWebTest#checkout_anonymous_customerLogin |
| TC-WEB-06 | CustomerSecurityIntegrationIT#authorization_singleKind_cannotUseOtherContext<br>CustomerSecurityIntegrationIT#login_credentialsForOtherKind_rejected |
| TC-WEB-07 | CartServiceTest#add_quantityOutsideLimits_rejected<br>CartServiceTest#add_mergedQuantityOver20_rejected |
| TC-WEB-08 | StorefrontWebTest#detail_discontinued_returns404<br>CartServiceTest#add_discontinued_rejected<br>CartServiceTest#view_discontinued_warnsAndDisablesCheckout<br>StorefrontServiceTest#detail_missingActiveProduct_returnsNotFound |
| TC-WEB-09 | CustomerSecurityIntegrationIT#stock_mixedSerialStatesAndReservations_countsOnlyAvailableByBranch |
| TC-WEB-10 | OrderPlacementServiceTest#place_valid_usesServerPriceAndPlacedExpiry<br>OrderPlacementServiceTest#place_online_initialHoldIs15Minutes<br>OrderWorkflowIT#place_mixedCart_reservesAtomicallyAndReplaysToken |
| TC-WEB-11 | OrderPlacementServiceTest#place_shortStock_rejectsWithoutPartialReservation<br>OrderPlacementServiceTest#place_severalShortProducts_reportsAllWithoutHoldingAnything<br>OrderWorkflowIT#place_oneLineShort_keepsEverythingUnreserved |
| TC-WEB-12 | OrderPlacementServiceTest#place_valid_usesServerPriceAndPlacedExpiry |
| TC-WEB-13 | OrderWorkflowIT#place_mixedCart_reservesAtomicallyAndReplaysToken<br>OrderWorkflowIT#place_sameTokenConcurrent_returnsSameOrderWithoutDoubleReservation |
| TC-WEB-14 | OrderPlacementServiceTest#place_deliveryWithoutAddress_rejected |
| TC-WEB-15 | OrderWorkflowIT#job_expiredOrder_releasesUsingInjectedTimeAndIsIdempotent |
| TC-WEB-16 | OrderWorkflowIT#pay_afterDeadline_rejected |
| TC-WEB-17 | OrderWorkflowIT#cancel_paidOrder_refundsExactlyOnceAndReleasesOnce |
| TC-WEB-18 | OrderWorkflowIT#place_twoCustomersLastMachine_onlyOneWinsRepeated20Times |
| TC-WEB-19 | OrderWorkflowIT#place_websiteVsPosLastMachine_onlyOneWinsRepeated20Times |
| TC-WEB-20 | OrderWorkflowIT#pay_racesExpiry_consistentResultRepeated20Times |
| TC-WEB-21 | OrderWorkflowIT#place_tenCustomersTenMachines_allWinRepeated20Times |
| TC-WEB-22 | OrderWebTest#detail_otherCustomer_returns404<br>OrderWebTest#payment_otherCustomer_returns404<br>OrderWebTest#customerOrders_statusFilter_usesOwnIdAndRendersPaging |
| TC-WEB-23 | StaffOrderRulesTest#allows_allPairs_matchesTransitionTable<br>StaffOrderServiceTest#pickup_shipping_rejected |
| TC-WEB-24 | OrderFulfillmentIT#complete_delivery_usesOrderPricesAndCreatesWarrantyInvoiceAndStock |
| TC-WEB-25 | OrderFulfillmentIT#complete_failureAfterInvoiceAndWarehouseWrites_rollsBackEverything |
| TC-WEB-27 | StaffOrderServiceTest#change_wrongBranch_returns404<br>StaffOrderWebTest#detail_otherBranch_returns404<br>OrderFulfillmentIT#scope_cashierOtherBranchRejected_adminCanProcessOtherBranch |
| TC-WEB-28 | OrderPlacementServiceTest#place_pickupWithCod_rejected<br>OrderPlacementServiceTest#place_deliveryWithPayAtStore_rejected |
| TC-WEB-29 | OrderWorkflowIT#cancel_paidOrder_refundsExactlyOnceAndReleasesOnce |
| TC-WEB-30 | OrderWorkflowIT#cancel_paidOrderRacesExpiry_refundsAndReleasesOnlyOnceRepeated20Times |
| TC-WEB-31 | OrderFulfillmentIT#cancel_customerAfterConfirmation_alwaysRejected |
| TC-WEB-32 | OrderFulfillmentIT#cancelCustomer_vsConfirm_onlyOneStateWinsRepeated20Times |
| TC-WEB-33 | OrderFulfillmentIT#complete_online_transfersPaymentOnceAndDbRejectsSecondInvoice |
| TC-WEB-34 | StaffOrderServiceTest#confirm_afterExpiry_rejected<br>StaffOrderServiceTest#confirm_jobAlreadyExpired_rejected<br>OrderFulfillmentIT#confirm_jobAlreadyReleased_rejectsAsExpired |
| TC-WEB-35 | OrderFulfillmentIT#confirm_thenJob_runsAfterOldDeadline_leavesOrderAndSerialUntouched |
| TC-WEB-36 | CustomerSecurityIntegrationIT#checkout_anonymousCart_thenUppercaseEmailLoginPreservesCart<br>StorefrontWebTest#cart_anonymous_public |
| TC-WEB-37 | CustomerSecurityIntegrationIT#register_valid_autoLoginKeepsEmployeeAndUsesBcrypt<br>CustomerSecurityIntegrationIT#login_bothKindsInSameSession_contextsAndAuthoritiesRemainSeparate<br>CustomerSecurityIntegrationIT#logout_customer_preservesEmployeeAndCart<br>CustomerSecurityIntegrationIT#logout_employee_preservesCustomer<br>CustomerSecurityIntegrationIT#csrf_tokensFromOtherChain_rejectedWithoutLoggingOut |

## Bổ sung Bước 9B

- TC-SEC-02: ActiveEmployeeFilterTest (3 method) và LockedEmployeeSessionIT (2 method): kiểm tra trạng thái DB, chặn request/POST sau khi khóa và giữ context khách/giỏ.
- TC-SEC-03: SecuritySurfaceIT#csrf_anyRegisteredWriteWithoutToken_rejectsEveryIdentity: 48 endpoint ghi, 7 danh tính, 336 invocation; không phải ma trận phân quyền.
- TC-SEC-04: SecuritySurfaceIT#headers_bothLoginPages_protectContentFrameAndReferrer: 2 trang đăng nhập; đã qua với Referrer-Policy: no-referrer.

Các mã mới được yêu cầu trong nhiệm vụ 9B, không gắn lại mã cho 391 method mở rộng đã chốt. Không dùng lại WEB-26.

- TC-SEC-05..10: LoginProtectionIT và LoginAttemptLimiterTest (các method có annotation tương ứng): ngưỡng cặp/IP, hết hạn bằng clock giả, hai chain độc lập, tài khoản không tồn tại, reset khi thành công.
- TC-SEC-09 bổ sung LoginProtectionTest#authentication_missingAndWrongPassword_performsSameBcryptWork.
- TC-SEC-11: LoginProtectionTest: nguồn IP, cấu hình XFF/allowlist, input XFF sai.
- TC-SEC-12: LoginAttemptLimiterTest: TTL/sức chứa và thay cấu hình ngưỡng/thời gian.
- TC-SEC-13: PublicInputSafetyIT và QueryInputTest, 166 lượt: 17 đường dẫn và các biên phân trang/size/sort/keyword/LIKE. Mọi mã mới nằm trong nguồn sự thật test-cases.md.
