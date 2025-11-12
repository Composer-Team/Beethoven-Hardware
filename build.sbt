val chiselVersion = "7.1.0"
val myScalaVersion = "2.13.16"

This / scalaVersion := myScalaVersion
// organization := "edu.duke.cs.apex"
// homepage := Some(url("https://github.com/Composer-Team/Beethoven-Hardware"))
// scmInfo := Some(ScmInfo(url("https://github.com/Composer-Team/Beethoven-Hardware"),
//                             "git@github.com:Composer-Team/Beethoven-Hardware.git"))
// developers := List(Developer("ChrisKjellqvist",
//   "Chris Kjellqvist",
//   "chriskjellqvist@gmail.com",
//   url("https://github.com/ChrisKjellqvist")))
// licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
// publishMavenStyle := true

// // Add sonatype repository settings
// publishTo := Some(
//   if (isSnapshot.value)
//     Opts.resolver.sonatypeSnapshots
//   else
//     Opts.resolver.sonatypeStaging
// )

lazy val beethoven = (project in file(".")).settings(
  name := "beethoven-hardware",
  version := "0.1.4-dev21",
  organization := "edu.duke.cs.apex",
  libraryDependencies ++= Seq(
    "org.chipsalliance" %% "chisel" % chiselVersion,
    "edu.duke.cs.apex" %% "diplomacy" % "0.0.2",
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0"
  ),
  resolvers += ("reposilite-repository-releases" at "http://54.165.244.214:8080/releases")
    .withAllowInsecureProtocol(true),
  publishTo := Some(
    ("reposilite-repository" at "http://54.165.244.214:8080/releases/").withAllowInsecureProtocol(
      true
    )
  ),
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
)
