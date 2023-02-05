def passed(passedFile: os.Path): Seq[String] = os.read.lines(passedFile).map(s => s"tests.run[$s].ciRun")
def all(root: os.Path): Seq[String] = os.proc("mill", "resolve", "tests.run[__].ciRun").call(root).out.text.split("\n").toSeq
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] = alltests.grouped(math.ceil(alltests.size.toDouble / bucketSize).toInt).toSeq.map(_.mkString(","))
def writeJson(buckets: Seq[String], outputFile: os.Path) = os.write.over(outputFile, ujson.Obj("include" -> buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

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
  if (exitCode != 0) {
    throw new Exception(s"runTest failed with exit code ${exitCode}")
  }
}
