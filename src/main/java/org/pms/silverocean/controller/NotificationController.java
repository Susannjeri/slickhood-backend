package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationProjection;
import org.pms.silverocean.service.notification.NotificationReportService;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/notification")
public class NotificationController {
    private final NotificationReportService notificationReportService;
    private final I18NService i18NService;

    @Autowired
    public NotificationController(NotificationReportService notificationReportService, I18NService i18NService) {
        this.notificationReportService = notificationReportService;
        this.i18NService = i18NService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_NOTIFICATIONS)")
    public ResponseEntity<ResponseDTO> getNotifications(Pageable pageable, @RequestParam Optional<String> filter) {
        Page<NotificationProjection> notifications = notificationReportService.getNotifications(pageable, filter.orElse(""));
        ResponseDTO body = new ResponseDTO(true, ResponseCode.NOTIFICATION_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.NOTIFICATION_LIST), notifications.getContent());
        body.setSize(notifications.getSize());
        body.setTotalPages(notifications.getTotalPages());
        body.setTotalElements(notifications.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/sms/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_NOTIFICATIONS)")
    public ResponseEntity<ResponseDTO> viewSMSLogs(Pageable pageable, @RequestParam Optional<Long> notificationId) {
        Page<ATSMSDTO> sentSMS = notificationReportService.getSentSMS(pageable, notificationId);
        ResponseDTO body = new ResponseDTO(true, ResponseCode.SMS_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.SMS_LIST), sentSMS.getContent());
        body.setSize(sentSMS.getSize());
        body.setTotalPages(sentSMS.getTotalPages());
        body.setTotalElements(sentSMS.getTotalElements());
        return ResponseEntity.ok(body);
    }
}
