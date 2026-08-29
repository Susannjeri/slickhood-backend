package org.pms.silverocean.service.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EncryptionService {

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Cipher cipher;
    // AES-GCM parameters
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128; // authentication tag length
    private static final int IV_LENGTH_BYTES = 12; // recommended for GCM

    private final KeyDao keyDao;

    public EncryptionService(KeyDao keyDao) throws NoSuchPaddingException, NoSuchAlgorithmException {
        this.keyDao = keyDao;
        cipher = Cipher.getInstance(CIPHER_ALGO);
    }

    @PostConstruct
    void init() {
        secretKey = generateNewKey(keyDao.getActiveSecretKey());
    }

    /**
     * Encrypt plaintext and return base64 encoded string containing IV + ciphertext.
     * <p>
     * Format: base64( iv || ciphertext )
     */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext (so we can use it during decryption)
            byte[] ivAndCipher = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCipher, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivAndCipher, iv.length, ciphertext.length);

            return ivAndCipher;
        } catch (Exception e) {
            throw new PMSCustomException(ResponseCode.FAILED_TO_ENCRYPT, e);
        }
    }

    /**
     * Decrypts ciphertext using the primary/current secret key.
     * Delegates to the key-cycling method with a single-element list.
     * * @param ivAndCipher The combined IV and ciphertext bytes.
     *
     * @return The decrypted plaintext string.
     * @throws PMSCustomException if decryption fails.
     */
    public DecryptDTO decrypt(byte[] ivAndCipher) throws PMSCustomException {
        if (PMSUtils.isByteArrayEmpty(ivAndCipher) || ivAndCipher.length < IV_LENGTH_BYTES + 1) {
//            throw new PMSCustomException(ResponseCode.CANNOT_DECRYPT_EMPTY_VALUE);
            return null;
        }

        // 1. Separate IV and Ciphertext (outside the loop as they are constant)
        byte[] iv = new byte[IV_LENGTH_BYTES];
        System.arraycopy(ivAndCipher, 0, iv, 0, IV_LENGTH_BYTES);

        byte[] ciphertext = new byte[ivAndCipher.length - IV_LENGTH_BYTES];
        System.arraycopy(ivAndCipher, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

        AlgorithmParameterSpec spec;
        try {

            spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        } catch (Exception e) {
            // This is a configuration error, usually fatal.
            throw new PMSCustomException(ResponseCode.CIPHER_INITIALIZATION_ERROR, e);
        }

        // 2. Try primary key first
        try {
            return new DecryptDTO(false, decryptWithKey(spec, ciphertext, this.secretKey));
        } catch (BadPaddingException ignored) {
            // Key didn't match — continue to old keys
            log.warn("Could not decrypt with active key, trying older keys");
        } catch (Exception e) {
            throw new PMSCustomException(ResponseCode.UNEXPECTED_DECRYPTION_ERROR, e);
        }
        // 3. Try old keys
        for (SecretKey oldKey : getOldKeys()) {
            try {
                return new DecryptDTO(true, decryptWithKey(spec, ciphertext, oldKey));
            } catch (BadPaddingException ignored) {
                log.warn("Wrong old key, trying next");
            } catch (Exception e) {
                log.warn("Unexpected exception when decrypting with old key, trying next");
            }
        }
        log.error("Could not find decryption key");
        throw new PMSCustomException(ResponseCode.FAILED_TO_DECRYPT);
    }

    public void rotateKey(long userId) throws Exception {
        keyDao.rotateActiveKey(userId);
    }


    /**
     * Decrypts ciphertext using a single specified SecretKey.
     * This is the core decryption operation.
     *
     * @param spec       The GCM Specification.
     * @param ciphertext The ciphertext bytes.
     * @param secretKey  The SecretKey to use for decryption.
     * @return The decrypted plaintext string.
     * @throws BadPaddingException if the key is wrong or the data was tampered with (AEAD tag mismatch).
     * @throws PMSCustomException     if a configuration/initialization error occurs.
     * @throws Exception           for other JCE exceptions like InvalidKeyException, IllegalBlockSizeException.
     */
    private String decryptWithKey(AlgorithmParameterSpec spec, byte[] ciphertext, SecretKey secretKey)
            throws BadPaddingException, PMSCustomException, Exception {
        // Initialize cipher with the key
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        // Perform decryption
        byte[] plain = cipher.doFinal(ciphertext);

        // Success
        return new String(plain, StandardCharsets.UTF_8);
    }

    private Set<SecretKey> getOldKeys() {
        return keyDao.getOldKeys().stream().map(this::generateNewKey).collect(Collectors.toSet());
    }

    private SecretKey generateNewKey(byte[] secretKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Provided secret key must be 32 bytes (base64 of 32 bytes).");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

}

