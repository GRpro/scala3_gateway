val Http4sVersion = "0.23.20"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.4.8"
val MunitCatsEffectVersion = "1.0.7"
val TypesafeConfigVersion = "1.3.0"
val JansiVersion = "2.3.4"

lazy val root = (project in file("."))
  .settings(
    organization := "com.gr",
    name := "gateway",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "3.3.0",
    libraryDependencies ++= Seq(
      "com.typesafe"    % "config"               % TypesafeConfigVersion,
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "org.fusesource.jansi" % "jansi"           % JansiVersion
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
