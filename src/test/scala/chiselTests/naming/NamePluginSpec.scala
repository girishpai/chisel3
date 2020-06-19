// See LICENSE for license details.

package chiselTests.naming

import chisel3._
import chisel3.aop.Select
import chisel3.experimental.{prefix, treedump}
import chiselTests.ChiselPropSpec

class NamePluginSpec extends ChiselPropSpec {

  property("Scala plugin should name internally scoped components") {
    class Test extends MultiIOModule {
      { val mywire = Wire(UInt(3.W))}

    }
    aspectTest(() => new Test) {
      top: Test => Select.wires(top).head.toTarget.ref should be("mywire")
    }
  }

  property("Scala plugin should name internally scoped instances") {
    class Inner extends MultiIOModule { }
    class Test extends MultiIOModule {
      { val myinstance = Module(new Inner) }
    }
    aspectTest(() => new Test) {
      top: Test => Select.instances(top).head.instanceName should be("myinstance")
    }
  }

  property("Scala plugin interact with prefixing") {
    class Test extends MultiIOModule {
      def builder() = {
        val wire = Wire(UInt(3.W))
      }
      prefix("first") {
        builder()
      }
      prefix("second") {
        builder()
      }
    }
    aspectTest(() => new Test) {
      top: Test => Select.wires(top).map(_.instanceName) should be (List("first_wire", "second_wire"))
    }
  }

  property("Scala plugin should interact with prefixing so last val name wins") {
    class Test extends MultiIOModule {
      def builder() = {
        val wire1 = Wire(UInt(3.W))
        val wire2 = Wire(UInt(3.W))
        wire2
      }
      {
        val x1 = prefix("first") {
          builder()
        }
      }
      {
        val x2 = prefix("second") {
          builder()
        }
      }
    }
    aspectTest(() => new Test) {
      top: Test => Select.wires(top).map(_.instanceName) should be (List("x1_first_wire1", "x1", "x2_second_wire1", "x2"))
    }
  }

  property("Naming on option should work") {

    class Test extends MultiIOModule {
      def builder(): Option[UInt] = {
        val a = Wire(UInt(3.W))
        Some(a)
      }

      { val blah = builder() }
    }
    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (List("blah"))
    }
  }

  property("Naming on iterables should work") {

    class Test extends MultiIOModule {
      def builder(): Seq[UInt] = {
        val a = Wire(UInt(3.W))
        val b = Wire(UInt(3.W))
        Seq(a, b)
      }
      {
        val blah = {
          builder()
        }
      }
    }
    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (List("blah_0", "blah_1"))
    }
  }

  property("Naming on nested iterables should work") {

    class Test extends MultiIOModule {
      def builder(): Seq[Seq[UInt]] = {
        val a = Wire(UInt(3.W))
        val b = Wire(UInt(3.W))
        val c = Wire(UInt(3.W))
        val d = Wire(UInt(3.W))
        Seq(Seq(a, b), Seq(c, d))
      }
      {
        val blah = {
          builder()
        }
      }
    }
    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (
          List(
            "blah_0_0",
            "blah_0_1",
            "blah_1_0",
            "blah_1_1"
          ))
    }
  }

  property("Naming on custom case classes should not work") {
    case class Container(a: UInt, b: UInt)

    class Test extends MultiIOModule {
      def builder(): Container = {
        val a = Wire(UInt(3.W))
        val b = Wire(UInt(3.W))
        Container(a, b)
      }

      { val blah = builder() }
    }
    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (List("a", "b"))
    }
  }

  property("Multiple names on an IO within a module gets the first name") {
    class Test extends RawModule {
      val a = IO(Output(UInt(3.W)))
      val b = a
    }

    aspectTest(() => new Test) {
      top: Test =>
        Select.ios(top).map(_.instanceName) should be (List("a"))
    }
  }

  property("Multiple names on a non-IO gets the last name") {
    class Test extends MultiIOModule {
      {
        val a = Wire(UInt(3.W))
        val b = a
      }
    }

    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (List("b"))
    }
  }

  property("Unapply assignments should still be named") {
    class Test extends MultiIOModule {
      {
        @treedump
        val (a, b) = (Wire(UInt(3.W)), Wire(UInt(3.W)))
      }
    }

    aspectTest(() => new Test) {
      top: Test =>
        Select.wires(top).map(_.instanceName) should be (List("a", "b"))
    }
  }
}
