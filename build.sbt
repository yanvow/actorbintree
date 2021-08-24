course := "reactive"
assignment := "actorbintree"

libraryDependencies += "org.scalameta" %% "munit" % "0.7.22"


val MUnitFramework = new TestFramework("munit.Framework")
testFrameworks += MUnitFramework
// Decode Scala names
testOptions += Tests.Argument(MUnitFramework, "-s")
parallelExecution in Test := false

val akkaVersion = "2.6.0"

scalaVersion := "3.0.0-RC1"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-language:implicitConversions"
)

libraryDependencies += ("com.typesafe.akka" %% "akka-actor" % akkaVersion).withDottyCompat(scalaVersion.value)
testSuite := "actorbintree.BinaryTreeSuite"
