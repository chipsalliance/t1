// Generate list of tests that must be passed
def passed(passedFile: os.Path): Seq[String] = {
  val verilatorType = Seq("v1024l8b2-test");
  val runType = Seq("debug");
  verilatorType.flatMap(
    vtype => runType.flatMap(
      rtype => os.read.lines(passedFile).map(
        test => s"verilatorEmulator[$vtype,$test,$rtype].run"
      )
    )
  )
}

def all(root: os.Path): Seq[String] = os.proc("mill", "resolve", "verilatorEmulator[__].Run").call(root).out.text.split("\n").toSeq
// Turn Seq( "A", "B", "C", "D" ) to Seq( "A,B", "C,D" )
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] = scala.util.Random.shuffle(alltests).grouped(math.ceil(alltests.size.toDouble / bucketSize).toInt).toSeq.map(_.mkString(","))
// Turn Seq( "A,B", "C,D" ) to { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
def writeJson(buckets: Seq[String], outputFile: os.Path) = os.write.over(outputFile, ujson.Obj("include" -> buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

// passed.txt String => Array[MillTask String] => Array[MillTaskBucket String] => Json(include: [ name: MillTaskBucket ])
@main
def passedJson(bucketSize: Int, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets(passed(passedFile),bucketSize),outputFile)

@main
def unpassedJson(bucketSize: Int, root: os.Path, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets((all(root).toSet -- passed(passedFile).toSet).toSeq,bucketSize),outputFile)

@main
def allJson(bucketSize: Int, root: os.Path, outputFile: os.Path) = writeJson(buckets(all(root),bucketSize),outputFile)

@main
def runTest(root: os.Path, jobs: String, outputFile: os.Path) = {
  jobs.split(",").foreach(job => os.proc("mill", job).call(root))
  val allJson = os.walk(os.pwd).filter(_.last == "ciRun.json")
  val exitCode = allJson.map(f => ujson.read(os.read(f))("value").num.toInt).reduce(_ max _)
  allJson.map(f => s"|${f.segments.toSeq.dropRight(1).last}|${ujson.Bool(ujson.read(os.read(f))("value").num.toInt == 0)}|\n").foreach(os.write.append(outputFile, _))

  val perfCases = os.read(root / os.RelPath(".github/perf-cases.txt")).split('\n').filter(_.length > 0)
  val existCases = perfCases.filter(c => os.exists(root / os.RelPath(s"out/tests/run/$c/ciRun.dest/perf.txt")))
  existCases.foreach{c =>
    val output = s"""### $c
    |```
    |${os.read(root / os.RelPath(s"out/tests/run/$c/ciRun.dest/perf.txt")).trim}
    |```
    |\n""".stripMargin
    os.write(root / s"perf-result-$c.md", output)
  }

  if (exitCode != 0) {
    throw new Exception(s"runTest failed with exit code ${exitCode}")
  }
}

@main
def genTestElf(testDir: os.Path, outDir: os.Path) = {
  // Prepare output directory
  //   output/
  //      configs/
  //      tests/
  //        mlir/
  //        asm/
  //        .../
  os.remove.all(outDir)
  // Ensure there is no cache
  os.remove.all(testDir / "out")
  os.makeDir.all(outDir)
  val outConfigDir = outDir / "configs"
  os.makeDir.all(outConfigDir)
  val outElfDir = outDir / "tests"
  os.makeDir.all(outElfDir)

  os.proc("mill", "--no-server", "resolve", "_[_]")
    .call(testDir).out.text
    .split('\n').toSeq
    .foreach(task => {
      // Compile and get abosolute path to the final elf binary
      val rawElfPath = os.proc("mill", "--no-server", "show", s"$task.elf").call(testDir).out.text
      val elfPath = os.Path(ujson.read(rawElfPath).str.split(':')(2))
      // Get original test config for this task
      val rawTestConfig = os.proc("mill", "--no-server", "show", s"$task.testConfig").call(testDir).out.text
      val testConfig = ujson.read(rawTestConfig)
      val taskType = testConfig("type").str
      // { elf: { path: "../tests/$type/$elf" } }
      testConfig("elf") = ujson.Obj("path" -> s"tests/$taskType/${elfPath.last}")

      val testCategory = outElfDir / taskType
      os.makeDir.all(testCategory)
      os.move(elfPath, testCategory / elfPath.last)

      os.write(outConfigDir / s"${elfPath.baseName}-$taskType.json", ujson.write(testConfig))
    })
}
