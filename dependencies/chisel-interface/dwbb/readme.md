# DWBB(DesignWare Building Block)

[The DesignWare Library's Datapath and Building Block IP](https://www.synopsys.com/dw/buildingblock.php) is a collection of reusable intellectual property blocks that are tightly integrated into the Synopsys synthesis environment.

This is its wrapper in Chisel. This project is designed for providing an interface for Chisel user to easily instantiate the DWBB IP to improve the circuit performance and reduce the duplicate library work.

## Project Structure

There are two folders for the project.

- interface

  The interface is interface that need to be implemented in reference and instantiated in wrapper, it also contains the chisel parameter for specific building block.

- wrapper
  
  this is the wrapper for dwbb, is what normal user requires to instantiate DWBB blackbox in their project.

## User Guide

Import this project into your project via submodule. Project example will be given in the future.

## Contribution Guide

Add interface to interface folder:

```scala
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.interface.SOME_DWBB

import chisel3._
import chisel3.experimental.SerializableModuleParameter
import upickle.default

object Parameter {
  implicit def rw: default.ReadWriter[Parameter] =
    upickle.default.macroRW[Parameter]
}
// add scala parameter for Verilog parameter, it will be automatically serialized to json via upickle.
// All the parameter should be meaningful and should be the basic type.
case class Parameter(someParameter: Int) extends SerializableModuleParameter {
  // fill requirements here, which is usually in the dwbb pdf documentation.
  require(???)
}

// declare bundle with 
class Interface(parameter: Parameter) extends Bundle {
  // declare bundle here, order should be same with DWBB.
  val SOMEINPUT: UInt = Input(UInt(parameter.someParaemter.W))
}
```

Add wrapper:

```scala
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface._

import scala.collection.immutable.SeqMap

class SOME_DWBB(parameter: SOME_DWBB.Parameter)
    extends WrapperModule[SOME_DWBB.Interface, SOME_DWBB.Parameter](
      new SOME_DWBB.Interface(parameter),
      parameter,
      p =>
        SeqMap(
          // convert Scala parameter to Verilog Param.
          // this should be conformed to the DWBB documentation.
          "width" -> IntParam(p.width)
        )
    )
```

## License

The DesignWare Building Block is the property of Synopsys, Inc. All Rights Reserved.

The Hardware IP generators implemented in this library are under [Apache-2.0 License](https://opensource.org/licenses/Apache-2.0).

Copyright All Rights Reserved Jiuyang Liu <liu@jiuyang.me>
