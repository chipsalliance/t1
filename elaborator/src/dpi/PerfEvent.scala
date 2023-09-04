package elaborate.dpi

import chisel3._
import v.{LaneState, VParameter}

case class PerfEventParameter(triggerDelay: Int, vParameter: VParameter)

class PerfEvent(p: PerfEventParameter) extends DPIModule {
  val isImport: Boolean = true

  val clock = dpiTrigger("clock", Input(Bool()))

  // 指令发射到执行单元
  // instructionEnqReady = executionReady && slotReady && instructionRAWReady && gatherReadReady
  val instructionEnqValid = dpiIn("instructionEnqValid", Input(Bool()))
  val instructionEnqReady = dpiIn("instructionEnqReady", Input(Bool()))
  val executionReady = dpiIn("executionReady", Input(Bool()))
  val slotReady = dpiIn("slotReady", Input(Bool()))
  val instructionRAWReady = dpiIn("instructionRAWReady", Input(Bool()))

  val slotShifterEnqueue = Seq.tabulate(p.vParameter.laneNumber) { laneIndex =>
    dpiIn(s"slotShifterEnqueue${laneIndex}", Seq.tabulate(p.vParameter.chainingSize)(_ => Input(Bool())))
  }

  // todo: state 在lane的for里面
//  val slotState = Seq.tabulate(p.vParameter.laneNumber) { laneIndex =>
//    Seq.tabulate(p.vParameter.chainingSize) { slotIndex =>
//      dpiIn(s"lane${laneIndex}_slot${slotIndex}_state", new LaneState(p.vParameter.laneParam))
//    }
//  }

  // lsu
  // store unit
  val storeUnitIdle = dpiIn("storeUnitIdle", Input(Bool()))
  val storeUnitReleasePort = dpiIn("storeUnitReleasePort", Seq.fill(p.vParameter.lsuParam.memoryBankSize)(Input(Bool())))
  val storeUnitReadVrfValid = dpiIn("storeUnitReadVrfValid", Seq.fill(p.vParameter.laneNumber)(Input(Bool())))
  val storeUnitReadVrfReady = dpiIn("storeUnitReadVrfReady", Seq.fill(p.vParameter.laneNumber)(Input(Bool())))
  val vrfReadyToStore: DPIReference[Bool] = dpiIn("vrfReadyToStore", Input(Bool()))
  val storeUnitAccessTileLinkValid =
    dpiIn("storeUnitAccessTileLinkValid", Seq.fill(p.vParameter.memoryBankSize)(Input(Bool())))
  val storeUnitAccessTileLinkReady =
    dpiIn("storeUnitAccessTileLinkReady", Seq.fill(p.vParameter.memoryBankSize)(Input(Bool())))

  val loadUnitIdle = dpiIn("loadUnitIdle", Input(Bool()))
  val writeReadyForLsu: DPIReference[Bool] = dpiIn("writeReadyForLsu", Input(Bool()))
  val loadUnitAccessTileLinkValid = dpiIn("loadUnitAccessTileLinkValid", Input(Bool()))
  val loadUnitAccessTileLinkReady = dpiIn("loadUnitAccessTileLinkReady", Input(Bool()))
  val loadUnitTileLinkAckValid = dpiIn("loadUnitTileLinkAckValid", Seq.fill(p.vParameter.memoryBankSize)(Input(Bool())))
  val loadUnitTileLinkAckReady = dpiIn("loadUnitTileLinkAckReady", Seq.fill(p.vParameter.memoryBankSize)(Input(Bool())))
  val loadUnitWriteVrfValid = dpiIn("loadUnitWriteVrfValid", Seq.fill(p.vParameter.laneNumber)(Input(Bool())))
  val loadUnitWriteVrfReady = dpiIn("loadUnitWriteVrfReady", Seq.fill(p.vParameter.laneNumber)(Input(Bool())))

  val otherUnitIdle = dpiIn("otherUnitIdle", Input(Bool()))
  val otherUnitReadVrfValid = dpiIn("otherUnitReadVrfValid", Input(Bool()))
  val otherUnitReadVrfReady = dpiIn("otherUnitReadVrfReady", Input(Bool()))
  val otherUnitAccessTileLinkValid = dpiIn("otherUnitAccessTileLinkValid", Input(Bool()))
  val otherUnitAccessTileLinkReady = dpiIn("otherUnitAccessTileLinkReady", Input(Bool()))
  val otherUnitTileLinkAckValid = dpiIn("otherUnitTileLinkAckValid", Input(Bool()))
  val otherUnitTileLinkAckReady = dpiIn("otherUnitTileLinkAckReady", Input(Bool()))
  val otherUnitWriteVrfValid = dpiIn("otherUnitWriteVrfValid", Input(Bool()))
  val otherUnitWriteVrfReady = dpiIn("otherUnitWriteVrfReady", Input(Bool()))
  val otherUnitAccessVrfTargetLane = dpiIn("otherUnitWriteVrfReady", Input(UInt(p.vParameter.laneNumber.W)))


  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
