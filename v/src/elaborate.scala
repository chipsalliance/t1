package v

import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.VerilogEmitter
import firrtl.options.TargetDirAnnotation
import firrtl.stage.RunFirrtlTransformAnnotation

object elaborate extends App {
  (new chisel3.stage.ChiselStage).run(
    Seq(
      TargetDirAnnotation("./builds/"),
      //EmitAllModulesAnnotation(classOf[VerilogEmitter]),
      ChiselGeneratorAnnotation(() => new VRF(VRFParam(7, 32))),
      RunFirrtlTransformAnnotation(new VerilogEmitter)
    )
  )
}