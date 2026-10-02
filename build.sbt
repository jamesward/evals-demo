scalaVersion := "3.9.0"

name := "evals-demo"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-evals" % "0.1.2",
  "com.jamesward" % "skills" % "0.0.4",
)

fork := true

// sbt-mcp (loopback-only: its tools can execute build tasks)
Global / mcpEnabled := true
Global / mcpHost := "127.0.0.1"
Global / mcpPort := 5101

// SkillsJars: extract agent Skills with `./sbt extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.10" % Skills
