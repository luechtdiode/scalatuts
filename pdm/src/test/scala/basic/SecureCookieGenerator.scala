/*
 ===========================================================================
 @    $Author$
 @  $Revision$
 @      $Date$
 @
 ===========================================================================
 */
package basic

/**
 *
 */
object SecureCookieGenerator extends App {
  println(akka.util.Crypt.generateSecureCookie)
}