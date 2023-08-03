package v

import chisel3._
import chisel3.util._

abstract class IndexUnit(param: MSHRParam) extends Module with LSUPublic {
  /** offset of indexed load/store instructions. */
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))

  // TODO: remove me.
  val indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))

  /** the current index in offset group for [[indexedInstructionOffsets]]
   * TODO: remove `val indexOfIndexedInstructionOffsetsNext: UInt = Wire(UInt(2.W))`
   */
  val indexOfIndexedInstructionOffsets: UInt =
    RegEnable(indexOfIndexedInstructionOffsetsNext, lsuRequest.valid || offsetReadResult.head.valid)
  indexOfIndexedInstructionOffsetsNext := Mux(lsuRequest.valid, 3.U(2.W), indexOfIndexedInstructionOffsets + 1.U)

  /** the storeage of a group of offset for indexed instructions.
   *
   * @note this group is the offset group.
   */
  val indexedInstructionOffsets: Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(param.laneNumber)(0.U.asTypeOf(Valid(UInt(param.datapathWidth.W)))))
  )

  /** enable signal to update the offset group. */
  val updateOffsetGroupEnable: Bool = WireDefault(false.B)

  /** record the used [[indexedInstructionOffsets]] for sending memory transactions. */
  val usedIndexedInstructionOffsets: Vec[Bool] = Wire(Vec(param.laneNumber, Bool()))

  indexedInstructionOffsets.zipWithIndex.foreach {
    case (offset, index) =>
      // offsetReadResult(index).valid: new offset came
      // (offset.valid && !usedIndexedInstructionOffsets(index)): old unused offset
      offset.valid := offsetReadResult(index).valid || (offset.valid && !usedIndexedInstructionOffsets(index))
      // select from new and old.
      offset.bits := Mux(offsetReadResult(index).valid, offsetReadResult(index).bits, offset.bits)
  }

  /** only need to request offset when changing offset group,
   * don't send request for the first offset group for each instruction.
   */
  val needRequestOffset: Bool =
    RegEnable(offsetReadResult.head.valid, false.B, offsetReadResult.head.valid || lsuRequest.valid)

  /** signal indicate that the offset group for all lanes are valid. */
  val allOffsetValid: Bool = VecInit(indexedInstructionOffsets.map(_.valid)).asUInt.andR

  /** signal used for aligning offset groups. */
  val offsetGroupsAlign: Bool = RegInit(false.B)
  // to fix the bug that after the first group being used, the second group is not valid,
  // MSHR will change group by mistake.
  // TODO: need perf the case, if there are too much "misalignment", use a state vector for each lane(bit) in the group.
  when(!offsetGroupsAlign && allOffsetValid) {
    offsetGroupsAlign := true.B
  }.elsewhen(status.offsetGroupEnd) {
    offsetGroupsAlign := false.B
  }

  val offsetOfOffsetGroup: UInt = Wire(UInt(param.maxOffsetPerLaneAccessBits.W))

  val offsetGroupCheck: Bool = (!offsetEEW(0) || !offsetGroupMatch(0)) && (!offsetEEW(1) || offsetGroupMatch === 0.U)

  /** check offset we are using is valid or not. */
  val offsetValidCheck: Bool =
    (
      VecInit(indexedInstructionOffsets.map(_.valid)).asUInt >> (
        // offsetOfOffsetGroup is in byte level
        offsetOfOffsetGroup >>
          // shift it to word level
          log2Ceil(param.datapathWidth / 8)
        ).asUInt
      ).asUInt(0)

  /** no need index, when use a index, check it is valid or not. */
  val indexCheck: Bool = (offsetValidCheck && offsetGroupCheck && offsetGroupsAlign)

  /** latch [[requestOffset]] */
  val requestOffsetNext: Bool = RegNext(requestOffset)

  // ask Scheduler to change offset group
  status.offsetGroupEnd := needRequestOffset && requestOffset && !requestOffsetNext
}