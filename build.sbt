scalaVersion := "3.10.0"

name := "evals-demo"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-evals" % "0.2.0",
  "com.jamesward" % "skills" % "0.0.11",
)

scalacOptions ++= Seq(
  "-language:strictEquality",
  "-deprecation",
  "-Werror",
)

fork := true

// sbt-mcp (loopback-only: its tools can execute build tasks)
ThisBuild / mcpEnabled := true
ThisBuild / mcpHost := "127.0.0.1"
ThisBuild / mcpPort := 5101

// SkillsJars: extract agent Skills with `./sbt extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.11" % Skills
