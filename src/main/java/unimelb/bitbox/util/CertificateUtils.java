package unimelb.bitbox.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.bouncycastle.util.encoders.Base64;


public class CertificateUtils {
    private static final int VALUE_LENGTH = 4;
    private static final byte[] INITIAL_PREFIX = new byte[]{0x00, 0x00, 0x00, 0x07, 0x73, 0x73, 0x68, 0x2d, 0x72, 0x73, 0x61};
    private static final Pattern SSH_RSA_PATTERN = Pattern.compile("ssh-rsa[\\s]+([A-Za-z0-9/+]+=*)[\\s]+.*");

// SSH-RSA key format
//
//        00 00 00 07             The length in bytes of the next field
//        73 73 68 2d 72 73 61    The key type (ASCII encoding of "ssh-rsa")
//        00 00 00 03             The length in bytes of the public exponent
//        01 00 01                The public exponent (usually 65537, as here)
//        00 00 01 01             The length in bytes of the modulus (here, 257)
//        00 c3 a3...             The modulus

    public static RSAPublicKey parseSSHPublicKey(String key) throws InvalidKeyException {
        Matcher matcher = SSH_RSA_PATTERN.matcher(key.trim());
        if (!matcher.matches()) {
            throw new InvalidKeyException("Key format is invalid for SSH RSA.");
        }
        String keyStr = matcher.group(1);

        ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(keyStr));
        byte[] prefix = new byte[INITIAL_PREFIX.length];

        try {
//        	|| !ArrayUtils.isEquals(INITIAL_PREFIX, prefix)
            if (INITIAL_PREFIX.length != is.read(prefix) || !Objects.deepEquals(INITIAL_PREFIX, prefix)) {
                throw new InvalidKeyException("Initial [ssh-rsa] key prefix missed.");
            }

            BigInteger exponent = getValue(is);
            BigInteger modulus = getValue(is);

            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new InvalidKeyException("Failed to read SSH RSA certificate from string", e);
        }
    }

    private static BigInteger getValue(InputStream is) throws IOException {
        byte[] lenBuff = new byte[VALUE_LENGTH];
        if (VALUE_LENGTH != is.read(lenBuff)) {
            throw new InvalidParameterException("Unable to read value length.");
        }

        int len = ByteBuffer.wrap(lenBuff).getInt();
        byte[] valueArray = new byte[len];
        if (len != is.read(valueArray)) {
            throw new InvalidParameterException("Unable to read value.");
        }

        return new BigInteger(valueArray);
    }
}


//    public static void main(String[] args) throws Exception
//    {
//        // some weird 617 bit key, which is way too small and not a multiple of 8
////        byte[] pkcs1PublicKeyEncoding = Base64.getDecoder().decode("MFUCTgF/uLsPBS13Gy7C3dPpiDF6SYCLUyyl6CFqPtZT1h5bwKR9EDFLQjG/kMiwkRMcmEeaLKe5qdj9W/FfFitwRAm/8F53pQw2UETKQI2b2wIDAQAB");
////        byte[] pkcs1PublicKeyEncoding = Base64.getDecoder().decode("2EAAAADAQABAAABAQC/ewAP7S3eDEYfz9BblnPY3dFKcfKx773CSBukrqrtRv9NvtNKKrVQHajrTLwBs0z6xBu+UanNNQn6T+buRnOcZJmljIVj99DJt5iiJfSBGBBqkpKMN4pKWyqX+F+DIhFzaDLggHgVmYDU4OoYwlpMsS2llBZqORPjopXLO866J3qNyNedU7mOs0gE45v4BY9UW2W4d0GoV1OfAki/MQqgQKtnXQSllX+fPjNKu/m211C4HJvwVSWoMhB9gjBFp285fACKzkE9+2t+W8rK/UJpiY1CDwsYp4g/743q0D+L6+mHLQGp97bCdrQbb2JRZcvkJNbf+oFaxXxb05SO66CH");
////        RSAPublicKey generatePublic = decodePKCS1PublicKey(pkcs1PublicKeyEncoding);
//    	System.out.println(CertificateUtils.parseSSHPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC/ewAP7S3eDEYfz9BblnPY3dFKcfKx773CSBukrqrtRv9NvtNKKrVQHajrTLwBs0z6xBu+UanNNQn6T+buRnOcZJmljIVj99DJt5iiJfSBGBBqkpKMN4pKWyqX+F+DIhFzaDLggHgVmYDU4OoYwlpMsS2llBZqORPjopXLO866J3qNyNedU7mOs0gE45v4BY9UW2W4d0GoV1OfAki/MQqgQKtnXQSllX+fPjNKu/m211C4HJvwVSWoMhB9gjBFp285fACKzkE9+2t+W8rK/UJpiY1CDwsYp4g/743q0D+L6+mHLQGp97bCdrQbb2JRZcvkJNbf+oFaxXxb05SO66CH yilumac@YilusdeMBP-10.gateway"));
//    }
//}