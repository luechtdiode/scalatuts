package ch.seidel.polrho

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.math.BigInt.int2bigInt
import scala.util.Random
import sun.management.ManagementFactoryHelper
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import scala.collection.immutable.Set


object PollardRho {
 
  /**
   * Pollard's Rho Algorithm.
   * 
   * @param n value to factor; 
   * @return 0, or a factor of n
   */
  def rho(n: BigInt): List[BigInt] = {
    println("rho: " + n + " ")

    /**
     * Pollard.rho algorithm 
     */ 
    @tailrec
    def findFactor(x: BigInt, y: BigInt, c: BigInt, interruptor: => Boolean): BigInt = {
      if(interruptor) {
        // terminate with zero
        BigInt(0)
      }
      else {
        def f(x: BigInt) = {
          (x*x + c) % n
        }
        val xx = f(x)
        val yy = f(f(y))
        val p = gcd(xx - yy, n).abs
        if (p == n) 0
        else if (p != 1) p 
        else findFactor(xx, yy, c, /*stack + x,*/ interruptor)
      }
    }
    
    try {
      if(n % 2 == 0) List(2)
      else if(n > 2 && n < 10000) {
        val range = (BigInt(3) to BigInt(math.sqrt(n.toDouble).toInt)).toList
        List(range.collectFirst[BigInt]({case x if(n % x == 0) => x}).getOrElse(0))
      }
      else {
        val c = BigInt(n.bitLength, Random)
        val processors = Runtime.getRuntime().availableProcessors()
        val interruptor = new AtomicBoolean(false)

        def startPointAroundSqrt(processor: Int): BigInt = sqrt(n) * processor / processors
        def startPointAtProcessor(processor: Int): BigInt = n * processor / processors
        
        println(" starting with " + processors + " threads ")
        val workers = for {
          cpu <- 1 to processors
        }
        yield (Future[BigInt] {
//          val sp = startPointAtProcessor(cpu)
          val sp = startPointAroundSqrt(cpu)
          if(!interruptor.get) {
            //println("  Startpoint in thread " + cpu + ": " + sp)
            if(n % sp == 0) {
              interruptor.set(true)
              println("  Factor found in thread " + cpu + ": " + sp)
              sp
            }
            else {
              val ret = findFactor(c, c, sp, interruptor.get())
//              val ret = findFactor(2, 2, sp, interruptor.get())
//              val ret = findFactor(sp, sp, c, interruptor.get())
              if(ret > 0) {
                interruptor.set(true)
                println("  Factor found in thread " + cpu + ": " + ret)
              }
              ret
            }
          }
          else {
            0
          }
        })
        val results = Await.result(Future.sequence(workers), Duration.Inf) filter (_ > 0)
        //val results = List(Await.result(Future.firstCompletedOf(workers), Duration.Inf) )
        // stopping all other threads
        //interruptor.set(true)
        while(workers forall(w => !w.isCompleted)) {}
        results.toSet.toList
      } 
    }
    finally {
      println(" done ")
    }
  }

  def isPrime(n: BigInt) = {
    n.isProbablePrime(25)
//    if(n < 2) false
//    else if(n == 2) true
//    else if(n > 2 && (n % 2 == 0)) false
//    else (3 to math.sqrt(n.toDouble).toInt by 2) forall(n % _ != 0)
  }
  
  def gcd(a: BigInt, b: BigInt): BigInt = {
    //Euclid's algorithm
    if (b == 0) a
    else gcd(b, a % b)
  }
  
  def sqrt(number : BigInt) = {
    def next(n : BigInt, i : BigInt) : BigInt = (n + i/n) >> 1
    
    val one = BigInt(1)
    var n = one
    var n1 = next(n, number)
    
    while ((n1 - n).abs > one) {
      n = n1
      n1 = next(n, number)
    }
     
    while (n1 * n1 > number) {
      n1 -= one
    }
     
    n1
  }
  
  def factorize(n: BigInt): List[BigInt] = {
    println("starting " + n.bitLength + "bits factoring ... ")
    val time = System.currentTimeMillis();
    val cputime = getJVMCpuTime
    
    @tailrec
    def factor(nl: List[BigInt], stack: Int, acc: List[BigInt]): List[BigInt] = {
      if(nl.isEmpty) acc
      else {
        val nn = nl.head
        if (nn == 1) factor(nl.tail, stack + 1, acc)
        else if (isPrime(nn)) factor(nl.tail, stack + 1, nn :: acc)
        else {
          val divisors = rho(nn)
          if(divisors.size == 0) factor(nl.tail, stack + 1, acc)
          else {
            factor(divisors.map(d => nn/d).filter(d => !divisors.contains(d)) ::: divisors ::: nl.tail, stack + 1, acc)
          }
        }
      }
    }
    
    val ret = factor(List(n), 1, List())
    println("terminated in " + (System.currentTimeMillis() - time) + "ms; in " + (getJVMCpuTime - cputime) + "cpu-time")
    val n2 = ret.foldLeft(BigInt(1))((m, f) => m * f)
    if(n2 != n) {
      println("Warning, not all or too much factors found. [" + n + " != " + n2 + "]")
    }
    ret
  }
  
  def getJVMCpuTime: Long = {
    val bean = ManagementFactory.getOperatingSystemMXBean
    if (!bean.isInstanceOf[OperatingSystemMXBean])
      0
    else 
      bean.asInstanceOf[OperatingSystemMXBean].getProcessCpuTime
  }
  
  def main(args: Array[String]) {
    println(factorize(BigInt(220)))
    // => List(2, 2, 5, 11) 1ms
    println(factorize(BigInt(9999)))
    // => List(3, 3, 11, 101) 1ms
    println(factorize(BigInt("9449868410449")))
    // => List(1234577, 7654337) 35ms
    println(factorize(BigInt("9398726230209357241")))
    // => List(503, 3541, 443, 1511, 997, 7907) 11ms
    println(factorize(BigInt("13565005454706599869")))
    // => List(10987654367, 1234567907) 200ms
    println(factorize(BigInt("1137047281562824484226171575219374004320812483047")))
    // => List(12553, 7907, 156007, 12391, 191913031, 4302407713, 7177162612387) 383ms
    //println(factorize(BigInt("982301348481615682763349336546115836409")))
    // List(20989897656489026809, 46798767890987654401) 23'741'599ms
    // Next challenges
    // println(factorize(BigInt("26196077648981785233796902948145280025374347621094198197324282087")))
    // ..println(factorize(BigInt("1000602106143806596478722974273666950903906112131794745457338659266842446985022076792112309173975243506969710503")))
    // ..println(factorize(BigInt("85837016912053409666185379966858278365266030036183816201195732973049879641847994920829742573044114566952879")))
    // ..println(factorize(BigInt("8054519744023028025352855397096582374520599609288150154940014354231948920132119256904357940606560436047")))
    // ..println(factorize(BigInt("3493091121918862422566163082871435601031181091419106838820427786093607176277452062897")))
    // ..println(factorize(BigInt("263340758041153396593739821660568241890170475817053650289462052221651230607393")))
    // ..println(factorize(BigInt("26196077648981785233796902948145280025374347621094198197324282087")))
     println(factorize(BigInt("25195908475657893494027183240048398571429282126204032027777137836043662020707595556264018525880784406918290641249515082189298559149176184502808489120072844992687392807287776735971418347270261896375014971824691165077613379859095700097330459748808428401797429100642458691817195118746121515172654632282216869987549182422433637259085141865462043576798423387184774447920739934236584823824281198163815010674810451660377306056201619676256133844143603833904414952634432190114657544454178424020924616515723350778707749817125772467962926386356373289912154831438167899885040445364023527381951378636564391212010397122822120720357")))
  }
}