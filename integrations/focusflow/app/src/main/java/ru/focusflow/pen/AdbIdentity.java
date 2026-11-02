package ru.focusflow.pen;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.security.auth.x500.X500Principal;

/** A per-installation RSA identity. Private material never leaves internal app storage. */
public final class AdbIdentity {
    public final PrivateKey key;public final X509Certificate certificate;
    public AdbIdentity(File directory)throws Exception{
        File privateFile=new File(directory,"adb-private.pk8"),certificateFile=new File(directory,"adb-client.der");
        if(privateFile.isFile()&&certificateFile.isFile()){
            key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(read(privateFile)));
            certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(read(certificateFile)));return;
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048,new SecureRandom());java.security.KeyPair pair=generator.generateKeyPair();key=pair.getPrivate();
        byte[] algorithm=seq(der(6,new byte[]{0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,11}),der(5,new byte[0]));
        byte[] subject=new X500Principal("CN=FocusFlow Local ADB").getEncoded();
        long now=System.currentTimeMillis();SimpleDateFormat date=new SimpleDateFormat("yyMMddHHmmss'Z'",Locale.ROOT);date.setTimeZone(TimeZone.getTimeZone("UTC"));
        byte[] validity=seq(der(23,date.format(new Date(now-86400000L)).getBytes(StandardCharsets.US_ASCII)),der(23,date.format(new Date(now+10L*365*86400000L)).getBytes(StandardCharsets.US_ASCII)));
        byte[] tbs=seq(der(0xa0,der(2,new byte[]{2})),der(2,new BigInteger(128,new SecureRandom()).add(BigInteger.ONE).toByteArray()),algorithm,subject,validity,subject,pair.getPublic().getEncoded());
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key);signature.update(tbs);
        byte[] rawSignature=signature.sign(),bits=new byte[rawSignature.length+1];System.arraycopy(rawSignature,0,bits,1,rawSignature.length);
        byte[] encoded=seq(tbs,algorithm,der(3,bits));
        certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));certificate.verify(pair.getPublic());
        write(privateFile,key.getEncoded());write(certificateFile,encoded);
    }
    private static byte[] seq(byte[]...parts)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] part:parts)out.write(part);return der(48,out.toByteArray());}
    private static byte[] der(int tag,byte[] value)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);
        int length=value.length;if(length<128)out.write(length);else{int count=length<=255?1:length<=65535?2:3;out.write(128|count);for(int i=count-1;i>=0;i--)out.write(length>>(8*i));}
        out.write(value);return out.toByteArray();
    }
    private static byte[] read(File f)throws IOException{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static void write(File f,byte[] bytes)throws IOException{try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);out.getFD().sync();}f.setReadable(false,false);f.setWritable(false,false);f.setReadable(true,true);f.setWritable(true,true);}
}
