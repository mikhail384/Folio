package ru.folio.notes;
import org.json.*;import java.nio.charset.StandardCharsets;import java.security.SecureRandom;import java.util.Base64;import javax.crypto.*;import javax.crypto.spec.*;
/** Identical AES-256-GCM/HMAC envelope on Android, cloud Worker and desktop MCP. */
public final class AgentCrypto {
    static byte[] decode(String s){return Base64.getUrlDecoder().decode(s);}
    static String encode(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String role(String token,String role,String room)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(decode(token),"HmacSHA256"));return encode(mac.doFinal(("folio-"+role+":"+room).getBytes(StandardCharsets.UTF_8)));}
    public static JSONObject seal(String key,JSONObject value,String aad)throws Exception{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(decode(key),"AES"),new GCMParameterSpec(128,iv));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return new JSONObject().put("iv",encode(iv)).put("data",encode(c.doFinal(value.toString().getBytes(StandardCharsets.UTF_8))));}
    public static JSONObject open(String key,JSONObject value,String aad)throws Exception{byte[] iv=decode(value.getString("iv"));if(iv.length!=12)throw new IllegalArgumentException("Invalid nonce");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(decode(key),"AES"),new GCMParameterSpec(128,iv));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return new JSONObject(new String(c.doFinal(decode(value.getString("data"))),StandardCharsets.UTF_8));}
}
