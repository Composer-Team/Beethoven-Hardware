
val chiselVersion = "3.5.6"

ThisBuild / scalaVersion := "2.13.10"

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

lazy val beethoven =(project in file(".")).settings(
  name := "beethoven-hardware",
  version := "0.0.46",
  organization := "edu.duke.cs.apex",
  libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % chiselVersion,
    "edu.duke.cs.apex" %% "rocketchip-rocketchip-fork" % "0.1.12",
    "org.scalatra.scalate" %% "scalate-core" % "1.9.6",
    "edu.berkeley.cs" %% "chiseltest" % "0.5.2"
  ),
  resolvers += ("reposilite-repository-releases" at "http://54.165.244.214:8080/releases").withAllowInsecureProtocol(true),
  publishTo := Some(("reposilite-repository" at "http://54.165.244.214:8080/releases/").withAllowInsecureProtocol(true)),
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
)
