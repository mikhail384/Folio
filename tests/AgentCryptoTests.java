package ru.folio.notes;
import org.json.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
public class AgentCryptoTests {
    public static void main(String[] args)throws Exception{
        JSONObject v=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));String key=v.getString("key"),aad=v.getString("aad");
        if(!AgentCrypto.open(key,v.getJSONObject("value"),aad).getString("text").equals("Привет, Folio!"))throw new AssertionError("Worker→Android decrypt");
        if(!AgentCrypto.role(key,"device","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").equals(v.getString("role")))throw new AssertionError("Worker/Android role authentication");
        boolean rejected=false;try{AgentCrypto.open(key,v.getJSONObject("value"),aad+"wrong");}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("AAD was ignored");
        JSONObject out=new JSONObject().put("value",AgentCrypto.seal(key,new JSONObject().put("text","Android → Folio"),aad));Files.write(Paths.get(args[1]),out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS Worker→Android AES-GCM, HMAC roles, wrong-request rejection and Android→desktop envelope");
    }
}
