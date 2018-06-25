import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

name := "universal-recommender"

version := "0.7.3"

organization := "com.actionml"

scalaVersion := "2.11.11"

scalaVersion in ThisBuild := "2.11.11"

val mahoutVersion = "0.13.0"

val pioVersion = "0.12.0-incubating"

val elasticsearchVersion = "5.5.2"

val sparkVersion = "2.1.1"

libraryDependencies ++= Seq(
  "org.apache.predictionio" %% "apache-predictionio-core" % pioVersion % "provided",
  "org.elasticsearch.client" % "rest" % elasticsearchVersion,
  "org.elasticsearch"       %% "elasticsearch-spark-20" % elasticsearchVersion % "provided"
    exclude("org.apache.spark", "*"),
  "org.elasticsearch"        % "elasticsearch-hadoop-mr"  % elasticsearchVersion % "provided",
  "org.apache.spark" %% "spark-core" % "2.1.1" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.1.1" % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
  // Mahout's Spark libs. They're custom compiled for Scala 2.11
  // and included in the local Maven repo in the .custom-scala-m2/repo resolver below
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


//resolvers += "Local Repository" at "file:///<path-to>/.custom-scala-m2/repo"

resolvers += "Temp Scala 2.11 build of Mahout" at "https://github.com/actionml/mahout_2.11/raw/mvn-repo/"

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

//assemblyShadeRules in assembly := Seq(
//  ShadeRule.rename("org.apache.http.**" -> "shadeio.ur.data.http.@1").inAll,
//  ShadeRule.rename("org.elasticsearch.client.**" -> "shadeio.ur.data.client.@1").inAll)
