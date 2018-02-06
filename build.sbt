import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

name := "universal-recommender"

version := "0.8.0-SNAPSHOT"

organization := "com.actionml"

scalaVersion := "2.11.11"

val scalaCompatVersion = "2.11"

scalaVersion in ThisBuild := "2.11.11"

val mahoutVersion = "0.13.0"

val pioVersion = "0.12.0-incubating"

val elasticsearchVersion = "5.5.2"

val sparkVersion = "2.1.2"

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % pioVersion % "provided",
  "org.elasticsearch.client" % "rest" % elasticsearchVersion,
  "org.elasticsearch"       %% "elasticsearch-spark-20" % elasticsearchVersion % "provided"
    exclude("org.apache.spark", "*"),
  "org.elasticsearch"        % "elasticsearch-hadoop-mr"  % elasticsearchVersion % "provided",
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-hive" % sparkVersion % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
  // Mahout's Spark libs
  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion,
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_" + scalaCompatVersion),
  "org.apache.mahout"  % "mahout-math" % mahoutVersion,
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client"),
  //"org.apache.hbase"        % "hbase-client"   % "0.98.5-hadoop2" % "provided",
  //  exclude("org.apache.zookeeper", "zookeeper"),
  // other external libs
  "com.thoughtworks.xstream" % "xstream" % "1.4.4"
    exclude("xmlpull", "xmlpull")
  //"org.json4s" %% "json4s-native" % "3.2.10")
  // for Spark 2.2.0: "org.json4s" %% "json4s-native" % "3.2.11" % "provided")
  // for greater than what Spark provides:
  //"org.json4s" %% "json4s-native" % "3.5.3"
  )
  .map(_.exclude("org.apache.lucene","lucene-core")).map(_.exclude("org.apache.lucene","lucene-analyzers-common"))

/*
libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % pioVersion % "provided",
  "org.elasticsearch.client" % "rest" % elasticsearchVersion,
  "org.elasticsearch"       %% "elasticsearch-spark-20" % elasticsearchVersion % "provided"
    exclude("org.apache.spark", "*"),
  "org.elasticsearch"        % "elasticsearch-hadoop-mr"  % elasticsearchVersion % "provided",
  "org.apache.spark" %% "spark-core" % "1.4.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "1.4.0" % "provided",
  "org.apache.spark" %% "spark-hive" % "1.6.0" % "provided",
  //"org.apache.spark" %% "spark-sql" % "1.6.3" % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
  // Mahout's Spark libs. They're custom compiled for Scala 2.11
  // and included in the local Maven repo in the .custom-scala-m2/repo resolver below
  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion,
  // for 0.13.1
  // "org.apache.mahout" %% "mahout-spark" % mahoutVersion classifier "spark_2.1"
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_" + scalaCompatVersion),
  "org.apache.mahout"  % "mahout-math" % mahoutVersion,
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client"),
  // other external libs
  "com.thoughtworks.xstream" % "xstream" % "1.4.4"
    exclude("xmlpull", "xmlpull"),
  "org.json4s" %% "json4s-native" % "3.2.10")
  .map(_.exclude("org.apache.lucene","lucene-core")).map(_.exclude("org.apache.lucene","lucene-analyzers-common"))
*/

resolvers += "Local Repository" at "file:///Users/pat/.custom-scala-m2/repo"

resolvers += Resolver.mavenLocal

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)

assemblyMergeStrategy in assembly := {
  case "plugin.properties" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith "package-info.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "UnusedStubClass.class" =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
