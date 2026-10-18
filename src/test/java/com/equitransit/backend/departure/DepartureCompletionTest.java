package com.equitransit.backend.departure;

import com.equitransit.backend.dto.departure.DepartureModels.DepartureAuthorizeRequest;
import com.equitransit.backend.dto.departure.DepartureModels.DepartureCommand;
import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptView;
import com.equitransit.backend.dto.departure.DepartureModels.HorseReceiptRequest;
import com.equitransit.backend.dto.departure.DepartureModels.IdentityResult;
import com.equitransit.backend.dto.departure.DepartureModels.LegDepartRequest;
import com.equitransit.backend.dto.departure.DepartureModels.ReceiptState;
import com.equitransit.backend.dto.plan.PlanModels.AssignmentState;
import com.equitransit.backend.dto.plan.PlanModels.GateState;
import com.equitransit.backend.dto.plan.PlanModels.LegState;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegMode;
import com.equitransit.backend.dto.plan.PlanModels.PlanLegView;
import com.equitransit.backend.dto.plan.PlanModels.PlanState;
import com.equitransit.backend.dto.plan.PlanModels.TripRole;
import com.equitransit.backend.dto.plan.PlanModels.PlanTripAssignmentView;
import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.dto.order.request.OrderDraftRequest;
import com.equitransit.backend.dto.order.response.OrderSnapshot;
import com.equitransit.backend.entity.assessment.CaseOrder;
import com.equitransit.backend.entity.order.OrderRevisionRecord;
import com.equitransit.backend.entity.pricing.QuarantineAssessmentResult;
import com.equitransit.backend.entity.quote.CommercialReadiness;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.departure.DepartureRepository;
import com.equitransit.backend.repository.departure.DepartureAuditRepository;
import com.equitransit.backend.repository.departure.DepartureRepository.FirstLegRow;
import com.equitransit.backend.repository.departure.DepartureRepository.HorseIdentityRow;
import com.equitransit.backend.repository.departure.DepartureRepository.OrderScopeRow;
import com.equitransit.backend.repository.departure.DepartureRepository.PlanGateRow;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.departure.DepartureEvidenceStorage;
import com.equitransit.backend.service.departure.DepartureFinancialAdapter;
import com.equitransit.backend.service.departure.DepartureGateService;
import com.equitransit.backend.service.departure.DepartureLegalAdapter;
import com.equitransit.backend.service.pricing.ServiceAssessmentFacade;
import com.equitransit.backend.service.pricing.StallFitEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.equitransit.backend.dto.plan.PlanModels.CarrierAcceptanceState;
import com.equitransit.backend.dto.plan.PlanModels.PlanFlightReservationView;
import com.equitransit.backend.dto.plan.PlanModels.PlanStallAssignmentView;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.dto.plan.PlanModels.StallSharing;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.horse.response.HorseResponse;
import com.equitransit.backend.dto.order.request.OrderServicePreferences;
import com.equitransit.backend.entity.order.AirStallPreference;
import com.equitransit.backend.entity.pricing.FitPolicyRecord;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** F11 orchestration, policy and disk storage with explicit BE boundaries. */
public class DepartureCompletionTest {

    private static final String VERSION = "0000000000000001";
    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");
    private Path directory;
    private DepartureRepository departures;
    private PlanRepository plans;
    private AssessmentRepository cases;
    private ResourceRepository resources;
    private DepartureFinancialAdapter financial;
    private DepartureLegalAdapter legal;
    private DepartureAuditRepository audit;
    private CurrentActorService actors;
    private DepartureEvidenceStorage storage;
    private DepartureGateService service;
    private PlanGateRow plan;
    private FirstLegRow first;
    private HorseReceiptView receipt;
    private CommercialReadiness commercial;
    private OrderSnapshot snapshot;
    private OrderDraftRequest request;
    private String key;

    /** Current revision, sourced ROAD booking and real stored photo. */
    @BeforeEach
    public void setup() throws Exception {
        directory = java.nio.file.Files.createDirectories(Path.of(
                "target", "departure-tests", UUID.randomUUID().toString()));
        departures = mock(DepartureRepository.class);
        plans = mock(PlanRepository.class);
        cases = mock(AssessmentRepository.class);
        resources = mock(ResourceRepository.class);
        financial = mock(DepartureFinancialAdapter.class);
        legal = mock(DepartureLegalAdapter.class);
        audit = mock(DepartureAuditRepository.class);
        actors = mock(CurrentActorService.class);
        var orders = mock(OrderRepository.class);
        var codec = mock(OrderJsonCodec.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        snapshot = mock(OrderSnapshot.class);
        when(snapshot.orderId()).thenReturn(101L);
        var captured = mock(HorseResponse.class);
        when(captured.horseId()).thenReturn(61L);
        when(captured.microchipId()).thenReturn("CHIP");
        when(captured.passportNumber()).thenReturn("PASS");
        when(snapshot.horse()).thenReturn(captured);
        request = mock(OrderDraftRequest.class);
        var revision = mock(OrderRevisionRecord.class);
        var assessments = mock(ServiceAssessmentFacade.class);
        storage = new DepartureEvidenceStorage(directory.toString());
        key = storage.store(101, 311, 401, 41, image(), now).fileKey();
        receipt = new HorseReceiptView(701, 311, 401L, 31L, "CHIP", "PASS",
                IdentityResult.MATCHED, ReceiptState.ACCEPTED, "Healthy",
                key, "Stable A", now);
        plan = plan(PlanState.CONFIRMED, 32L);
        first = new FirstLegRow(401, 311, 1, PlanLegMode.ROAD,
                LegState.CONFIRMED, now.minusHours(1), now.plusHours(2), null);
        when(departures.ready()).thenReturn(true);
        when(plans.ready()).thenReturn(true);
        when(resources.ready()).thenReturn(true);
        when(departures.plan(eq(101L), eq(311L), anyBoolean()))
                .thenAnswer(i -> Optional.of(plan));
        when(departures.firstLeg(311)).thenAnswer(i -> Optional.of(first));
        when(departures.orderScope(101)).thenReturn(Optional.of(
                new OrderScopeRow(101, 51, 201L, 311L)));
        when(departures.receipts(311)).thenAnswer(i -> List.of(receipt));
        when(departures.horseIdentity(eq(101L), anyBoolean()))
                .thenReturn(Optional.of(new HorseIdentityRow(
                        61, "CHIP", "CHIP", "PASS")));
        when(departures.assignedDriverOnLeg(311, 401, 31)).thenReturn(true);
        when(departures.assignedDriverOnPlan(311, 31)).thenReturn(true);
        when(departures.legDriversActive(401)).thenReturn(true);
        when(departures.driverAssignedAt(401, 31, now)).thenReturn(true);
        when(departures.driverAssignedAt(401, 0, now)).thenReturn(true);
        when(cases.order(eq(101L), anyBoolean())).thenReturn(Optional.of(
                new CaseOrder(101, "EQ101", "CONFIRMED", VERSION, 201L)));
        when(cases.activeStaff(anyLong(), anyString())).thenReturn(true);
        when(actors.current()).thenReturn(
                new CurrentActor(41L, "DRIVER", null, 31L));
        when(orders.revision(101, 201)).thenReturn(Optional.of(revision));
        when(revision.snapshot()).thenReturn("snapshot");
        when(codec.snapshot("snapshot")).thenReturn(snapshot);
        when(snapshot.request()).thenReturn(request);
        when(request.getDeliveryDeadline()).thenReturn(now.plusDays(2));
        when(codec.canonical(any())).thenAnswer(i -> mapper.writeValueAsString(
                i.getArgument(0)));
        when(codec.write(any())).thenAnswer(i -> mapper.writeValueAsString(
                i.getArgument(0)));
        when(assessments.quarantine(anyLong(), any(), any(), any()))
                .thenReturn(new QuarantineAssessmentResult(
                        "NOT_REQUIRED", null, 9L));
        commercial = mock(CommercialReadiness.class);
        when(financial.readiness(101, 201, 501, now)).thenReturn(commercial);
        when(commercial.departureBlockers()).thenReturn(List.of());
        when(legal.blockers(101, 201, now)).thenReturn(List.of());
        when(plans.distanceSourceValid(101, 201, 33)).thenReturn(true);
        var staff = new PlanTripAssignmentView(801, 401, 31, TripRole.DRIVER,
                now.minusHours(1), now.plusHours(2), AssignmentState.CONFIRMED,
                0, null);
        var leg = new PlanLegView(401, 311, 11L, 1, PlanLegMode.ROAD,
                "A", "B", BigDecimal.TEN, now.minusHours(1), now.plusHours(2),
                LegState.CONFIRMED, "UTC", "UTC", null, null, 0, 0, null,
                "verified", 33L, now.minusDays(1), List.of(staff), List.of(),
                List.of());
        when(plans.legs(311)).thenReturn(List.of(leg));
        var vehicle = mock(ResourceVehicleView.class);
        var data = mock(ResourceVehicleData.class);
        var window = mock(ResourceWindow.class);
        when(resources.get(ResourceKind.VEHICLE, 11,
                ResourceVehicleView.class)).thenReturn(vehicle);
        when(vehicle.data()).thenReturn(data);
        when(data.status()).thenReturn(AssetState.ACTIVE);
        when(data.horseCapacity()).thenReturn(1);
        when(data.window()).thenReturn(window);
        when(window.bufferMinutes()).thenReturn(0);
        when(window.documentExpiresAt()).thenReturn(now.plusDays(10));
        when(resources.activeSupplier(any())).thenReturn(true);
        service = new DepartureGateService(departures, plans, cases, orders,
                resources, financial, legal, assessments, actors, audit, codec,
                mapper, Clock.fixed(now.toInstant(), ZoneOffset.UTC), storage,
                new StallFitEvaluator());
    }

    /** Happy path uses all three live gates and real image evidence. */
    @Test
    public void driverDepartsAndWritesReceiptAndAudit() {
        doAnswer(i -> {
            plan = plan(PlanState.IN_TRANSIT, 32L);
            first = new FirstLegRow(401, 311, 1, PlanLegMode.ROAD,
                    LegState.IN_TRANSIT, now.minusHours(1), now.plusHours(2),
                    now);
            return null;
        }).when(departures).departFirstLeg(101, 311, 401, now);
        var result = service.depart(101, depart());
        verify(departures).departFirstLeg(101, 311, 401, now);
        verify(plans).saveOperation(eq(101L), eq(41L), any(), anyString(),
                anyString());
        verify(audit).append(eq(101L), eq(41L), eq(311L),
                eq("DEPART_FIRST_LEG"), any(), eq(now));
        assertThat(result.readyForDeparture()).isFalse();
    }

    /** A bank-transfer report alone does not authorize departure. */
    @Test
    public void unreconciledPaymentBlocksDeparture() {
        when(commercial.departureBlockers())
                .thenReturn(List.of("NO_RECONCILED_RECEIPT"));
        assertBlocked("NO_RECONCILED_RECEIPT");
    }

    /** Expired legal evidence is rechecked despite a previous authorization. */
    @Test
    public void revokedClearanceBlocksDeparture() {
        when(legal.blockers(101, 201, now))
                .thenReturn(List.of("PRE_EXPORT_NOT_CLEARED"));
        assertBlocked("PRE_EXPORT_NOT_CLEARED");
    }

    /** Maintenance created after signing closes the operational gate. */
    @Test
    public void resourceConflictBlocksDeparture() {
        when(resources.scheduleBlockers(eq(ResourceKind.VEHICLE), eq(11L),
                any(), any(), eq(0), eq(401L)))
                .thenReturn(List.of(ResourceBlocker.OVERLAP));
        assertBlocked("ROAD_VEHICLE_UNAVAILABLE");
    }

    /** Ended driver assignment cannot start the trip. */
    @Test
    public void assignmentOutsideActualWindowBlocksDeparture() {
        when(departures.driverAssignedAt(401, 31, now)).thenReturn(false);
        assertBlocked("driver's assignment");
    }

    /** Manager revocation takes effect immediately even while gates pass. */
    @Test
    public void missingAuthorizationBlocksDeparture() {
        plan = plan(PlanState.CONFIRMED, null);
        assertBlocked("active MANAGER authorization");
    }

    /** A valid older receipt never overrides a newer receipt. */
    @Test
    public void oldReceiptCannotStartTrip() {
        when(departures.receipts(311)).thenReturn(List.of(new HorseReceiptView(
                702, 311, 401L, 31L, "BAD", "PASS", IdentityResult.MISMATCHED,
                ReceiptState.REJECTED, "Mismatch", key, "Stable A", now),
                receipt));
        assertBlocked("latest horse receipt");
    }

    /** Stored key syntax cannot replace real evidence bytes. */
    @Test
    public void missingEvidenceBlocksDeparture() throws Exception {
        java.nio.file.Files.delete(directory.resolve(key + ".evidence"));
        assertBlocked("valid scoped PNG/JPEG");
    }

    /** A completed start cannot be executed again under a new command. */
    @Test
    public void alreadyStartedPlanCannotDepartAgain() {
        plan = plan(PlanState.IN_TRANSIT, 32L);
        assertBlocked("unstarted CONFIRMED");
    }

    /** SQL infrastructure failure propagates; it is not converted into 409. */
    @Test
    public void databaseFailurePropagates() {
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(departures).departFirstLeg(101, 311, 401, now);
        assertThatThrownBy(() -> service.depart(101, depart()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(audit);
    }

    /** Manager explicitly revokes authorization and appends an audit fact. */
    @Test
    public void managerCanRevokeBeforeDeparture() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "MANAGER", null, 32L));
        service.revoke(101, new DepartureAuthorizeRequest(command(), 311));
        verify(departures).revoke(101, 311);
        verify(audit).append(eq(101L), eq(42L), eq(311L),
                eq("REVOKE_DEPARTURE"), any(), eq(now));
    }

    /** Coordinator has no Manager revocation privilege. */
    @Test
    public void driverCannotRevokeAuthorization() {
        assertThatThrownBy(() -> service.revoke(101,
                new DepartureAuthorizeRequest(command(), 311)))
                .isInstanceOf(org.springframework.security.access.
                        AccessDeniedException.class);
        verify(departures, never()).revoke(anyLong(), anyLong());
    }

    /** Customer owns the case but may never read another case's receipt. */
    @Test
    public void fileDownloadMustBeBoundToRecordedReceipt() {
        when(actors.current()).thenReturn(
                new CurrentActor(43L, "CUSTOMER", 51L, null));
        assertThatThrownBy(() -> service.receiptFile(101, 999))
                .hasMessageContaining("not found");
    }

    /** Customer can retrieve the actual recorded receipt image. */
    @Test
    public void ownerCanDownloadReceiptPhoto() throws Exception {
        when(actors.current()).thenReturn(
                new CurrentActor(43L, "CUSTOMER", 51L, null));
        assertThat(service.receiptFile(101, 701)).isEqualTo(image());
    }

    /** A valid real-photo pickup records both observed identifiers. */
    @Test
    public void assignedDriverRecordsRealPhotoReceipt() {
        service.recordHorseReceipt(101, receiptRequest("CHIP", key));
        verify(departures).insertHorseReceipt(eq(311L), eq(401L), eq(31L),
                any(), eq(IdentityResult.MATCHED), eq(ReceiptState.ACCEPTED),
                eq(now));
    }

    /** A mismatched horse is recorded as rejected. */
    @Test
    public void wrongHorseRecordsRejectedReceipt() {
        service.recordHorseReceipt(101, receiptRequest("OTHER", key));
        verify(departures).insertHorseReceipt(eq(311L), eq(401L), eq(31L),
                any(), eq(IdentityResult.MISMATCHED), eq(ReceiptState.REJECTED),
                eq(now));
    }

    /** An arbitrary plausible UUID cannot be used as receipt evidence. */
    @Test
    public void fabricatedKeyCannotCreateReceipt() {
        assertThatThrownBy(() -> service.recordHorseReceipt(101,
                receiptRequest("CHIP", UUID.randomUUID().toString())))
                .hasMessageContaining("valid scoped");
        verify(departures, never()).insertHorseReceipt(anyLong(), anyLong(),
                anyLong(), any(), any(), any(), any());
    }

    /** Manager may sign only after current financial and legal checks pass. */
    @Test
    public void managerAuthorizesOnlyAfterLiveChecks() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "MANAGER", null, 32L));
        service.authorize(101, new DepartureAuthorizeRequest(command(), 311));
        verify(financial, atLeastOnce()).readiness(101, 201, 501, now);
        verify(legal, atLeastOnce()).blockers(101, 201, now);
        verify(departures).authorizeDeparture(101, 311, 32, now);
    }

    /** Actor privilege never overrides unpaid money. */
    @Test
    public void managerCannotBypassFinancialGate() {
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "MANAGER", null, 32L));
        when(commercial.departureBlockers())
                .thenReturn(List.of("NO_RECONCILED_RECEIPT"));
        assertThatThrownBy(() -> service.authorize(101,
                new DepartureAuthorizeRequest(command(), 311)))
                .hasMessageContaining("NO_RECONCILED_RECEIPT");
        verify(departures, never()).authorizeDeparture(anyLong(), anyLong(),
                anyLong(), any());
    }

    /** A successful retry returns the original receipt. */
    @Test
    public void departRetryReplaysWithoutAnotherTransition() {
        var request = depart();
        service.depart(101, request);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(plans).saveOperation(eq(101L), eq(41L),
                eq(request.command().operationId()), hash.capture(),
                json.capture());
        when(plans.operation(101, 41, request.command().operationId()))
                .thenReturn(List.of(java.util.Map.of("RequestHash",
                        hash.getValue(), "ReceiptJSON", json.getValue())));
        service.depart(101, request);
        verify(departures).departFirstLeg(101, 311, 401, now);
    }

    private HorseReceiptRequest receiptRequest(String chip, String file) {
        return new HorseReceiptRequest(command(), 311, 401, chip, "PASS", true,
                "Healthy", file, "Stable A");
    }

    /** An approved sourced fit policy permits an otherwise valid AIR leg. */
    @Test
    public void eligibleAirFitPassesOperationalGate() {
        useAirPlan();
        assertThat(service.getReadiness(101).operationalGate().blockers())
                .isEmpty();
    }

    /** Removing a reviewed fit source closes the live gate. */
    @Test
    public void missingAirFitSourceClosesGate() {
        useAirPlan();
        when(resources.policies(eq(12L), eq(21L), any(), any(), any(), any()))
                .thenReturn(List.of());
        assertThat(service.getReadiness(101).operationalGate().blockers())
                .contains("AIR_FIT_INVALID");
    }

    /** Expired or changed supplier-offer provenance cannot be bypassed. */
    @Test
    public void invalidFlightSourceClosesGate() {
        useAirPlan();
        when(plans.flightSourceValid(311, 201, 402, 901)).thenReturn(false);
        assertThat(service.getReadiness(101).operationalGate().blockers())
                .contains("AIR_FLIGHT_SOURCE_INVALID");
    }

    private void useAirPlan() {
        var departure = now.plusHours(2);
        var arrival = now.plusHours(6);
        plan = new PlanGateRow(311, 101, 201, 1, PlanState.CONFIRMED, 501L,
                now.minusHours(1), arrival, GateState.PASS, GateState.PASS,
                GateState.PASS, now, 32L, now.minusMinutes(5));
        var stall = new PlanStallAssignmentView(801, 402, 12,
                BigDecimal.valueOf(3), StallSharing.PRIVATE, "SUITABLE",
                CarrierAcceptanceState.ACCEPTED, departure, arrival,
                AssignmentState.CONFIRMED, null, "carrier.pdf", now, null);
        var flight = new PlanFlightReservationView(901, 402, 601, "Airline",
                "VN51", "REF1", ReservationState.CONFIRMED, now,
                "ticket.pdf", now.plusDays(1), 33L, null,
                BigDecimal.valueOf(3));
        var air = new PlanLegView(402, 311, null, 2, PlanLegMode.AIR, "B", "C",
                BigDecimal.valueOf(1000), departure, arrival,
                LegState.CONFIRMED, "UTC", "UTC", 21L, "VN51", 0, 0,
                null, "verified route", 33L, now, List.of(), List.of(stall),
                List.of(flight));
        var road = plans.legs(311).getFirst();
        when(plans.legs(311)).thenReturn(List.of(road, air));
        when(plans.flightSourceValid(311, 201, 402, 901)).thenReturn(true);
        var choices = mock(OrderServicePreferences.class);
        when(request.getServices()).thenReturn(choices);
        when(choices.getAirStall()).thenReturn(AirStallPreference.PRIVATE);
        var horse = mock(HorseResponse.class);
        when(horse.horseId()).thenReturn(61L);
        when(horse.microchipId()).thenReturn("CHIP");
        when(horse.passportNumber()).thenReturn("PASS");
        when(snapshot.horse()).thenReturn(horse);
        when(horse.measurementsComplete()).thenReturn(true);
        when(horse.measuredAt()).thenReturn(now.minusDays(1));
        when(horse.weightKg()).thenReturn(BigDecimal.valueOf(500));
        when(horse.heightCm()).thenReturn(BigDecimal.valueOf(170));
        when(horse.bodyWidthCm()).thenReturn(BigDecimal.valueOf(80));
        var equipment = mock(ResourceStallView.class);
        var data = mock(ResourceStallData.class);
        var window = mock(ResourceWindow.class);
        when(resources.get(ResourceKind.STALL, 12, ResourceStallView.class))
                .thenReturn(equipment);
        when(equipment.data()).thenReturn(data);
        when(data.status()).thenReturn(AssetState.ACTIVE);
        when(data.capacitySlots()).thenReturn(BigDecimal.valueOf(3));
        when(data.weightLimitKg()).thenReturn(BigDecimal.valueOf(900));
        when(data.internalHeightCm()).thenReturn(BigDecimal.valueOf(250));
        when(data.internalWidthCm()).thenReturn(BigDecimal.valueOf(250));
        when(data.window()).thenReturn(window);
        when(window.bufferMinutes()).thenReturn(0);
        when(window.documentExpiresAt()).thenReturn(now.plusDays(10));
        when(resources.policies(eq(12L), eq(21L), any(), any(), any(), any()))
                .thenReturn(List.of(new FitPolicyRecord(1, "CARRIER", 1,
                        BigDecimal.valueOf(3), BigDecimal.valueOf(650),
                        BigDecimal.valueOf(200), BigDecimal.valueOf(100))));
    }

    /** A changed master passport cannot replace the approved revision. */
    @Test
    public void changedMasterPassportCannotProduceAcceptedReceipt() {
        liveIdentity(61, "CHIP", "CHIP", "NEW-PASSPORT");
        var request = new HorseReceiptRequest(command(), 311, 401, "CHIP",
                "NEW-PASSPORT", true, "Healthy", key, "Stable A");
        var result = service.recordHorseReceipt(101, request);
        verify(departures).insertHorseReceipt(eq(311L), eq(401L), eq(31L),
                any(), eq(IdentityResult.PENDING), eq(ReceiptState.REJECTED),
                any());
        assertThat(result.readyForDeparture()).isFalse();
    }

    /** Old gate approvals cannot authorize departure under a new passport. */
    @Test
    public void changedMasterPassportBlocksDeparture() {
        liveIdentity(61, "CHIP", "CHIP", "NEW-PASSPORT");
        receipt = new HorseReceiptView(701, 311, 401L, 31L, "CHIP",
                "NEW-PASSPORT", IdentityResult.MATCHED, ReceiptState.ACCEPTED,
                "Healthy", key, "Stable A", now);
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** Manager privilege does not bypass identity drift. */
    @Test
    public void managerCannotAuthorizeChangedIdentity() {
        liveIdentity(61, "CHIP", "CHIP", "NEW-PASSPORT");
        when(actors.current()).thenReturn(
                new CurrentActor(42L, "MANAGER", null, 32L));
        assertThatThrownBy(() -> service.authorize(101,
                new DepartureAuthorizeRequest(command(), 311)))
                .hasMessageContaining("HORSE_IDENTITY_REVISION_MISMATCH");
        verify(departures, never()).authorizeDeparture(anyLong(), anyLong(),
                anyLong(), any());
    }

    /** A changed master chip cannot be masked by the detail's older chip. */
    @Test
    public void changedMasterChipClosesReadiness() {
        liveIdentity(61, "CHIP", "NEW-CHIP", "PASS");
        assertThat(service.getReadiness(101).readyForAuthorization()).isFalse();
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** A snapshot is mandatory even if the current master profile is valid. */
    @Test
    public void absentCapturedHorseDoesNotFallBackToMaster() {
        when(snapshot.horse()).thenReturn(null);
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** An incomplete captured identity cannot be filled from mutable master. */
    @Test
    public void missingCapturedPassportDoesNotFallBackToMaster() {
        when(snapshot.horse().passportNumber()).thenReturn(null);
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** The captured horse must be the horse belonging to this order. */
    @Test
    public void wrongCapturedHorseClosesGate() {
        when(snapshot.horse().horseId()).thenReturn(999L);
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** An unrelated order snapshot never supplies pickup identifiers. */
    @Test
    public void wrongSnapshotOrderClosesGate() {
        when(snapshot.orderId()).thenReturn(999L);
        assertBlocked("HORSE_IDENTITY_REVISION_MISMATCH");
    }

    /** Formatting normalization remains compatible with receipt comparison. */
    @Test
    public void matchingIdentityWithWhitespaceAndCaseRemainsValid() {
        liveIdentity(61, " chip ", " chip ", " pass ");
        service.recordHorseReceipt(101, receiptRequest("chip", key));
        verify(departures).insertHorseReceipt(eq(311L), eq(401L), eq(31L),
                any(), eq(IdentityResult.MATCHED), eq(ReceiptState.ACCEPTED),
                any());
    }

    private void liveIdentity(long horse, String detailChip, String chip,
            String passport) {
        when(departures.horseIdentity(eq(101L), anyBoolean()))
                .thenReturn(Optional.of(new HorseIdentityRow(
                        horse, detailChip, chip, passport)));
    }

    private void assertBlocked(String message) {
        assertThatThrownBy(() -> service.depart(101, depart()))
                .hasMessageContaining(message);
        verify(departures, never()).departFirstLeg(anyLong(), anyLong(),
                anyLong(), any());
    }

    private DepartureCommand command() {
        return new DepartureCommand(UUID.randomUUID(), VERSION);
    }

    private LegDepartRequest depart() {
        return new LegDepartRequest(command(), 311, 401, 701);
    }

    private PlanGateRow plan(PlanState state, Long manager) {
        return new PlanGateRow(311, 101, 201, 1, state, 501L,
                now.minusHours(1), now.plusHours(2), GateState.PASS,
                GateState.PASS, GateState.PASS, now, manager,
                manager == null ? null : now.minusMinutes(5));
    }

    static byte[] image() throws Exception {
        var stream = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
                "png", stream);
        return stream.toByteArray();
    }
}

