package ch.seidel.template

import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationWithJUnit

class MySpec extends SpecificationWithJUnit {
  "The 'Hello world' string" should {
    "contain 11 characters" in {
      "Hello world" must have size(11)
    }
    "start with 'Hello'" in {
      "Hello world" must startWith("Hello")
    }
    "end with 'world'" in {
      "Hello world" must endWith("world")
    }
  }
}