#!/usr/bin/env python3
"""A stand-in for the TV's adbd, used to test AdbClient.java off-device.

It checks the parts that matter the way real adbd does:
  * the public key is in Android's RSAPublicKey layout (n0inv and rr must be right),
  * the AUTH signature verifies as PKCS#1 v1.5 over the token taken as a SHA-1 digest
    (adbd calls RSA_verify(NID_sha1, token, 20, ...)),
  * stream ids, OKAY acknowledgements and CLSE handshakes are correct.

Usage: python3 fake_adbd.py <port> <keys_file> <log_file>
Needs: pip install cryptography
"""
import base64
import os
import socket
import struct
import sys
import threading

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding, rsa, utils

CNXN, AUTH, OPEN, OKAY, CLSE, WRTE = (0x4E584E43, 0x48545541, 0x4E45504F, 0x59414B4F, 0x45534C43, 0x45545257)
PORT, KEYS_FILE, LOG_FILE = int(sys.argv[1]), sys.argv[2], sys.argv[3]
lock = threading.Lock()


def log(line):
    with lock, open(LOG_FILE, "a") as f:
        f.write(line + "\n")


def send(conn, cmd, a0, a1, data=b""):
    conn.sendall(struct.pack("<6I", cmd, a0, a1, len(data), sum(data) & 0xFFFFFFFF, cmd ^ 0xFFFFFFFF) + data)


def recv(conn):
    head = b""
    while len(head) < 24:
        chunk = conn.recv(24 - len(head))
        if not chunk:
            raise EOFError
        head += chunk
    cmd, a0, a1, n, crc, magic = struct.unpack("<6I", head)
    assert magic == cmd ^ 0xFFFFFFFF, "bad magic"
    data = b""
    while len(data) < n:
        chunk = conn.recv(n - len(data))
        if not chunk:
            raise EOFError
        data += chunk
    assert crc == sum(data) & 0xFFFFFFFF, "bad checksum"
    return cmd, a0, a1, data


def parse_android_key(blob):
    text = blob.rstrip(b"\x00").decode()
    b64, _, name = text.partition(" ")
    raw = base64.b64decode(b64)
    words = struct.unpack_from("<I", raw, 0)[0]
    assert words == 64, f"expected 64 words, got {words}"
    n0inv = struct.unpack_from("<I", raw, 4)[0]
    n_words = struct.unpack_from(f"<{words}I", raw, 8)
    rr_words = struct.unpack_from(f"<{words}I", raw, 8 + 4 * words)
    e = struct.unpack_from("<I", raw, 8 + 8 * words)[0]
    n = sum(w << (32 * i) for i, w in enumerate(n_words))
    rr = sum(w << (32 * i) for i, w in enumerate(rr_words))
    assert (n * n0inv) % (1 << 32) == (1 << 32) - 1, "n0inv is not -1/n mod 2^32"
    assert rr == pow(1 << 2048, 2, n), "rr is not R^2 mod n"
    assert e == 65537, "unexpected exponent"
    return rsa.RSAPublicNumbers(e, n).public_key(), name


def verifies(pub, token, sig):
    try:
        pub.verify(sig, token, padding.PKCS1v15(), utils.Prehashed(hashes.SHA1()))
        return True
    except InvalidSignature:
        return False


def load_keys():
    if not os.path.exists(KEYS_FILE):
        return []
    with open(KEYS_FILE, "rb") as f:
        return [parse_android_key(line.strip() + b"\x00")[0] for line in f if line.strip()]


def handle(conn):
    try:
        cmd, version, maxdata, banner = recv(conn)
        assert cmd == CNXN and banner.startswith(b"host::"), "client must open with CNXN host::"
        log(f"CNXN version={version:#x} maxdata={maxdata}")
        token = os.urandom(20)
        send(conn, AUTH, 1, 0, token)
        last_sig = None
        while True:
            cmd, a0, a1, data = recv(conn)
            assert cmd == AUTH, f"expected AUTH, got {cmd:#x}"
            if a0 == 2:
                last_sig = data
                if any(verifies(k, token, data) for k in load_keys()):
                    log("AUTH signature accepted (known key)")
                    break
                log("AUTH signature from unknown key")
                token = os.urandom(20)
                send(conn, AUTH, 1, 0, token)
            elif a0 == 3:
                pub, name = parse_android_key(data)
                log(f"AUTH public key received, layout valid, name={name}")
                log("USER PRESSED ALLOW")
                with open(KEYS_FILE, "ab") as f:
                    f.write(data.rstrip(b"\x00") + b"\n")
                break
        send(conn, CNXN, 0x01000001, 256 * 1024, b"device::ro.product.name=fake;features=shell_v2\x00")
        remote_id = 100
        while True:
            cmd, a0, a1, data = recv(conn)
            if cmd != OPEN:
                continue
            local = a0
            remote_id += 1
            service = data.rstrip(b"\x00").decode()
            log(f"OPEN {service}")
            send(conn, OKAY, remote_id, local)
            send(conn, WRTE, remote_id, local, f"ran: {service}\n".encode())
            c, x, y, _ = recv(conn)
            assert c == OKAY and x == local and y == remote_id, "client must OKAY each WRTE with its ids"
            send(conn, CLSE, remote_id, local)
            c, x, y, _ = recv(conn)
            assert c == CLSE and x == local and y == remote_id, "client must answer CLSE"
    except EOFError:
        pass
    except AssertionError as e:
        log(f"PROTOCOL ERROR: {e}")
    finally:
        conn.close()


srv = socket.socket()
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", PORT))
srv.listen(4)
log("listening")
while True:
    c, _ = srv.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
