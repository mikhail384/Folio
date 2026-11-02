import ru.focusflow.pen.AdbIdentity;
import java.io.File;
import java.security.Signature;
import java.util.Arrays;
public class IdentityTest {
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();AdbIdentity first=new AdbIdentity(dir);first.certificate.checkValidity();first.certificate.verify(first.certificate.getPublicKey());
        Signature s=Signature.getInstance("SHA256withRSA");s.initSign(first.key);s.update(new byte[]{1,2,3});byte[] sig=s.sign();s.initVerify(first.certificate.getPublicKey());s.update(new byte[]{1,2,3});if(!s.verify(sig))throw new AssertionError("Signature");
        AdbIdentity second=new AdbIdentity(dir);if(!Arrays.equals(first.key.getEncoded(),second.key.getEncoded())||!first.certificate.equals(second.certificate))throw new AssertionError("Identity must survive restart");
        System.out.println("PASS: local certificate, key matching and persisted identity");
    }
}
