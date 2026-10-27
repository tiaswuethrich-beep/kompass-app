package ch.akh.kompass.portfolio

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Bitcoin nur zum Anschauen (read-only).
 *
 * Aus dem öffentlichen Konto-Schlüssel (xpub/ypub/zpub), den Ledger Live anzeigt, werden die
 * Adressen direkt auf dem Handy berechnet. Der Schlüssel selbst verlässt das Handy nie.
 * Mit einem xpub kann niemand Geld bewegen, er verrät aber alle Adressen und Beträge.
 * Darum wird er verschlüsselt gespeichert und nie an einen Server geschickt.
 *
 * Keine Bibliothek nötig: secp256k1, BIP32 (öffentliche Ableitung), Base58, Bech32/Bech32m
 * und RIPEMD-160 sind hier selbst umgesetzt und mit den offiziellen Testvektoren geprüft.
 */
object Btc {

    enum class AddrType(val label: String, val short: String) {
        NATIVE_SEGWIT("Native SegWit (bc1q…)", "bc1q"),
        TAPROOT("Taproot (bc1p…)", "bc1p"),
        NESTED_SEGWIT("SegWit (3…)", "3…"),
        LEGACY("Legacy (1…)", "1…"),
    }

    class ExtKey(val pub: ByteArray, val chain: ByteArray, val depth: Int, val hint: AddrType?)

    // ---------------- secp256k1 ----------------

    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val G = Pt(
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
    )
    private val SEVEN = BigInteger.valueOf(7)

    /** Punkt auf der Kurve; null steht für den Punkt im Unendlichen. */
    private data class Pt(val x: BigInteger, val y: BigInteger)

    private fun add(a: Pt?, b: Pt?): Pt? {
        if (a == null) return b
        if (b == null) return a
        if (a.x == b.x) {
            if ((a.y + b.y).mod(P) == BigInteger.ZERO) return null
            val l = (BigInteger.valueOf(3) * a.x * a.x).mod(P) * (BigInteger.valueOf(2) * a.y).modInverse(P) % P
            val x = (l * l - a.x - a.x).mod(P)
            return Pt(x, (l * (a.x - x) - a.y).mod(P))
        }
        val l = (b.y - a.y).mod(P) * (b.x - a.x).mod(P).modInverse(P) % P
        val x = (l * l - a.x - b.x).mod(P)
        return Pt(x, (l * (a.x - x) - a.y).mod(P))
    }

    private fun mul(k: BigInteger, p: Pt): Pt? {
        var r: Pt? = null
        var q: Pt? = p
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) r = add(r, q)
            q = add(q, q)
        }
        return r
    }

    private fun decompress(b: ByteArray): Pt {
        require(b.size == 33 && (b[0].toInt() == 2 || b[0].toInt() == 3)) { "Ungültiger öffentlicher Schlüssel" }
        val x = BigInteger(1, b.copyOfRange(1, 33))
        val y2 = (x.modPow(BigInteger.valueOf(3), P) + SEVEN).mod(P)
        var y = y2.modPow((P + BigInteger.ONE).shiftRight(2), P)
        require(y.modPow(BigInteger.valueOf(2), P) == y2) { "Punkt liegt nicht auf der Kurve" }
        if (y.testBit(0) != (b[0].toInt() == 3)) y = P - y
        return Pt(x, y)
    }

    private fun be32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val n = minOf(raw.size, 32)
        System.arraycopy(raw, raw.size - n, out, 32 - n, n)
        return out
    }

    private fun compress(p: Pt): ByteArray = byteArrayOf(if (p.y.testBit(0)) 3 else 2) + be32(p.x)

    // ---------------- Hashes ----------------

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hmac512(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA512").run { init(SecretKeySpec(key, "HmacSHA512")); doFinal(data) }
    private fun hash160(b: ByteArray) = Ripemd160.digest(sha256(b))
    private fun taggedHash(tag: String, msg: ByteArray): ByteArray {
        val t = sha256(tag.toByteArray())
        return sha256(t + t + msg)
    }

    // ---------------- Base58 ----------------

    private const val B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun base58Check(payload: ByteArray): String {
        val data = payload + sha256(sha256(payload)).copyOf(4)
        var n = BigInteger(1, data)
        val sb = StringBuilder()
        val b58 = BigInteger.valueOf(58)
        while (n > BigInteger.ZERO) {
            val qr = n.divideAndRemainder(b58)
            sb.append(B58[qr[1].toInt()])
            n = qr[0]
        }
        for (b in data) if (b.toInt() == 0) sb.append('1') else break
        return sb.reverse().toString()
    }

    fun base58CheckDecode(s: String): ByteArray {
        var n = BigInteger.ZERO
        for (c in s) {
            val i = B58.indexOf(c)
            require(i >= 0) { "Ungültiges Zeichen '$c'" }
            n = n * BigInteger.valueOf(58) + BigInteger.valueOf(i.toLong())
        }
        var raw = n.toByteArray()
        if (raw.size > 1 && raw[0].toInt() == 0) raw = raw.copyOfRange(1, raw.size)
        val zeros = s.takeWhile { it == '1' }.length
        val full = ByteArray(zeros) + raw
        require(full.size > 4) { "Zu kurz" }
        val body = full.copyOfRange(0, full.size - 4)
        val sum = full.copyOfRange(full.size - 4, full.size)
        require(sha256(sha256(body)).copyOf(4).contentEquals(sum)) { "Prüfsumme stimmt nicht. Ist der Schlüssel vollständig kopiert?" }
        return body
    }

    // ---------------- Bech32 / Bech32m ----------------

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    private fun polymod(values: IntArray): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((b ushr i) and 1 == 1) chk = chk xor gen[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray =
        (hrp.map { it.code ushr 5 } + 0 + hrp.map { it.code and 31 }).toIntArray()

    private fun convertBits(data: ByteArray): IntArray {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        for (b in data) {
            acc = (acc shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out += (acc ushr bits) and 31
            }
        }
        if (bits > 0) out += (acc shl (5 - bits)) and 31
        return out.toIntArray()
    }

    private fun segwitAddress(version: Int, program: ByteArray): String {
        val hrp = "bc"
        val data = intArrayOf(version) + convertBits(program)
        val const = if (version == 0) 1 else 0x2bc830a3
        val pm = polymod(hrpExpand(hrp) + data + IntArray(6)) xor const
        val chk = IntArray(6) { (pm ushr (5 * (5 - it))) and 31 }
        return hrp + "1" + (data + chk).joinToString("") { CHARSET[it].toString() }
    }

    // ---------------- BIP32 ----------------

    /** Liest xpub, ypub oder zpub. ypub/zpub verraten den Adresstyp gleich mit. */
    fun parseExtKey(s: String): ExtKey {
        val b = base58CheckDecode(s.trim())
        require(b.size == 78) { "Das ist kein öffentlicher Konto-Schlüssel (xpub)." }
        val hint = when (b.copyOfRange(0, 4).joinToString("") { "%02x".format(it) }) {
            "0488b21e" -> null
            "049d7cb2" -> AddrType.NESTED_SEGWIT
            "04b24746" -> AddrType.NATIVE_SEGWIT
            "04358394", "045f1cf6", "044a5262" -> error("Das ist ein Testnet-Schlüssel.")
            "0488ade4", "049d7878", "04b2430c" -> error("Das ist ein PRIVATER Schlüssel. Bitte sofort löschen und niemandem zeigen. Kompass braucht nur den öffentlichen (xpub).")
            else -> error("Unbekanntes Schlüsselformat.")
        }
        val pub = b.copyOfRange(45, 78)
        decompress(pub)
        return ExtKey(pub, b.copyOfRange(13, 45), b[4].toInt() and 0xff, hint)
    }

    /** Nicht gehärtete Kind-Ableitung (CKDpub). Geht nur mit öffentlichen Daten. */
    fun child(k: ExtKey, index: Int): ExtKey {
        require(index >= 0)
        val data = k.pub + byteArrayOf((index ushr 24).toByte(), (index ushr 16).toByte(), (index ushr 8).toByte(), index.toByte())
        val i = hmac512(k.chain, data)
        val il = BigInteger(1, i.copyOfRange(0, 32))
        require(il < N) { "Ungültiger Ableitungsschritt" }
        val p = add(mul(il, G), decompress(k.pub)) ?: error("Ungültiger Ableitungsschritt")
        return ExtKey(compress(p), i.copyOfRange(32, 64), k.depth + 1, k.hint)
    }

    fun address(pub: ByteArray, type: AddrType): String = when (type) {
        AddrType.LEGACY -> base58Check(byteArrayOf(0) + hash160(pub))
        AddrType.NESTED_SEGWIT -> base58Check(byteArrayOf(5) + hash160(byteArrayOf(0, 20) + hash160(pub)))
        AddrType.NATIVE_SEGWIT -> segwitAddress(0, hash160(pub))
        AddrType.TAPROOT -> {
            val pt = decompress(pub)
            val xOnly = be32(pt.x)
            val even = if (pt.y.testBit(0)) Pt(pt.x, P - pt.y) else pt
            val t = BigInteger(1, taggedHash("TapTweak", xOnly))
            require(t < N)
            val q = add(even, mul(t, G)) ?: error("Ungültiger Taproot-Schlüssel")
            segwitAddress(1, be32(q.x))
        }
    }

    /** Adresse Nummer [index] auf der Empfangs- (change = 0) oder Wechselgeld-Kette (change = 1). */
    fun deriveAddress(account: ExtKey, change: Int, index: Int, type: AddrType): String =
        address(child(child(account, change), index).pub, type)

    /** Erkennt, ob ein Text eine einzelne Bitcoin-Adresse ist. */
    fun looksLikeAddress(s: String): Boolean {
        val t = s.trim()
        val l = t.lowercase()
        if (l.startsWith("bc1")) return (t == l || t == t.uppercase()) && Regex("^bc1[qp][02-9ac-hj-np-z]{38,58}$").matches(l)
        return Regex("^[13][1-9A-HJ-NP-Za-km-z]{25,34}$").matches(t)
    }
}

/** RIPEMD-160, weil Android den Algorithmus nicht zuverlässig mitliefert. */
object Ripemd160 {
    private val R1 = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12, 1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13,
    )
    private val R2 = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12, 6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13, 8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11,
    )
    private val S1 = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8, 7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5, 11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6,
    )
    private val S2 = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6, 9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5, 15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11,
    )
    private val K1 = intArrayOf(0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC.toInt(), 0xA953FD4E.toInt())
    private val K2 = intArrayOf(0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000)

    private fun f(j: Int, x: Int, y: Int, z: Int): Int = when (j / 16) {
        0 -> x xor y xor z
        1 -> (x and y) or (x.inv() and z)
        2 -> (x or y.inv()) xor z
        3 -> (x and z) or (y and z.inv())
        else -> x xor (y or z.inv())
    }

    fun digest(msg: ByteArray): ByteArray {
        val bitLen = msg.size.toLong() * 8
        val padLen = ((56 - (msg.size + 1) % 64) + 64) % 64
        val m = msg + byteArrayOf(0x80.toByte()) + ByteArray(padLen) + ByteArray(8) { (bitLen ushr (8 * it)).toByte() }
        var h0 = 0x67452301; var h1 = 0xEFCDAB89.toInt(); var h2 = 0x98BADCFE.toInt(); var h3 = 0x10325476; var h4 = 0xC3D2E1F0.toInt()
        val x = IntArray(16)
        for (blk in 0 until m.size / 64) {
            for (i in 0 until 16) {
                val o = blk * 64 + i * 4
                x[i] = (m[o].toInt() and 0xff) or ((m[o + 1].toInt() and 0xff) shl 8) or
                    ((m[o + 2].toInt() and 0xff) shl 16) or ((m[o + 3].toInt() and 0xff) shl 24)
            }
            var al = h0; var bl = h1; var cl = h2; var dl = h3; var el = h4
            var ar = h0; var br = h1; var cr = h2; var dr = h3; var er = h4
            for (j in 0 until 80) {
                var t = Integer.rotateLeft(al + f(j, bl, cl, dl) + x[R1[j]] + K1[j / 16], S1[j]) + el
                al = el; el = dl; dl = Integer.rotateLeft(cl, 10); cl = bl; bl = t
                t = Integer.rotateLeft(ar + f(79 - j, br, cr, dr) + x[R2[j]] + K2[j / 16], S2[j]) + er
                ar = er; er = dr; dr = Integer.rotateLeft(cr, 10); cr = br; br = t
            }
            val t = h1 + cl + dr
            h1 = h2 + dl + er; h2 = h3 + el + ar; h3 = h4 + al + br; h4 = h0 + bl + cr; h0 = t
        }
        return intArrayOf(h0, h1, h2, h3, h4).flatMap { v -> (0 until 4).map { (v ushr (8 * it)).toByte() } }.toByteArray()
    }
}
