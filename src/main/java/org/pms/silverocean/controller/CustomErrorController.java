package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RestController
public class CustomErrorController  implements ErrorController {
    private final ErrorAttributes errorAttributes;
    private final I18NService i18NService;

    public CustomErrorController(ErrorAttributes errorAttributes, I18NService i18NService) {
        this.errorAttributes = errorAttributes;
        this.i18NService = i18NService;
    }

    @RequestMapping("/error")
    public ResponseEntity<ResponseDTO> handleError(WebRequest webRequest) {
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
                webRequest, ErrorAttributeOptions.defaults());

        int status = (int) attributes.get("status");
        ResponseDTO response;
        if (status == HttpStatus.FORBIDDEN.value()) {
            response =  new ResponseDTO(false, ResponseCode.FORBIDDEN_ACCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.FORBIDDEN_ACCESS));
        } else {
            response =  new ResponseDTO(false, ResponseCode.SOMETHING_WENT_WRONG.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.SOMETHING_WENT_WRONG));
        }

        return new ResponseEntity<>(response, HttpStatus.valueOf(status));
    }

}
