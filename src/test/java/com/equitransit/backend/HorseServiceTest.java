/*
 * HorseServiceTest
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify profile validation and service boundary invariants.
 */
package com.equitransit.backend;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.equitransit.backend.dto.request.HorseProfileRequest;
import com.equitransit.backend.dto.request.HorseUpdateRequest;
import com.equitransit.backend.entity.Horse;
import com.equitransit.backend.exception.HorseException;
import com.equitransit.backend.repository.HorseRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.HorseService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/** Fast tests; real SQL rowversion and authorization have separate tests. */
public class HorseServiceTest {

    private HorseRepository repository;
    private HorseService service;

    /** Sets up a trusted actor without any client-owned customer key. */
    @BeforeEach
    public void prepare() {
        CurrentActorService actors = mock(CurrentActorService.class);

        repository = mock(HorseRepository.class);
        when(actors.current()).thenReturn(
                new CurrentActor(7L, "CUSTOMER", 42L, null));
        service = new HorseService(repository, actors);
    }

    /** Owner filtering applies equally to the page and total count. */
    @Test
    public void pagesCannotIncludeOtherCustomers() {
        when(repository.list(42, 0, 20, "Thunder"))
                .thenReturn(List.of());
        when(repository.count(42, "Thunder")).thenReturn(21L);

        assertThat(service.list(0, 20, " Thunder ").totalPages()).isEqualTo(2);
        verify(repository).list(42, 0, 20, "Thunder");
        verify(repository).count(42, "Thunder");
    }

    /** Missing and foreign horse keys have the same domain failure. */
    @Test
    public void foreignHorseDoesNotRevealItsOwner() {
        when(repository.findOwned(1, 42)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1))
                .isInstanceOf(HorseException.class)
                .hasMessage("Horse profile was not found.");
    }

    /** Atomic persistence failure prevents silently overwriting new data. */
    @Test
    public void staleUpdateIsAConflict() {
        Horse horse = horse();
        HorseUpdateRequest input = new HorseUpdateRequest();

        input.setName("New name");
        input.setRecordVersion("0000000000000001");
        when(repository.findOwned(1, 42)).thenReturn(Optional.of(horse));
        when(repository.update(any(), eq(input.getRecordVersion())))
                .thenReturn(false);
        assertThatThrownBy(() -> service.update(1, input))
                .isInstanceOf(HorseException.class)
                .hasMessageContaining("Reload");
    }

    /** Drafts retain unknown measurements rather than zeros or suitability. */
    @Test
    public void incompleteProfileIsNotMeasurementComplete() {
        when(repository.findOwned(1, 42)).thenReturn(Optional.of(horse()));

        assertThat(service.get(1).measurementsComplete()).isFalse();
        assertThat(service.get(1).weightKg()).isNull();
    }

    /** Checks identifier encoding and numeric scale/positive limits. */
    @Test
    public void invalidProfileFieldsAreRejected() {
        HorseProfileRequest input = new HorseProfileRequest();

        input.setName("Valid name");
        input.setMicrochipId("\u2603");
        input.setWeightKg(BigDecimal.ZERO);
        input.setHeightCm(new BigDecimal("10000"));
        input.setBodyWidthCm(new BigDecimal("80.001"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(input)).hasSize(4);
        }
    }

    /** Draft name alone is valid; update requires an eight-byte version. */
    @Test
    public void draftAndUpdateHaveDifferentVersionRequirements() {
        HorseProfileRequest draft = new HorseProfileRequest();
        HorseUpdateRequest update = new HorseUpdateRequest();

        draft.setName("Thunder");
        update.setName("Thunder");
        update.setRecordVersion("1234");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(draft)).isEmpty();
            assertThat(factory.getValidator().validate(update)).hasSize(1);
        }
    }

    private Horse horse() {
        Horse horse = new Horse();

        horse.setHorseId(1L);
        horse.setCustomerId(42L);
        horse.setName("Thunder");
        horse.setRecordVersion("0000000000000001");
        return horse;
    }
}
