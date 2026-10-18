/*
 * StaffExamples
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Document private staff commands without real credentials.
 */
package com.equitransit.backend.dto.staff;

/** Documentation only; these values are not seed accounts. */
public final class StaffExamples {
    public static final String COMMAND = """
            {"recordVersion":"000000000001A2B3",
             "operationId":"e4324ce2-f726-44e4-a655-a692e50405bd"}
            """;
    public static final String PROFILE = """
            {"email":"specialist@equitransit.example",
             "fullName":"Alex Nguyen","phone":"+84901234567",
             "role":"SPECIALIST","employeeCode":"EMP-2026-001",
             "department":"Compliance",
             "specialization":"Equine export documentation"}
            """;
    private StaffExamples() { }
}
