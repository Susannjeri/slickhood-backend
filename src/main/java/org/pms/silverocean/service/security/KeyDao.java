package org.pms.silverocean.service.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.PMSKeyRepo;
import org.pms.silverocean.database.pms.entities.PMSKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeyDao {
    private final PMSKeyRepo pmsKeyRepo;
    @Getter
    private byte[] activeSecretKey;

    @Value("${server.ssl.key-store}")
    private String keystorePath;
    @Value("${server.ssl.key-store-password}")
    private String keystorePassword;
    @Value("${server.ssl.key-store-type}")
    private String keystoreType;
    @Value("${server.ssl.key-store.key-alias}")
    private String alias;
    private PrivateKey privateKeyFromKeystore;
    private PublicKey publicKeyFromKeystore;

    @PostConstruct
    public void init() throws Exception {
        initializeKeyPairFromKeystore();

        setActiveSecretKey();
    }

    private void setActiveSecretKey() throws Exception {
        PMSKey pmsKey = getActiveKeyFromDb().orElseGet(() -> {
            try {
                return createNewActiveKey(0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        activeSecretKey = decrypt(pmsKey.getValue());
    }

    public KeyDao(PMSKeyRepo pmsKeyRepo) {
        this.pmsKeyRepo = pmsKeyRepo;
    }

    private Optional<PMSKey> getActiveKeyFromDb() {
        return pmsKeyRepo.findByActiveTrue();
    }

    public void rotateActiveKey(long createdBy) throws Exception {
        Optional<PMSKey> activeKeyFromDb = getActiveKeyFromDb();
        if (activeKeyFromDb.isPresent()) {
            createNewActiveKey(createdBy);

            PMSKey pmsKey = activeKeyFromDb.get();
            pmsKey.setActive(false);
            pmsKeyRepo.save(pmsKey);
            setActiveSecretKey();
        }
    }


    private PMSKey createNewActiveKey(long createdBy) throws Exception {
        PMSKey pmsKey = new PMSKey();
        pmsKey.setActive(true);
        pmsKey.setCreatedBy(createdBy);
        pmsKey.setValue(encrypt(PMSUtils.randomMask().getBytes()));
        pmsKeyRepo.save(pmsKey);
        return pmsKey;
    }

    public Set<byte[]> getOldKeys() {
        return pmsKeyRepo.getAllInactiveKeys()
                .stream().map(encryptedData -> {
                    try {
                        return decrypt(encryptedData);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());
    }

    private KeyStore loadKeyStore() throws Exception {
        try (InputStream is = new FileInputStream(keystorePath)) {
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            keyStore.load(is, keystorePassword.toCharArray());
            log.info("Aliases:{} ", Collections.list(keyStore.aliases()));

            return keyStore;
        }
    }

    private void initializeKeyPairFromKeystore() throws Exception {
        KeyStore keyStore = loadKeyStore();
        Key key = keyStore.getKey(alias, keystorePassword.toCharArray());
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("Key under alias is not a private key");
        }
        privateKeyFromKeystore = (PrivateKey) key;
        Certificate cert = keyStore.getCertificate(alias);
        publicKeyFromKeystore = cert.getPublicKey();
    }


    private byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKeyFromKeystore);
        return cipher.doFinal(data);
    }

    private byte[] decrypt(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKeyFromKeystore);
        return cipher.doFinal(encryptedData);
    }
}
