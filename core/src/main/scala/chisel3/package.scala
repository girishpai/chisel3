// SPDX-License-Identifier: Apache-2.0

import chisel3.internal.firrtl.BinaryPoint
import java.util.{MissingFormatArgumentException, UnknownFormatConversionException}
import scala.collection.mutable
/** This package contains the main chisel3 API.
  */
package object chisel3 {
  import internal.firrtl.{Port, Width}
  import internal.Builder

  import scala.language.implicitConversions

  /**
    * These implicit classes allow one to convert [[scala.Int]] or [[scala.BigInt]] to
    * Chisel.UInt|Chisel.SInt by calling .asUInt|.asSInt on them, respectively.
    * The versions .asUInt(width)|.asSInt(width) are also available to explicitly
    * mark a width for the new literal.
    *
    * Also provides .asBool to scala.Boolean and .asUInt to String
    *
    * Note that, for stylistic reasons, one should avoid extracting immediately
    * after this call using apply, ie. 0.asUInt(1)(0) due to potential for
    * confusion (the 1 is a bit length and the 0 is a bit extraction position).
    * Prefer storing the result and then extracting from it.
    *
    * Implementation note: the empty parameter list (like `U()`) is necessary to prevent
    * interpreting calls that have a non-Width parameter as a chained apply, otherwise things like
    * `0.asUInt(16)` (instead of `16.W`) compile without error and produce undesired results.
    */
  implicit class fromBigIntToLiteral(bigint: BigInt) {

    /** Int to Bool conversion, allowing compact syntax like 1.B and 0.B
      */
    def B: Bool = bigint match {
      case bigint if bigint == 0 => Bool.Lit(false)
      case bigint if bigint == 1 => Bool.Lit(true)
      case bigint                => Builder.error(s"Cannot convert $bigint to Bool, must be 0 or 1"); Bool.Lit(false)
    }

    /** Int to UInt conversion, recommended style for constants.
      */
    def U: UInt = UInt.Lit(bigint, Width())

    /** Int to SInt conversion, recommended style for constants.
      */
    def S: SInt = SInt.Lit(bigint, Width())

    /** Int to UInt conversion with specified width, recommended style for constants.
      */
    def U(width: Width): UInt = UInt.Lit(bigint, width)

    /** Int to SInt conversion with specified width, recommended style for constants.
      */
    def S(width: Width): SInt = SInt.Lit(bigint, width)

    /** Int to UInt conversion, recommended style for variables.
      */
    def asUInt: UInt = UInt.Lit(bigint, Width())

    @deprecated(
      "Calling this function with an empty argument list is invalid in Scala 3. Use the form without parentheses instead",
      "Chisel 3.5"
    )
    def asUInt(dummy: Int*): UInt = asUInt

    /** Int to SInt conversion, recommended style for variables.
      */
    def asSInt: SInt = SInt.Lit(bigint, Width())

    @deprecated(
      "Calling this function with an empty argument list is invalid in Scala 3. Use the form without parentheses instead",
      "Chisel 3.5"
    )
    def asSInt(dummy: Int*): SInt = asSInt

    /** Int to UInt conversion with specified width, recommended style for variables.
      */
    def asUInt(width: Width): UInt = UInt.Lit(bigint, width)

    /** Int to SInt conversion with specified width, recommended style for variables.
      */
    def asSInt(width: Width): SInt = SInt.Lit(bigint, width)
  }

  implicit class fromIntToLiteral(int: Int) extends fromBigIntToLiteral(int)
  implicit class fromLongToLiteral(long: Long) extends fromBigIntToLiteral(long)

  implicit class fromStringToLiteral(str: String) {

    /** String to UInt parse, recommended style for constants.
      */
    def U: UInt = str.asUInt

    /** String to UInt parse with specified width, recommended style for constants.
      */
    def U(width: Width): UInt = str.asUInt(width)

    /** String to UInt parse, recommended style for variables.
      */
    def asUInt: UInt = {
      val bigInt = parse(str)
      UInt.Lit(bigInt, Width(bigInt.bitLength.max(1)))
    }

    @deprecated(
      "Calling this function with an empty argument list is invalid in Scala 3. Use the form without parentheses instead",
      "Chisel 3.5"
    )
    def asUInt(dummy: Int*): UInt = asUInt

    /** String to UInt parse with specified width, recommended style for variables.
      */
    def asUInt(width: Width): UInt = UInt.Lit(parse(str), width)

    protected def parse(n: String): BigInt = {
      val (base, num) = n.splitAt(1)
      val radix = base match {
        case "x" | "h" => 16
        case "d"       => 10
        case "o"       => 8
        case "b"       => 2
        case _         => Builder.error(s"Invalid base $base"); 2
      }
      BigInt(num.filterNot(_ == '_'), radix)
    }
  }

  implicit class fromIntToBinaryPoint(int: Int) {
    def BP: BinaryPoint = BinaryPoint(int)
  }

  implicit class fromBooleanToLiteral(boolean: Boolean) {

    /** Boolean to Bool conversion, recommended style for constants.
      */
    def B: Bool = Bool.Lit(boolean)

    /** Boolean to Bool conversion, recommended style for variables.
      */
    def asBool: Bool = Bool.Lit(boolean)

    @deprecated(
      "Calling this function with an empty argument list is invalid in Scala 3. Use the form without parentheses instead",
      "Chisel 3.5"
    )
    def asBool(dummy: Int*): Bool = asBool
  }

  // Fixed Point is experimental for now, but we alias the implicit conversion classes here
  // to minimize disruption with existing code.
  implicit class fromDoubleToLiteral(double: Double)
      extends experimental.FixedPoint.Implicits.fromDoubleToLiteral(double)

  implicit class fromBigDecimalToLiteral(bigDecimal: BigDecimal)
      extends experimental.FixedPoint.Implicits.fromBigDecimalToLiteral(bigDecimal)

  // Interval is experimental for now, but we alias the implicit conversion classes here
  //  to minimize disruption with existing code.
  implicit class fromIntToLiteralInterval(int: Int)
      extends experimental.Interval.Implicits.fromIntToLiteralInterval(int)

  implicit class fromLongToLiteralInterval(long: Long)
      extends experimental.Interval.Implicits.fromLongToLiteralInterval(long)

  implicit class fromBigIntToLiteralInterval(bigInt: BigInt)
      extends experimental.Interval.Implicits.fromBigIntToLiteralInterval(bigInt)

  implicit class fromDoubleToLiteralInterval(double: Double)
      extends experimental.Interval.Implicits.fromDoubleToLiteralInterval(double)

  implicit class fromBigDecimalToLiteralInterval(bigDecimal: BigDecimal)
      extends experimental.Interval.Implicits.fromBigDecimalToLiteralInterval(bigDecimal)

  implicit class fromIntToWidth(int: Int) {
    def W: Width = Width(int)
  }

  val WireInit = WireDefault

  object Vec extends VecFactory

  // Some possible regex replacements for the literal specifier deprecation:
  // (note: these are not guaranteed to handle all edge cases! check all replacements!)
  // Bool\((true|false)\)
  //  => $1.B
  // UInt\(width\s*=\s*(\d+|[_a-zA-Z][_0-9a-zA-Z]*)\)
  //  => UInt($1.W)
  // (UInt|SInt|Bits).width\((\d+|[_a-zA-Z][_0-9a-zA-Z]*)\)
  //  => $1($2.W)
  // (U|S)Int\((-?\d+|0[xX][0-9a-fA-F]+)\)
  //  => $2.$1
  // UInt\((\d+|0[xX][0-9a-fA-F]+),\s*(?:width\s*=)?\s*(\d+|[_a-zA-Z][_0-9a-zA-Z]*)\)
  //  => $1.U($2.W)
  // (UInt|SInt|Bool)\(([_a-zA-Z][_0-9a-zA-Z]*)\)
  //  => $2.as$1
  // (UInt|SInt)\(([_a-zA-Z][_0-9a-zA-Z]*),\s*(?:width\s*=)?\s*(\d+|[_a-zA-Z][_0-9a-zA-Z]*)\)
  //  => $2.as$1($3.W)

  object Bits extends UIntFactory
  object UInt extends UIntFactory
  object SInt extends SIntFactory
  object Bool extends BoolFactory

  type InstanceId = internal.InstanceId

  @deprecated("MultiIOModule is now just Module", "Chisel 3.5")
  type MultiIOModule = chisel3.Module

  /** Implicit for custom Printable string interpolator */
  implicit class PrintableHelper(val sc: StringContext) extends AnyVal {

    /** Custom string interpolator for generating Printables: p"..."
      * Will call .toString on any non-Printable arguments (mimicking s"...")
      */
    def pold(args: Any*): Printable = {
      sc.checkLengths(args) // Enforce sc.parts.size == pargs.size + 1
      val pargs: Seq[Option[Printable]] = args.map {
        case p: Printable => Some(p)
        case d: Data      => Some(d.toPrintable)
        case any =>
          for {
            v <- Option(any) // Handle null inputs
            str = v.toString
            if !str.isEmpty // Handle empty Strings
          } yield PString(str)
      }
      val parts = sc.parts.map(StringContext.treatEscapes)
      // Zip sc.parts and pargs together ito flat Seq
      // eg. Seq(sc.parts(0), pargs(0), sc.parts(1), pargs(1), ...)
      val seq = for { // append None because sc.parts.size == pargs.size + 1
        (literal, arg) <- parts.zip(pargs :+ None)
        optPable <- Seq(Some(PString(literal)), arg)
        pable <- optPable // Remove Option[_]
      } yield pable
      Printables(seq)
    }

    def p(args: Any*): Printable = {
      val t = sc.parts.map { _.replaceAll("%","%%")}
      StringContext(t : _*).cf(args : _*)
    }

    /*
       Custom string interpolator for generating formatted Printables : cf"..."
     * High level algorithm is as follows
     * Extending the algorithm mentioned in the regular scala "f" interpolator - except this has to be enhanced for handling Chisel Data types and Printables.
     * /** The formatted string interpolator.
     *  The `f` method works by assembling a format string from all the `parts` strings and using
     *  `java.lang.String.format` to format all arguments with that format string. The format string is
     *  obtained by concatenating all `parts` strings, and performing two transformations:
     *   1. Let a _formatting position_ be a start of any `parts` string except the first one.
     *      If a formatting position does not refer to a `%` character (which is assumed to
     *      start a format specifier), then the string format specifier `%s` is inserted. This means any string with no format specifiers should behave same as p interpolator.
     *   2. If format specifiers specifically target Chisel types - Like Data/Bits -  specific internal methods are called on that argument (like FirrtlFormat)
     *   3. If format specifers target regular scala data types - java.lang.String.format method is called with specifier and argument to get the formatted string.
     *
     * Yet to document all the corener cases which this code tries to cover - but here are few of them
     * 1. Allow calling %f or %2.2f (or something similar) on Int type data (Int, Short, Long) - this requires doing an explicit cast of the data before calling String.format
     * 2. For bits - if no format specifier given (%s i.e) call .toPrintable to pick the default one. For regular data - let the String.format handle canonical case of %s too.
     * Still testing this code - I am pretty sure this can be refactored and made more efficient / readable. WIP.
     *
     * /
     */
     */
    def cf(args: Any*): Printable = {

      def PercentSplitter(s : String) : Seq[Option[Printable]] = {
        if(s.isEmpty()) return Seq(Some(PString("")))
        var iter = 0
        var curr_start = 0
        val buf = mutable.ListBuffer.empty[Option[Printable]]
        while(iter < s.size) {
          if(s(iter) == '%' ) {
            require(iter < s.size - 1 && s(iter+1) == '%',"Un-escaped % found!")
            buf += Some(PString(s.substring(curr_start,iter)))
            buf += Some(Percent)
            curr_start = iter + 2
            iter += 2
          }
          else {
            iter += 1
          }
        }
        buf += Some(PString(s.substring(curr_start,iter)))
        buf.toSeq
      }
      sc.checkLengths(args) // Enforce sc.parts.size == pargs.size + 1
      val parts = sc.parts.map(StringContext.treatEscapes)

      // The 1st part is assumed never to contain a format specifier.
      // If the 1st part of a string is an argument - then the 1st part will be an empty String.
      // So we need to parse parts following the 1st one to get the format specifiers if any
      val partsAfterFirst = parts.slice(1, parts.size)

      // Align parts to their potential specifiers
      val partsAndSpecifierSeq = partsAfterFirst.zip(args).map {
        case (part, arg) => {

          // Check if part starts with a format specifier (with % - disambiguate with literal % checking the next character if needed to be %)
          // In the case of %f specifier there is a chance that we need more information - so capture till the 1st letter (a-zA-Z).
          // Example cf"This is $val%2.2f here" - parts - Seq("This is ","%2.2f here") - the format specifier here is %2.2f.
          val idx_of_fmt_str =
            if (
              !part.isEmpty() && part.charAt(0) == '%' && (part.size == 1 || (part.size >= 2 && part.charAt(1) != '%'))
            ) part.indexWhere { _.isLetter }
            else -1

          // If no format specifier - pick default - %s

          val fmt = if (idx_of_fmt_str >= 0) part.substring(0, idx_of_fmt_str + 1) else "%s"
          val fmtArgs: Printable = arg match {
            case b: Bits => {
              require(fmt.size == 2, "In the case of bits, only single format char allowed!")
              fmt match {
                case "%s" => b.toPrintable
                case "%n" => Name(b)
                case "%N" => FullName(b)
                // Default - let FirrtlFormat check validity of the format string to avoid repeating checks.
                case f => FirrtlFormat(f.substring(1, 2), b)
              }
            }
            case d: Data => {
              require(
                fmt == "%s" || fmt == "%n" || fmt == "%N",
                "Non-bits only  allowed with (%s,%n, %N) format specifiers!"
              )
              fmt match {
                case "%n" => Name(d)
                case "%N" => FullName(d)
                case "%s" => d.toPrintable
                case x => {
                  val msg = s"Illegal format specifier '$x'!\n"
                  throw new UnknownFormatConversionException(msg)
                }
              }
            }
            case p: Printable => {
              require(fmt == "%s", "Printables not allowed with format specifiers!")
              p
            }

            // Generic case - use String.format (for example %d,%2.2f etc on regular Scala types)
            case t => {
              PString(fmt.format(t))
            }
          }

          // Remove format specifier from parts string
          val modP = part.zipWithIndex.filter { _._2 > idx_of_fmt_str }.map { _._1 }.mkString

          (modP, Some(fmtArgs))
        }
      }
      // Combine the 1st part with the rest of the modified (format specifier removed) parts
      val combParts = parts(0) +: partsAndSpecifierSeq.map { _._1 }

      val pargsPables: Seq[Option[Printable]] = partsAndSpecifierSeq.map { _._2 }
      val seq = for { // append None because sc.parts.size == pargs.size + 1
        (literal, arg) <- combParts.zip(pargsPables :+ None)
        optPable <- PercentSplitter(literal) ++ Seq(arg)
        pable <- optPable // Remove Option[_]
      } yield pable
      Printables(seq)
    }
  }

  implicit def string2Printable(str: String): Printable = PString(str)

  type ChiselException = internal.ChiselException

  // Debugger/Tester access to internal Chisel data structures and methods.
  def getDataElements(a: Aggregate): Seq[Element] = {
    a.allElements
  }
  @deprecated(
    "duplicated with DataMirror.fullModulePorts, this returns an internal API, will be removed in Chisel 3.6",
    "Chisel 3.5"
  )
  def getModulePorts(m: Module): Seq[Port] = m.getPorts

  class BindingException(message: String) extends ChiselException(message)

  /** A function expected a Chisel type but got a hardware object
    */
  case class ExpectedChiselTypeException(message: String) extends BindingException(message)

  /** A function expected a hardware object but got a Chisel type
    */
  case class ExpectedHardwareException(message: String) extends BindingException(message)

  /** An aggregate had a mix of specified and unspecified directionality children
    */
  case class MixedDirectionAggregateException(message: String) extends BindingException(message)

  /** Attempted to re-bind an already bound (directionality or hardware) object
    */
  case class RebindingException(message: String) extends BindingException(message)
  // Connection exceptions.
  case class BiConnectException(message: String) extends ChiselException(message)
  case class MonoConnectException(message: String) extends ChiselException(message)
}
