import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys


name := "universal-recommender"

scalaVersion := "2.11.8"

version := "0.5.0"

organization := "com.actionml"

val mahoutVersion = "0.13.0"
val pioVersion = "0.12.0-incubating"
val elasticsearchVersion = "5.5.2"

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % pioVersion % "provided",
  "org.elasticsearch.client" % "rest" % elasticsearchVersion,
  "org.elasticsearch"       %% "elasticsearch-spark-20" % elasticsearchVersion % "provided"
    exclude("org.apache.spark", "*"),
  "org.elasticsearch"        % "elasticsearch-hadoop-mr"  % elasticsearchVersion % "provided",
  "org.apache.spark" %% "spark-core" % "2.1.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.1.0" % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
  // Mahout's Spark libs. They're custom compiled for Scala 2.11
  // and included in the buildpack's local Maven repo.
  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion,
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_2.11"),
  "org.apache.mahout"  % "mahout-math" % mahoutVersion,
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client"),
  // other external libs
  "com.thoughtworks.xstream" % "xstream" % "1.4.4"
    exclude("xmlpull", "xmlpull"),
  "org.json4s" %% "json4s-native" % "3.2.10")
  .map(_.exclude("org.apache.lucene","lucene-core")).map(_.exclude("org.apache.lucene","lucene-analyzers-common"))

resolvers += Resolver.mavenLocal

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)

assemblyMergeStrategy in assembly := {
  case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
  case PathList("org", "aopalliance", xs @ _*) => MergeStrategy.last
  case PathList("org", "apache", "commons", xs @ _*) => MergeStrategy.last
  case "plugin.properties" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith "package-info.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "UnusedStubClass.class" =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
