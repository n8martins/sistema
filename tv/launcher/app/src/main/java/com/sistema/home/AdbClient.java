package com.sistema.home;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * A minimal ADB client that talks to the TV's own debugging service on 127.0.0.1:5555.
 *
 * Android doesn't let a normal app press keys inside other apps. The ADB shell can, so the
 * launcher connects to it the same way a computer does. The TV asks once to allow this
 * app's key ("Allow USB debugging?"), then remembers it.
 *
 * Protocol: AOSP packages/modules/adb/protocol.txt. Pure Java so it can be tested off-device.
 */
final class AdbClient implements Closeable {

    static final int CNXN = 0x4e584e43;
    static final int AUTH = 0x48545541;
    static final int OPEN = 0x4e45504f;
    static final int OKAY = 0x59414b4f;
    static final int CLSE = 0x45534c43;
    static final int WRTE = 0x45545257;

    static final int AUTH_TOKEN = 1;
    static final int AUTH_SIGNATURE = 2;
    static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000001;
    private static final int MAX_PAYLOAD = 256 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** ASN.1 DigestInfo prefix for SHA-1, which is what adbd's RSA_verify expects. */
    private static final byte[] SHA1_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    interface Listener {
        /** Called when the TV is showing its "Allow debugging?" prompt for this app. */
        void onWaitingForApproval();
    }

    private final KeyPair keys;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextLocalId = 1;
    private int maxPayload = 4096;

    AdbClient(KeyPair keys) {
        this.keys = keys;
    }

    // ---------------------------------------------------------------- keys

    /** Loads this app's ADB key from dir, creating one on first use. */
    static KeyPair loadOrCreateKeys(File dir) throws Exception {
        File priv = new File(dir, "adbkey.pk8");
        File pub = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            try {
                return new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            } catch (Exception corrupt) {
                // Fall through and make a fresh pair.
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        dir.mkdirs();
        writeAll(priv, kp.getPrivate().getEncoded());
        writeAll(pub, kp.getPublic().getEncoded());
        return kp;
    }

    /** The public key in Android's RSAPublicKey layout, base64, plus a name the TV shows. */
    static byte[] androidPublicKey(RSAPublicKey key, String name) {
        int words = 2048 / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n = key.getModulus();
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n);
        BigInteger n0inv = r32.subtract(n.remainder(r32).modInverse(r32));

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        BigInteger t = n;
        for (int i = 0; i < words; i++) {
            b.putInt(t.remainder(r32).intValue());
            t = t.shiftRight(32);
        }
        t = rr;
        for (int i = 0; i < words; i++) {
            b.putInt(t.remainder(r32).intValue());
            t = t.shiftRight(32);
        }
        b.putInt(key.getPublicExponent().intValue());
        String s = Base64.getEncoder().encodeToString(b.array()) + " " + name + "\u0000";
        return s.getBytes(UTF8);
    }

    /** PKCS#1 v1.5 signature of the 20 byte token, treated as an already hashed SHA-1 digest. */
    static byte[] sign(RSAPrivateKey key, byte[] token) {
        int k = (key.getModulus().bitLength() + 7) / 8;
        byte[] block = new byte[k];
        block[0] = 0x00;
        block[1] = 0x01;
        int padEnd = k - SHA1_PREFIX.length - token.length - 1;
        for (int i = 2; i < padEnd; i++) block[i] = (byte) 0xff;
        block[padEnd] = 0x00;
        System.arraycopy(SHA1_PREFIX, 0, block, padEnd + 1, SHA1_PREFIX.length);
        System.arraycopy(token, 0, block, padEnd + 1 + SHA1_PREFIX.length, token.length);
        BigInteger s = new BigInteger(1, block).modPow(key.getPrivateExponent(), key.getModulus());
        byte[] raw = s.toByteArray();
        byte[] sig = new byte[k];
        int copy = Math.min(raw.length, k);
        System.arraycopy(raw, raw.length - copy, sig, k - copy, copy);
        return sig;
    }

    // ---------------------------------------------------------------- connection

    /**
     * Connects and authenticates. If the TV doesn't know this key yet it shows its approval
     * prompt; this call then waits up to approvalTimeoutMs for you to press Allow.
     */
    synchronized void connect(String host, int port, int approvalTimeoutMs, Listener listener) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        write(CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".getBytes(UTF8));

        boolean sentSignature = false;
        boolean sentKey = false;
        while (true) {
            Message m;
            try {
                m = read();
            } catch (SocketTimeoutException e) {
                throw new IOException(sentKey ? "Not approved on the TV in time" : "No answer from the TV's debugging service");
            }
            if (m.command == CNXN) {
                maxPayload = Math.max(1024, Math.min(m.arg1, MAX_PAYLOAD));
                socket.setSoTimeout(15000);
                return;
            }
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (!sentSignature) {
                sentSignature = true;
                write(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
            } else if (!sentKey) {
                sentKey = true;
                write(AUTH, AUTH_RSAPUBLICKEY, 0, androidPublicKey((RSAPublicKey) keys.getPublic(), "sistema-home@tv"));
                socket.setSoTimeout(approvalTimeoutMs);
                if (listener != null) listener.onWaitingForApproval();
            } else {
                throw new IOException("The TV rejected this app's key");
            }
        }
    }

    boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Runs one shell command and returns what it printed. */
    synchronized String shell(String command) throws IOException {
        if (!isConnected()) throw new IOException("Not connected");
        int local = nextLocalId++;
        write(OPEN, local, 0, ("shell:" + command + "\u0000").getBytes(UTF8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            Message m = read();
            if (m.arg1 != local && m.command != CNXN) continue;
            switch (m.command) {
                case OKAY:
                    remote = m.arg0;
                    break;
                case WRTE:
                    remote = m.arg0;
                    result.write(m.data, 0, m.data.length);
                    write(OKAY, local, remote, new byte[0]);
                    break;
                case CLSE:
                    if (remote != 0) write(CLSE, local, remote, new byte[0]);
                    return new String(result.toByteArray(), UTF8);
                default:
                    break;
            }
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }

    // ---------------------------------------------------------------- wire format

    static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private void write(int command, int arg0, int arg1, byte[] data) throws IOException {
        if (data.length > maxPayload && command == WRTE) throw new IOException("Payload too large");
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(command);
        h.putInt(arg0);
        h.putInt(arg1);
        h.putInt(data.length);
        h.putInt(checksum(data));
        h.putInt(command ^ 0xffffffff);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    private Message read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        h.getInt(); // checksum, not verified by modern adbd either
        int magic = h.getInt();
        if (magic != (m.command ^ 0xffffffff)) throw new IOException("Bad ADB packet");
        if (len < 0 || len > MAX_PAYLOAD * 4) throw new IOException("Bad ADB payload length");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    private static int checksum(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        return sum;
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream is = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        } finally {
            is.close();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(data);
        } finally {
            os.close();
        }
    }
}
