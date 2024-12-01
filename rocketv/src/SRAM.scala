package chisel3.hack.util

import chisel3._
import chisel3.experimental.SourceInfo
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.internal.Builder
import chisel3.properties.{Path, Property}

import chisel3.util.experimental.{CIRCTSRAMInterface, CIRCTSRAMParameter}
import chisel3.util.{log2Ceil, MemoryFile, SRAMDescription, SRAMInterface}
import firrtl.transforms.BlackBoxInlineAnno
import chisel3.experimental.ChiselAnnotation
import chisel3.experimental.hierarchy.core.Hierarchy.HierarchyBaseModuleExtensions
import chisel3.util.HasExtModuleInline

class SRAMBlackbox(parameter: CIRCTSRAMParameter)
    extends FixedIOExtModule(new CIRCTSRAMInterface(parameter))
    with HasExtModuleInline { self =>

  private val verilogInterface: String =
    (Seq.tabulate(parameter.write)(idx =>
      Seq(
        s"// Write Port $idx",
        s"input [${log2Ceil(parameter.depth) - 1}:0] W${idx}_addr",
        s"input W${idx}_en",
        s"input W${idx}_clk",
        s"input [${parameter.width - 1}:0] W${idx}_data"
      ) ++
        Option.when(parameter.masked)(s"input [${parameter.width / parameter.maskGranularity - 1}:0] W${idx}_mask")
    ) ++
      Seq.tabulate(parameter.read)(idx =>
        Seq(
          s"// Read Port $idx",
          s"input [${log2Ceil(parameter.depth) - 1}:0] R${idx}_addr",
          s"input R${idx}_en",
          s"input R${idx}_clk",
          s"output [${parameter.width - 1}:0] R${idx}_data"
        )
      ) ++
      Seq.tabulate(parameter.readwrite)(idx =>
        Seq(
          s"// ReadWrite Port $idx",
          s"input [${log2Ceil(parameter.depth) - 1}:0] RW${idx}_addr",
          s"input RW${idx}_en",
          s"input RW${idx}_clk",
          s"input RW${idx}_wmode",
          s"input [${parameter.width - 1}:0] RW${idx}_wdata",
          s"output [${parameter.width - 1}:0] RW${idx}_rdata"
        ) ++ Option
          .when(parameter.masked)(
            s"input [${parameter.width / parameter.maskGranularity - 1}:0] RW${idx}_wmask"
          )
      )).flatten.mkString(",\n")

  private val rLogic = Seq
    .tabulate(parameter.read) { idx =>
      val prefix = s"R${idx}"
      Seq(
        s"reg _${prefix}_en;",
        s"reg [${log2Ceil(parameter.depth) - 1}:0] _${prefix}_addr;"
      ) ++
        Seq(
          s"always @(posedge ${prefix}_clk) begin // ${prefix}",
          s"_${prefix}_en <= ${prefix}_en;",
          s"_${prefix}_addr <= ${prefix}_addr;",
          s"end // ${prefix}"
        ) ++
        Some(s"assign ${prefix}_data = _${prefix}_en ? Memory[_${prefix}_addr] : ${parameter.width}'bx;")
    }
    .flatten

  private val wLogic = Seq
    .tabulate(parameter.write) { idx =>
      val prefix = s"W${idx}"
      Seq(s"always @(posedge ${prefix}_clk) begin // ${prefix}") ++
        (if (parameter.masked)
           Seq.tabulate(parameter.width / parameter.maskGranularity)(i =>
             s"if (${prefix}_en & ${prefix}_wmask[${i}]) Memory[${prefix}_addr][${i * parameter.maskGranularity} +: ${parameter.maskGranularity}] <= ${prefix}_wdata[${(i + 1) * parameter.maskGranularity - 1}:${i * parameter.maskGranularity}];"
           )
         else
           Seq(s"if (${prefix}_en) Memory[${prefix}_addr] <= ${prefix}_data;")) ++
        Seq(s"end // ${prefix}")
    }
    .flatten

  private val rwLogic = Seq
    .tabulate(parameter.readwrite) { idx =>
      val prefix = s"RW${idx}"
      Seq(
        s"reg [${log2Ceil(parameter.depth) - 1}:0] _${prefix}_raddr;",
        s"reg _${prefix}_ren;",
        s"reg _${prefix}_rmode;"
      ) ++
        Seq(s"always @(posedge ${prefix}_clk) begin // ${prefix}") ++
        Seq(
          s"_${prefix}_raddr <= ${prefix}_addr;",
          s"_${prefix}_ren <= ${prefix}_en;",
          s"_${prefix}_rmode <= ${prefix}_wmode;"
        ) ++
        (if (parameter.masked)
           Seq.tabulate(parameter.width / parameter.maskGranularity)(i =>
             s"if(${prefix}_en & ${prefix}_wmask[${i}] & ${prefix}_wmode) Memory[${prefix}_addr][${i * parameter.maskGranularity} +: ${parameter.maskGranularity}] <= ${prefix}_wdata[${(i + 1) * parameter.maskGranularity - 1}:${i * parameter.maskGranularity}];"
           )
         else
           Seq(s"if (${prefix}_en & ${prefix}_wmode) Memory[${prefix}_addr] <= ${prefix}_wdata;")) ++
        Seq(s"end // ${prefix}") ++
        Seq(
          s"assign ${prefix}_rdata = _${prefix}_ren & ~_${prefix}_rmode ? Memory[_${prefix}_raddr] : ${parameter.width}'bx;"
        )
    }
    .flatten

  private val logic =
    (Seq(s"reg [${parameter.width - 1}:0] Memory[0:${parameter.depth - 1}];") ++ wLogic ++ rLogic ++ rwLogic)
      .mkString("\n")

  override def desiredName = parameter.moduleName

  setInline(
    desiredName + ".sv",
    s"""module ${parameter.moduleName}(
       |${verilogInterface}
       |);
       |${logic}
       |endmodule
       |""".stripMargin
  )
}

/** This should be upstreamed to Chisel for implementing the InstanceChoice.
  *   - Here are different right for implementing the SRAM, bug chisel choose none of them.
  *   - SRAM Blackbox with metadata.
  *   - Intrinsic SRAM and then lower to
  */
object SRAM {
  def apply[T <: Data](
    size:                BigInt,
    tpe:                 T,
    numReadPorts:        Int,
    numWritePorts:       Int,
    numReadwritePorts:   Int
  )(
    implicit sourceInfo: SourceInfo
  ): SRAMInterface[T] = {
    val clock = Builder.forcedClock
    memInterface_impl(
      size,
      tpe,
      Seq.fill(numReadPorts)(clock),
      Seq.fill(numWritePorts)(clock),
      Seq.fill(numReadwritePorts)(clock),
      None,
      None,
      sourceInfo
    )
  }

  def apply[T <: Data](
    size:                BigInt,
    tpe:                 T,
    numReadPorts:        Int,
    numWritePorts:       Int,
    numReadwritePorts:   Int,
    memoryFile:          MemoryFile
  )(
    implicit sourceInfo: SourceInfo
  ): SRAMInterface[T] = {
    val clock = Builder.forcedClock
    memInterface_impl(
      size,
      tpe,
      Seq.fill(numReadPorts)(clock),
      Seq.fill(numWritePorts)(clock),
      Seq.fill(numReadwritePorts)(clock),
      Some(memoryFile),
      None,
      sourceInfo
    )
  }

  def apply[T <: Data](
    size:                BigInt,
    tpe:                 T,
    readPortClocks:      Seq[Clock],
    writePortClocks:     Seq[Clock],
    readwritePortClocks: Seq[Clock]
  )(
    implicit sourceInfo: SourceInfo
  ): SRAMInterface[T] =
    memInterface_impl(
      size,
      tpe,
      readPortClocks,
      writePortClocks,
      readwritePortClocks,
      None,
      None,
      sourceInfo
    )

  def apply[T <: Data](
    size:                BigInt,
    tpe:                 T,
    readPortClocks:      Seq[Clock],
    writePortClocks:     Seq[Clock],
    readwritePortClocks: Seq[Clock],
    memoryFile:          MemoryFile
  )(
    implicit sourceInfo: SourceInfo
  ): SRAMInterface[T] =
    memInterface_impl(
      size,
      tpe,
      readPortClocks,
      writePortClocks,
      readwritePortClocks,
      Some(memoryFile),
      None,
      sourceInfo
    )

  def masked[T <: Data](
    size:              BigInt,
    tpe:               T,
    numReadPorts:      Int,
    numWritePorts:     Int,
    numReadwritePorts: Int
  )(
    implicit evidence: T <:< Vec[_],
    sourceInfo:        SourceInfo
  ): SRAMInterface[T] = {
    val clock = Builder.forcedClock
    memInterface_impl(
      size,
      tpe,
      Seq.fill(numReadPorts)(clock),
      Seq.fill(numWritePorts)(clock),
      Seq.fill(numReadwritePorts)(clock),
      None,
      Some(evidence),
      sourceInfo
    )
  }

  def masked[T <: Data](
    size:              BigInt,
    tpe:               T,
    numReadPorts:      Int,
    numWritePorts:     Int,
    numReadwritePorts: Int,
    memoryFile:        MemoryFile
  )(
    implicit evidence: T <:< Vec[_],
    sourceInfo:        SourceInfo
  ): SRAMInterface[T] = {
    val clock = Builder.forcedClock
    memInterface_impl(
      size,
      tpe,
      Seq.fill(numReadPorts)(clock),
      Seq.fill(numWritePorts)(clock),
      Seq.fill(numReadwritePorts)(clock),
      Some(memoryFile),
      Some(evidence),
      sourceInfo
    )
  }

  def masked[T <: Data](
    size:                BigInt,
    tpe:                 T,
    readPortClocks:      Seq[Clock],
    writePortClocks:     Seq[Clock],
    readwritePortClocks: Seq[Clock]
  )(
    implicit evidence:   T <:< Vec[_],
    sourceInfo:          SourceInfo
  ): SRAMInterface[T] =
    memInterface_impl(
      size,
      tpe,
      readPortClocks,
      writePortClocks,
      readwritePortClocks,
      None,
      Some(evidence),
      sourceInfo
    )

  def masked[T <: Data](
    size:                BigInt,
    tpe:                 T,
    readPortClocks:      Seq[Clock],
    writePortClocks:     Seq[Clock],
    readwritePortClocks: Seq[Clock],
    memoryFile:          MemoryFile
  )(
    implicit evidence:   T <:< Vec[_],
    sourceInfo:          SourceInfo
  ): SRAMInterface[T] =
    memInterface_impl(
      size,
      tpe,
      readPortClocks,
      writePortClocks,
      readwritePortClocks,
      Some(memoryFile),
      Some(evidence),
      sourceInfo
    )

  private def memInterface_impl[T <: Data](
    size:                BigInt,
    tpe:                 T,
    readPortClocks:      Seq[Clock],
    writePortClocks:     Seq[Clock],
    readwritePortClocks: Seq[Clock],
    memoryFile:          Option[MemoryFile],
    evidenceOpt:         Option[T <:< Vec[_]],
    sourceInfo:          SourceInfo
  ): SRAMInterface[T] = {
    val numReadPorts      = readPortClocks.size
    val numWritePorts     = writePortClocks.size
    val numReadwritePorts = readwritePortClocks.size
    val isVecMem          = evidenceOpt.isDefined
    val isValidSRAM       = ((numReadPorts + numReadwritePorts) > 0) && ((numWritePorts + numReadwritePorts) > 0)

    if (!isValidSRAM) {
      val badMemory =
        if (numReadPorts + numReadwritePorts == 0)
          "write-only SRAM (R + RW === 0)"
        else
          "read-only SRAM (W + RW === 0)"
      Builder.error(
        s"Attempted to initialize a $badMemory! SRAMs must have both at least one read accessor and at least one write accessor."
      )
    }

    val mem = Instantiate(
      new SRAMBlackbox(
        new CIRCTSRAMParameter(
          s"sram_${size}x${tpe.getWidth}",
          numReadPorts,
          numWritePorts,
          numReadwritePorts,
          size.intValue,
          tpe.getWidth,
          tpe match {
            case vec: Vec[_] => vec.size
            case _ => 0
          }
        )
      )
    )

    val sramReadPorts      = Seq.tabulate(numReadPorts)(i => mem.io.R(i))
    val sramWritePorts     = Seq.tabulate(numWritePorts)(i => mem.io.W(i))
    val sramReadwritePorts = Seq.tabulate(numReadwritePorts)(i => mem.io.RW(i))

    val includeMetadata = Builder.includeUtilMetadata

    val out = Wire(
      new SRAMInterface(size, tpe, numReadPorts, numWritePorts, numReadwritePorts, isVecMem, includeMetadata)
    )

    out.readPorts.zip(sramReadPorts).zip(readPortClocks).map { case ((intfReadPort, sramReadPort), readClock) =>
      sramReadPort.address := intfReadPort.address
      sramReadPort.clock   := readClock
      intfReadPort.data    := sramReadPort.data.asTypeOf(tpe)
      sramReadPort.enable  := intfReadPort.enable
    }
    out.writePorts.zip(sramWritePorts).zip(writePortClocks).map { case ((intfWritePort, sramWritePort), writeClock) =>
      sramWritePort.address := intfWritePort.address
      sramWritePort.clock   := writeClock
      sramWritePort.data    := intfWritePort.data.asUInt
      sramWritePort.enable  := intfWritePort.enable
      sramWritePort.mask match {
        case Some(mask) => mask := intfWritePort.mask.get.asUInt
        case None       => assert(intfWritePort.mask.isEmpty)
      }
    }
    out.readwritePorts.zip(sramReadwritePorts).zip(readwritePortClocks).map {
      case ((intfReadwritePort, sramReadwritePort), readwriteClock) =>
        sramReadwritePort.address     := intfReadwritePort.address
        sramReadwritePort.clock       := readwriteClock
        sramReadwritePort.enable      := intfReadwritePort.enable
        intfReadwritePort.readData    := sramReadwritePort.readData.asTypeOf(tpe)
        sramReadwritePort.writeData   := intfReadwritePort.writeData.asUInt
        sramReadwritePort.writeEnable := intfReadwritePort.isWrite
        sramReadwritePort.writeMask match {
          case Some(mask) => mask := intfReadwritePort.mask.get.asUInt
          case None       => assert(intfReadwritePort.mask.isEmpty)
        }
    }

    out.description.foreach { description =>
      val descriptionInstance: Instance[SRAMDescription] = Instantiate(new SRAMDescription)
      descriptionInstance.depthIn           := Property(size)
      descriptionInstance.widthIn           := Property(tpe.getWidth)
      descriptionInstance.maskedIn          := Property(isVecMem)
      descriptionInstance.readIn            := Property(numReadPorts)
      descriptionInstance.writeIn           := Property(numWritePorts)
      descriptionInstance.readwriteIn       := Property(numReadwritePorts)
      descriptionInstance.maskGranularityIn := Property(
        Option
          .when(isVecMem)(tpe match {
            case t: Vec[_] => t.sample_element.getWidth
          })
          .getOrElse(0)
      )
      descriptionInstance.hierarchyIn       := Property(Path(mem.toTarget))
      description                           := descriptionInstance.getPropertyReference
    }
    out
  }

}
