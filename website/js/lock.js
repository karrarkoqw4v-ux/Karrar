/* Tiny dependency-free SHA-256 / HMAC / PBKDF2 so the download lock also works on plain http:// hosts
   (the browser's WebCrypto exists only on https). Scheme (v2): key = PBKDF2-HMAC-SHA256(password, salt, iter, 64 bytes);
   enc key = bytes 0..31, mac key = bytes 32..63; keystream = SHA256(encKey || iv || counter32be); tag = HMAC-SHA256(macKey, iv || ct).
   unlock() returns the decrypted text, or null when the password is wrong. */
(function (root) {
  "use strict";
  var K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2]);
  var INIT = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
  var W = new Uint32Array(64);

  function compress(H, b, o) { // b: Uint32Array of words, o: word offset
    var i, a = H[0], bb = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7], t1, t2, x, y;
    for (i = 0; i < 16; i++) W[i] = b[o + i];
    for (i = 16; i < 64; i++) {
      x = W[i - 15]; y = W[i - 2];
      W[i] = (W[i - 16] + (((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3)) + W[i - 7] + (((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10))) | 0;
    }
    for (i = 0; i < 64; i++) {
      t1 = (h + (((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7))) + ((e & f) ^ (~e & g)) + K[i] + W[i]) | 0;
      t2 = ((((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10))) + ((a & bb) ^ (a & c) ^ (bb & c))) | 0;
      h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = bb; bb = a; a = (t1 + t2) | 0;
    }
    H[0] = (H[0] + a) | 0; H[1] = (H[1] + bb) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0;
    H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0;
  }

  /* hash bytes starting from state H (copied), where prefixLen bytes were already consumed */
  function sha(bytes, H0, prefixLen) {
    var H = Uint32Array.from(H0 || INIT), n = bytes.length, total = (prefixLen || 0) + n;
    var padded = new Uint8Array(((n + 9 + 63) >> 6) << 6);
    padded.set(bytes); padded[n] = 0x80;
    var bits = total * 8, len = padded.length;
    padded[len - 4] = (bits >>> 24) & 255; padded[len - 3] = (bits >>> 16) & 255; padded[len - 2] = (bits >>> 8) & 255; padded[len - 1] = bits & 255;
    padded[len - 8] = Math.floor(total / 0x20000000) & 255; // high bits of the 64-bit length (unused for small inputs)
    var words = new Uint32Array(len >> 2), i;
    for (i = 0; i < words.length; i++) words[i] = (padded[4 * i] << 24) | (padded[4 * i + 1] << 16) | (padded[4 * i + 2] << 8) | padded[4 * i + 3];
    for (i = 0; i < words.length; i += 16) compress(H, words, i);
    return H;
  }
  function toBytes(H) {
    var o = new Uint8Array(32);
    for (var i = 0; i < 8; i++) { o[4 * i] = H[i] >>> 24; o[4 * i + 1] = H[i] >>> 16; o[4 * i + 2] = H[i] >>> 8; o[4 * i + 3] = H[i]; }
    return o;
  }
  function sha256(b) { return toBytes(sha(b)); }
  function padKey(key, v) {
    var k = key.length > 64 ? sha256(key) : key, o = new Uint8Array(64), i;
    for (i = 0; i < 64; i++) o[i] = (i < k.length ? k[i] : 0) ^ v;
    return o;
  }
  function wordsOf(b) {
    var w = new Uint32Array(b.length >> 2);
    for (var i = 0; i < w.length; i++) w[i] = (b[4 * i] << 24) | (b[4 * i + 1] << 16) | (b[4 * i + 2] << 8) | b[4 * i + 3];
    return w;
  }
  function hmac(key, msg) {
    var ip = padKey(key, 0x36), op = padKey(key, 0x5c);
    var inner = sha256(concat(ip, msg));
    return sha256(concat(op, inner));
  }
  function concat() {
    var n = 0, i, a = arguments;
    for (i = 0; i < a.length; i++) n += a[i].length;
    var o = new Uint8Array(n), p = 0;
    for (i = 0; i < a.length; i++) { o.set(a[i], p); p += a[i].length; }
    return o;
  }
  function pbkdf2(pw, salt, iter, outLen) {
    var Hi = Uint32Array.from(INIT), Ho = Uint32Array.from(INIT);
    compress(Hi, wordsOf(padKey(pw, 0x36)), 0); compress(Ho, wordsOf(padKey(pw, 0x5c)), 0);
    var blocks = Math.ceil(outLen / 32), out = new Uint8Array(blocks * 32), b, k, i;
    var blk = new Uint32Array(16), H = new Uint32Array(8), T = new Uint32Array(8), U = new Uint32Array(8);
    for (b = 1; b <= blocks; b++) {
      var m = concat(salt, new Uint8Array([b >>> 24, b >>> 16, b >>> 8, b]));
      U = sha(toBytes(sha(m, Hi, 64)), Ho, 64); // U1 = HMAC(pw, salt||INT(b))
      T.set(U);
      for (k = 1; k < iter; k++) {
        for (i = 0; i < 8; i++) blk[i] = U[i];
        blk[8] = 0x80000000; for (i = 9; i < 15; i++) blk[i] = 0; blk[15] = 768;
        H.set(Hi); compress(H, blk, 0);
        for (i = 0; i < 8; i++) blk[i] = H[i];
        H.set(Ho); compress(H, blk, 0);
        U.set(H);
        for (i = 0; i < 8; i++) T[i] ^= U[i];
      }
      out.set(toBytes(T), (b - 1) * 32);
    }
    return out.subarray(0, outLen);
  }
  function b64(s) {
    var bin = typeof atob === "function" ? atob(s) : Buffer.from(s, "base64").toString("binary"), o = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) o[i] = bin.charCodeAt(i);
    return o;
  }
  function utf8(s) {
    if (typeof TextEncoder !== "undefined") return new TextEncoder().encode(s);
    return new Uint8Array(Buffer.from(s, "utf8"));
  }
  function unlock(password, L) {
    var km = pbkdf2(utf8(password), b64(L.salt), L.iter, 64), ek = km.subarray(0, 32), mk = km.subarray(32, 64);
    var iv = b64(L.iv), ct = b64(L.ct), tag = b64(L.tag), want = hmac(mk, concat(iv, ct)), diff = 0, i;
    for (i = 0; i < 32; i++) diff |= want[i] ^ tag[i];
    if (diff !== 0) return null;
    var out = new Uint8Array(ct.length);
    for (i = 0; i < ct.length; i += 32) {
      var ks = sha256(concat(ek, iv, new Uint8Array([(i / 32) >>> 24, (i / 32) >>> 16, (i / 32) >>> 8, (i / 32) & 255])));
      for (var j = 0; j < 32 && i + j < ct.length; j++) out[i + j] = ct[i + j] ^ ks[j];
    }
    return typeof TextDecoder !== "undefined" ? new TextDecoder().decode(out) : Buffer.from(out).toString("utf8");
  }
  root.BawsalaLock = { unlock: unlock, sha256: sha256, pbkdf2: pbkdf2, hmac: hmac };
  if (typeof module !== "undefined") module.exports = root.BawsalaLock;
})(typeof window !== "undefined" ? window : globalThis);
