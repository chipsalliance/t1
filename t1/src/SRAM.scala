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

  private val wLogic = Seq
    .tabulate(parameter.write)(idx =>
      Seq(
        s"reg [${log2Ceil(parameter.depth) - 1}:0] _R${idx}_addr;",
        s"reg _R${idx}_en;"
      ) ++
        Seq(s"always @(posedge R${idx}_clk) begin // RW${idx}") ++
        (if (parameter.masked)
           Seq.tabulate(parameter.width / parameter.maskGranularity)(i =>
             s"if (W${idx}_en & W${idx}_wmask[${i}]) Memory[W${idx}_addr][${i * parameter.maskGranularity}+:${parameter.maskGranularity}] <= RW${idx}_wdata[${(i + 1) * parameter.maskGranularity - 1}:${i * parameter.maskGranularity}];"
           )
         else
           Seq(s"if (W${idx}) Memory[W${idx}_addr] <= W${idx}_data;")) ++
        Seq(s"end // RW${idx}")
    )
    .flatten

  private val rLogic = Seq
    .tabulate(parameter.read)(idx =>
      Seq(
        s"reg [${log2Ceil(parameter.depth) - 1}:0] _R${idx}_en;",
        s"reg _R${idx}_addr;"
      ) ++
        Seq(
          s"always @(posedge R${idx}_clk) begin // R${idx}",
          s"_R${idx}_raddr <= R${idx}_addr;",
          s"_R${idx}_ren <= R${idx}_ren;",
          s"end // RW${idx}"
        ) ++
        Some(s"R${idx}_data = _R${idx}_ren ? Memory[_R${idx}_raddr] : ${parameter.width}'bx;")
    )
    .flatten

  private val rwLogic = Seq
    .tabulate(parameter.readwrite)(idx =>
      Seq(
        s"reg [${log2Ceil(parameter.depth) - 1}:0] _RW${idx}_raddr;",
        s"reg _RW${idx}_ren;",
        s"reg _RW${idx}_rmode;"
      ) ++
        Seq(s"always @(posedge RW${idx}_clk) begin // RW${idx}") ++
        Seq(
          s"_RW${idx}_raddr <= RW${idx}_addr;",
          s"_RW${idx}_ren <= RW${idx}_ren;",
          s"_RW${idx}_rmode <= RW${idx}_rmode;"
        ) ++
        (if (parameter.masked)
           Seq.tabulate(parameter.width / parameter.maskGranularity)(i =>
             s"if(RW${idx}_en & RW${idx}_wmask[${i}] & RW${idx}_wmode) Memory[RW${idx}_addr][${parameter.width / parameter.maskGranularity}'${i * parameter.maskGranularity}+:${parameter.maskGranularity}] <= RW${idx}_wdata[${(i + 1) * parameter.maskGranularity - 1}:${i * parameter.maskGranularity}];"
           )
         else
           Seq(s"if (RW${idx}) Memory[RW${idx}_addr] <= RW${idx}_data;")) ++
        Seq(s"end // RW${idx}") ++
        Seq(s"RW${idx}_rdata = _RW${idx}_ren ? Memory[_RW${idx}_raddr] : ${parameter.width}'bx;")
    )
    .flatten

  private val logic =
    (Seq(s"reg [${parameter.depth - 1}:0] Memory[0:${parameter.width - 1}];") ++ wLogic ++ rLogic ++ rwLogic)
      .mkString("\n")

  override def desiredName = parameter.moduleName

  setInline(
    desiredName,
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

      Module.currentModule.foreach { case m: RawModule =>
        m.atModuleBodyEnd {
          descriptionInstance.hierarchyIn := Property(Path(mem.toTarget))
        }
      }
      description := descriptionInstance.getPropertyReference
    }
    out
  }

}
