package org.pms.silverocean.service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.ChargeTypeRepo;
import org.pms.silverocean.database.pms.ConfigRepo;
import org.pms.silverocean.database.pms.UtilitiesRepo;
import org.pms.silverocean.database.pms.entities.ChargeType;
import org.pms.silverocean.database.pms.entities.Config;
import org.pms.silverocean.database.pms.entities.Utility;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.property.PMSUtilities;
import org.pms.silverocean.service.property.UnitTypeDao;
import org.pms.silverocean.service.property.charges.PMSChargeTypes;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component("configInitService")
@Slf4j
public class ConfigInitService {
    private final ConfigRepo configRepo;
    private final EncryptionService encryptionService;
   private final UnitTypeDao unitTypeDao;

    private final UtilitiesRepo utilitiesRepo;
    private final ChargeTypeRepo chargeTypeRepo;

    public static final String ROLES_INITIALIZED_CONFIG = "Roles Initialized";
    public static final String MPESA_STK_AUTHENTICATION_URL = "M-Pesa STK Authentication URL";
    public static final String MPESA_SEND_STK_URL = "M-Pesa Process STK  URL";
    public static final String MPESA_ICON = "M-Pesa Payment Icon";
    public static final String MPESA_STK_CALLBACK_URL = "M-Pesa STK Callback URL";
    public static final String MPESA_VALIDATION_CALLBACK_URL = "M-Pesa Validation Callback URL";
    public static final String MPESA_CONFIRMATION_CALLBACK_URL = "M-Pesa Confirmation Callback URL";
    public static final String PMS_MPESA_PAYBILL = "PMS M-Pesa Paybill";
    public static final String PMS_MPESA_STK_PASSKEY = "PMS M-Pesa STK Passkey";
    public static final String PMS_MPESA_CONSUMER_KEY = "PMS M-Pesa Consumer Key";
    public static final String PMS_MPESA_CONSUMER_SECRET = "PMS M-Pesa Consumer Secret";
    public static final String EMAIL_SMS_OTP_VALIDITY_SECONDS = "OTP(SMS & E-Mail) Validity In Seconds";
    public static final String JWT_VALIDITY_IN_SECONDS = "JWT Validity In Seconds";
    public static final String AFRICAS_TALKING_USERNAME = "Africastalking username";
    public static final String AFRICAS_TALKING_PASSWORD = "Africastalking password/API key";
    public static final String SMS_SENDER_NAME = "SMS Sender Name";
    public static final String AFRICAS_TALKING_URL = "Callback URL for SMS delivery reports";
    public static final String SMS_RETRY_THREAD_POOL = "SMS Retry Thread Pool Size";
    public static final String EMAIL_RETRY_THREAD_POOL = "E-Mail Retry Thread Pool Size";
    public static final String SMS_RETRY_QUEUE_CAPACITY = "SMS Retry Queue Capacity";
    public static final String EMAIL_RETRY_QUEUE_CAPACITY = "E-Mail Retry Queue Capacity";
    public static final String SMS_NEXT_RETRY_IN_SECONDS = "SMS Retry Delay in Seconds";
    public static final String EMAIL_NEXT_RETRY_IN_SECONDS = "E-Mail Retry Delay in Seconds";
    public static final String SMS_MAXIMUM_RETRIES = "SMS Maximum Retries";
    public static final String EMAIL_MAXIMUM_RETRIES = "E-Mail Maximum Retries";

    public static final String FLUTTER_WAVE_PAYMENT_URL = "FlutterWave Initiate Payment URL";
    public static final String FLUTTER_WAVE_REDIRECT_URL = "URL to Redirect Customer to After FlutterWave Payment";
    public static final String FLUTTER_WAVE_CALLBACK_URL = "URL to receive FlutterWave Payment WAStatus";
    public static final String FLUTTER_WAVE_LOGO_URL = "Logo used on Card Payment screen";
    public static final String FLUTTER_WAVE_PUBLIC_KEY = "FlutterWave Public Key";
    public static final String FLUTTER_WAVE_SECRET_KEY = "FlutterWave Secret Key";
    public static final String FLUTTER_WAVE_ENCRYPTION_KEY = "FlutterWave Encryption Key";
    public static final String FLUTTER_ICON = "FlutterWave Payment Icon";
    public static final String FLUTTER_WAVE_SESSION_DURATION_SECONDS = "Duration (minutes) that the FlutterWave card payment session should remain valid for.";
    public static final String FLUTTER_WAVE_MAX_RETRIES = "Maximum number of times that a customer can retry a FlutterWave card payment before the checkout is closed.";
    public static final String FLUTTER_WAVE_MAX_VERIFY_RETRIES = "Maximum number of times to send a verify FlutterWave transaction event to confirm the status.";
    public static final String DUPLICATE_UNITS_MAX_LIMIT = "Maximum number of units that can be created at a time using the create similar units job.";
    public static final String INVITE_LINK_EXPIRY_PERIOD_DAYS = "The period, in days, it takes for an invite link to expire.";
    public static final String INVITE_LINK_URL = "The page an invited user will be forwarded to.";
    public static final String REGISTRATION_LINK_URL = "The page an unregistered, invited property manager, guard etc will be forwarded to.";
    public static final String HOME_PAGE_LINK_URL = "The page a logged in, property manager, guard etc will be forwarded to.";
    public static final String ROLE_ASSIGN_LINK_URL = "The page an unregistered invited user will be forwarded to.";
    public static final String LEASE_TEMPLATE = "Lease HTML Template File Name";
    public static final String OTP_MAX_ATTEMPTS = "Max Attempts Allowed before OTP expires";
    public static final String LEASE_PAYMENT_RECORDS_BATCH_SIZE = "Number of lease records to process payments at a time";
    public static final String TEXT_SMS_URL = "TextSMS Send SMS URL";
    public static final String TEXT_DLR_URL = "TextSMS Send DLR  URL";
    public static final String TEXT_SMS_API_KEY = "TextSMS API Key";
    public static final String TEXT_SMS_PARTNER_ID = "TextSMS Partner ID";
    public static final String TEXT_SMS_COST_CENTS = "TextSMS Cost In Cents";
    public static final String ACTIVE_SMS_PROVIDER = "Active SMS Gateway Provider";
    public static final String WHATSAPP_URL = "WhatsApp Messaging API";
    public static final String WHATSAPP_BUSINESS_PHONENUMBER_ID = "Phone number ID from Meta identifying the business account tagged to the WhatsApp number";
    public static final String WHATSAPP_ACCESS_TOKEN = "System user access token generated on System Users within https://business.facebook.com";
    public static final String CURRENCY_EXCHANGE_URL = "ExchangeRate-API endpoint";
    public static final String CURRENCY_EXCHANGE_API_KEY = "ExchangeRate-API API key";
    public static final String PESA_LINK_AUTH_URL = "PesaLink Auth URL";
    public static final String PESA_LINK_VALIDATION_URL = "PesaLink Account Validation URL";
    public static final String PESA_LINK_DISBURSEMENT_URL = "PesaLink Disbursement URL";
    public static final String PESA_LINK_ICON = "PesaLink Image";
    public static final String PESA_LINK_CLIENT_ID = "PesaLink Client ID";
    public static final String PESA_LINK_CLIENT_SECRET = "PesaLink Client Secret";
    public static final String PAYMENT_PESA_LINK_ENABLED = "Enable Pesalink payments";
    public static final String PAYMENT_MPESA_ENABLED = "Enable M-Pesa payments";
    public static final String PAYMENT_FW_ENABLED = "Enable FlutterWave payments";
    public static final String PESA_LINK_IPN_PASSWORD = "IPN Signature Password";
    public static final String VISITOR_NOTIFICATION_CHANNEL = "Visitor Notification Channel";
    public static final String VISITOR_NOTIFICATION_ENABLED = "Enable Visitor Notifications";
    public static final String VISITOR_EXPIRY_BATCH_SIZE = "Number of pending expired visitor records to process per batch";
    public static final String VISITOR_EXPIRY_DAYS = "Number of days after expected arrival a visitor record is considered expired";

    public static final String SP_TRUSTED_THRESHOLD = "SP Trusted Threshold (min highly-rated bookings)";
    public static final String SP_TRUSTED_MIN_STARS = "SP Trusted Min Stars (qualifying star floor)";
    public static final String SP_LOW_RATING_THRESHOLD = "SP Low Rating Alert Threshold";
    public static final String SP_FRAUD_FAILURE_LIMIT = "SP Fraud Failure Blacklist Limit";

    public ConfigInitService(ConfigRepo configRepo, EncryptionService encryptionService, UnitTypeDao unitTypeDao, UtilitiesRepo utilitiesRepo, ChargeTypeRepo chargeTypeRepo) {
        this.configRepo = configRepo;
        this.encryptionService = encryptionService;
        this.unitTypeDao = unitTypeDao;
        this.utilitiesRepo = utilitiesRepo;
        this.chargeTypeRepo = chargeTypeRepo;
    }

    @PostConstruct
    private void init() {
        long configInDbCount = configRepo.count();
        if (configInDbCount < EnumSet.allOf(PMSConfigs.class).size()) {
            log.info("Found some missing configs, initializing...");
            try {
                initConfigs();
                log.info("Successfully initialized configs...");
            } catch (Exception e) {
                log.error("Error while initializing configs.", e);
            }
        } else {
            log.info("Configs already initialized. Skipping...");
        }

        unitTypeDao.initUnitTypes();
        initUtilities();
        initChargeTypes();
    }

    private void initConfigs() throws Exception {
        Set<Config> configRecords = EnumSet.allOf(PMSConfigs.class).stream().map(c -> {
            boolean empty = configRepo.findByName(c.getName()).isEmpty();
            if (empty) {
                Config cConfig = new Config();
                cConfig.setName(c.getName());
                byte[] value = c.isEncrypted() ? encryptionService.encrypt(c.getStringValue()) : c.getStringValue().getBytes();
                cConfig.setStringValue(value);
                cConfig.setIntValue(c.getIntValue());
                cConfig.setEncrypted(c.isEncrypted());
                return cConfig;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        configRepo.saveAll(configRecords);
    }



    private void initUtilities() {
        Set<String> utilitiesRecords = utilitiesRepo.queryAllUtilitiesNames();
        EnumSet<PMSUtilities> pmsUtilities = EnumSet.allOf(PMSUtilities.class);
        if (utilitiesRecords.size() < pmsUtilities.size()) {
            log.info("Found some missing utilities. Updating database...");
            Set<Utility> collect = pmsUtilities.stream().map(u -> {
                if (!utilitiesRecords.contains(u.getName())) {
                    Utility ut = new Utility();
                    ut.setName(u.getName());
                    ut.setActive(true);
                    return ut;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            utilitiesRepo.saveAll(collect);
        } else {
            log.info("Utility already initialized. Skipping...");
        }
    }

    private void initChargeTypes() {
        Set<String> chargeTypeNamesFromDb = chargeTypeRepo.queryAllChargeTypeNames();
        EnumSet<PMSChargeTypes> pmsChargeTypes = EnumSet.allOf(PMSChargeTypes.class);
        if (chargeTypeNamesFromDb.size() != pmsChargeTypes.size()) {
            log.info("Found some missing charge types. Updating database...");
            Set<ChargeType> collect = pmsChargeTypes.stream().map(u -> {
                if (!chargeTypeNamesFromDb.contains(u.getName())) {
                    ChargeType ut = new ChargeType();
                    ut.setName(u.getName());
                    ut.setActive(true);
                    return ut;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            chargeTypeRepo.saveAll(collect);
        } else {
            log.info("Charge types already initialized. Skipping...");
        }
    }
}
