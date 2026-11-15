/* AuralPlayer — Ed25519 puro en JavaScript (RFC 8032).
 *
 * Un solo archivo, sin CDN, sin dependencias, sin nada externo.
 * Funciona en el navegador y en Node. Todo es síncrono.
 *
 * Expone window.AuralEd25519 (y module.exports en Node) con:
 *   keyPairFromSeedHex(seedHex)  -> { publicKeyHex, publicKeyBytes }
 *   signPayload(payload, seedHex) -> firma de 64 bytes (Uint8Array)
 *   verifyPayload(payload, sigBytes, pubKeyBytes) -> true/false
 *   buildCode(deviceId, plan, expiry, seedHex) -> { code, payload, publicKeyHex, expiry }
 *   parseCode(code) -> { deviceId, plan, expiry, payload, signatureBytes }
 *   expiryForPlan(plan, nowSec) -> segundos epoch (0 = lifetime)
 *   base32Encode(bytes) / base32Decode(text) (RFC 4648, sin padding)
 *   hexToBytes / bytesToHex
 *
 * Formato del código (lo verifica la app): payload "deviceId|plan|expiry"
 * (plan = M/A/L, expiry = segundos epoch, 0 = lifetime). Firma Ed25519
 * sobre los bytes UTF-8 del payload. Código = Base32 sin padding de
 * UTF8(payload) + firma (64 bytes), en grupos de 5 con guiones.
 */
(function (root) {
  'use strict';

  /* ---------- utilidades ---------- */

  var MASK64 = (1n << 64n) - 1n;

  function utf8Encode(str) {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(str);
    var out = [], i, c;
    for (i = 0; i < str.length; i++) {
      c = str.charCodeAt(i);
      if (c < 0x80) out.push(c);
      else if (c < 0x800) { out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f)); }
      else if (c >= 0xd800 && c < 0xdc00) {
        var hi = c, lo = str.charCodeAt(++i);
        var cp = 0x10000 + ((hi - 0xd800) << 10) + (lo - 0xdc00);
        out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
      } else out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
    }
    return new Uint8Array(out);
  }

  function utf8Decode(bytes) {
    if (typeof TextDecoder !== 'undefined') return new TextDecoder().decode(bytes);
    var s = '', i;
    for (i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return s;
  }

  function concatBytes() {
    var total = 0, i, j, k = 0, out;
    for (i = 0; i < arguments.length; i++) total += arguments[i].length;
    out = new Uint8Array(total);
    for (i = 0; i < arguments.length; i++) {
      for (j = 0; j < arguments[i].length; j++) out[k++] = arguments[i][j];
    }
    return out;
  }

  function hexToBytes(hex) {
    if (typeof hex !== 'string') throw new Error('La clave debe ser texto hexadecimal.');
    hex = hex.replace(/[\s:]/g, '').toLowerCase();
    if (!/^[0-9a-f]*$/.test(hex)) throw new Error('La clave solo puede tener caracteres 0-9 y a-f.');
    if (hex.length % 2 !== 0) throw new Error('La clave hexadecimal tiene longitud impar.');
    var out = new Uint8Array(hex.length / 2), i;
    for (i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
  }

  function bytesToHex(bytes) {
    var s = '', i;
    for (i = 0; i < bytes.length; i++) s += ('0' + bytes[i].toString(16)).slice(-2);
    return s;
  }

  /* ---------- SHA-512 (FIPS 180-4, con BigInt) ---------- */

  var K512 = [
    0x428a2f98d728ae22n, 0x7137449123ef65cdn, 0xb5c0fbcfec4d3b2fn, 0xe9b5dba58189dbbcn,
    0x3956c25bf348b538n, 0x59f111f1b605d019n, 0x923f82a4af194f9bn, 0xab1c5ed5da6d8118n,
    0xd807aa98a3030242n, 0x12835b0145706fben, 0x243185be4ee4b28cn, 0x550c7dc3d5ffb4e2n,
    0x72be5d74f27b896fn, 0x80deb1fe3b1696b1n, 0x9bdc06a725c71235n, 0xc19bf174cf692694n,
    0xe49b69c19ef14ad2n, 0xefbe4786384f25e3n, 0x0fc19dc68b8cd5b5n, 0x240ca1cc77ac9c65n,
    0x2de92c6f592b0275n, 0x4a7484aa6ea6e483n, 0x5cb0a9dcbd41fbd4n, 0x76f988da831153b5n,
    0x983e5152ee66dfabn, 0xa831c66d2db43210n, 0xb00327c898fb213fn, 0xbf597fc7beef0ee4n,
    0xc6e00bf33da88fc2n, 0xd5a79147930aa725n, 0x06ca6351e003826fn, 0x142929670a0e6e70n,
    0x27b70a8546d22ffcn, 0x2e1b21385c26c926n, 0x4d2c6dfc5ac42aedn, 0x53380d139d95b3dfn,
    0x650a73548baf63den, 0x766a0abb3c77b2a8n, 0x81c2c92e47edaee6n, 0x92722c851482353bn,
    0xa2bfe8a14cf10364n, 0xa81a664bbc423001n, 0xc24b8b70d0f89791n, 0xc76c51a30654be30n,
    0xd192e819d6ef5218n, 0xd69906245565a910n, 0xf40e35855771202an, 0x106aa07032bbd1b8n,
    0x19a4c116b8d2d0c8n, 0x1e376c085141ab53n, 0x2748774cdf8eeb99n, 0x34b0bcb5e19b48a8n,
    0x391c0cb3c5c95a63n, 0x4ed8aa4ae3418acbn, 0x5b9cca4f7763e373n, 0x682e6ff3d6b2b8a3n,
    0x748f82ee5defb2fcn, 0x78a5636f43172f60n, 0x84c87814a1f0ab72n, 0x8cc702081a6439ecn,
    0x90befffa23631e28n, 0xa4506cebde82bde9n, 0xbef9a3f7b2c67915n, 0xc67178f2e372532bn,
    0xca273eceea26619cn, 0xd186b8c721c0c207n, 0xeada7dd6cde0eb1en, 0xf57d4f7fee6ed178n,
    0x06f067aa72176fban, 0x0a637dc5a2c898a6n, 0x113f9804bef90daen, 0x1b710b35131c471bn,
    0x28db77f523047d84n, 0x32caab7b40c72493n, 0x3c9ebe0a15c9bebcn, 0x431d67c49c100d4cn,
    0x4cc5d4becb3e42b6n, 0x597f299cfc657e2an, 0x5fcb6fab3ad6faecn, 0x6c44198c4a475817n
  ];

  function rotr64(x, n) {
    n = BigInt(n);
    return ((x >> n) | (x << (64n - n))) & MASK64;
  }

  function sha512(bytes) {
    var h = [
      0x6a09e667f3bcc908n, 0xbb67ae8584caa73bn, 0x3c6ef372fe94f82bn, 0xa54ff53a5f1d36f1n,
      0x510e527fade682d1n, 0x9b05688c2b3e6c1fn, 0x1f83d9abfb41bd6bn, 0x5be0cd19137e2179n
    ];
    var bitLen = BigInt(bytes.length) * 8n;
    var totalLen = bytes.length + 1 + 16;
    while (totalLen % 128 !== 0) totalLen++;
    var msg = new Uint8Array(totalLen);
    msg.set(bytes, 0);
    msg[bytes.length] = 0x80;
    var hi = bitLen >> 64n, lo = bitLen & MASK64;
    for (var i = 0; i < 8; i++) msg[totalLen - 16 + i] = Number((hi >> BigInt(56 - 8 * i)) & 0xffn);
    for (i = 0; i < 8; i++) msg[totalLen - 8 + i] = Number((lo >> BigInt(56 - 8 * i)) & 0xffn);

    var w = new Array(80);
    for (var off = 0; off < totalLen; off += 128) {
      for (i = 0; i < 16; i++) {
        var v = 0n;
        for (var j = 0; j < 8; j++) v = (v << 8n) | BigInt(msg[off + i * 8 + j]);
        w[i] = v;
      }
      for (i = 16; i < 80; i++) {
        var s0 = rotr64(w[i - 15], 1) ^ rotr64(w[i - 15], 8) ^ (w[i - 15] >> 7n);
        var s1 = rotr64(w[i - 2], 19) ^ rotr64(w[i - 2], 61) ^ (w[i - 2] >> 6n);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) & MASK64;
      }
      var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
      for (i = 0; i < 80; i++) {
        var S1 = rotr64(e, 14) ^ rotr64(e, 18) ^ rotr64(e, 41);
        var ch = (e & f) ^ ((~e & MASK64) & g);
        var t1 = (hh + S1 + ch + K512[i] + w[i]) & MASK64;
        var S0 = rotr64(a, 28) ^ rotr64(a, 34) ^ rotr64(a, 39);
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + maj) & MASK64;
        hh = g; g = f; f = e; e = (d + t1) & MASK64; d = c; c = b; b = a; a = (t1 + t2) & MASK64;
      }
      h[0] = (h[0] + a) & MASK64; h[1] = (h[1] + b) & MASK64;
      h[2] = (h[2] + c) & MASK64; h[3] = (h[3] + d) & MASK64;
      h[4] = (h[4] + e) & MASK64; h[5] = (h[5] + f) & MASK64;
      h[6] = (h[6] + g) & MASK64; h[7] = (h[7] + hh) & MASK64;
    }
    var out = new Uint8Array(64);
    for (i = 0; i < 8; i++) {
      for (j = 0; j < 8; j++) out[i * 8 + j] = Number((h[i] >> BigInt(56 - 8 * j)) & 0xffn);
    }
    return out;
  }

  /* ---------- Base32 RFC 4648, sin padding ---------- */

  var B32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  var B32_LOOKUP = {};
  (function () {
    for (var i = 0; i < B32_ALPHABET.length; i++) B32_LOOKUP[B32_ALPHABET[i]] = i;
  })();

  function base32Encode(bytes) {
    var out = '', i, bits = 0, value = 0;
    for (i = 0; i < bytes.length; i++) {
      value = (value << 8) | bytes[i];
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        out += B32_ALPHABET[(value >> bits) & 31];
      }
    }
    if (bits > 0) out += B32_ALPHABET[(value << (5 - bits)) & 31];
    return out;
  }

  function base32Decode(text) {
    if (typeof text !== 'string') throw new Error('El código debe ser texto.');
    text = text.replace(/[\s\-]/g, '').toUpperCase();
    if (text.length === 0) throw new Error('El código está vacío.');
    if (/[^A-Z2-7]/.test(text)) throw new Error('El código tiene caracteres que no son Base32.');
    var rem = text.length % 8;
    if (rem === 1 || rem === 3 || rem === 6) throw new Error('El código tiene una longitud Base32 inválida.');
    var bytes = [], i, bits = 0, value = 0;
    for (i = 0; i < text.length; i++) {
      value = (value << 5) | B32_LOOKUP[text[i]];
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        bytes.push((value >> bits) & 0xff);
      }
    }
    return new Uint8Array(bytes);
  }

  /* ---------- Ed25519 (RFC 8032) ---------- */

  var P = (1n << 255n) - 19n;
  var L = (1n << 252n) + 27742317777372353535851937790883648493n;

  function mod(a) {
    a = a % P;
    return a < 0n ? a + P : a;
  }

  function modPow(base, exp, m) {
    var r = 1n;
    base = ((base % m) + m) % m;
    while (exp > 0n) {
      if (exp & 1n) r = (r * base) % m;
      base = (base * base) % m;
      exp >>= 1n;
    }
    return r;
  }

  function modInv(a) {
    return modPow(mod(a), P - 2n, P);
  }

  var D = mod(-121665n * modInv(121666n));
  var SQRT_M1 = modPow(2n, (P - 1n) / 4n, P); // raíz de -1 mod p

  function sqrtModP(u) {
    u = mod(u);
    var x = modPow(u, (P + 3n) / 8n, P);
    if (mod(x * x - u) !== 0n) x = mod(x * SQRT_M1);
    if (mod(x * x - u) !== 0n) throw new Error('Sin raíz cuadrada.');
    return x;
  }

  function xRecover(y, sign) {
    var xx = mod((y * y - 1n) * modInv(D * y * y + 1n));
    var x = sqrtModP(xx);
    if ((x & 1n) !== BigInt(sign)) x = P - x;
    return x;
  }

  // Punto en coordenadas extendidas (X, Y, Z, T): x = X/Z, y = Y/Z, xy = T/Z.
  function pointAdd(p1, p2) {
    var A = mod((p1.Y - p1.X) * (p2.Y - p2.X));
    var B = mod((p1.Y + p1.X) * (p2.Y + p2.X));
    var C = mod(2n * D * p1.T * p2.T);
    var Dd = mod(2n * p1.Z * p2.Z);
    var E = mod(B - A), F = mod(Dd - C), G = mod(Dd + C), H = mod(B + A);
    return { X: mod(E * F), Y: mod(G * H), T: mod(E * H), Z: mod(F * G) };
  }

  function pointDouble(p1) {
    var A = mod(p1.X * p1.X), B = mod(p1.Y * p1.Y), C = mod(2n * p1.Z * p1.Z);
    var Dd = mod(-A), E = mod((p1.X + p1.Y) * (p1.X + p1.Y) - A - B);
    var G = mod(Dd + B), F = mod(G - C), H = mod(Dd - B);
    return { X: mod(E * F), Y: mod(G * H), T: mod(E * H), Z: mod(F * G) };
  }

  function scalarMult(s, pt) {
    var r = { X: 0n, Y: 1n, Z: 1n, T: 0n }; // identidad
    var bits = s.toString(2);
    for (var i = 0; i < bits.length; i++) {
      r = pointDouble(r);
      if (bits[i] === '1') r = pointAdd(r, pt);
    }
    return r;
  }

  function pointToBytes(pt) {
    var zInv = modInv(pt.Z);
    var x = mod(pt.X * zInv), y = mod(pt.Y * zInv);
    var out = new Uint8Array(32);
    var v = y;
    for (var i = 0; i < 32; i++) {
      out[i] = Number(v & 0xffn);
      v >>= 8n;
    }
    if ((x & 1n) === 1n) out[31] |= 0x80;
    return out;
  }

  function pointFromBytes(bytes) {
    if (bytes.length !== 32) throw new Error('Punto inválido.');
    var sign = (bytes[31] >> 7) & 1;
    // Copia: Uint8Array.prototype.slice copia, pero Buffer.slice comparte
    // memoria y el &= de abajo mutaría la clave del llamador.
    var yBytes = new Uint8Array(bytes);
    yBytes[31] &= 0x7f;
    var y = 0n;
    for (var i = 31; i >= 0; i--) y = (y << 8n) | BigInt(yBytes[i]);
    if (y >= P) throw new Error('Punto fuera de la curva.');
    var x = xRecover(y, sign);
    return { X: x, Y: y, Z: 1n, T: mod(x * y) };
  }

  var BASE_Y = mod(4n * modInv(5n));
  var BASE_X = xRecover(BASE_Y, 0);
  var BASE = { X: BASE_X, Y: BASE_Y, Z: 1n, T: mod(BASE_X * BASE_Y) };

  function leToBigInt(bytes) {
    var v = 0n;
    for (var i = bytes.length - 1; i >= 0; i--) v = (v << 8n) | BigInt(bytes[i]);
    return v;
  }

  function bigIntToLe(v, len) {
    var out = new Uint8Array(len);
    for (var i = 0; i < len; i++) {
      out[i] = Number(v & 0xffn);
      v >>= 8n;
    }
    return out;
  }

  function clampScalar(h) {
    var a = h.slice(0, 32);
    a[0] &= 248; a[31] &= 127; a[31] |= 64;
    return leToBigInt(a);
  }

  function keyPairFromSeed(seedBytes) {
    if (!(seedBytes instanceof Uint8Array) || seedBytes.length !== 32) {
      throw new Error('La semilla debe tener 32 bytes.');
    }
    var h = sha512(seedBytes);
    var a = clampScalar(h);
    var pub = pointToBytes(scalarMult(a, BASE));
    return { secretScalar: a, prefix: h.slice(32, 64), publicKey: pub };
  }

  function signBytes(messageBytes, seedBytes) {
    var kp = keyPairFromSeed(seedBytes);
    var r = leToBigInt(sha512(concatBytes(kp.prefix, messageBytes))) % L;
    var Rb = pointToBytes(scalarMult(r, BASE));
    var k = leToBigInt(sha512(concatBytes(Rb, kp.publicKey, messageBytes))) % L;
    var S = (r + k * kp.secretScalar) % L;
    return concatBytes(Rb, bigIntToLe(S, 32));
  }

  function verifyBytes(messageBytes, sigBytes, pubBytes) {
    try {
      if (sigBytes.length !== 64 || pubBytes.length !== 32) return false;
      var R = pointFromBytes(sigBytes.slice(0, 32));
      var A = pointFromBytes(pubBytes);
      var S = leToBigInt(sigBytes.slice(32, 64));
      if (S >= L) return false;
      var h = leToBigInt(sha512(concatBytes(sigBytes.slice(0, 32), pubBytes, messageBytes))) % L;
      var left = scalarMult(S, BASE);
      var right = pointAdd(R, scalarMult(h, A));
      var lx = mod(left.X * modInv(left.Z)), ly = mod(left.Y * modInv(left.Z));
      var rx = mod(right.X * modInv(right.Z)), ry = mod(right.Y * modInv(right.Z));
      return lx === rx && ly === ry;
    } catch (e) {
      return false;
    }
  }

  /* ---------- Código de activación Aural Pro ---------- */

  var PLANS = { M: 'Mensual', A: 'Anual', L: 'Lifetime' };

  function expiryForPlan(plan, nowSec) {
    if (plan === 'L') return 0;
    if (plan === 'M') return nowSec + 30 * 86400;
    if (plan === 'A') return nowSec + 365 * 86400;
    throw new Error('Plan inválido. Usa M, A o L.');
  }

  function validateDeviceId(deviceId) {
    if (typeof deviceId !== 'string') throw new Error('El ID del equipo debe ser texto.');
    deviceId = deviceId.trim();
    if (!deviceId) throw new Error('Falta el ID del equipo.');
    if (deviceId.indexOf('|') !== -1) throw new Error('El ID del equipo no puede tener el carácter |.');
    if (/\s/.test(deviceId)) throw new Error('El ID del equipo no puede tener espacios.');
    if (deviceId.length > 64) throw new Error('El ID del equipo es demasiado largo.');
    return deviceId;
  }

  function buildCode(deviceId, plan, expiry, seedHex) {
    deviceId = validateDeviceId(deviceId);
    if (!PLANS[plan]) throw new Error('Plan inválido. Usa M (Mensual), A (Anual) o L (Lifetime).');
    expiry = Number(expiry);
    if (!Number.isInteger(expiry) || expiry < 0) throw new Error('La fecha de vencimiento no es válida.');
    var seed = hexToBytes(seedHex);
    if (seed.length !== 32) throw new Error('La clave privada debe ser una semilla de 32 bytes (64 caracteres hex).');
    var payload = deviceId + '|' + plan + '|' + String(expiry);
    var sig = signBytes(utf8Encode(payload), seed);
    var raw = concatBytes(utf8Encode(payload), sig);
    var b32 = base32Encode(raw);
    var grouped = b32.match(/.{1,5}/g).join('-');
    var kp = keyPairFromSeed(seed);
    return {
      code: grouped,
      payload: payload,
      deviceId: deviceId,
      plan: plan,
      planName: PLANS[plan],
      expiry: expiry,
      publicKeyHex: bytesToHex(kp.publicKey)
    };
  }

  function parseCode(code) {
    var raw = base32Decode(code);
    if (raw.length < 65) throw new Error('El código es demasiado corto.');
    var payloadBytes = raw.slice(0, raw.length - 64);
    var sigBytes = raw.slice(raw.length - 64);
    var payload = utf8Decode(payloadBytes);
    var parts = payload.split('|');
    if (parts.length !== 3) throw new Error('El código no tiene el formato deviceId|plan|expiry.');
    var expiry = Number(parts[2]);
    if (!PLANS[parts[1]] || !Number.isInteger(expiry) || expiry < 0) {
      throw new Error('El código tiene un plan o vencimiento inválido.');
    }
    return {
      deviceId: parts[0], plan: parts[1], planName: PLANS[parts[1]],
      expiry: expiry, payload: payload, signatureBytes: sigBytes
    };
  }

  function keyPairFromSeedHex(seedHex) {
    var seed = hexToBytes(seedHex);
    if (seed.length !== 32) throw new Error('La clave privada debe ser una semilla de 32 bytes (64 caracteres hex).');
    var kp = keyPairFromSeed(seed);
    return { publicKeyHex: bytesToHex(kp.publicKey), publicKeyBytes: kp.publicKey };
  }

  function randomSeedHex() {
    var bytes = new Uint8Array(32);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(bytes);
    } else {
      for (var i = 0; i < 32; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    return bytesToHex(bytes);
  }

  var api = {
    sha512: sha512,
    base32Encode: base32Encode,
    base32Decode: base32Decode,
    hexToBytes: hexToBytes,
    bytesToHex: bytesToHex,
    keyPairFromSeedHex: keyPairFromSeedHex,
    randomSeedHex: randomSeedHex,
    signPayload: function (payload, seedHex) {
      var seed = hexToBytes(seedHex);
      if (seed.length !== 32) throw new Error('La clave privada debe ser una semilla de 32 bytes (64 caracteres hex).');
      return signBytes(utf8Encode(payload), seed);
    },
    verifyPayload: function (payload, sigBytes, pubKeyBytes) {
      return verifyBytes(utf8Encode(payload), sigBytes, pubKeyBytes);
    },
    buildCode: buildCode,
    parseCode: parseCode,
    expiryForPlan: expiryForPlan,
    validateDeviceId: validateDeviceId,
    PLANS: PLANS
  };

  root.AuralEd25519 = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));
