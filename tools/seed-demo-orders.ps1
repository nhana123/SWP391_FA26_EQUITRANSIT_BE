<# Normal business API transitions against an isolated, freshly seeded Demo DB.
Secrets stay in memory. All sources/evidence are explicitly synthetic DEMO.
#>
[CmdletBinding()]
param([string]$Server = 'localhost,1433', [switch]$Continue)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local/demo'
$base = 'http://127.0.0.1:8080'
$tokens = @{}
function Api([string]$Role,[string]$Method,[string]$Path,$Body = $null) {
    $args = @{Uri=$base+$Path;Method=$Method;TimeoutSec=30
        Headers=@{Authorization='Bearer '+$tokens[$Role]}}
    if ($null -ne $Body) {
        $args.ContentType='application/json'
        $args.Body=$Body | ConvertTo-Json -Depth 30 -Compress
    }
    try { Invoke-RestMethod @args }
    catch { throw "$Role $Method $Path : $($_.ErrorDetails.Message)" }
}
$connection = [Data.SqlClient.SqlConnection]::new(
    "Server=$Server;Database=EquiTransit_Demo;Integrated Security=True;" +
    'Encrypt=True;TrustServerCertificate=True')
$connection.Open()
function Sql([string]$Text,[hashtable]$Parameters=@{}) {
    $cmd=$connection.CreateCommand()
    $cmd.CommandText="IF DB_NAME()<>'EquiTransit_Demo' THROW 51000," +
        "'Wrong demo catalog',1; SET QUOTED_IDENTIFIER ON;" +
        'SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON;'+$Text
    foreach($key in $Parameters.Keys) {
        [void]$cmd.Parameters.AddWithValue('@'+$key,$Parameters[$key])
    }
    $table=[Data.DataTable]::new()
    $adapter=[Data.SqlClient.SqlDataAdapter]::new($cmd)
    try { [void]$adapter.Fill($table); return ,$table }
    finally { $adapter.Dispose();$cmd.Dispose() }
}
function Command([long]$OrderId) {
    $row=(Sql 'SELECT SUBSTRING(sys.fn_varbintohexstr(RecordVersion),3,16) AS v
        FROM dbo.TransportOrder WHERE OrderID=@id' @{id=$OrderId}).Rows[0]
    @{recordVersion=$row.v;operationId=[guid]::NewGuid().ToString()}
}
function SaveState {
    $state|ConvertTo-Json -Depth 40|Set-Content (
        Join-Path $local 'seed-state.json') -Encoding utf8
}
# Readable, synthetic PNG evidence; never an actual certificate or statement.
Add-Type -AssemblyName System.Drawing
$bitmap=[Drawing.Bitmap]::new(1000,200)
$graphics=[Drawing.Graphics]::FromImage($bitmap)
$font=[Drawing.Font]::new('Arial',22)
$memory=[IO.MemoryStream]::new()
try {
    $graphics.Clear([Drawing.Color]::White)
    $graphics.DrawString('DEMO ONLY - NOT BANK OR LEGAL EVIDENCE',
        $font,[Drawing.Brushes]::Red,20,40)
    $graphics.DrawString('Synthetic fixture. No money was transferred.',
        $font,[Drawing.Brushes]::Black,20,100)
    $bitmap.Save($memory,[Drawing.Imaging.ImageFormat]::Png)
    $imageBase64=[Convert]::ToBase64String($memory.ToArray())
    [IO.File]::WriteAllBytes((Join-Path $local 'DEMO-bank-proof.png'),
        $memory.ToArray())
} finally {
    $font.Dispose();$graphics.Dispose();$bitmap.Dispose();$memory.Dispose()
}
$now=[DateTimeOffset]::UtcNow
$until=$now.AddDays(30).ToString('yyyy-MM-ddTHH:mm:ssZ')
$state=[ordered]@{seededAt=$now.ToString('o');accounts=@();orders=@()}
if ($Continue) {
    $state=Get-Content (Join-Path $local 'seed-state.json') -Raw |
        ConvertFrom-Json -AsHashtable
}
try {
    foreach($role in @('CUSTOMER','MANAGER','SPECIALIST','COORDINATOR','DRIVER')) {
        $login=Invoke-RestMethod "$base/api/auth/login" -Method Post `
            -ContentType application/json -Body (@{email=
                $role.ToLower()+'.demo@equitransit.com'
                password='EquiTransit@123'} | ConvertTo-Json)
        if ($login.role -ne $role) { throw "Wrong demo role: $role" }
        $tokens[$role]=$login.accessToken
    }
    $staff=(Sql "SELECT s.StaffID,r.RoleCode FROM dbo.StaffProfile s
        JOIN dbo.[User] u ON u.UserID=s.UserID
        JOIN dbo.Role r ON r.RoleID=u.RoleID").Rows
    $ids=@{}
    foreach($row in $staff) { $ids[$row.RoleCode]=[long]$row.StaffID }
    $horses=@((Sql 'SELECT HorseID,Name FROM dbo.Horse ORDER BY HorseID').Rows)
    $accounts=Sql 'SELECT u.UserID,u.Email,r.RoleCode,s.StaffID
        FROM dbo.[User] u JOIN dbo.Role r ON r.RoleID=u.RoleID
        LEFT JOIN dbo.StaffProfile s ON s.UserID=u.UserID ORDER BY u.UserID'
    foreach($row in $accounts.Rows) {
        $state.accounts+=@{userId=[long]$row.UserID;email=$row.Email
            role=$row.RoleCode;staffId=$(if($row.StaffID -ne [DBNull]::Value){
                [long]$row.StaffID}else{$null})}
    }
    if (-not $Continue -and
        (Sql 'SELECT COUNT(*) AS n FROM dbo.TransportOrder').Rows[0].n -gt 0) {
        throw 'Orders already exist; run the reset wrapper, not this helper.'
    }
    if (-not $Continue) {
    for($index=1;$index -le 5;$index++) {
        $horseIndex=if($index -eq 3){1}elseif($index -eq 4){2}else{0}
        $horseId=[long]$horses[$horseIndex].HorseID
        $stall=if($index -in @(3,4)){'WIDE'}else{'SHARED_ONE'}
        $services=@{roadArrangement='EQUITRANSIT'
            veterinaryArrangement='EQUITRANSIT';airStall=$stall
            enhancedCare=$false;groomingDays=0;boardingNights=0
            quarantinePackage='STANDARD';insurance='NONE'}
        if($index -in @(3,4)) {
            $services.roadArrangement='CUSTOMER'
            $services.roadProviderName='DEMO customer ground provider'
            $services.roadProviderContact='+84901234567'
        }
        $day=if($index -in @(3,4)){10}else{$index+1}
        $start=$now.AddDays($day)
        $draft=@{horseId=$horseId;originCountryCode='VN'
            destinationCountryCode='SG';originAddress='DEMO HCM Stable'
            destinationAddress='DEMO Singapore Stable'
            originTimezone='Asia/Ho_Chi_Minh'
            destinationTimezone='Asia/Singapore'
            preferredDepartureAt=$start.ToString('o')
            deliveryDeadline=$start.AddDays(2).ToString('o')
            importType='TEMPORARY';receiverName='DEMO Receiver'
            receiverPhone='+6590123456';receiverEmail='demo@example.invalid'
            receiverAuthority='DEMO authorized receiver';transitAirports=@()
            specialNeeds='DEMO synthetic order; never a real shipment'
            services=$services}
        $order=Api CUSTOMER POST '/api/transport-orders' $draft
        $id=[long]$order.orderId
        $number='DEMO-ORD-'+$index.ToString('00')
        $null=Sql 'UPDATE dbo.TransportOrder SET OrderNumber=@number
            WHERE OrderID=@id' @{number=$number;id=$id}
        $null=Api CUSTOMER POST "/api/transport-orders/$id/submit" (Command $id)
        $o=Api CUSTOMER GET "/api/transport-orders/$id"
        $decision=@{revisionId=$o.currentRevisionId
            recordVersion=$o.recordVersion;operationId=[guid]::NewGuid().ToString()
            decision='APPROVED';specialistId=$ids.SPECIALIST
            coordinatorId=$ids.COORDINATOR
            assignmentNote='DEMO rehearsal only'}
        $null=Api MANAGER POST "/api/manager/transport-orders/$id/decisions" $decision
        $assessment=@{command=(Command $id);applicability='REQUIRED'
            requiredDays=2;visitCount=1;testCount=1;doseCount=0;certificateCount=1
            sourceReference='DEMO synthetic specialist assessment, not law'
            validUntil=$until}
        $null=Api SPECIALIST POST "/api/transport-orders/$id/compliance/assessments" $assessment
        # A draft itinerary supplies sourced quantities without claiming
        # a flight reservation or a confirmed operational booking.
        $legs=@(
            @{sequenceNo=1;transportMode='ROAD';startPoint='DEMO Stable VN'
                endPoint='SGN';distanceKm=40;distanceSource='DEMO synthetic map'
                plannedDeparture=$start.ToString('o')
                plannedArrival=$start.AddHours(1).ToString('o')
                startTimezone='Asia/Ho_Chi_Minh';endTimezone='Asia/Ho_Chi_Minh'},
            @{sequenceNo=2;transportMode='AIR';startPoint='SGN';endPoint='SIN'
                distanceKm=1100;distanceSource='DEMO synthetic air route'
                plannedDeparture=$start.AddHours(4).ToString('o')
                plannedArrival=$start.AddHours(6).ToString('o')
                startTimezone='Asia/Ho_Chi_Minh';endTimezone='Asia/Singapore'
                supplierId=[long](Sql "SELECT SupplierID FROM dbo.Supplier
                    WHERE Name=N'DEMO Air Cargo'").Rows[0].SupplierID
                plannedFlightNumber='DEMO101';connectionBufferMinutes=60}
        )
        if($index -in @(3,4)) {
            $legs=@($legs[1])
            $legs[0].sequenceNo=1
        }
        $plan=Api COORDINATOR POST "/api/transport-orders/$id/plans" @{
            command=(Command $id);revisionId=$o.currentRevisionId
            setAsCurrentDraft=$true;legs=$legs}
        $o=Api CUSTOMER GET "/api/transport-orders/$id"
        $entry=[ordered]@{orderId=$id;number=$number;horseId=$horseId
            revisionId=$o.currentRevisionId;plan=$plan;services=$services}
        $state.orders+=$entry
        $state|ConvertTo-Json -Depth 35|Set-Content (
            Join-Path $local 'seed-state.json') -Encoding utf8
        Write-Output "Prepared $number ($id)"
    }
    }
    foreach($entry in $state.orders) {
        if ($Continue -and $entry.quote) { continue }
        $id=[long]$entry.orderId
        $revision=[long]$entry.revisionId
        $offer=Api COORDINATOR POST "/api/transport-orders/$id/supplier-offers" @{
            command=(Command $id);revisionId=$revision;serviceCode='AIRFARE'
            providerName='DEMO Air Cargo';offerReference="DEMO-OFFER-$id"
            unitCode='SHIPMENT';quantity='1';purchaseUnitPrice='1000.00'
            retailUnitPrice='1500.00';validUntil=$until
            evidenceFileKey="DEMO-fictional-offer-$id.pdf"
            includesStall=$false;includedCharges=@();bundleCoverage='ENTIRE_ROUTE'}
        $offerId=[long](Sql 'SELECT MAX(OfferID) AS id FROM dbo.SupplierOffer
            WHERE RevisionID=@revision' @{revision=$revision}).Rows[0].id
        # F06 offer DTO records a provider name; F10 also needs its catalog ID.
        $null=Sql "UPDATE dbo.SupplierOffer SET SupplierID=(SELECT SupplierID
            FROM dbo.Supplier WHERE Name=N'DEMO Air Cargo') WHERE OfferID=@id" @{
                id=$offerId}
        $assessments=@(
            @{code='AIRFARE';status='PRICED';quantity='1';offerId=$offerId
                sourceReference='DEMO fictional retail carrier offer'
                validUntil=$until;reason='DEMO rehearsal only'},
            @{code='GOVERNMENT_DUTY';status='PRICED';quantity='1'
                tariffId=[long](Sql "SELECT TariffID FROM dbo.ServiceTariff
                    WHERE ServiceCode='GOVERNMENT_DUTY'").Rows[0].TariffID
                sourceReference='DEMO fictional authority assessment'
                validUntil=$until;reason='DEMO rehearsal, not an actual duty'}
        )
        # Determine the full amount from the real preparation calculator.
        # A first DRAFT may retain a schedule mismatch blocker; it is never
        # approved. Its successor uses the calculated amount exactly.
        $prepare=@{command=(Command $id);revisionId=$revision
            validUntil=$until;termsVersion='DEMO-TERMS-1'
            terms='DEMO only. No real transport or financial obligation.'
            cancellationTerms='DEMO only. Reset discards synthetic demo data.'
            paymentSchedule=@(@{sequenceNo=1;paymentStage='FULL'
                amountDue='1.00';dueAt=$now.AddDays(1).ToString('o')})
            assessments=$assessments}
        $quote=Api COORDINATOR POST "/api/transport-orders/$id/quotes" $prepare
        $prepare.paymentSchedule[0].amountDue=[string]$quote.quote.total
        $prepare.command=Command $id
        $quote=Api COORDINATOR POST "/api/transport-orders/$id/quotes" $prepare
        $qid=[long]$quote.quote.quoteId
        $entry.quote=$quote
        if ($entry.number -ne 'DEMO-ORD-01') {
            foreach($action in @('APPROVE','ISSUE','ACCEPT')) {
                $role=if($action -eq 'ACCEPT'){'CUSTOMER'}else{'MANAGER'}
                $quote=Api $role POST "/api/transport-orders/$id/quotes/$qid/actions" @{
                    command=(Command $id);action=$action}
            }
            $entry.quote=$quote
        }
        $state|ConvertTo-Json -Depth 40|Set-Content (
            Join-Path $local 'seed-state.json') -Encoding utf8
        Write-Output "Priced $($entry.number): $($quote.quote.total) USD"
    }
    foreach($entry in $state.orders | Where-Object {
            $_.number -in @('DEMO-ORD-02','DEMO-ORD-05') }) {
        $id=[long]$entry.orderId
        $prefix="/api/transport-orders/$id"
        if (-not $entry.paymentRequestId) {
            $scheduleId=[long](Sql 'SELECT ScheduleID FROM dbo.QuotePaymentSchedule
                WHERE QuoteID=@id' @{id=[long]$entry.quote.quote.quoteId}).Rows[0].ScheduleID
            $payment=Api MANAGER POST "$prefix/payments/requests" @{
                command=(Command $id);quoteId=$entry.quote.quote.quoteId
                scheduleId=$scheduleId}
            $entry.paymentRequestId=[long](Sql 'SELECT PaymentRequestID AS id
                FROM dbo.PaymentRequest WHERE ScheduleID=@id' `
                @{id=$scheduleId}).Rows[0].id
            SaveState
        }
        if (-not $entry.checklistReady) {
            $rule=Api SPECIALIST POST "$prefix/compliance/rules" @{
                command=(Command $id);phase='PRE_EXPORT';ruleCode='DEMO-VN-SG'
                documentType='DEMO_HEALTH';displayName='DEMO Health Certificate'
                sourceUrl='https://example.invalid/DEMO-not-a-legal-rule'
                effectiveFrom=$now.AddDays(-1).ToString('o');effectiveTo=$until
                instructions='DEMO simulation only, not legal advice or a permit'
                authorityRequired=$true}
            $entry.ruleId=[long]$rule.entityId
            $null=Api SPECIALIST POST "$prefix/compliance/checklists" @{
                command=(Command $id);phase='PRE_EXPORT';transitCountryCodes=@()
                sourceReference='DEMO synthetic route checklist';validUntil=$until}
            $entry.checklistReady=$true
            SaveState
        }
        if ($entry.number -eq 'DEMO-ORD-02') { continue }
        if (-not $entry.paid) {
            $proof=Api MANAGER POST "$prefix/payments/proofs" @{
                command=(Command $id);mediaType='image/png'
                contentBase64=$imageBase64}
            $key=(Sql 'SELECT TOP(1) CONVERT(varchar(36),FileKey) AS k
                FROM dbo.PaymentProof WHERE OrderID=@id ORDER BY CreatedAt DESC' `
                @{id=$id}).Rows[0].k
            $bank=Api MANAGER POST "$prefix/payments/transactions" @{
                command=(Command $id);amountReceived=$entry.quote.quote.total
                currency='USD';recipientAccount='DEMO-NOT-A-BANK-ACCOUNT'
                bankTransactionReference='DEMO-BANK-ORD-05'
                postedAt=[DateTimeOffset]::UtcNow.AddMinutes(-1).ToString('o')
                verificationSource='DEMO fictional bank statement, no actual money'
                proofFileKey=$key;internalNotes='DEMO synthetic reconciliation'}
            $transactionId=[long](Sql "SELECT TransactionID FROM dbo.PaymentTransaction
                WHERE BankTransactionReference='DEMO-BANK-ORD-05'").Rows[0].TransactionID
            $null=Api MANAGER POST "$prefix/payments/allocations" @{
                command=(Command $id);transactionId=$transactionId
                paymentRequestId=$entry.paymentRequestId
                amount=$entry.quote.quote.total}
            $entry.paid=$true
            SaveState
        }
        if (-not $entry.cleared) {
            $upload=Api CUSTOMER POST "$prefix/compliance/documents" @{
                command=(Command $id);documentType='DEMO_HEALTH'
                fileName='DEMO-NOT-A-HEALTH-CERTIFICATE.png'
                contentType='image/png';contentBase64=$imageBase64
                issuedDate=$now.AddDays(-1).ToString('yyyy-MM-dd')
                expiryDate=$now.AddDays(45).ToString('yyyy-MM-dd')
                documentNumber='DEMO-HEALTH-05'}
            $requirementId=[long](Sql 'SELECT RequirementID FROM dbo.DocumentRequirement
                WHERE RevisionID=@id AND RuleID=@rule' `
                @{id=[long]$entry.revisionId;rule=[long]$entry.ruleId}).Rows[0].RequirementID
            $submission=Api CUSTOMER POST "$prefix/compliance/requirements/$requirementId/submissions" @{
                command=(Command $id);documentVersionId=$upload.entityId}
            $null=Api SPECIALIST POST "$prefix/compliance/submissions/$($submission.entityId)/reviews" @{
                command=(Command $id);decision='APPROVED'
                feedback='DEMO synthetic approval, not a genuine legal decision'}
            $authorityFile=Api SPECIALIST POST "$prefix/compliance/files" @{
                command=(Command $id);fileName='DEMO-NOT-A-PERMIT.png'
                contentType='image/png';contentBase64=$imageBase64}
            Start-Sleep -Milliseconds 1100
            $null=Api SPECIALIST POST "$prefix/compliance/procedures" @{
                command=(Command $id);ruleId=$entry.ruleId;decision='APPROVED'
                authorityName='DEMO fictional authority'
                applicationReference='DEMO-AUTH-05'
                sourceReference='DEMO simulation; no authority has issued a permit'
                evidenceFileKey=$authorityFile.fileKey
                decidedAt=[DateTimeOffset]::UtcNow.AddSeconds(-1).ToString(
                    'yyyy-MM-ddTHH:mm:ssZ')
                expiresAt=$now.AddDays(20).ToString('o')}
            $null=Api SPECIALIST POST "$prefix/compliance/clearances" @{
                command=(Command $id);phase='PRE_EXPORT'
                expiresAt=$now.AddDays(15).ToString('o')}
            $entry.cleared=$true
            SaveState
        }
        Write-Output "Funded and legally staged $($entry.number) (DEMO only)"
    }
    foreach($entry in $state.orders | Where-Object {
            $_.number -in @('DEMO-ORD-03','DEMO-ORD-04','DEMO-ORD-05') }) {
        if ($entry.operationsReady) { continue }
        $id=[long]$entry.orderId
        $pidValue=[long]$entry.plan.planId
        $prefix="/api/transport-orders/$id/plans/$pidValue"
        $plan=Api COORDINATOR GET $prefix
        $air=@($plan.legs|Where-Object transportMode -eq 'AIR')[0]
        $offerId=[long](Sql 'SELECT MAX(OfferID) AS id FROM dbo.SupplierOffer
            WHERE RevisionID=@id' @{id=[long]$entry.revisionId}).Rows[0].id
        $null=Sql "UPDATE dbo.SupplierOffer SET SupplierID=(SELECT SupplierID
            FROM dbo.Supplier WHERE Name=N'DEMO Air Cargo') WHERE OfferID=@id" @{
                id=$offerId}
        $null=Api COORDINATOR POST "$prefix/legs/$($air.legId)/flight-reservation" @{
            command=(Command $id);offerId=$offerId;airlineName='DEMO Air Cargo'
            flightNumber='DEMO101';reservationReference="DEMO-FLIGHT-$id"
            status='CONFIRMED';evidenceFileKey="DEMO-fictional-flight-$id.pdf"
            expiresAt=$until;supplierContact='demo@example.invalid'
            usableCapacitySlots=3}
        if ($entry.number -eq 'DEMO-ORD-05') {
            $road=@($plan.legs|Where-Object transportMode -eq 'ROAD')[0]
            $vehicle=(Sql "SELECT VehicleID,
                SUBSTRING(sys.fn_varbintohexstr(RecordVersion),3,16) AS v
                FROM dbo.FleetVehicle WHERE VehicleCode='DEMO-TRUCK-01'").Rows[0]
            $null=Api COORDINATOR POST "$prefix/legs/$($road.legId)/road-assignment" @{
                command=(Command $id);vehicleId=[long]$vehicle.VehicleID
                vehicleRecordVersion=$vehicle.v;driverStaffId=$ids.DRIVER
                driverBufferMinutes=30;conflictNotes='DEMO synthetic road assignment'}
            $stall=(Sql "SELECT StallID,
                SUBSTRING(sys.fn_varbintohexstr(RecordVersion),3,16) AS v
                FROM dbo.StallEquipment WHERE StallCode='DEMO-STALL-PRIVATE'").Rows[0]
            $null=Api COORDINATOR POST "$prefix/legs/$($air.legId)/stall-assignment" @{
                command=(Command $id);stallId=[long]$stall.StallID
                stallRecordVersion=$stall.v;carrierAcceptanceStatus='ACCEPTED'
                status='CONFIRMED';positionDescription='DEMO forward bay'
                carrierApprovalFileKey='DEMO-not-a-carrier-permit.pdf'
                carrierContact='demo@example.invalid'}
            $null=Api COORDINATOR POST "$prefix/quarantine-reservation" @{
                command=(Command $id);revisionId=$entry.revisionId
                facilityName='DEMO Quarantine Facility'
                reservationReference='DEMO-QUARANTINE-05'
                startsAt=[DateTimeOffset]::Parse($air.plannedArrival).ToString('o')
                endsAt=[DateTimeOffset]::Parse($air.plannedArrival).AddDays(2).ToString('o')
                status='CONFIRMED';supplierId=[long](Sql "SELECT SupplierID
                    FROM dbo.Supplier WHERE Name=N'DEMO Quarantine Facility'").Rows[0].SupplierID
                evidenceFileKey='DEMO-not-a-facility-confirmation.pdf'
                expiresAt=$until;facilityContact='demo@example.invalid'}
            $confirmed=Api COORDINATOR POST "$prefix/confirm" @{
                command=(Command $id);acceptedQuoteId=$entry.quote.quote.quoteId}
            $entry.plan=$confirmed
            $gates=Api MANAGER POST "/api/transport-orders/$id/departure/gates/evaluate" @{
                command=(Command $id)}
            $entry.departure=$gates
            if (-not $gates.readyForAuthorization) {
                SaveState
                throw ('Demo 05 not ready: '+($gates|ConvertTo-Json -Depth 10 -Compress))
            }
        } else {
            # Leave physical stall assignment interactive for the demo.
            $entry.plan=Api COORDINATOR GET $prefix
        }
        $entry.operationsReady=$true
        SaveState
        Write-Output "Operationally staged $($entry.number)"
    }
    $null=Sql @'
INSERT dbo.Notification(UserID,OrderID,Title,Message,CreatedAt,EmailState,
 EmailAttempts)
SELECT u.UserID,o.OrderID,N'DEMO workspace ready',
 N'DEMO synthetic data only. No real payment, permit or shipment.',
 SYSUTCDATETIME(),'SKIPPED',0
FROM dbo.[User] u CROSS JOIN dbo.TransportOrder o
WHERE o.OrderNumber='DEMO-ORD-05' AND NOT EXISTS(
 SELECT 1 FROM dbo.Notification n WHERE n.UserID=u.UserID
 AND n.OrderID=o.OrderID AND n.Title=N'DEMO workspace ready');
'@
    foreach($entry in $state.orders) {
        $o=Api CUSTOMER GET "/api/transport-orders/$($entry.orderId)"
        $entry.currentRecordVersion=$o.recordVersion
        $entry.orderStatus=$o.orderStatus
        $entry.departure=$(if($entry.number -eq 'DEMO-ORD-05') {
            Api MANAGER GET "/api/transport-orders/$($entry.orderId)/departure"
        } else { $null })
    }
    $state.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
    SaveState
    $draft=Api CUSTOMER GET '/api/transport-orders/1'
    $samples=[ordered]@{
        note='Refresh recordVersion after every write; new UUID per new action.'
        newDraft=$draft.draft
        approveQuote01=@{command=(Command 1);action='APPROVE'}
        authorize05=@{command=(Command 5);planId=5}
        receipt05=@{command=@{
            recordVersion='<GET departure after authorize>'
            operationId=[guid]::NewGuid().ToString()}
            planId=5;legId=7;observedMicrochip='985112000000001'
            observedPassportNumber='DEMO-PASS-01';fitToTravel=$true
            conditionNotes='DEMO physical receipt';locationDescription='DEMO Stable'}
    }
    $samples|ConvertTo-Json -Depth 20|Set-Content (
        Join-Path $local 'swagger-requests.json') -Encoding utf8
} finally {
    $connection.Dispose()
    $tokens.Clear()
}
