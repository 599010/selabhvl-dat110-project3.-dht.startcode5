package no.hvl.dat110.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

    public static BigInteger hashOf(String entity) {
        try {
            // Create a MessageDigest instance for MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Perform the hashing
            byte[] messageDigest = md.digest(entity.getBytes("UTF-8"));

            // Convert the byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            return no;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static BigInteger addressSize() {
        // Since MD5 produces a 128-bit hash, the address size is 2^128
        return new BigInteger("2").pow(128);
    }

    public static int bitSize() {
        // MD5 produces a 128-bit hash
        return 128;
    }

    public static String toHex(byte[] digest) {
        StringBuilder strbuilder = new StringBuilder();
        for (byte b : digest) {
            strbuilder.append(String.format("%02x", b & 0xff));
        }
        return strbuilder.toString();
    }
}
