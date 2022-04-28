version := "1.2.1"

scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "PipeOraToPG"
  )

libraryDependencies ++= {
  val oraVer            = "12.2.0.1"
  val slickVersion      = "3.3.3"
  val logbackVer        = "1.2.3"
  val scalaTestVersion  = "3.1.0"

  Seq(
    "ch.qos.logback"    % "logback-classic"           % logbackVer,
    "ch.qos.logback"    % "logback-core"              % logbackVer,
    "com.typesafe"      % "config"                    % "1.3.4",
    "org.postgresql"    % "postgresql"                % "42.2.23",
    "com.oracle.database.jdbc" % "ojdbc8"             % oraVer,
    "com.typesafe.slick" %% "slick"                   % slickVersion,
    "com.typesafe.slick" %% "slick-hikaricp"          % slickVersion,
    "org.scalatest" 	%% "scalatest" 		      % scalaTestVersion
  )
}
