scalaVersion := "3.10.1"

name := "evals-demo"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-evals" % "0.3.0",
  "com.jamesward" % "skills" % "0.0.24",
)

scalacOptions ++= Seq(
  "-language:strictEquality",
  "-deprecation",
  "-Werror",
)

fork := true

// SkillsJars: extract agent Skills with `./sbta extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.24" % Skills
