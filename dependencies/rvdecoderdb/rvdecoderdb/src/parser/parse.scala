// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rvdecoderdb.parser

import org.chipsalliance.rvdecoderdb.{Arg, Instruction, InstructionSet}

object parse {
  def apply(
    opcodeFiles: Iterable[(String, String, Boolean, Boolean)],
    argLut:      Map[String, (Int, Int)]
  ): Iterable[Instruction] = {
    val rawInstructionSets: Iterable[RawInstructionSet] = opcodeFiles.map {
      case (instructionSet, content, ratified, custom) =>
        RawInstructionSet(
          instructionSet,
          ratified,
          custom,
          content
            .split("\n")
            .map(_.trim)
            .filter(!_.startsWith("#"))
            .filter(_.nonEmpty)
            .map(
              _.split(" ")
                .filter(_.nonEmpty)
                .map {
                  case "$import"          => Import
                  case "$pseudo_op"       => PseudoOp
                  case RefInst(i)         => i
                  case SameValue(s)       => s
                  case FixedRangeValue(f) => f
                  case BitValue(b)        => b
                  case ArgLUT(a)          => a
                  case BareStr(i)         => i
                }
            )
            .map(new RawInstruction(_))
        )
    }
    // for general instructions which doesn't collide.
    val instructionSetsMap = collection.mutable.HashMap.empty[String, Seq[String]]
    val ratifiedMap = collection.mutable.HashMap.empty[String, Boolean]
    val argsMap = collection.mutable.HashMap.empty[String, Seq[Arg]]
    val customMap = collection.mutable.HashMap.empty[String, Boolean]
    val encodingMap = collection.mutable.HashMap.empty[String, org.chipsalliance.rvdecoderdb.Encoding]
    // for pseudo instructions, they only exist in on instruction set, and pseudo from another general instruction
    // thus key should be (set:String, name: String)
    val pseudoFromMap = collection.mutable.HashMap.empty[(String, String), String]
    val pseudoCustomMap = collection.mutable.HashMap.empty[(String, String), Boolean]
    val pseudoArgsMap = collection.mutable.HashMap.empty[(String, String), Seq[Arg]]
    val pseudoRatifiedMap = collection.mutable.HashMap.empty[(String, String), Boolean]
    val pseudoEncodingMap = collection.mutable.HashMap.empty[(String, String), org.chipsalliance.rvdecoderdb.Encoding]

    // create normal instructions
    rawInstructionSets.foreach { set: RawInstructionSet =>
      set.rawInstructions.foreach {
        case rawInst: RawInstruction if rawInst.isNormal =>
          require(
            instructionSetsMap.get(rawInst.name).isEmpty,
            s"redefined instruction: ${rawInst.name} in ${instructionSetsMap(rawInst.name).head} and ${set.name}"
          )
          instructionSetsMap.update(rawInst.name, Seq(set.name))
          ratifiedMap.update(rawInst.name, set.ratified)
          customMap.update(rawInst.name, set.custom)
          encodingMap.update(rawInst.name, rawInst.encoding)
          argsMap.update(rawInst.name, rawInst.args.map(al => Arg(al.name, argLut(al.name)._1, argLut(al.name)._2)))
        case rawInst: RawInstruction if rawInst.pseudoInstruction.isDefined =>
          val k = (set.name, rawInst.name)
          pseudoFromMap.update(k, rawInst.pseudoInstruction.get._2)
          pseudoRatifiedMap.update(k, set.ratified)
          pseudoCustomMap.update(k, set.custom)
          pseudoEncodingMap.update(k, rawInst.encoding)
          pseudoArgsMap.update(k, rawInst.args.map(al => Arg(al.name, argLut(al.name)._1, argLut(al.name)._2)))
        case _ =>
      }
    }

    // imported_instructions - these are instructions which are borrowed from an extension into a new/different extension/sub-extension. Only regular instructions can be imported. Pseudo-op or already imported instructions cannot be imported.
    rawInstructionSets.foreach { set: RawInstructionSet =>
      set.rawInstructions.foreach {
        case rawInst: RawInstruction if rawInst.importInstructionSet.isDefined =>
          instructionSetsMap.filter(_._2.head == rawInst.importInstructionSet.get).map {
            case (k, v) =>
              instructionSetsMap.update(k, v ++ Some(set.name))
          }
        case rawInst: RawInstruction if rawInst.importInstruction.isDefined =>
          val k = rawInst.importInstruction.get._2
          val v = instructionSetsMap(k)
          instructionSetsMap.update(k, v ++ Some(set.name))
        case _ =>
      }
    }

    val instructions = encodingMap.keys.map(instr =>
      Instruction(
        instr,
        encodingMap(instr),
        argsMap(instr).sortBy(_.lsb), {
          val sets = instructionSetsMap(instr).map(InstructionSet.apply)
          sets.head +: sets.tail.sortBy(_.name)
        },
        None,
        ratifiedMap(instr),
        customMap(instr)
      )
    )

    val pseudoInstructions = pseudoEncodingMap.keys.map(instr =>
      Instruction(
        instr._2,
        pseudoEncodingMap(instr),
        pseudoArgsMap(instr).sortBy(_.lsb),
        Seq(InstructionSet(instr._1)).sortBy(_.name),
        Some(
          instructions
            .find(_.name == pseudoFromMap(instr))
            .getOrElse(throw new Exception("pseudo not found"))
        ),
        pseudoRatifiedMap(instr),
        pseudoCustomMap(instr)
      )
    )

    (instructions ++ pseudoInstructions).toSeq
      // sort instructions by default
      .sortBy(s =>
        (
          // sort by ratified, custom, unratified
          (s.ratified, s.custom) match {
            case (true, false)  => 0
            case (false, true)  => 1
            case (false, false) => 2
            case (true, true)   => throw new Exception("unreachable")
          },
          // sort by removing rv_, rv32_, rv64_, rv128_
          s.instructionSets.head.name.split("_").tail.mkString("_"),
          // sort by rv_, rv32_, rv64_, rv128_
          s.instructionSets.head.name.split("_").head match {
            case "rv"    => 0
            case "rv64"  => 64
            case "rv32"  => 32
            case "rv128" => 128
          }
        )
      )
  }
}
