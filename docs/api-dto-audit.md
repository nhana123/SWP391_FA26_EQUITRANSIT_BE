# Audit DTO horses / transport-orders — trước refactor

Nguồn: API_openAI.md và quyết định trực tiếp của người dùng ngày14/10/2026.
Phạm vi gồm DTO request/response của customer, manager, assigned staff và các schema transitive snapshot/services.

Đã quét 34 type, 246 field; một field có thể vi phạm nhiều rule.

| Type | Field | Vấn đề trước sửa |
|---|---|---|
| `AssessmentRequest` | `revisionId` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `AssessmentRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `decision` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `reason` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `specialistId` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `coordinatorId` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `dueAt` | Rule 3: thiếu example tường minh |
| `AssessmentRequest` | `assignmentNote` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `CaseAssignmentRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `staffId` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `reason` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `dueAt` | Rule 3: thiếu example tường minh |
| `CaseAssignmentRequest` | `assignmentNote` | Rule 3: thiếu example tường minh |
| `EndCaseAssignmentRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `EndCaseAssignmentRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `EndCaseAssignmentRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `EndCaseAssignmentRequest` | `reason` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `name` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `microchipId` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `microchipId` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `HorseProfileRequest` | `passportNumber` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `passportNumber` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `HorseProfileRequest` | `breed` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `dateOfBirth` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `sexCode` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `weightKg` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `heightCm` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `bodyWidthCm` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `measuredAt` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `specialCareNotes` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `handlingNotes` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `feedWaterRequirements` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `stallPreferences` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `veterinaryInstructions` | Rule 3: thiếu example tường minh |
| `HorseProfileRequest` | `veterinaryInstructionSource` | Rule 3: thiếu example tường minh |
| `HorseUpdateRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `HorseUpdateRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `horseId` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `originCountryCode` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `originCountryCode` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `destinationCountryCode` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `destinationCountryCode` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `originAddress` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `destinationAddress` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `originTimezone` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `originTimezone` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `destinationTimezone` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `destinationTimezone` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `preferredDepartureAt` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `deliveryDeadline` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `dateFlexibilityNotes` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `importType` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `receiverName` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `receiverPhone` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `receiverPhone` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `receiverEmail` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `receiverEmail` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `receiverAuthority` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `eventStartAt` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `eventName` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `eventVenue` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `eventTimezone` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `eventTimezone` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `transitAirports` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `transitAirports` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderDraftRequest` | `specialNeeds` | Rule 3: thiếu example tường minh |
| `OrderDraftRequest` | `services` | Rule 3: thiếu example tường minh |
| `OrderDraftUpdateRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `OrderDraftUpdateRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderRevisionRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `OrderRevisionRequest` | `changeReason` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `roadArrangement` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `veterinaryArrangement` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `airStall` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `enhancedCare` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `groomingDays` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `boardingNights` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `quarantinePackage` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `insurance` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `existingPolicyReference` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `escortNotes` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `roadProviderName` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `roadProviderContact` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `veterinaryProviderName` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `veterinaryProviderContact` | Rule 3: thiếu example tường minh |
| `OrderSubmitRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `OrderSubmitRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `OrderSubmitRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationRequest` | `recordVersion` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationRequest` | `recordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `ServiceConfirmationRequest` | `operationId` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationRequest` | `horseRecordVersion` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationRequest` | `horseRecordVersion` | Rule 2: rà/chỉnh regex anchored, charset và example |
| `ServiceConfirmationRequest` | `services` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationRequest` | `changeReason` | Rule 3: thiếu example tường minh |
| `AssessmentReceipt` | `orderId` | Rule 3: thiếu example tường minh |
| `AssessmentReceipt` | `orderStatus` | Rule 3: thiếu example tường minh |
| `AssessmentReceipt` | `recordVersion` | Rule 3: thiếu example tường minh |
| `AssessmentReceipt` | `decision` | Rule 3: thiếu example tường minh |
| `AssessmentReceipt` | `assignments` | Rule 3: thiếu example tường minh |
| `BookingSectionResponse` | `code` | Rule 3: thiếu example tường minh |
| `BookingSectionResponse` | `title` | Rule 3: thiếu example tường minh |
| `BookingSectionResponse` | `options` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `assignmentId` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `staffId` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `role` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `assignedAt` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `endedAt` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `assignedBy` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `dueAt` | Rule 3: thiếu example tường minh |
| `CaseAssignmentResponse` | `assignmentNote` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `decisionId` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `revisionId` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `managerId` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `decision` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `reason` | Rule 3: thiếu example tường minh |
| `CaseDecisionResponse` | `decidedAt` | Rule 3: thiếu example tường minh |
| `CasePageResponse` | `items` | Rule 3: thiếu example tường minh |
| `CasePageResponse` | `page` | Rule 3: thiếu example tường minh |
| `CasePageResponse` | `size` | Rule 3: thiếu example tường minh |
| `CasePageResponse` | `totalElements` | Rule 3: thiếu example tường minh |
| `CasePageResponse` | `totalPages` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `orderNumber` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `orderStatus` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `recordVersion` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `currentRevisionId` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `revision` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `decision` | Rule 3: thiếu example tường minh |
| `CaseResponse` | `assignments` | Rule 3: thiếu example tường minh |
| `CaseStaffPageResponse` | `items` | Rule 3: thiếu example tường minh |
| `CaseStaffPageResponse` | `page` | Rule 3: thiếu example tường minh |
| `CaseStaffPageResponse` | `size` | Rule 3: thiếu example tường minh |
| `CaseStaffPageResponse` | `totalElements` | Rule 3: thiếu example tường minh |
| `CaseStaffPageResponse` | `totalPages` | Rule 3: thiếu example tường minh |
| `CaseStaffResponse` | `staffId` | Rule 3: thiếu example tường minh |
| `CaseStaffResponse` | `fullName` | Rule 3: thiếu example tường minh |
| `CaseStaffResponse` | `role` | Rule 3: thiếu example tường minh |
| `CaseStaffResponse` | `activeCases` | Rule 3: thiếu example tường minh |
| `CaseStaffResponse` | `availabilityStatus` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `orderNumber` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `orderStatus` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `recordVersion` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `currentRevisionId` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `originCountryCode` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `destinationCountryCode` | Rule 3: thiếu example tường minh |
| `CaseSummaryResponse` | `deliveryDeadline` | Rule 3: thiếu example tường minh |
| `CustomerAssessmentResponse` | `currentRevisionId` | Rule 3: thiếu example tường minh |
| `CustomerAssessmentResponse` | `decision` | Rule 3: thiếu example tường minh |
| `CustomerAssessmentResponse` | `reason` | Rule 3: thiếu example tường minh |
| `HorsePageResponse` | `items` | Rule 3: thiếu example tường minh |
| `HorsePageResponse` | `page` | Rule 3: thiếu example tường minh |
| `HorsePageResponse` | `size` | Rule 3: thiếu example tường minh |
| `HorsePageResponse` | `totalElements` | Rule 3: thiếu example tường minh |
| `HorsePageResponse` | `totalPages` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `horseId` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `name` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `microchipId` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `passportNumber` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `breed` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `dateOfBirth` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `sexCode` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `weightKg` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `heightCm` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `bodyWidthCm` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `measuredAt` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `specialCareNotes` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `handlingNotes` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `feedWaterRequirements` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `stallPreferences` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `veterinaryInstructions` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `veterinaryInstructionSource` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `recordVersion` | Rule 3: thiếu example tường minh |
| `HorseResponse` | `measurementsComplete` | Rule 3: thiếu example tường minh |
| `OrderChangeResponse` | `changeRequestId` | Rule 3: thiếu example tường minh |
| `OrderChangeResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `OrderChangeResponse` | `baseRevisionId` | Rule 3: thiếu example tường minh |
| `OrderChangeResponse` | `status` | Rule 3: thiếu example tường minh |
| `OrderChangeResponse` | `createdAt` | Rule 3: thiếu example tường minh |
| `OrderPageResponse` | `items` | Rule 3: thiếu example tường minh |
| `OrderPageResponse` | `page` | Rule 3: thiếu example tường minh |
| `OrderPageResponse` | `size` | Rule 3: thiếu example tường minh |
| `OrderPageResponse` | `totalElements` | Rule 3: thiếu example tường minh |
| `OrderPageResponse` | `totalPages` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `orderNumber` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `orderStatus` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `currency` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `recordVersion` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `currentRevisionId` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `currentPlanId` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `createdAt` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `updatedAt` | Rule 3: thiếu example tường minh |
| `OrderResponse` | `draft` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `revisionId` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `revisionNo` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `changeReason` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `submittedAt` | Rule 3: thiếu example tường minh |
| `OrderRevisionResponse` | `snapshot` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `schemaVersion` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `operationId` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `operationType` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `requestHash` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `sourceOrderVersion` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `orderId` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `revisionNo` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `submittedAt` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `request` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `horse` | Rule 3: thiếu example tường minh |
| `OrderSnapshot` | `services` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `code` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `uiSection` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `costGroup` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `label` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `mandatory` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `selected` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `unit` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `quantity` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `unitRate` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `amount` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `pricingStatus` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `tariffId` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `tariffVersion` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `formula` | Rule 3: thiếu example tường minh |
| `ServiceChargeResponse` | `blockers` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationResponse` | `order` | Rule 3: thiếu example tường minh |
| `ServiceConfirmationResponse` | `revision` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `orderId` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `recordVersion` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `revisionId` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `horseRecordVersion` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `asOf` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `currency` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `selected` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `sections` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `airStalls` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `quarantine` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `charges` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `knownSubtotal` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `totalEstimate` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `payable` | Rule 3: thiếu example tường minh |
| `ServiceDisclosureResponse` | `carrierConfirmation` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `serviceCode` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `optionCode` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `arrangedBy` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `requestedInputs` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `providerName` | Rule 3: thiếu example tường minh |
| `OrderServiceIntent` | `providerContact` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `option` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `slots` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `status` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `policyId` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `policyVersion` | Rule 3: thiếu example tường minh |
| `StallFitResult` | `reasons` | Rule 3: thiếu example tường minh |
| `QuarantineAssessmentResult` | `applicability` | Rule 3: thiếu example tường minh |
| `QuarantineAssessmentResult` | `requiredDays` | Rule 3: thiếu example tường minh |
| `QuarantineAssessmentResult` | `assessmentId` | Rule 3: thiếu example tường minh |
| `OrderServicePreferences` | `declaredValue` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderServicePreferences` | `roadProviderContact` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderServicePreferences` | `veterinaryProviderContact` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `AssessmentReceipt` | `orderStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `AssessmentReceipt` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `CaseAssignmentResponse` | `role` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseDecisionResponse` | `decision` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseResponse` | `orderStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseResponse` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `CaseStaffResponse` | `role` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseStaffResponse` | `availabilityStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseSummaryResponse` | `orderStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `CaseSummaryResponse` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `CaseSummaryResponse` | `originCountryCode` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `CaseSummaryResponse` | `destinationCountryCode` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `CustomerAssessmentResponse` | `decision` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `HorseResponse` | `microchipId` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `HorseResponse` | `passportNumber` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `HorseResponse` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderChangeResponse` | `status` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderResponse` | `orderStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderResponse` | `currency` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderResponse` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderSnapshot` | `operationType` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderSnapshot` | `requestHash` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `OrderSnapshot` | `sourceOrderVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceChargeResponse` | `uiSection` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `ServiceChargeResponse` | `costGroup` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `ServiceChargeResponse` | `unit` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `ServiceChargeResponse` | `unitRate` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceChargeResponse` | `amount` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceChargeResponse` | `pricingStatus` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `ServiceDisclosureResponse` | `recordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceDisclosureResponse` | `horseRecordVersion` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceDisclosureResponse` | `currency` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceDisclosureResponse` | `knownSubtotal` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceDisclosureResponse` | `totalEstimate` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `ServiceDisclosureResponse` | `carrierConfirmation` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderServiceIntent` | `optionCode` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderServiceIntent` | `arrangedBy` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `OrderServiceIntent` | `requestedInputs` | Rule 1: Map lộ additionalProperties/additionalProp1 |
| `OrderServiceIntent` | `providerContact` | Rule 1/2: thiếu format/pattern tương ứng ở schema |
| `StallFitResult` | `option` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `StallFitResult` | `status` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |
| `QuarantineAssessmentResult` | `applicability` | Mã hữu hạn đang dùng String và thiếu enum trong OpenAPI |

## Quyết định triển khai

- Không đổi tên/property JSON, không rewrite snapshots/DB. Mã String hữu hạn khai báo enum OpenAPI theo codes thật; không invent AVAILABLE/BUSY khi thực tế NOT_CONFIGURED.
- requestedInputs dùng schema DTO tường minh cho toàn bộ key version1, giữ Map nội bộ để snapshot lịch sử không mất key hoặc xuất thêm null fields.
- Receiver/event/provider optional cả draft/submit; supplied values không rỗng và đúng format. Preferred departure/import type bắt buộc khi submit theo quyết định người dùng.
- Draft partial chưa chọn horse vẫn là draft; khi submit phải có đúng một horse. Bản skill mới không buộc fields lịch trình ở draft.
- Thay đổi validation có thể trả400 cho client cũ dùng country/airport lowercase, UTC shortcut, phone free text, chip/passport sai định dạng hoặc empty optional fields.
- Monetary values giữ decimal strings; quantity precision sẽ theo quyết định riêng của người dùng, không làm tròn thay công thức phí.

## Kết quả refactor

- Examples tường minh trên mọi field đã quét và schema requestedInputs mới;
  examples cấp command kế thừa chứa đủ recordVersion/operationId/changeReason.
- Pattern request và response dùng ApiPatterns: 16-hex version, uppercase ISO/IATA,
  IANA timezone, phone E.164, microchip/passport; email chỉ dùng @Email/format email.
- Mã trạng thái giữ dạng JSON string nhưng là enum trong OpenAPI qua allowableValues.
  Đây là refactor hợp đồng schema, không đổi persistence codes hoặc deserialize
  lịch sử thành Java Enum không biết các giá trị legacy.
- requestedInputs tham chiếu RequestedServiceInputsSchema, additionalProperties=false;
  Map nội bộ giữ nguyên để không rewrite stored snapshot hoặc thêm null keys.
- OptionalText cho phép null nhưng từ chối chuỗi rỗng/whitespace; không dùng regex
  mở để kiểm blank. Receiver/event/provider không bị biến thành required khi submit.
- Submit thêm preferredDepartureAt và importType bắt buộc; các trường định tuyến
  cũ (horse/country/address/timezone/deadline) vẫn phải đủ. Draft partial vẫn được lưu.
- quantity SLOT_KM có thể có3decimals do slot1.5×AIRkm2decimals: schema phản ánh tối
  đa3, không đổi phép nhân hoặc làm tròn quantity; money/rate/subtotal tối đa2.
  Đây là ngoại lệ kỹ thuật giữ phép tính hiện có, không claim người dùng đã đổi
  quy tắc tính phí. Các mức giá ví dụ chỉ là documentation, không runtime defaults.
- Sáu controller thuộc scope khai báo produces application/json. Auth/customers/
  health DTO không nằm trong lần audit này; không claim toàn repository đã sạch.

## Compatibility và kiểm thử

Input cũ dùng lowercase country/airport, timezone UTC shorthand, phone/contact
free-text, invalid chip/passport hoặc empty optional strings sẽ bị400. Core fields
mới phải có khi submit; FE cần bổ sung preferredDepartureAt và importType. Không
backfill/xóa/cập nhật database hoặc snapshots. Dữ liệu legacy sai format cần sửa
có chủ đích ở lần edit, không silently xóa trường chỉ để vượt validation.

Evidence và PR/CI thực tế được ghi trong PR; generated OpenAPI được kiểm ví dụ,
regex, UUID, enum, root required fields, array items và media type. Native SQL
regression dùng database test riêng, không fixture writes trên EquiTransit thật.
