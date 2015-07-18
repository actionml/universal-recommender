name := "template-scala-parallel-recommendation"

organization := "io.prediction"

val mahoutVersion = "0.11.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "io.prediction"    %% "core" % pioVersion.value % "provided",
  "org.apache.spark" %% "spark-core" % "1.3.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "1.3.0" % "provided",
  "org.xerial.snappy" % "snappy-java" % "1.1.1.7",
  // Mahout's Spark code
  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion
    from "file://./ext-lib/mahout-math-scala_2.10-" + mahoutVersion + ".jar",
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_2.10")
    from "file://./ext-lib/mahout-spark-scala_2.10-" + mahoutVersion + ".jar",
  "org.apache.mahout"  % "mahout-math" % mahoutVersion
    from "file://./ext-lib/mahout-math-" + mahoutVersion + ".jar",
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client")
    from "file://./ext-lib/mahout-hdfs-" + mahoutVersion + ".jar",
  "com.thoughtworks.xstream" % "xstream" % "1.4.4"
    exclude("xmlpull", "xmlpull"),
  "org.elasticsearch" % "elasticsearch-spark_2.10" % "2.1.0.Beta4"
    exclude("org.apache.spark", "spark-catalyst_2.10")
    exclude("org.apache.spark", "spark-sql_2.10"),
  "org.json4s" %% "json4s-native" % "3.2.11"
)

resolvers += Resolver.mavenLocal

assemblyMergeStrategy in assembly := {
  case "plugin.properties" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith "package-info.class" =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

/*
  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion
    from "file://./lib/mahout-math-scala_2.10-" + mahoutVersion + ".jar",
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_2.10")
    from "file://./lib/mahout-spark-scala_2.10-" + mahoutVersion + ".jar",
  "org.apache.mahout"  % "mahout-math" % mahoutVersion
    from "file://./lib/mahout-math-" + mahoutVersion + ".jar",
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client")
    from "file://./lib/mahout-hdfs-" + mahoutVersion + ".jar",


  "org.apache.mahout" %% "mahout-math-scala" % mahoutVersion,
  "org.apache.mahout" %% "mahout-spark" % mahoutVersion
    exclude("org.apache.spark", "spark-core_2.10"),
  "org.apache.mahout"  % "mahout-math" % mahoutVersion,
  "org.apache.mahout"  % "mahout-hdfs" % mahoutVersion
    exclude("com.thoughtworks.xstream", "xstream")
    exclude("org.apache.hadoop", "hadoop-client"),

 */