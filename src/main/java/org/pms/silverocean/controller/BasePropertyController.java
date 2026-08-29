package org.pms.silverocean.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BasePropertyController {

    protected I18NService i18NService;


    private final Validator validator;

    public BasePropertyController (I18NService i18NService) {
        this.i18NService = i18NService;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    /**
     * Validates an object using Jakarta Validator.
     * Returns an optional ResponseDTO containing all validation errors.
     */
    protected  <T> Optional<ResponseDTO> validate(T t) {
        Set<ConstraintViolation<T>> violations = validator.validate(t);
        if (!violations.isEmpty()) {
            Set<String> errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.toSet());

            return Optional.of(new ResponseDTO(
                    false,
                    ResponseCode.INVALID_FIELD_DATA_CONSTRAINT.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT),
                    Set.of(String.join(",", errors))
            ));
        }
        return Optional.empty();
    }
}
