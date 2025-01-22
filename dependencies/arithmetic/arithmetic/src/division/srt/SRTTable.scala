package division.srt

import com.cibo.evilplot.colors.HTMLNamedColors
import com.cibo.evilplot.numeric.Bounds
import com.cibo.evilplot.plot._
import com.cibo.evilplot.plot.aesthetics.DefaultTheme._
import com.cibo.evilplot.plot.renderers.PointRenderer
import os.Path
import spire.implicits._
import spire.math._

/** Base SRT class.
  *
  * @param radix is the radix of SRT.
  *              It defined how many rounds can be calculate in one cycle.
  *              @note 5.2
  * @param a digit set
  * @param dTruncateWidth
  * @param xTruncateWidth
  * @param dMin
  * @param dMax
  */
case class SRTTable(
  radix:          Algebraic,
  a:              Algebraic,
  dTruncateWidth: Algebraic,
  xTruncateWidth: Algebraic,
  dMin:           Algebraic = 0.5,
  dMax:           Algebraic = 1) {
  require(a > 0)
  lazy val xMin: Algebraic = -rho * dMax * radix
  lazy val xMax: Algebraic = rho * dMax * radix

  /** P-D Diagram
    *
    * @note Graph 5.17(b)
    */
  lazy val pd: Plot = Overlay((aMin.toBigInt to aMax.toBigInt).flatMap { k: BigInt =>
    Seq(
      FunctionPlot.series(
        _ * uRate(k.toInt).toDouble,
        s"U($k)",
        HTMLNamedColors.blue,
        Some(Bounds(dMin.toDouble, dMax.toDouble)),
        strokeWidth = Some(1)
      ),
      FunctionPlot.series(
        _ * lRate(k.toInt).toDouble,
        s"L($k)",
        HTMLNamedColors.red,
        Some(Bounds(dMin.toDouble, dMax.toDouble)),
        strokeWidth = Some(1)
      )
    ) ++ qdsPoints :+ mesh
  }: _*)
    .title(s"P-D Graph of $this")
    .xLabel("d")
    .yLabel(s"${radix.toInt}ω[j]")
    .rightLegend()
    .standard()

  lazy val aMax:   Algebraic = a
  lazy val aMin:   Algebraic = -a
  lazy val deltaD: Algebraic = pow(2, -dTruncateWidth.toDouble)
  lazy val deltaX: Algebraic = pow(2, -xTruncateWidth.toDouble)

  /** redundancy factor
    * @note 5.8
    */
  lazy val rho: Algebraic = a / (radix - 1)
  //                     k            d       m    xSet
  lazy val tables: Seq[(Int, Seq[(Algebraic, Seq[Algebraic])])] = {
    (aMin.toInt to aMax.toInt).drop(1).map { k =>
      k -> dSet.dropRight(1).map { d =>
        val (floor, ceil) = xRange(k, d, d + deltaD)
        val m: Seq[Algebraic] = xSet.filter { x: Algebraic => x <= (ceil - deltaX) && x >= floor }
        (d, m)
      }
    }
  }
  lazy val qdsPoints: Seq[Plot] = {
    tables.map {
      case (i, ps) =>
        ScatterPlot(
          ps.flatMap { case (d, xs) => xs.map(x => com.cibo.evilplot.numeric.Point(d.toDouble, x.toDouble)) },
          Some(
            PointRenderer
              .default[com.cibo.evilplot.numeric.Point](pointSize = Some(1), color = Some(HTMLNamedColors.gold))
          )
        )
    }
  }

  // TODO: select a Constant from each m, then offer the table to QDS.
  // todo: ? select rule: symmetry and draw a line parallel to the X-axis, how define the rule
  lazy val tablesToQDS: Seq[Seq[Int]] = {
    (aMin.toInt to aMax.toInt).drop(1).map { k =>
      k -> dSet.dropRight(1).map { d =>
        val (floor, ceil) = xRange(k, d, d + deltaD)
        val m: Seq[Algebraic] = xSet.filter { x: Algebraic => x <= (ceil - deltaX) && x >= floor }
        (d, m.head)
      }
    }
  }.flatMap {
    case (i, ps) =>
      ps.map {
        case (x, y) => (x.toDouble, y.toDouble * (1 << xTruncateWidth.toInt))
      }
  }.groupBy(_._1).toSeq.sortBy(_._1).map { case (x, y) => y.map { case (x, y) => y.toInt }.reverse }

  private val xStep = (xMax - xMin) / deltaX
  // @note 5.7
  require(a >= radix / 2)
  private val xSet = Seq.tabulate((xStep / 2 + 1).toInt) { n => deltaX * n } ++ Seq.tabulate((xStep / 2 + 1).toInt) {
    n => -deltaX * n
  }

  private val dStep: Algebraic = (dMax - dMin) / deltaD
  assert((rho > 1 / 2) && (rho <= 1))
  private val dSet = Seq.tabulate((dStep + 1).toInt) { n => dMin + deltaD * n }

  private val mesh =
    ScatterPlot(
      xSet.flatMap { y =>
        dSet.map { x =>
          com.cibo.evilplot.numeric.Point(x.toDouble, y.toDouble)
        }
      },
      Some(
        PointRenderer
          .default[com.cibo.evilplot.numeric.Point](pointSize = Some(0.5), color = Some(HTMLNamedColors.gray))
      )
    )

  override def toString: String =
    s"SRT${radix.toInt} with quotient set: from ${aMin.toInt} to ${aMax.toInt}"

  /** Robertson Diagram
    *
    * @note Graph 5.17(a)
    */
  def robertson(d: Algebraic): Plot = {
    require(d > dMin && d < dMax)
    Overlay((aMin.toBigInt to aMax.toBigInt).map { k: BigInt =>
      FunctionPlot.series(
        _ - (Algebraic(k) * d).toDouble,
        s"$k",
        HTMLNamedColors.black,
        xbounds = Some(Bounds(((Algebraic(k) - rho) * d).toDouble, ((Algebraic(k) + rho) * d).toDouble))
      )
    }: _*)
      .title(s"Robertson Graph of $this divisor: $d")
      .xLabel("rω[j]")
      .yLabel("ω[j+1]")
      .xbounds((-radix * rho * dMax).toDouble, (radix * rho * dMax).toDouble)
      .ybounds((-rho * d).toDouble, (rho * d).toDouble)
      .rightLegend()
      .standard()
  }

  def dumpGraph(plot: Plot, path: Path) = {
    javax.imageio.ImageIO.write(
      plot.render().asBufferedImage,
      "png",
      path.wrapped.toFile
    )
  }

  // select four points, then drop the first and the last one.
  /** for range `dLeft` to `dRight`, return the `rOmegaCeil` and `rOmegaFloor`
    * this is used for constructing the rectangle where m_k(i) is located.
    */
  private def xRange(k: Algebraic, dLeft: Algebraic, dRight: Algebraic): (Algebraic, Algebraic) = {
    Seq(L(k, dLeft), L(k, dRight), U(k - 1, dLeft), U(k - 1, dRight))
      // not safe
      .sortBy(_.toDouble)
      .drop(1)
      .dropRight(1) match { case Seq(l, r) => (l, r) }
  }

  // U_k = (k + rho) * d, L_k = (k - rho) * d
  /** find the intersection point between L`k` and `d` */
  private def L(k: Algebraic, d: Algebraic): Algebraic = lRate(k) * d

  /** slope factor of L_k
    *
    * @note 5.56
    */
  private def lRate(k: Algebraic): Algebraic = k - rho

  /** find the intersection point between U`k` and `d` */
  private def U(k: Algebraic, d: Algebraic): Algebraic = uRate(k) * d

  /** slope factor of U_k
    *
    * @note 5.56
    */
  private def uRate(k: Algebraic): Algebraic = k + rho
}
