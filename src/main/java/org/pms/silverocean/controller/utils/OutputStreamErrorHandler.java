package org.pms.silverocean.controller.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class OutputStreamErrorHandler {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    protected final I18NService i18NService;

    public OutputStreamErrorHandler(I18NService i18NService) {
        this.i18NService = i18NService;
    }

    public void writeErrorResponse(HttpServletResponse response, ResponseCode code) {
        try {
            response.reset();
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ResponseDTO dto = new ResponseDTO(
                    false,
                    code.getCode(),
                    i18NService.getLocalizedMessage(code)
            );

            MAPPER.writeValue(response.getOutputStream(), dto);
        } catch (IOException ignored) {
            // nothing else we can do
        }
    }
}
