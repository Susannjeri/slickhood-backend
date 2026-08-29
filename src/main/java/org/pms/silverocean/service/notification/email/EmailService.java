package org.pms.silverocean.service.notification.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.common.AbstractNotificationRetryService;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationPoolConfigs;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Service("EMAIL")
@DependsOn("configInitService")
@Slf4j
public class EmailService extends AbstractNotificationRetryService {

    private final JavaMailSender javaMailSender;
    private final I18NService i18NService;
    @Value("${spring.mail.username}")
    private String mailUserName;
    @Value("${app.mail.display.name:SlickHood}")
    private String mailDisplayName;

    private static final NotificationPoolConfigs CONFIGS = new NotificationPoolConfigs(
            "Email-retry",
            PMSConfigs.EMAIL_THREAD_POOL_SIZE,
            PMSConfigs.EMAIL_RETRY_QUEUE_SIZE,
            PMSConfigs.EMAIL_RETRY_DELAY_IN_SECONDS,
            PMSConfigs.EMAIL_MAX_RETRIES
    );


    public EmailService(JavaMailSender javaMailSender, I18NService i18NService, NotificationDao notificationDao, ConfigService configService, ThreadPoolBeans threadPoolBeans) {
        super(notificationDao, configService, threadPoolBeans);
        this.javaMailSender = javaMailSender;
        this.i18NService = i18NService;
    }

    @Override
    protected NotificationPoolConfigs getPoolConfigs() {
        return CONFIGS;
    }


    @Override
    public int callProviderApi(NotificationDTO notificationDTO, long notificationId) throws IOException {
        sendEmail(notificationDTO.recipient(), notificationDTO.formattedMessage(), i18NService.getLocalizedMessage(notificationDTO.notificationType().getSubject()));
        updateNotification(notificationId);
        return 1;
    }

    @Override
    protected boolean isRetryableStatusCode(int statusCode) {
        return statusCode == 0;
    }


    private void sendEmail(final String email, final String body, final String subject) throws IOException {
        log.info("sending {} email", subject);
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(new InternetAddress(mailUserName, mailDisplayName));
            javaMailSender.send(message);
        } catch (Exception e) {
            throw new IOException(e);
        }

    }

    public void sendAttachment(final String email, final String body, final String subject, final String rootdir,
                               final String filepaths) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(body, true);


        if (filepaths != null) {
            for (String filepath : filepaths.split(",")) {
                filepath = rootdir + filepath;
                final Path path = Paths.get(filepath);
                if (Files.exists(path)) {
                    final FileSystemResource file
                            = new FileSystemResource(new File(filepath));
                    helper.addAttachment(file.getFilename(), file);
                }
            }
        }
        javaMailSender.send(message);
    }

    public void sendAttachment(final String email, final String body, final String subject,
                               final byte[] pdfContent, final String fileName) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            // The 'true' flag indicates a multipart message for attachments
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, true); // true indicates the body is HTML
            helper.setFrom(new InternetAddress(mailUserName, mailDisplayName));

            if (pdfContent != null && pdfContent.length > 0) {
                // We use ByteArrayResource to attach the PDF directly from memory
                helper.addAttachment(fileName, new ByteArrayResource(pdfContent));
            }


            javaMailSender.send(message);
        } catch (Exception e) {
            throw new MessagingException(e.getMessage());
        }

    }

    private void updateNotification(long notificationId) {
        notificationDao.findById(notificationId)
                .ifPresent(notification -> {
                    notification.setUpdatedOn(LocalDateTime.now());
                    notification.setDelivered(true);
                    notificationDao.save(notification);
                });
    }
}
