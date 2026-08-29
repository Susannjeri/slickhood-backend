package org.pms.silverocean.controller;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.reports.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reports;
    private final I18NService i18n;

    @GetMapping("/catalog")
    public ResponseEntity<ResponseDTO> catalog() {
        return ok(reports.catalog());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ResponseDTO> report(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(reports.generate(code, from, to));
    }

    @GetMapping(value = "/{code}/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String filename = "slickhood-" + code.toLowerCase(Locale.ROOT).replace('_', '-') + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(reports.csv(code, from, to));
    }

    private ResponseEntity<ResponseDTO> ok(Object data) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), data));
    }
}
