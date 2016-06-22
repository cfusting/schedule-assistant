name := "bot"

version := "1.0"

lazy val `bot` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

val corenlp = "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0"

//val corenlpmodels = "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models"

val joda = "joda-time" % "joda-time" % "2.9.4"

val h2 = "com.h2database" % "h2" % "1.4.187"

val playslick = "com.typesafe.play" %% "play-slick" % "2.0.0"

val slickev = "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0"

val gocal = "com.google.api-client" % "google-api-client" % "1.19.1"

libraryDependencies ++= Seq(cache , ws  , specs2 % Test,
  corenlp, joda, playslick, slickev, h2, gocal )

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

herokuAppName in Compile := "cryptic-gorge-10562"