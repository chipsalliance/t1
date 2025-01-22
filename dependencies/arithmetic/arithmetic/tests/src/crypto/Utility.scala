package crypto.modmul

class Utility {
  // Check if given p is a prime
  def isPrime(p: Int): Boolean = {
    if (p <= 2) {
      return false
    }
    var j = 2
    while ((j * j) <= p) {
      if ((p % j) == 0) return false
      j = j + 1
    }
    return true
  }

  // Generate random prime with length-bit
  def randPrime(length: Int): Int = {
    var max = (scala.math.pow(2, length)).toInt
    var min = (scala.math.pow(2, length - 1)).toInt
    var p = scala.util.Random.nextInt(max - min) + min
    while (!isPrime(p)) {
      p = scala.util.Random.nextInt(max) + 2
    }
    return p
  }

  // Extended Euclidean algorithm
  def egcd(a: Int, b: Int): (Int, Int, Int) = {
    if (a == 0) {
      return (b, 0, 1)
    }
    var (g, y, x) = egcd(b % a, a)
    return (g, x - ((b / a) * y), y)
  }

  // Modulus Inversion
  def modinv(R: Int, p: Int): Int = {
    var (g, x, y) = egcd(R, p)
    if (g != 1) { // does not exist
      return 0
    }
    if (x < 0) {
      return p + x
    }
    return (x % p)
  }
}
