scalaVersion := "3.9.0"

name := "evals-demo"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-evals" % "0.0.3",
  "com.jamesward" % "skills" % "0.0.3",
)

fork := true
