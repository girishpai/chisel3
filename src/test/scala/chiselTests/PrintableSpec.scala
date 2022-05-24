// SPDX-License-Identifier: Apache-2.0

package chiselTests

import chisel3._
import chisel3.experimental.ChiselAnnotation
import chisel3.stage.ChiselStage
import chisel3.testers.BasicTester
import firrtl.annotations.{ReferenceTarget, SingleTargetAnnotation}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chisel3.util._

import java.io.File

/** Dummy [[printf]] annotation.
  * @param target target of component to be annotated
  */
case class PrintfAnnotation(target: ReferenceTarget) extends SingleTargetAnnotation[ReferenceTarget] {
  def duplicate(n: ReferenceTarget): PrintfAnnotation = this.copy(target = n)
}

object PrintfAnnotation {

  /** Create annotation for a given [[printf]].
    * @param c component to be annotated
    */
  def annotate(c: VerificationStatement): Unit = {
    chisel3.experimental.annotate(new ChiselAnnotation {
      def toFirrtl: PrintfAnnotation = PrintfAnnotation(c.toTarget)
    })
  }
}

/* Printable Tests */
class PrintableSpec extends AnyFlatSpec with Matchers  with Utils{
  // This regex is brittle, it specifically finds the clock and enable signals followed by commas
  private val PrintfRegex = """\s*printf\(\w+, [^,]+,(.*)\).*""".r
  private val StringRegex = """([^"]*)"(.*?)"(.*)""".r
  private case class Printf(str: String, args: Seq[String])
  private case class ErrorString(firrtl : String, actual : Seq[Printf])
  private def getPrintfs(firrtl: String): Seq[Printf] = {
    def processArgs(str: String): Seq[String] =
      str.split(",").map(_.trim).filter(_.nonEmpty)
    def processBody(str: String): (String, Seq[String]) = {
      str match {
        case StringRegex(_, fmt, args) =>
          (fmt, processArgs(args))
        case _ => fail(s"Regex to process Printf should work on $str!")
      }
    }
    firrtl.split("\n").collect {
      case PrintfRegex(matched) =>
        val (str, args) = processBody(matched)
        Printf(str, args)
    }
  }


  // Generates firrtl, gets Printfs
  // Returns None if failed match; else calls the partial function which could have its own check
  // Returns Some(true) to caller 
  // Not calling fail() here - and letting caller do so - helps in localizing errors correctly. 
  private def generateAndCheck(gen : => RawModule)(check : PartialFunction[Seq[Printf],Unit]) = {
    val firrtl = ChiselStage.emitChirrtl(gen)
    val printfs = getPrintfs(firrtl)
    if(!check.isDefinedAt(printfs)) {
      println("Firrtl = \n",firrtl)
      println("Actual = ",printfs)
      None
    }
    else {
      check(printfs)
      Some(true)
    } 
  }

  behavior.of("Printable & Custom Interpolator")

  
  it should "pass exact strings through" in {
    class MyModule extends BasicTester {
      printf(p"An exact string")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("An exact string", Seq())) =>
    }.getOrElse(fail())
  }
  it should "handle Printable and String concatination" in {
    class MyModule extends BasicTester {
      printf(p"First " + PString("Second ") + "Third")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("First Second Third", Seq())) => 
    }.getOrElse(fail())
  }
  it should "call toString on non-Printable objects" in {
    class MyModule extends BasicTester {
      val myInt = 1234
      printf(p"myInt = $myInt")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("myInt = 1234", Seq())) =>
    }.getOrElse(fail())
  }
  it should "generate proper printf for simple Decimal printing" in {
    class MyModule extends BasicTester {
      val myWire = WireDefault(1234.U)
      printf(p"myWire = ${Decimal(myWire)}")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("myWire = %d", Seq("myWire"))) =>
    }.getOrElse(fail())
  }
  it should "handle printing literals" in {
    class MyModule extends BasicTester {
      printf(Decimal(10.U(32.W)))
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("%d", Seq(lit))) =>
        assert(lit contains "UInt<32>")
    }.getOrElse(fail())
  }
  it should "correctly escape percent" in {
    class MyModule extends BasicTester {
      printf(p"%")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("%%", Seq())) =>
    }.getOrElse(fail())
  }
  it should "correctly emit tab" in {
    class MyModule extends BasicTester {
      printf(p"\t")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("\\t", Seq())) => 
    }.getOrElse(fail())
  }
  it should "support names of circuit elements including submodule IO" in {
    // Submodule IO is a subtle issue because the Chisel element has a different
    // parent module
    class MySubModule extends Module {
      val io = IO(new Bundle {
        val fizz = UInt(32.W)
      })
    }
    class MyBundle extends Bundle {
      val foo = UInt(32.W)
    }
    class MyModule extends BasicTester {
      override def desiredName: String = "MyModule"
      val myWire = Wire(new MyBundle)
      val myInst = Module(new MySubModule)
      printf(p"${Name(myWire.foo)}")
      printf(p"${FullName(myWire.foo)}")
      printf(p"${FullName(myInst.io.fizz)}")
    }
    generateAndCheck(new MyModule) { 
      case Seq(Printf("foo", Seq()), Printf("myWire.foo", Seq()), Printf("myInst.io.fizz", Seq())) =>
    }.getOrElse(fail())
  }
  it should "handle printing ports of submodules" in {
    class MySubModule extends Module {
      val io = IO(new Bundle {
        val fizz = UInt(32.W)
      })
    }
    class MyModule extends BasicTester {
      val myInst = Module(new MySubModule)
      printf(p"${myInst.io.fizz}")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("%d", Seq("myInst.io.fizz"))) =>
    }.getOrElse(fail())
  }
  it should "print UInts and SInts as Decimal by default" in {
    class MyModule extends BasicTester {
      val myUInt = WireDefault(0.U)
      val mySInt = WireDefault(-1.S)
      printf(p"$myUInt & $mySInt")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("%d & %d", Seq("myUInt", "mySInt"))) =>
    }.getOrElse(fail())
  }
  it should "print Vecs like Scala Seqs by default" in {
    class MyModule extends BasicTester {
      val myVec = Wire(Vec(4, UInt(32.W)))
      myVec.foreach(_ := 0.U)
      printf(p"$myVec")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("Vec(%d, %d, %d, %d)", Seq("myVec[0]", "myVec[1]", "myVec[2]", "myVec[3]"))) => 
    }.getOrElse(fail())
  }
  it should "print Bundles like Scala Maps by default" in {
    class MyModule extends BasicTester {
      val myBun = Wire(new Bundle {
        val foo = UInt(32.W)
        val bar = UInt(32.W)
      })
      myBun.foo := 0.U
      myBun.bar := 0.U
      printf(p"$myBun")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("AnonymousBundle(foo -> %d, bar -> %d)", Seq("myBun.foo", "myBun.bar"))) =>
    }.getOrElse(fail())
  }
  it should "get emitted with a name and annotated" in {

    /** Test circuit containing annotated and renamed [[printf]]s. */
    class PrintfAnnotationTest extends Module {
      val myBun = Wire(new Bundle {
        val foo = UInt(32.W)
        val bar = UInt(32.W)
      })
      myBun.foo := 0.U
      myBun.bar := 0.U
      val howdy = printf(p"hello ${myBun}")
      PrintfAnnotation.annotate(howdy)
      PrintfAnnotation.annotate(printf(p"goodbye $myBun"))
      PrintfAnnotation.annotate(printf(p"adieu $myBun").suggestName("farewell"))
    }

    // compile circuit
    val testDir = new File("test_run_dir", "PrintfAnnotationTest")
    (new ChiselStage).emitSystemVerilog(
      gen = new PrintfAnnotationTest,
      args = Array("-td", testDir.getPath)
    )

    // read in annotation file
    val annoFile = new File(testDir, "PrintfAnnotationTest.anno.json")
    annoFile should exist
    val annoLines = scala.io.Source.fromFile(annoFile).getLines.toList

    // check for expected annotations
    exactly(3, annoLines) should include("chiselTests.PrintfAnnotation")
    exactly(1, annoLines) should include("~PrintfAnnotationTest|PrintfAnnotationTest>farewell")
    exactly(1, annoLines) should include("~PrintfAnnotationTest|PrintfAnnotationTest>printf")
    exactly(1, annoLines) should include("~PrintfAnnotationTest|PrintfAnnotationTest>howdy")

    // read in FIRRTL file
    val firFile = new File(testDir, "PrintfAnnotationTest.fir")
    firFile should exist
    val firLines = scala.io.Source.fromFile(firFile).getLines.toList

    // check that verification components have expected names
    exactly(1, firLines) should include(
      """printf(clock, UInt<1>("h1"), "hello AnonymousBundle(foo -> %d, bar -> %d)", myBun.foo, myBun.bar) : howdy"""
    )
    exactly(1, firLines) should include(
      """printf(clock, UInt<1>("h1"), "goodbye AnonymousBundle(foo -> %d, bar -> %d)", myBun.foo, myBun.bar) : printf"""
    )
    exactly(1, firLines) should include(
      """printf(clock, UInt<1>("h1"), "adieu AnonymousBundle(foo -> %d, bar -> %d)", myBun.foo, myBun.bar) : farewell"""
    )
  }

  // Unit tests for cf
  it should "print regular scala variables with cf format specifier" in {

    class MyModule extends BasicTester {
      val f1 = 20.45156
      val i1 = 10
      val s1: Short = 15
      val l1: Long = 253
      printf(cf"F1 = $f1 D1 = $i1 F1 formatted = $f1%2.2f s1 = $s1 l1 = $l1")

    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("F1 = 20.45156 D1 = 10 F1 formatted = 20.45 s1 = 15 l1 = 253", Seq())) =>
    }.getOrElse(fail())
  }

  it should "print chisel bits with cf format specifier" in {

    class MyBundle extends Bundle {
      val foo = UInt(32.W)
      val bar = UInt(32.W)
      override def toPrintable: Printable = {
        cf"Bundle : " +
          cf"Foo : $foo%x Bar : $bar%x"
      }
    }
    class MyModule extends BasicTester {
      val b1 = 10.U
      val w1 = Wire(new MyBundle)
      w1.foo := 5.U
      w1.bar := 10.U
      printf(cf"w1 = $w1")
    }
    generateAndCheck(new MyModule) {
    case Seq(Printf("w1 = Bundle : Foo : %x Bar : %x", Seq("w1.foo", "w1.bar"))) => 
    }.getOrElse(fail())
  }

  it should "support names of circuit elements using format specifier including submodule IO with cf format specifier" in {
    // Submodule IO is a subtle issue because the Chisel element has a different
    // parent module
    class MySubModule extends Module {
      val io = IO(new Bundle {
        val fizz = UInt(32.W)
      })
    }
    class MyBundle extends Bundle {
      val foo = UInt(32.W)
    }
    class MyModule extends BasicTester {
      override def desiredName: String = "MyModule"
      val myWire = Wire(new MyBundle)
      val myInst = Module(new MySubModule)
      printf(cf"${myWire.foo}%n")
      printf(cf"${myWire.foo}%N")
      printf(cf"${myInst.io.fizz}%N")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("foo", Seq()), Printf("myWire.foo", Seq()), Printf("myInst.io.fizz", Seq())) => 
    }.getOrElse(fail())
  }

  it should "correctly print strings after modifier" in {
    class MyModule extends BasicTester {
      val b1 = 10.U
      printf(cf"This is here $b1%x!!!! And should print everything else")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("This is here %x!!!! And should print everything else",Seq("UInt<4>(\"ha\")"))) => 
    }.getOrElse(fail())
  }

  it should "correctly print strings with a lot of literal %% and different format specifiers for Wires" in {
    class MyModule extends BasicTester {
      val b1 = 10.U
      val b2 = 20.U
      printf(cf"%%  $b1%x%%$b2%b = ${b1%b2}%d %%%% Tail String")
    }

    generateAndCheck(new MyModule) {
      case Seq(Printf("%%  %x%%%b = %d %%%% Tail String", Seq(lita,litb,_))) =>
        assert(lita.contains("UInt<4>") && litb.contains("UInt<5>"))
    }.getOrElse(fail())
  }

  it should "not allow unescaped % in the message" in {
    class MyModule extends BasicTester {
      printf(cf"This should error out for sure because of % - it should be %%")
    }
    a[java.util.UnknownFormatConversionException] should be thrownBy {
      extractCause[java.util.UnknownFormatConversionException] {
        ChiselStage.elaborate { new MyModule }
      }
    }
  }

  it should "allow Printables to be expanded and used" in {
    class MyModule extends BasicTester {
      val w1  = 20.U
      val f1 = 30.2
      val i1 = 14
      val pable = cf"w1 = $w1%b f1 = $f1%2.2f"
      printf(cf"Trying to expand printable $pable and mix with i1 = $i1%d")
    }
    generateAndCheck(new MyModule) {
      case Seq(Printf("Trying to expand printable w1 = %b f1 = 30.20 and mix with i1 = 14",Seq(lit))) => 
        assert(lit.contains("UInt<5>"))
    }.getOrElse(fail())
  }

}
