/*
 * ResourceExamples
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.dto.resource;

/** Documentation examples only, never seeded policies or prices. */
public final class ResourceExamples {


    /** Complete command input example. */
    public static final String COMMAND =
            "{\"operationId\":\"c2046c13-4c31-49b9-8695-ed"
            + "bab59eec2b\",\"recordVersion\":null}";

    /** Complete vehicle input example. */
    public static final String VEHICLE =
            "{\"vehicleCode\":\"VEH-101\",\"plateNumber\":\"51"
            + "C-12345\",\"horseCapacity\":2,\"climateSystemT"
            + "ype\":\"Ventilated\",\"status\":\"ACTIVE\",\"owner"
            + "SupplierId\":null,\"window\":{\"maintenanceFro"
            + "m\":null,\"maintenanceTo\":null,\"documentExpi"
            + "resAt\":\"2028-01-01T00:00:00Z\",\"bufferMinut"
            + "es\":60,\"notes\":null}}";

    /** Complete stall input example. */
    public static final String STALL =
            "{\"stallCode\":\"STALL-101\",\"capacitySlots\":3"
            + ",\"internalHeightCm\":240,\"internalWidthCm\":"
            + "240,\"weightLimitKg\":1800,\"status\":\"ACTIVE\""
            + ",\"ownerSupplierId\":null,\"window\":{\"mainten"
            + "anceFrom\":null,\"maintenanceTo\":null,\"docum"
            + "entExpiresAt\":\"2028-01-01T00:00:00Z\",\"buff"
            + "erMinutes\":60,\"notes\":null}}";

    /** Complete supplier input example. */
    public static final String SUPPLIER =
            "{\"name\":\"Approved transport partner\",\"cont"
            + "actName\":null,\"email\":\"operations@example."
            + "com\",\"phone\":\"+84901234567\",\"services\":[\"R"
            + "oad transport\"],\"active\":true}";

    /** Complete policy input example. */
    public static final String POLICY =
            "{\"policyCode\":\"WIDE-2027\",\"stallId\":null,\""
            + "carrierSupplierId\":null,\"slotQuantity\":1.5"
            + ",\"maxWeightKg\":650,\"maxHeightCm\":190,\"maxW"
            + "idthCm\":90,\"effectiveFrom\":\"2027-01-01T00:"
            + "00:00Z\",\"effectiveTo\":\"2028-01-01T00:00:00"
            + "Z\",\"sourceUrl\":\"https://carrier.example.co"
            + "m/fit-policy\",\"sourceFileKey\":null,\"status"
            + "\":\"DRAFT\"}";
    private ResourceExamples() { }
}
