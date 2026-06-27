package com.smart.kf.utils;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@Service
public class RsaService {

    private final KeyPair keyPair;

    public RsaService() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
    }

    /** 返回 PKCS#8 PEM 格式公钥，供前端 JSEncrypt 使用 */
    public String getPublicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    /** RSA/ECB/PKCS1Padding 解密，输入为 Base64 密文，返回原始明文 */
    public String decrypt(String encryptedBase64) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }
}
