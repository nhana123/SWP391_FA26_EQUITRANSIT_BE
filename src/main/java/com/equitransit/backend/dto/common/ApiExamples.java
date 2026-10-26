/*
 * ApiExamples
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Keep lifecycle examples consistent across nested DTOs.
 */
package com.equitransit.backend.dto.common;


/** Documentation fixtures, never runtime business defaults. */
public final class ApiExamples {

    /** Consistent services documentation example. */
    public static final String SERVICES =
            "{\"roadArrangement\":\"CUSTOMER\",\"veter" +
            "inaryArrangement\":\"CUSTOMER\",\"airSta" +
            "ll\":\"WIDE\",\"enhancedCare\":false,\"gro" +
            "omingDays\":2,\"boardingNights\":1,\"qua" +
            "rantinePackage\":\"STANDARD\",\"insuranc" +
            "e\":\"EXISTING\",\"declaredValue\":\"50000" +
            ".00\",\"existingPolicyReference\":\"POL-" +
            "2026-0147\",\"escortNotes\":\"Escort sup" +
            "port requested for airport handover." +
            "\",\"roadProviderName\":\"Customer road " +
            "provider\",\"roadProviderContact\":\"+84" +
            "901234567\",\"veterinaryProviderName\":" +
            "\"Customer veterinary provider\",\"vete" +
            "rinaryProviderContact\":\"+84909876543" +
            "\"}";

    /** Consistent horse documentation example. */
    public static final String HORSE =
            "{\"horseId\":12,\"name\":\"Thunder Strike" +
            "\",\"microchipId\":\"985112003456789\",\"p" +
            "assportNumber\":\"FEI-10459921\",\"breed" +
            "\":\"Thoroughbred\",\"dateOfBirth\":\"2019" +
            "-04-15\",\"sexCode\":\"GELDING\",\"weightK" +
            "g\":520,\"heightCm\":165,\"bodyWidthCm\":" +
            "80,\"measuredAt\":\"2026-10-13T08:00:00" +
            "Z\",\"recordVersion\":\"000000000001A2B3" +
            "\",\"measurementsComplete\":true}";

    /** Customer health and vaccination statement example. */
    public static final String HEALTH_DECLARATION =
            "{\"influenzaVaccinated\":true,\"lastInfl" +
            "uenzaVaccinationDate\":\"2026-08-20\",\"v" +
            "accinationRecordReference\":\"FEI pass" +
            "port p.12\",\"healthNotes\":\"No illness" +
            " in the last 30 days.\",\"declaredAccu" +
            "rate\":true}";

    /** Consistent draft documentation example. */
    public static final String DRAFT =
            "{\"horseId\":12,\"originCountryCode\":\"V" +
            "N\",\"destinationCountryCode\":\"SG\",\"or" +
            "iginAddress\":\"12 Nguyen Hue, Ho Chi " +
            "Minh City\",\"destinationAddress\":\"1 T" +
            "urf Club Avenue, Singapore\",\"originT" +
            "imezone\":\"Asia/Ho_Chi_Minh\",\"destina" +
            "tionTimezone\":\"Asia/Singapore\",\"pref" +
            "erredDepartureAt\":\"2026-11-01T06:00:" +
            "00Z\",\"deliveryDeadline\":\"2026-11-03T" +
            "18:00:00Z\",\"importType\":\"TEMPORARY\"," +
            "\"receiverName\":\"Alex Nguyen\",\"receiv" +
            "erPhone\":\"+6590123456\",\"receiverEmai" +
            "l\":\"receiver@equitransit.com\",\"recei" +
            "verAuthority\":\"Authorized horse rece" +
            "iver\",\"eventStartAt\":\"2026-11-10T08:" +
            "00:00Z\",\"eventName\":\"Autumn Racing E" +
            "vent\",\"eventVenue\":\"Singapore\",\"even" +
            "tTimezone\":\"Asia/Singapore\",\"transit" +
            "Airports\":[\"BKK\"],\"specialNeeds\":\"Ca" +
            "lm handling required.\",\"services\":{\"" +
            "roadArrangement\":\"CUSTOMER\",\"veterin" +
            "aryArrangement\":\"CUSTOMER\",\"airStall" +
            "\":\"WIDE\",\"enhancedCare\":false,\"groom" +
            "ingDays\":2,\"boardingNights\":1,\"quara" +
            "ntinePackage\":\"STANDARD\",\"insurance\"" +
            ":\"EXISTING\",\"declaredValue\":\"50000.0" +
            "0\",\"existingPolicyReference\":\"POL-20" +
            "26-0147\",\"escortNotes\":\"Escort suppo" +
            "rt requested for airport handover.\"," +
            "\"roadProviderName\":\"Customer road pr" +
            "ovider\",\"roadProviderContact\":\"+8490" +
            "1234567\",\"veterinaryProviderName\":\"C" +
            "ustomer veterinary provider\",\"veteri" +
            "naryProviderContact\":\"+84909876543\"}" +
            ",\"dateFlexibilityNotes\":\"Departure m" +
            "ay shift by one day.\"}";

    /** Consistent inputs documentation example. */
    public static final String INPUTS =
            "{\"schemaVersion\":1,\"status\":\"REQUEST" +
            "ED\",\"fitStatus\":\"PENDING\",\"slotQuant" +
            "ity\":1.5,\"carrierConfirmation\":\"NOT_" +
            "CONFIRMED\"}";

    /** Consistent intents documentation example. */
    public static final String INTENTS =
            "[{\"serviceCode\":\"ROAD_TRANSPORT\",\"op" +
            "tionCode\":\"CUSTOMER\",\"arrangedBy\":\"C" +
            "USTOMER\",\"requestedInputs\":{\"schemaV" +
            "ersion\":1,\"status\":\"REQUESTED\"},\"pro" +
            "viderName\":\"Customer road provider\"," +
            "\"providerContact\":\"+84901234567\"},{\"" +
            "serviceCode\":\"VETERINARY\",\"optionCod" +
            "e\":\"CUSTOMER\",\"arrangedBy\":\"CUSTOMER" +
            "\",\"requestedInputs\":{\"schemaVersion\"" +
            ":1,\"status\":\"REQUESTED\"},\"providerNa" +
            "me\":\"Customer veterinary provider\",\"" +
            "providerContact\":\"+84909876543\"},{\"s" +
            "erviceCode\":\"AIR_STALL\",\"optionCode\"" +
            ":\"WIDE\",\"arrangedBy\":\"EQUITRANSIT\",\"" +
            "requestedInputs\":{\"schemaVersion\":1," +
            "\"status\":\"REQUESTED\",\"fitStatus\":\"PE" +
            "NDING\",\"slotQuantity\":1.5,\"carrierCo" +
            "nfirmation\":\"NOT_CONFIRMED\"},\"provid" +
            "erName\":null,\"providerContact\":null}" +
            ",{\"serviceCode\":\"GROOMING\",\"optionCo" +
            "de\":\"EXTRA\",\"arrangedBy\":\"EQUITRANSI" +
            "T\",\"requestedInputs\":{\"requestedDays" +
            "\":2,\"schemaVersion\":1,\"status\":\"REQU" +
            "ESTED\"},\"providerName\":null,\"provide" +
            "rContact\":null},{\"serviceCode\":\"BOAR" +
            "DING\",\"optionCode\":\"EXTRA\",\"arranged" +
            "By\":\"EQUITRANSIT\",\"requestedInputs\":" +
            "{\"requestedNights\":1,\"schemaVersion\"" +
            ":1,\"status\":\"REQUESTED\"},\"providerNa" +
            "me\":null,\"providerContact\":null},{\"s" +
            "erviceCode\":\"QUARANTINE\",\"optionCode" +
            "\":\"STANDARD\",\"arrangedBy\":\"EQUITRANS" +
            "IT\",\"requestedInputs\":{\"requiredDays" +
            "Status\":\"UNKNOWN\",\"schemaVersion\":1," +
            "\"status\":\"REQUESTED\"},\"providerName\"" +
            ":null,\"providerContact\":null},{\"serv" +
            "iceCode\":\"INSURANCE\",\"optionCode\":\"E" +
            "XISTING\",\"arrangedBy\":\"EQUITRANSIT\"," +
            "\"requestedInputs\":{\"declaredValue\":\"" +
            "50000.00\",\"valueCurrency\":\"USD\",\"exi" +
            "stingPolicyReference\":\"POL-2026-0147" +
            "\",\"schemaVersion\":1,\"status\":\"REQUES" +
            "TED\"},\"providerName\":null,\"providerC" +
            "ontact\":null},{\"serviceCode\":\"ESCORT" +
            "\",\"optionCode\":\"REQUESTED\",\"arranged" +
            "By\":\"EQUITRANSIT\",\"requestedInputs\":" +
            "{\"notes\":\"Escort support requested f" +
            "or airport handover.\",\"schemaVersion" +
            "\":1,\"status\":\"REQUESTED\"},\"providerN" +
            "ame\":null,\"providerContact\":null}]";

    /** Consistent intent documentation example. */
    public static final String INTENT =
            "{\"serviceCode\":\"AIR_STALL\",\"optionCo" +
            "de\":\"WIDE\",\"arrangedBy\":\"EQUITRANSIT" +
            "\",\"requestedInputs\":{\"schemaVersion\"" +
            ":1,\"status\":\"REQUESTED\",\"fitStatus\":" +
            "\"PENDING\",\"slotQuantity\":1.5,\"carrie" +
            "rConfirmation\":\"NOT_CONFIRMED\"},\"pro" +
            "viderName\":null,\"providerContact\":nu" +
            "ll}";

    /** Consistent snapshot documentation example. */
    public static final String SNAPSHOT =
            "{\"schemaVersion\":1,\"operationId\":\"fa" +
            "2c8d7b-6c51-4976-a28d-613c4199cfd6\"," +
            "\"operationType\":\"SUBMIT\",\"requestHas" +
            "h\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"s" +
            "ourceOrderVersion\":\"000000000001A2B3" +
            "\",\"orderId\":101,\"revisionNo\":1,\"subm" +
            "ittedAt\":\"2026-10-14T12:00:00Z\",\"req" +
            "uest\":{\"horseId\":12,\"originCountryCo" +
            "de\":\"VN\",\"destinationCountryCode\":\"S" +
            "G\",\"originAddress\":\"12 Nguyen Hue, H" +
            "o Chi Minh City\",\"destinationAddress" +
            "\":\"1 Turf Club Avenue, Singapore\",\"o" +
            "riginTimezone\":\"Asia/Ho_Chi_Minh\",\"d" +
            "estinationTimezone\":\"Asia/Singapore\"" +
            ",\"preferredDepartureAt\":\"2026-11-01T" +
            "06:00:00Z\",\"deliveryDeadline\":\"2026-" +
            "11-03T18:00:00Z\",\"importType\":\"TEMPO" +
            "RARY\",\"receiverName\":\"Alex Nguyen\",\"" +
            "receiverPhone\":\"+6590123456\",\"receiv" +
            "erEmail\":\"receiver@equitransit.com\"," +
            "\"receiverAuthority\":\"Authorized hors" +
            "e receiver\",\"eventStartAt\":\"2026-11-" +
            "10T08:00:00Z\",\"eventName\":\"Autumn Ra" +
            "cing Event\",\"eventVenue\":\"Singapore\"" +
            ",\"eventTimezone\":\"Asia/Singapore\",\"t" +
            "ransitAirports\":[\"BKK\"],\"specialNeed" +
            "s\":\"Calm handling required.\",\"servic" +
            "es\":{\"roadArrangement\":\"CUSTOMER\",\"v" +
            "eterinaryArrangement\":\"CUSTOMER\",\"ai" +
            "rStall\":\"WIDE\",\"enhancedCare\":false," +
            "\"groomingDays\":2,\"boardingNights\":1," +
            "\"quarantinePackage\":\"STANDARD\",\"insu" +
            "rance\":\"EXISTING\",\"declaredValue\":\"5" +
            "0000.00\",\"existingPolicyReference\":\"" +
            "POL-2026-0147\",\"escortNotes\":\"Escort" +
            " support requested for airport hando" +
            "ver.\",\"roadProviderName\":\"Customer r" +
            "oad provider\",\"roadProviderContact\":" +
            "\"+84901234567\",\"veterinaryProviderNa" +
            "me\":\"Customer veterinary provider\",\"" +
            "veterinaryProviderContact\":\"+8490987" +
            "6543\"},\"dateFlexibilityNotes\":\"Depar" +
            "ture may shift by one day.\"},\"horse\"" +
            ":{\"horseId\":12,\"name\":\"Thunder Strik" +
            "e\",\"microchipId\":\"985112003456789\",\"" +
            "passportNumber\":\"FEI-10459921\",\"bree" +
            "d\":\"Thoroughbred\",\"dateOfBirth\":\"201" +
            "9-04-15\",\"sexCode\":\"GELDING\",\"weight" +
            "Kg\":520,\"heightCm\":165,\"bodyWidthCm\"" +
            ":80,\"measuredAt\":\"2026-10-13T08:00:0" +
            "0Z\",\"recordVersion\":\"000000000001A2B" +
            "3\",\"measurementsComplete\":true},\"ser" +
            "vices\":[{\"serviceCode\":\"ROAD_TRANSPO" +
            "RT\",\"optionCode\":\"CUSTOMER\",\"arrange" +
            "dBy\":\"CUSTOMER\",\"requestedInputs\":{\"" +
            "schemaVersion\":1,\"status\":\"REQUESTED" +
            "\"},\"providerName\":\"Customer road pro" +
            "vider\",\"providerContact\":\"+849012345" +
            "67\"},{\"serviceCode\":\"VETERINARY\",\"op" +
            "tionCode\":\"CUSTOMER\",\"arrangedBy\":\"C" +
            "USTOMER\",\"requestedInputs\":{\"schemaV" +
            "ersion\":1,\"status\":\"REQUESTED\"},\"pro" +
            "viderName\":\"Customer veterinary prov" +
            "ider\",\"providerContact\":\"+8490987654" +
            "3\"},{\"serviceCode\":\"AIR_STALL\",\"opti" +
            "onCode\":\"WIDE\",\"arrangedBy\":\"EQUITRA" +
            "NSIT\",\"requestedInputs\":{\"schemaVers" +
            "ion\":1,\"status\":\"REQUESTED\",\"fitStat" +
            "us\":\"PENDING\",\"slotQuantity\":1.5,\"ca" +
            "rrierConfirmation\":\"NOT_CONFIRMED\"}," +
            "\"providerName\":null,\"providerContact" +
            "\":null},{\"serviceCode\":\"GROOMING\",\"o" +
            "ptionCode\":\"EXTRA\",\"arrangedBy\":\"EQU" +
            "ITRANSIT\",\"requestedInputs\":{\"reques" +
            "tedDays\":2,\"schemaVersion\":1,\"status" +
            "\":\"REQUESTED\"},\"providerName\":null,\"" +
            "providerContact\":null},{\"serviceCode" +
            "\":\"BOARDING\",\"optionCode\":\"EXTRA\",\"a" +
            "rrangedBy\":\"EQUITRANSIT\",\"requestedI" +
            "nputs\":{\"requestedNights\":1,\"schemaV" +
            "ersion\":1,\"status\":\"REQUESTED\"},\"pro" +
            "viderName\":null,\"providerContact\":nu" +
            "ll},{\"serviceCode\":\"QUARANTINE\",\"opt" +
            "ionCode\":\"STANDARD\",\"arrangedBy\":\"EQ" +
            "UITRANSIT\",\"requestedInputs\":{\"requi" +
            "redDaysStatus\":\"UNKNOWN\",\"schemaVers" +
            "ion\":1,\"status\":\"REQUESTED\"},\"provid" +
            "erName\":null,\"providerContact\":null}" +
            ",{\"serviceCode\":\"INSURANCE\",\"optionC" +
            "ode\":\"EXISTING\",\"arrangedBy\":\"EQUITR" +
            "ANSIT\",\"requestedInputs\":{\"declaredV" +
            "alue\":\"50000.00\",\"valueCurrency\":\"US" +
            "D\",\"existingPolicyReference\":\"POL-20" +
            "26-0147\",\"schemaVersion\":1,\"status\":" +
            "\"REQUESTED\"},\"providerName\":null,\"pr" +
            "oviderContact\":null},{\"serviceCode\":" +
            "\"ESCORT\",\"optionCode\":\"REQUESTED\",\"a" +
            "rrangedBy\":\"EQUITRANSIT\",\"requestedI" +
            "nputs\":{\"notes\":\"Escort support requ" +
            "ested for airport handover.\",\"schema" +
            "Version\":1,\"status\":\"REQUESTED\"},\"pr" +
            "oviderName\":null,\"providerContact\":n" +
            "ull}]}";

    /** Consistent revision documentation example. */
    public static final String REVISION =
            "{\"revisionId\":201,\"orderId\":101,\"rev" +
            "isionNo\":1,\"changeReason\":null,\"subm" +
            "ittedAt\":\"2026-10-14T12:00:00Z\",\"sna" +
            "pshot\":{\"schemaVersion\":1,\"operation" +
            "Id\":\"fa2c8d7b-6c51-4976-a28d-613c419" +
            "9cfd6\",\"operationType\":\"SUBMIT\",\"req" +
            "uestHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
            "aaa\",\"sourceOrderVersion\":\"000000000" +
            "001A2B3\",\"orderId\":101,\"revisionNo\":" +
            "1,\"submittedAt\":\"2026-10-14T12:00:00" +
            "Z\",\"request\":{\"horseId\":12,\"originCo" +
            "untryCode\":\"VN\",\"destinationCountryC" +
            "ode\":\"SG\",\"originAddress\":\"12 Nguyen" +
            " Hue, Ho Chi Minh City\",\"destination" +
            "Address\":\"1 Turf Club Avenue, Singap" +
            "ore\",\"originTimezone\":\"Asia/Ho_Chi_M" +
            "inh\",\"destinationTimezone\":\"Asia/Sin" +
            "gapore\",\"preferredDepartureAt\":\"2026" +
            "-11-01T06:00:00Z\",\"deliveryDeadline\"" +
            ":\"2026-11-03T18:00:00Z\",\"importType\"" +
            ":\"TEMPORARY\",\"receiverName\":\"Alex Ng" +
            "uyen\",\"receiverPhone\":\"+6590123456\"," +
            "\"receiverEmail\":\"receiver@equitransi" +
            "t.com\",\"receiverAuthority\":\"Authoriz" +
            "ed horse receiver\",\"eventStartAt\":\"2" +
            "026-11-10T08:00:00Z\",\"eventName\":\"Au" +
            "tumn Racing Event\",\"eventVenue\":\"Sin" +
            "gapore\",\"eventTimezone\":\"Asia/Singap" +
            "ore\",\"transitAirports\":[\"BKK\"],\"spec" +
            "ialNeeds\":\"Calm handling required.\"," +
            "\"services\":{\"roadArrangement\":\"CUSTO" +
            "MER\",\"veterinaryArrangement\":\"CUSTOM" +
            "ER\",\"airStall\":\"WIDE\",\"enhancedCare\"" +
            ":false,\"groomingDays\":2,\"boardingNig" +
            "hts\":1,\"quarantinePackage\":\"STANDARD" +
            "\",\"insurance\":\"EXISTING\",\"declaredVa" +
            "lue\":\"50000.00\",\"existingPolicyRefer" +
            "ence\":\"POL-2026-0147\",\"escortNotes\":" +
            "\"Escort support requested for airpor" +
            "t handover.\",\"roadProviderName\":\"Cus" +
            "tomer road provider\",\"roadProviderCo" +
            "ntact\":\"+84901234567\",\"veterinaryPro" +
            "viderName\":\"Customer veterinary prov" +
            "ider\",\"veterinaryProviderContact\":\"+" +
            "84909876543\"},\"dateFlexibilityNotes\"" +
            ":\"Departure may shift by one day.\"}," +
            "\"horse\":{\"horseId\":12,\"name\":\"Thunde" +
            "r Strike\",\"microchipId\":\"98511200345" +
            "6789\",\"passportNumber\":\"FEI-10459921" +
            "\",\"breed\":\"Thoroughbred\",\"dateOfBirt" +
            "h\":\"2019-04-15\",\"sexCode\":\"GELDING\"," +
            "\"weightKg\":520,\"heightCm\":165,\"bodyW" +
            "idthCm\":80,\"measuredAt\":\"2026-10-13T" +
            "08:00:00Z\",\"recordVersion\":\"00000000" +
            "0001A2B3\",\"measurementsComplete\":tru" +
            "e},\"services\":[{\"serviceCode\":\"ROAD_" +
            "TRANSPORT\",\"optionCode\":\"CUSTOMER\",\"" +
            "arrangedBy\":\"CUSTOMER\",\"requestedInp" +
            "uts\":{\"schemaVersion\":1,\"status\":\"RE" +
            "QUESTED\"},\"providerName\":\"Customer r" +
            "oad provider\",\"providerContact\":\"+84" +
            "901234567\"},{\"serviceCode\":\"VETERINA" +
            "RY\",\"optionCode\":\"CUSTOMER\",\"arrange" +
            "dBy\":\"CUSTOMER\",\"requestedInputs\":{\"" +
            "schemaVersion\":1,\"status\":\"REQUESTED" +
            "\"},\"providerName\":\"Customer veterina" +
            "ry provider\",\"providerContact\":\"+849" +
            "09876543\"},{\"serviceCode\":\"AIR_STALL" +
            "\",\"optionCode\":\"WIDE\",\"arrangedBy\":\"" +
            "EQUITRANSIT\",\"requestedInputs\":{\"sch" +
            "emaVersion\":1,\"status\":\"REQUESTED\",\"" +
            "fitStatus\":\"PENDING\",\"slotQuantity\":" +
            "1.5,\"carrierConfirmation\":\"NOT_CONFI" +
            "RMED\"},\"providerName\":null,\"provider" +
            "Contact\":null},{\"serviceCode\":\"GROOM" +
            "ING\",\"optionCode\":\"EXTRA\",\"arrangedB" +
            "y\":\"EQUITRANSIT\",\"requestedInputs\":{" +
            "\"requestedDays\":2,\"schemaVersion\":1," +
            "\"status\":\"REQUESTED\"},\"providerName\"" +
            ":null,\"providerContact\":null},{\"serv" +
            "iceCode\":\"BOARDING\",\"optionCode\":\"EX" +
            "TRA\",\"arrangedBy\":\"EQUITRANSIT\",\"req" +
            "uestedInputs\":{\"requestedNights\":1,\"" +
            "schemaVersion\":1,\"status\":\"REQUESTED" +
            "\"},\"providerName\":null,\"providerCont" +
            "act\":null},{\"serviceCode\":\"QUARANTIN" +
            "E\",\"optionCode\":\"STANDARD\",\"arranged" +
            "By\":\"EQUITRANSIT\",\"requestedInputs\":" +
            "{\"requiredDaysStatus\":\"UNKNOWN\",\"sch" +
            "emaVersion\":1,\"status\":\"REQUESTED\"}," +
            "\"providerName\":null,\"providerContact" +
            "\":null},{\"serviceCode\":\"INSURANCE\",\"" +
            "optionCode\":\"EXISTING\",\"arrangedBy\":" +
            "\"EQUITRANSIT\",\"requestedInputs\":{\"de" +
            "claredValue\":\"50000.00\",\"valueCurren" +
            "cy\":\"USD\",\"existingPolicyReference\":" +
            "\"POL-2026-0147\",\"schemaVersion\":1,\"s" +
            "tatus\":\"REQUESTED\"},\"providerName\":n" +
            "ull,\"providerContact\":null},{\"servic" +
            "eCode\":\"ESCORT\",\"optionCode\":\"REQUES" +
            "TED\",\"arrangedBy\":\"EQUITRANSIT\",\"req" +
            "uestedInputs\":{\"notes\":\"Escort suppo" +
            "rt requested for airport handover.\"," +
            "\"schemaVersion\":1,\"status\":\"REQUESTE" +
            "D\"},\"providerName\":null,\"providerCon" +
            "tact\":null}]}}";

    /** Consistent order documentation example. */
    public static final String ORDER =
            "{\"orderId\":101,\"orderNumber\":\"ET-202" +
            "6-000101\",\"orderStatus\":\"SUBMITTED\"," +
            "\"currency\":\"USD\",\"recordVersion\":\"00" +
            "0000000001A2B4\",\"currentRevisionId\":" +
            "201,\"currentPlanId\":null,\"createdAt\"" +
            ":\"2026-10-14T10:00:00Z\",\"updatedAt\":" +
            "\"2026-10-14T12:05:00Z\",\"draft\":{\"hor" +
            "seId\":12,\"originCountryCode\":\"VN\",\"d" +
            "estinationCountryCode\":\"SG\",\"originA" +
            "ddress\":\"12 Nguyen Hue, Ho Chi Minh " +
            "City\",\"destinationAddress\":\"1 Turf C" +
            "lub Avenue, Singapore\",\"originTimezo" +
            "ne\":\"Asia/Ho_Chi_Minh\",\"destinationT" +
            "imezone\":\"Asia/Singapore\",\"preferred" +
            "DepartureAt\":\"2026-11-01T06:00:00Z\"," +
            "\"deliveryDeadline\":\"2026-11-03T18:00" +
            ":00Z\",\"importType\":\"TEMPORARY\",\"rece" +
            "iverName\":\"Alex Nguyen\",\"receiverPho" +
            "ne\":\"+6590123456\",\"receiverEmail\":\"r" +
            "eceiver@equitransit.com\",\"receiverAu" +
            "thority\":\"Authorized horse receiver\"" +
            ",\"eventStartAt\":\"2026-11-10T08:00:00" +
            "Z\",\"eventName\":\"Autumn Racing Event\"" +
            ",\"eventVenue\":\"Singapore\",\"eventTime" +
            "zone\":\"Asia/Singapore\",\"transitAirpo" +
            "rts\":[\"BKK\"],\"specialNeeds\":\"Calm ha" +
            "ndling required.\",\"services\":{\"roadA" +
            "rrangement\":\"CUSTOMER\",\"veterinaryAr" +
            "rangement\":\"CUSTOMER\",\"airStall\":\"WI" +
            "DE\",\"enhancedCare\":false,\"groomingDa" +
            "ys\":2,\"boardingNights\":1,\"quarantine" +
            "Package\":\"STANDARD\",\"insurance\":\"EXI" +
            "STING\",\"declaredValue\":\"50000.00\",\"e" +
            "xistingPolicyReference\":\"POL-2026-01" +
            "47\",\"escortNotes\":\"Escort support re" +
            "quested for airport handover.\",\"road" +
            "ProviderName\":\"Customer road provide" +
            "r\",\"roadProviderContact\":\"+849012345" +
            "67\",\"veterinaryProviderName\":\"Custom" +
            "er veterinary provider\",\"veterinaryP" +
            "roviderContact\":\"+84909876543\"},\"dat" +
            "eFlexibilityNotes\":\"Departure may sh" +
            "ift by one day.\"}}";

    /** Consistent charge documentation example. */
    public static final String CHARGE =
            "{\"code\":\"DOCUMENT_COORDINATION\",\"uiS" +
            "ection\":\"ADDITIONAL_CHARGES\",\"costGr" +
            "oup\":\"DOCUMENTATION_QUARANTINE\",\"lab" +
            "el\":\"Document coordination\",\"mandato" +
            "ry\":true,\"selected\":true,\"unit\":\"ROU" +
            "TE_KM\",\"quantity\":null,\"unitRate\":nu" +
            "ll,\"amount\":null,\"pricingStatus\":\"RA" +
            "TE_AND_QUANTITY_PENDING\",\"tariffId\":" +
            "null,\"tariffVersion\":null,\"formula\":" +
            "\"(ROAD km + AIR km) × rate\",\"blocker" +
            "s\":[\"RATE_PENDING\",\"QUANTITY_PENDING" +
            "\"]}";

    /** Consistent assignments documentation example. */
    public static final String ASSIGNMENTS =
            "[{\"assignmentId\":401,\"staffId\":31,\"r" +
            "ole\":\"SPECIALIST\",\"assignedAt\":\"2026" +
            "-10-14T14:05:00Z\",\"endedAt\":null,\"as" +
            "signedBy\":30,\"dueAt\":\"2026-10-16T18:" +
            "00:00Z\",\"assignmentNote\":\"Review rou" +
            "te conditions.\"}]";

    /** Consistent decision documentation example. */
    public static final String DECISION =
            "{\"decisionId\":301,\"revisionId\":201,\"" +
            "managerId\":30,\"decision\":\"APPROVED\"," +
            "\"reason\":null,\"decidedAt\":\"2026-10-1" +
            "4T14:00:00Z\"}";

    /** Consistent quarantine documentation example. */
    public static final String QUARANTINE =
            "{\"applicability\":\"UNKNOWN\",\"required" +
            "Days\":null,\"assessmentId\":null}";

    /** Consistent air_stalls documentation example. */
    public static final String AIR_STALLS =
            "[{\"option\":\"WIDE\",\"slots\":1.5,\"statu" +
            "s\":\"PENDING\",\"policyId\":null,\"policy" +
            "Version\":null,\"reasons\":[\"FIT_POLICY" +
            "_PENDING\"]}]";

    /** Consistent sections documentation example. */
    public static final String SECTIONS =
            "[{\"code\":\"TRANSPORTATION\",\"title\":\"T" +
            "ransportation\",\"options\":[\"EQUITRANS" +
            "IT\",\"CUSTOMER\"]},{\"code\":\"AIR_STALL\"" +
            ",\"title\":\"Available Air Stalls for Y" +
            "our Horse\",\"options\":[\"WIDE\",\"PRIVAT" +
            "E\"]},{\"code\":\"HORSE_CARE\",\"title\":\"H" +
            "orse Care\",\"options\":[\"ENHANCED\",\"GR" +
            "OOMING\",\"BOARDING\",\"ESCORT\"]},{\"code" +
            "\":\"VETERINARY\",\"title\":\"Veterinary\"," +
            "\"options\":[\"EQUITRANSIT\",\"CUSTOMER\"]" +
            "},{\"code\":\"QUARANTINE\",\"title\":\"Quar" +
            "antine Packages\",\"options\":[\"STANDAR" +
            "D\",\"ENHANCED\"]},{\"code\":\"INSURANCE\"," +
            "\"title\":\"Insurance\",\"options\":[\"NONE" +
            "\",\"EXISTING\",\"MORTALITY\",\"COMBINED\"]" +
            "},{\"code\":\"ADDITIONAL_CHARGES\",\"titl" +
            "e\":\"Additional Charges\",\"options\":[\"" +
            "SHOW_ALL\"]}]";

    /** Consistent fit documentation example. */
    public static final String FIT =
            "{\"option\":\"WIDE\",\"slots\":1.5,\"status" +
            "\":\"PENDING\",\"policyId\":null,\"policyV" +
            "ersion\":null,\"reasons\":[\"FIT_POLICY_" +
            "PENDING\"]}";

    /** Consistent disclosure documentation example. */
    public static final String DISCLOSURE =
            "{\"orderId\":101,\"recordVersion\":\"0000" +
            "00000001A2B3\",\"revisionId\":201,\"hors" +
            "eRecordVersion\":\"000000000001A2B3\",\"" +
            "asOf\":\"2026-10-14T13:00:00Z\",\"curren" +
            "cy\":\"USD\",\"selected\":{\"roadArrangeme" +
            "nt\":\"CUSTOMER\",\"veterinaryArrangemen" +
            "t\":\"CUSTOMER\",\"airStall\":\"WIDE\",\"enh" +
            "ancedCare\":false,\"groomingDays\":2,\"b" +
            "oardingNights\":1,\"quarantinePackage\"" +
            ":\"STANDARD\",\"insurance\":\"EXISTING\",\"" +
            "declaredValue\":\"50000.00\",\"existingP" +
            "olicyReference\":\"POL-2026-0147\",\"esc" +
            "ortNotes\":\"Escort support requested " +
            "for airport handover.\",\"roadProvider" +
            "Name\":\"Customer road provider\",\"road" +
            "ProviderContact\":\"+84901234567\",\"vet" +
            "erinaryProviderName\":\"Customer veter" +
            "inary provider\",\"veterinaryProviderC" +
            "ontact\":\"+84909876543\"},\"sections\":[" +
            "{\"code\":\"TRANSPORTATION\",\"title\":\"Tr" +
            "ansportation\",\"options\":[\"EQUITRANSI" +
            "T\",\"CUSTOMER\"]},{\"code\":\"AIR_STALL\"," +
            "\"title\":\"Available Air Stalls for Yo" +
            "ur Horse\",\"options\":[\"WIDE\",\"PRIVATE" +
            "\"]},{\"code\":\"HORSE_CARE\",\"title\":\"Ho" +
            "rse Care\",\"options\":[\"ENHANCED\",\"GRO" +
            "OMING\",\"BOARDING\",\"ESCORT\"]},{\"code\"" +
            ":\"VETERINARY\",\"title\":\"Veterinary\",\"" +
            "options\":[\"EQUITRANSIT\",\"CUSTOMER\"]}" +
            ",{\"code\":\"QUARANTINE\",\"title\":\"Quara" +
            "ntine Packages\",\"options\":[\"STANDARD" +
            "\",\"ENHANCED\"]},{\"code\":\"INSURANCE\",\"" +
            "title\":\"Insurance\",\"options\":[\"NONE\"" +
            ",\"EXISTING\",\"MORTALITY\",\"COMBINED\"]}" +
            ",{\"code\":\"ADDITIONAL_CHARGES\",\"title" +
            "\":\"Additional Charges\",\"options\":[\"S" +
            "HOW_ALL\"]}],\"airStalls\":[{\"option\":\"" +
            "WIDE\",\"slots\":1.5,\"status\":\"PENDING\"" +
            ",\"policyId\":null,\"policyVersion\":nul" +
            "l,\"reasons\":[\"FIT_POLICY_PENDING\"]}]" +
            ",\"quarantine\":{\"applicability\":\"UNKN" +
            "OWN\",\"requiredDays\":null,\"assessment" +
            "Id\":null},\"charges\":[{\"code\":\"DOCUME" +
            "NT_COORDINATION\",\"uiSection\":\"ADDITI" +
            "ONAL_CHARGES\",\"costGroup\":\"DOCUMENTA" +
            "TION_QUARANTINE\",\"label\":\"Document c" +
            "oordination\",\"mandatory\":true,\"selec" +
            "ted\":true,\"unit\":\"ROUTE_KM\",\"quantit" +
            "y\":null,\"unitRate\":null,\"amount\":nul" +
            "l,\"pricingStatus\":\"RATE_AND_QUANTITY" +
            "_PENDING\",\"tariffId\":null,\"tariffVer" +
            "sion\":null,\"formula\":\"(ROAD km + AIR" +
            " km) × rate\",\"blockers\":[\"RATE_PENDI" +
            "NG\",\"QUANTITY_PENDING\"]}],\"knownSubt" +
            "otal\":\"0.00\",\"totalEstimate\":null,\"p" +
            "ayable\":false,\"carrierConfirmation\":" +
            "\"NOT_CONFIRMED\"}";

    /** Consistent case documentation example. */
    public static final String CASE =
            "{\"orderId\":101,\"orderNumber\":\"ET-202" +
            "6-000101\",\"orderStatus\":\"APPROVED\",\"" +
            "recordVersion\":\"000000000001A2B4\",\"c" +
            "urrentRevisionId\":201,\"revision\":{\"r" +
            "evisionId\":201,\"orderId\":101,\"revisi" +
            "onNo\":1,\"changeReason\":null,\"submitt" +
            "edAt\":\"2026-10-14T12:00:00Z\",\"snapsh" +
            "ot\":{\"schemaVersion\":1,\"operationId\"" +
            ":\"fa2c8d7b-6c51-4976-a28d-613c4199cf" +
            "d6\",\"operationType\":\"SUBMIT\",\"reques" +
            "tHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
            "\",\"sourceOrderVersion\":\"000000000001" +
            "A2B3\",\"orderId\":101,\"revisionNo\":1,\"" +
            "submittedAt\":\"2026-10-14T12:00:00Z\"," +
            "\"request\":{\"horseId\":12,\"originCount" +
            "ryCode\":\"VN\",\"destinationCountryCode" +
            "\":\"SG\",\"originAddress\":\"12 Nguyen Hu" +
            "e, Ho Chi Minh City\",\"destinationAdd" +
            "ress\":\"1 Turf Club Avenue, Singapore" +
            "\",\"originTimezone\":\"Asia/Ho_Chi_Minh" +
            "\",\"destinationTimezone\":\"Asia/Singap" +
            "ore\",\"preferredDepartureAt\":\"2026-11" +
            "-01T06:00:00Z\",\"deliveryDeadline\":\"2" +
            "026-11-03T18:00:00Z\",\"importType\":\"T" +
            "EMPORARY\",\"receiverName\":\"Alex Nguye" +
            "n\",\"receiverPhone\":\"+6590123456\",\"re" +
            "ceiverEmail\":\"receiver@equitransit.c" +
            "om\",\"receiverAuthority\":\"Authorized " +
            "horse receiver\",\"eventStartAt\":\"2026" +
            "-11-10T08:00:00Z\",\"eventName\":\"Autum" +
            "n Racing Event\",\"eventVenue\":\"Singap" +
            "ore\",\"eventTimezone\":\"Asia/Singapore" +
            "\",\"transitAirports\":[\"BKK\"],\"special" +
            "Needs\":\"Calm handling required.\",\"se" +
            "rvices\":{\"roadArrangement\":\"CUSTOMER" +
            "\",\"veterinaryArrangement\":\"CUSTOMER\"" +
            ",\"airStall\":\"WIDE\",\"enhancedCare\":fa" +
            "lse,\"groomingDays\":2,\"boardingNights" +
            "\":1,\"quarantinePackage\":\"STANDARD\",\"" +
            "insurance\":\"EXISTING\",\"declaredValue" +
            "\":\"50000.00\",\"existingPolicyReferenc" +
            "e\":\"POL-2026-0147\",\"escortNotes\":\"Es" +
            "cort support requested for airport h" +
            "andover.\",\"roadProviderName\":\"Custom" +
            "er road provider\",\"roadProviderConta" +
            "ct\":\"+84901234567\",\"veterinaryProvid" +
            "erName\":\"Customer veterinary provide" +
            "r\",\"veterinaryProviderContact\":\"+849" +
            "09876543\"},\"dateFlexibilityNotes\":\"D" +
            "eparture may shift by one day.\"},\"ho" +
            "rse\":{\"horseId\":12,\"name\":\"Thunder S" +
            "trike\",\"microchipId\":\"98511200345678" +
            "9\",\"passportNumber\":\"FEI-10459921\",\"" +
            "breed\":\"Thoroughbred\",\"dateOfBirth\":" +
            "\"2019-04-15\",\"sexCode\":\"GELDING\",\"we" +
            "ightKg\":520,\"heightCm\":165,\"bodyWidt" +
            "hCm\":80,\"measuredAt\":\"2026-10-13T08:" +
            "00:00Z\",\"recordVersion\":\"00000000000" +
            "1A2B3\",\"measurementsComplete\":true}," +
            "\"services\":[{\"serviceCode\":\"ROAD_TRA" +
            "NSPORT\",\"optionCode\":\"CUSTOMER\",\"arr" +
            "angedBy\":\"CUSTOMER\",\"requestedInputs" +
            "\":{\"schemaVersion\":1,\"status\":\"REQUE" +
            "STED\"},\"providerName\":\"Customer road" +
            " provider\",\"providerContact\":\"+84901" +
            "234567\"},{\"serviceCode\":\"VETERINARY\"" +
            ",\"optionCode\":\"CUSTOMER\",\"arrangedBy" +
            "\":\"CUSTOMER\",\"requestedInputs\":{\"sch" +
            "emaVersion\":1,\"status\":\"REQUESTED\"}," +
            "\"providerName\":\"Customer veterinary " +
            "provider\",\"providerContact\":\"+849098" +
            "76543\"},{\"serviceCode\":\"AIR_STALL\",\"" +
            "optionCode\":\"WIDE\",\"arrangedBy\":\"EQU" +
            "ITRANSIT\",\"requestedInputs\":{\"schema" +
            "Version\":1,\"status\":\"REQUESTED\",\"fit" +
            "Status\":\"PENDING\",\"slotQuantity\":1.5" +
            ",\"carrierConfirmation\":\"NOT_CONFIRME" +
            "D\"},\"providerName\":null,\"providerCon" +
            "tact\":null},{\"serviceCode\":\"GROOMING" +
            "\",\"optionCode\":\"EXTRA\",\"arrangedBy\":" +
            "\"EQUITRANSIT\",\"requestedInputs\":{\"re" +
            "questedDays\":2,\"schemaVersion\":1,\"st" +
            "atus\":\"REQUESTED\"},\"providerName\":nu" +
            "ll,\"providerContact\":null},{\"service" +
            "Code\":\"BOARDING\",\"optionCode\":\"EXTRA" +
            "\",\"arrangedBy\":\"EQUITRANSIT\",\"reques" +
            "tedInputs\":{\"requestedNights\":1,\"sch" +
            "emaVersion\":1,\"status\":\"REQUESTED\"}," +
            "\"providerName\":null,\"providerContact" +
            "\":null},{\"serviceCode\":\"QUARANTINE\"," +
            "\"optionCode\":\"STANDARD\",\"arrangedBy\"" +
            ":\"EQUITRANSIT\",\"requestedInputs\":{\"r" +
            "equiredDaysStatus\":\"UNKNOWN\",\"schema" +
            "Version\":1,\"status\":\"REQUESTED\"},\"pr" +
            "oviderName\":null,\"providerContact\":n" +
            "ull},{\"serviceCode\":\"INSURANCE\",\"opt" +
            "ionCode\":\"EXISTING\",\"arrangedBy\":\"EQ" +
            "UITRANSIT\",\"requestedInputs\":{\"decla" +
            "redValue\":\"50000.00\",\"valueCurrency\"" +
            ":\"USD\",\"existingPolicyReference\":\"PO" +
            "L-2026-0147\",\"schemaVersion\":1,\"stat" +
            "us\":\"REQUESTED\"},\"providerName\":null" +
            ",\"providerContact\":null},{\"serviceCo" +
            "de\":\"ESCORT\",\"optionCode\":\"REQUESTED" +
            "\",\"arrangedBy\":\"EQUITRANSIT\",\"reques" +
            "tedInputs\":{\"notes\":\"Escort support " +
            "requested for airport handover.\",\"sc" +
            "hemaVersion\":1,\"status\":\"REQUESTED\"}" +
            ",\"providerName\":null,\"providerContac" +
            "t\":null}]}},\"decision\":{\"decisionId\"" +
            ":301,\"revisionId\":201,\"managerId\":30" +
            ",\"decision\":\"APPROVED\",\"reason\":null" +
            ",\"decidedAt\":\"2026-10-14T14:00:00Z\"}" +
            ",\"assignments\":[{\"assignmentId\":401," +
            "\"staffId\":31,\"role\":\"SPECIALIST\",\"as" +
            "signedAt\":\"2026-10-14T14:05:00Z\",\"en" +
            "dedAt\":null,\"assignedBy\":30,\"dueAt\":" +
            "\"2026-10-16T18:00:00Z\",\"assignmentNo" +
            "te\":\"Review route conditions.\"}]}";

    /** Consistent receipt documentation example. */
    public static final String RECEIPT =
            "{\"orderId\":101,\"orderStatus\":\"APPROV" +
            "ED\",\"recordVersion\":\"000000000001A2B" +
            "4\",\"decision\":{\"decisionId\":301,\"rev" +
            "isionId\":201,\"managerId\":30,\"decisio" +
            "n\":\"APPROVED\",\"reason\":null,\"decided" +
            "At\":\"2026-10-14T14:00:00Z\"},\"assignm" +
            "ents\":[{\"assignmentId\":401,\"staffId\"" +
            ":31,\"role\":\"SPECIALIST\",\"assignedAt\"" +
            ":\"2026-10-14T14:05:00Z\",\"endedAt\":nu" +
            "ll,\"assignedBy\":30,\"dueAt\":\"2026-10-" +
            "16T18:00:00Z\",\"assignmentNote\":\"Revi" +
            "ew route conditions.\"}]}";

    /** Consistent draft_update documentation example. */
    public static final String DRAFT_UPDATE =
            "{\"horseId\":12,\"originCountryCode\":\"V" +
            "N\",\"destinationCountryCode\":\"SG\",\"or" +
            "iginAddress\":\"12 Nguyen Hue, Ho Chi " +
            "Minh City\",\"destinationAddress\":\"1 T" +
            "urf Club Avenue, Singapore\",\"originT" +
            "imezone\":\"Asia/Ho_Chi_Minh\",\"destina" +
            "tionTimezone\":\"Asia/Singapore\",\"pref" +
            "erredDepartureAt\":\"2026-11-01T06:00:" +
            "00Z\",\"deliveryDeadline\":\"2026-11-03T" +
            "18:00:00Z\",\"importType\":\"TEMPORARY\"," +
            "\"receiverName\":\"Alex Nguyen\",\"receiv" +
            "erPhone\":\"+6590123456\",\"receiverEmai" +
            "l\":\"receiver@equitransit.com\",\"recei" +
            "verAuthority\":\"Authorized horse rece" +
            "iver\",\"eventStartAt\":\"2026-11-10T08:" +
            "00:00Z\",\"eventName\":\"Autumn Racing E" +
            "vent\",\"eventVenue\":\"Singapore\",\"even" +
            "tTimezone\":\"Asia/Singapore\",\"transit" +
            "Airports\":[\"BKK\"],\"specialNeeds\":\"Ca" +
            "lm handling required.\",\"services\":{\"" +
            "roadArrangement\":\"CUSTOMER\",\"veterin" +
            "aryArrangement\":\"CUSTOMER\",\"airStall" +
            "\":\"WIDE\",\"enhancedCare\":false,\"groom" +
            "ingDays\":2,\"boardingNights\":1,\"quara" +
            "ntinePackage\":\"STANDARD\",\"insurance\"" +
            ":\"EXISTING\",\"declaredValue\":\"50000.0" +
            "0\",\"existingPolicyReference\":\"POL-20" +
            "26-0147\",\"escortNotes\":\"Escort suppo" +
            "rt requested for airport handover.\"," +
            "\"roadProviderName\":\"Customer road pr" +
            "ovider\",\"roadProviderContact\":\"+8490" +
            "1234567\",\"veterinaryProviderName\":\"C" +
            "ustomer veterinary provider\",\"veteri" +
            "naryProviderContact\":\"+84909876543\"}" +
            ",\"recordVersion\":\"000000000001A2B3\"," +
            "\"dateFlexibilityNotes\":\"Departure ma" +
            "y shift by one day.\"}";

    /** Consistent revision_command documentation example. */
    public static final String REVISION_COMMAND =
            "{\"horseId\":12,\"originCountryCode\":\"V" +
            "N\",\"destinationCountryCode\":\"SG\",\"or" +
            "iginAddress\":\"12 Nguyen Hue, Ho Chi " +
            "Minh City\",\"destinationAddress\":\"1 T" +
            "urf Club Avenue, Singapore\",\"originT" +
            "imezone\":\"Asia/Ho_Chi_Minh\",\"destina" +
            "tionTimezone\":\"Asia/Singapore\",\"pref" +
            "erredDepartureAt\":\"2026-11-01T06:00:" +
            "00Z\",\"deliveryDeadline\":\"2026-11-03T" +
            "18:00:00Z\",\"importType\":\"TEMPORARY\"," +
            "\"receiverName\":\"Alex Nguyen\",\"receiv" +
            "erPhone\":\"+6590123456\",\"receiverEmai" +
            "l\":\"receiver@equitransit.com\",\"recei" +
            "verAuthority\":\"Authorized horse rece" +
            "iver\",\"eventStartAt\":\"2026-11-10T08:" +
            "00:00Z\",\"eventName\":\"Autumn Racing E" +
            "vent\",\"eventVenue\":\"Singapore\",\"even" +
            "tTimezone\":\"Asia/Singapore\",\"transit" +
            "Airports\":[\"BKK\"],\"specialNeeds\":\"Ca" +
            "lm handling required.\",\"services\":{\"" +
            "roadArrangement\":\"CUSTOMER\",\"veterin" +
            "aryArrangement\":\"CUSTOMER\",\"airStall" +
            "\":\"WIDE\",\"enhancedCare\":false,\"groom" +
            "ingDays\":2,\"boardingNights\":1,\"quara" +
            "ntinePackage\":\"STANDARD\",\"insurance\"" +
            ":\"EXISTING\",\"declaredValue\":\"50000.0" +
            "0\",\"existingPolicyReference\":\"POL-20" +
            "26-0147\",\"escortNotes\":\"Escort suppo" +
            "rt requested for airport handover.\"," +
            "\"roadProviderName\":\"Customer road pr" +
            "ovider\",\"roadProviderContact\":\"+8490" +
            "1234567\",\"veterinaryProviderName\":\"C" +
            "ustomer veterinary provider\",\"veteri" +
            "naryProviderContact\":\"+84909876543\"}" +
            ",\"recordVersion\":\"000000000001A2B3\"," +
            "\"operationId\":\"fa2c8d7b-6c51-4976-a2" +
            "8d-613c4199cfd6\",\"changeReason\":\"Upd" +
            "ate delivery schedule.\",\"dateFlexibi" +
            "lityNotes\":\"Departure may shift by o" +
            "ne day.\"}";

    /** Consistent profile documentation example. */
    public static final String PROFILE =
            "{\"name\":\"Thunder Strike\",\"microchipI" +
            "d\":\"985112003456789\",\"passportNumber" +
            "\":\"FEI-10459921\",\"breed\":\"Thoroughbr" +
            "ed\",\"dateOfBirth\":\"2019-04-15\",\"sexC" +
            "ode\":\"GELDING\",\"weightKg\":520,\"heigh" +
            "tCm\":165,\"bodyWidthCm\":80,\"measuredA" +
            "t\":\"2026-10-13T08:00:00Z\"}";

    /** Consistent profile_update documentation example. */
    public static final String PROFILE_UPDATE =
            "{\"name\":\"Thunder Strike\",\"microchipI" +
            "d\":\"985112003456789\",\"passportNumber" +
            "\":\"FEI-10459921\",\"breed\":\"Thoroughbr" +
            "ed\",\"dateOfBirth\":\"2019-04-15\",\"sexC" +
            "ode\":\"GELDING\",\"weightKg\":520,\"heigh" +
            "tCm\":165,\"bodyWidthCm\":80,\"measuredA" +
            "t\":\"2026-10-13T08:00:00Z\",\"recordVer" +
            "sion\":\"000000000001A2B3\"}";

    /** Consistent assessment documentation example. */
    public static final String ASSESSMENT =
            "{\"revisionId\":201,\"recordVersion\":\"0" +
            "00000000001A2B3\",\"operationId\":\"fa2c" +
            "8d7b-6c51-4976-a28d-613c4199cfd6\",\"d" +
            "ecision\":\"APPROVED\",\"specialistId\":3" +
            "1,\"coordinatorId\":32,\"reason\":null,\"" +
            "dueAt\":\"2026-10-16T18:00:00Z\",\"assig" +
            "nmentNote\":\"Review route conditions." +
            "\"}";

    /** Consistent service_confirm documentation example. */
    public static final String SERVICE_CONFIRM =
            "{\"recordVersion\":\"000000000001A2B3\"," +
            "\"horseRecordVersion\":\"000000000001A2" +
            "B3\",\"operationId\":\"fa2c8d7b-6c51-497" +
            "6-a28d-613c4199cfd6\",\"services\":{\"ro" +
            "adArrangement\":\"CUSTOMER\",\"veterinar" +
            "yArrangement\":\"CUSTOMER\",\"airStall\":" +
            "\"WIDE\",\"enhancedCare\":false,\"groomin" +
            "gDays\":2,\"boardingNights\":1,\"quarant" +
            "inePackage\":\"STANDARD\",\"insurance\":\"" +
            "EXISTING\",\"declaredValue\":\"50000.00\"" +
            ",\"existingPolicyReference\":\"POL-2026" +
            "-0147\",\"escortNotes\":\"Escort support" +
            " requested for airport handover.\",\"r" +
            "oadProviderName\":\"Customer road prov" +
            "ider\",\"roadProviderContact\":\"+849012" +
            "34567\",\"veterinaryProviderName\":\"Cus" +
            "tomer veterinary provider\",\"veterina" +
            "ryProviderContact\":\"+84909876543\"},\"" +
            "changeReason\":\"Update requested serv" +
            "ices.\"}";

    private ApiExamples() {
    }
}
