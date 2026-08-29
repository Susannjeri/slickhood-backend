package org.pms.silverocean.service.insurance;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InsuranceEmailSender {
    private final JavaMailSender mailSender;
    @Value("${app.insurance.mail.from}") private String fromAddress;
    @Value("${app.insurance.mail.reply-to}") private String replyTo;
    @Value("${app.insurance.mail.display.name:Silverwood Insurance Agency}") private String displayName;

    public String send(String recipient, String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipient);
        helper.setFrom(new InternetAddress(fromAddress, displayName));
        helper.setReplyTo(new InternetAddress(replyTo, displayName));
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
        return message.getMessageID();
    }
}
