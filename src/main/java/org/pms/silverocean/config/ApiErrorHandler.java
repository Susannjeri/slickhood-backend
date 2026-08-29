package org.pms.silverocean.config;


import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestControllerAdvice
@Slf4j
public class ApiErrorHandler extends ResponseEntityExceptionHandler {
    private final I18NService i18NService;

    public ApiErrorHandler(I18NService i18NService) {
        this.i18NService = i18NService;
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String error = ex.getMessage() + " parameter is missing";
//        List<String> errors= ex.getConstraintViolations().stream().map(violation->violation.getMessage()).collect(Collectors.toList());
        log.error("MissingServletRequest {}", error);
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.MISSING_REQUEST_PARAMETER.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.MISSING_REQUEST_PARAMETER, "Missing request parameter."), Set.of(error)), status);
    }

    /**
     * Handle HttpMediaTypeNotSupportedException. This one triggers when JSON is invalid as well.
     *
     * @param ex      HttpMediaTypeNotSupportedException
     * @param headers HttpHeaders
     * @param status  HttpStatus
     * @param request WebRequest
     * @return the ApiError object
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(ex.getContentType());
        builder.append(" media type is not supported. Supported media types are ");
        ex.getSupportedMediaTypes().forEach(t -> builder.append(t).append(", "));
        log.error(" media type is not supported");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type."), Set.of(builder)), status);
    }

    /**
     * Handle MethodArgumentNotValidException. Triggered when an object fails @Valid validation.
     *
     * @param ex      the MethodArgumentNotValidException that is thrown when @Valid validation fails
     * @param headers HttpHeaders
     * @param status  HttpStatus
     * @param request WebRequest
     * @return the ApiError object
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Set<String> errors = new HashSet<>();
//        errors.add("Validation error");
        ex.getBindingResult().getFieldErrors().forEach(ge -> errors.add(ge.getRejectedValue() + " - " + ge.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errors.add(ge.getObjectName() + " : " + ge.getDefaultMessage()));
        log.error("Validation error");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.INVALID_FIELD_DATA.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVALID_FIELD_DATA, "Invalid field data."), errors), status);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Resource not found error");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.RESOURCE_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.RESOURCE_NOT_FOUND, "Resource not found."),
                ex.getResourcePath() + " not found"), status);
    }

    /**
     * Handles javax.validation.ConstraintViolationException. Thrown when @Validated fails.
     *
     * @param ex the ConstraintViolationException
     * @return the ApiError object
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
        Set<String> errors = constraintViolations.stream().map(violation -> violation.getPropertyPath() + ": " +
                violation.getMessage()).collect(Collectors.toSet());
        log.error("ConstraintViolation error");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.INVALID_FIELD_DATA_CONSTRAINT.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT, "Invalid field data."),
                Set.of(String.join(",", errors))), BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED, "File size exceeds the allowable limit. Please upload a smaller file."),
                Set.of(Objects.requireNonNull(ex.getMessage()))), HttpStatus.PAYLOAD_TOO_LARGE);
    }


    /**
     * Handle HttpMessageNotReadableException. Happens when request JSON is malformed.
     *
     * @param ex      HttpMessageNotReadableException
     * @param headers HttpHeaders
     * @param status  HttpStatus
     * @param request WebRequest
     * @return the ApiError object
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
//        ServletWebRequest servletWebRequest = (ServletWebRequest) request;
//            log.info("{} to {}", servletWebRequest.getHttpMethod(), servletWebRequest.getRequest().getServletPath());
        String error = ex.getMessage();

        log.error("handleHttpMessageNotReadable {}", error);
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.MESSAGE_NOT_READABLE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.MESSAGE_NOT_READABLE, "Message not readable."),
                Set.of(error)), status);
    }

    /**
     * Handle HttpMessageNotWritableException.
     *
     * @param ex      HttpMessageNotWritableException
     * @param headers HttpHeaders
     * @param status  HttpStatus
     * @param request WebRequest
     * @return the ApiError object
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String error = "Error writing JSON output";
        log.error(ex.getMessage(), ex);
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.UNWRITABLE_FIELD_DATA.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNWRITABLE_FIELD_DATA, "Unwritable field data."),
                Set.of(error)), status);
    }

    /**
     * Handle NoHandlerFoundException.
     *
     * @param ex
     * @param headers
     * @param status
     * @param request
     * @return
     */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
//        ApiError apiError = new ApiError(BAD_REQUEST);
        String error = String.format("Could not find the %s method for URL %s", ex.getHttpMethod(), ex.getRequestURL());
//        apiError.setDebugMessage(ex.getMessage());
        log.error("missing handler");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.MISSING_HANDLER.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.MISSING_HANDLER, "Missing handler."),
                Set.of(error)), BAD_REQUEST);
    }


    /**
     * Handle DataIntegrityViolationException, inspects the cause for different DB causes.
     *
     * @param ex      the DataIntegrityViolationException
     * @param request the request
     * @return the ApiError object
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                  WebRequest request) {
        log.error("data integrity error {}", ex.getLocalizedMessage());
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.DATA_INTEGRITY_VIOLATION.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.DATA_INTEGRITY_VIOLATION, "Data integrity violation."),
                Set.of("Database error")), CONFLICT);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<Object> handleMissingRequestHeaderException(MissingRequestHeaderException ex,
                                                                  WebRequest request) {
        log.error("Missing Header error {}", ex.getLocalizedMessage());
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.MISSING_HEADER_ERROR.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.MISSING_HEADER_ERROR, ex.getMessage()),
                Set.of(ex.getHeaderName())), CONFLICT);
    }

    /**
     * Handle Exception, handle generic Exception.class
     *
     * @param ex      the Exception
     * @param request the request
     * @return the ApiError object
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                      WebRequest request) {
        String error = String.format("The parameter '%s' of WAValue '%s' could not be converted to type '%s'", ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName());

        log.error("parameter conversion error");
        return buildResponseEntity(new ResponseDTO(false, ResponseCode.INVALID_FIELD_DATA_TYPE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVALID_FIELD_DATA_TYPE, "Invalid field data type."),
                Set.of(error)), BAD_REQUEST);
    }

    @ExceptionHandler({PMSCustomException.class, PMSCustomException.class, PaymentRequestException.class})
    @ResponseBody
    public ResponseEntity<ResponseDTO> handleCustomResponseCodeExceptions(RuntimeException e) {
        PMSCustomException ex = (PMSCustomException) e;
        ResponseDTO response = new ResponseDTO(false, ex.getResponseCode().getCode(),
                i18NService.getLocalizedMessage(ex.getResponseCode()));
        log.warn(ex.getMessage(), ex);
        if (ex.getData() != null) {
            Object data = ex.getData();
            response.setData(data instanceof Collection ? List.copyOf((Collection<?>) data) : List.of(data));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }


    private ResponseEntity<Object> buildResponseEntity(ResponseDTO responseDTO, HttpStatusCode status) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(responseDTO);
    }

}
