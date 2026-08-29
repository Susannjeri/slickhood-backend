package org.pms.silverocean.common;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.config.ConfigDTO;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Slf4j
public class PMSUtils {
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH);

    public static final String ID_PREFIX = "SH";
    public static final String KE = "KE";

    public static final String systemImagesFolder = "system/";


    private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private static final SecureRandom secureRandom = new SecureRandom(); // Thread-safe

    private static final Set<String> TRUTHY_VALUES = Set.of("true", "yes", "y", "1");

    public static String getIPAddress(HttpServletRequest request) {
        String remoteAddress = StringUtils.trimToEmpty(request.getRemoteAddr());
        if (isTrustedProxyAddress(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(forwardedFor) && !"unknown".equalsIgnoreCase(forwardedFor)) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return remoteAddress;
    }

    private static boolean isTrustedProxyAddress(String address) {
        if ("127.0.0.1".equals(address)
                || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address)
                || "::ffff:127.0.0.1".equalsIgnoreCase(address)
                || address.startsWith("10.")
                || address.startsWith("192.168.")
                || address.regionMatches(true, 0, "fc", 0, 2)
                || address.regionMatches(true, 0, "fd", 0, 2)) {
            return true;
        }
        if (!address.startsWith("172.")) {
            return false;
        }
        String[] octets = address.split("\\.", 3);
        try {
            int secondOctet = Integer.parseInt(octets[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String stripNonLetters(String input) {
        return input.replaceAll("[^A-Za-z]", "");
    }

    public static boolean booleanizeConfig(ConfigDTO config) {
        if (config == null) {
            return false;
        }
        if (StringUtils.isNotBlank(config.stringValue())) {
            return TRUTHY_VALUES.contains(config.stringValue().trim().toLowerCase());
        }
        return config.intValue() == 1;
    }

    public static boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email != null && email.matches(regex);
    }

    public static String encodeToBase64(String value) {
        return BASE_64_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }


    public static String randomMask() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(randomBytes);
        return BASE_64_ENCODER.encodeToString(randomBytes);
    }

    public static String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int length = value.length();
        if (length <= 4) {
            return "****";
        }
        // Keeps the first 2 and last 2 characters, masks everything in between
        return value.substring(0, 2) + "*".repeat(length - 4) + value.substring(length - 2);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public static String generateRandomOTP() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static boolean isPhoneInvalid(String phone) {
        try {
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(phone, KE); // KE = Kenya
            return !phoneNumberUtil.isValidNumber(number);
        } catch (Exception e) {
            return true;
        }
    }


    public static Locale getLocaleFromPhoneNumber(String phoneNumber) {
        try {
            Phonenumber.PhoneNumber numberProto = phoneNumberUtil.parse(phoneNumber, KE);

            if (phoneNumberUtil.isValidNumber(numberProto)) {
                // Get the two-letter ISO region code (e.g., "US", "GB", "IN").
                String regionCode = phoneNumberUtil.getRegionCodeForNumber(numberProto);

                if (regionCode != null && !regionCode.equals("ZZ")) {
                    // Create a new Locale using the identified region code.
                    return new Locale("", regionCode);
                }
            }
        } catch (NumberParseException e) {
            System.err.println("NumberParseException was thrown: " + e.toString());
        }
        return null;
    }

    public static String getLocalisedPhoneNumber(String phoneNumber) {
        try {
            Phonenumber.PhoneNumber numberProto = phoneNumberUtil.parse(phoneNumber, KE);

            if (phoneNumberUtil.isValidNumber(numberProto)) {
                // Get the two-letter ISO region code (e.g., "US", "GB", "IN").
                return phoneNumberUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
            return null;
        } catch (NumberParseException e) {
            System.err.println("NumberParseException was thrown: " + e.toString());
        }
        return null;
    }

    public static boolean isByteArrayEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    public static String toKenyanTime(ZonedDateTime date) {
        return date == null ? null : date.format(DATE_TIME_FORMATTER);
    }


    public static String toFormattedDay(ZonedDateTime date) {
        return date == null ? null : date.format(DATE_FORMATTER);
    }

    public static ZoneId getZoneId() {
        return ZoneId.of("Africa/Nairobi");
    }

    public static String timeAgo(ZonedDateTime lastLogin) {
        if (lastLogin == null) {
            return "never";
        }

        ZonedDateTime now = ZonedDateTime.now(getZoneId());

        // First check if within the same day
        Duration duration = Duration.between(lastLogin, now);
        if (duration.toMinutes() < 1) {
            return duration.getSeconds() + " seconds ago";
        } else if (duration.toHours() < 1) {
            return duration.toMinutes() + " minutes ago";
        } else if (duration.toDays() < 1) {
            return duration.toHours() + " hours ago";
        }

        // For days/months/years
        Period period = Period.between(lastLogin.toLocalDate(), now.toLocalDate());
        if (period.getYears() > 0) {
            return period.getYears() + " years " + period.getMonths() + " months ago";
        } else if (period.getMonths() > 0) {
            return period.getMonths() + " months " + period.getDays() + " days ago";
        } else {
            return period.getDays() + " days ago";
        }
    }

    public static Currency getDefaultCurrency() {
        Locale kenya = new Locale("en", KE);
        return Currency.getInstance(kenya);
    }

    public static void createDirectoryIfNotExists(String dir) {
        Path path = Paths.get(dir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.error("Exception creating Image storage directory.", e);
            }
        }
    }

    public static byte[] signDataUsingHmacSha1(byte[] privateKey, byte[] data) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(privateKey, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC", e);
        }
    }

    public static String saveFile(String destination, MultipartFile file) throws IOException {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\s+", "_");
        Path target = Paths.get(destination, filename);

        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    public static void deleteFileAndParents(String filename) throws IOException {
        if (filename == null || filename.isEmpty()) {
            return;
        }

        Path path = Paths.get(filename);
        boolean fileDeleted = Files.deleteIfExists(path);

        // We want to climb up exactly 3 levels: unitId -> propertyId -> createdBy
        Path currentFolder = path.getParent();
        for (int i = 0; i < 3; i++) {
            if (currentFolder == null || currentFolder.getNameCount() == 0) {
                break;
            }

            try (var entries = Files.newDirectoryStream(currentFolder)) {
                if (!entries.iterator().hasNext()) {
                    Files.delete(currentFolder);
                    log.info("Cleaned up empty folder: {}", currentFolder);
                    currentFolder = currentFolder.getParent();
                } else {
                    // Folder is not empty (contains other units or properties), stop here
                    break;
                }
            } catch (IOException e) {
                log.warn("Could not delete folder {}: {}", currentFolder, e.getMessage());
                break;
            }
        }
        log.info("Image directory clean up success {}", fileDeleted);
    }

    public static String formatInviteLink(String url, String token) {
        URIBuilder builder;
        try {
            builder = new URIBuilder(url);
            builder.addParameter("token", token);
            return builder.build().toString();
        } catch (URISyntaxException e) {
            throw new PMSCustomException(ResponseCode.SOMETHING_WENT_WRONG, e);
        }
    }

}
