name := "bot"

version := "1.0"

lazy val `bot` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

val corenlp = "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0"

val corenlpmodels = "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models"

val joda = "joda-time" % "joda-time" % "2.9.4"

val post = "org.postgresql" % "postgresql" % "9.4.1208.jre7"

val slickpg = "com.github.tminglei" %% "slick-pg" % "0.14.1"

val jodapg = "com.github.tminglei" %% "slick-pg_joda-time" % "0.14.1"

val playslick = "com.typesafe.play" %% "play-slick" % "2.0.0"

val slickev = "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0"

val google = "com.google.api-client" % "google-api-client" % "1.19.1"

val cal = "com.google.apis" % "google-api-services-calendar" % "v3-rev192-1.22.0"

val guice = "net.codingwell" %% "scala-guice" % "4.0.0"

val silho = "com.mohiva" %% "play-silhouette" % "4.0.0-RC1"

val silper = "com.mohiva" % "play-silhouette-persistence_2.11" % "4.0.0-RC1"

val silcry = "com.mohiva" % "play-silhouette-password-bcrypt_2.11" % "4.0.0-RC1"

val silcrypto = "com.mohiva" % "play-silhouette-crypto-jca_2.11" % "4.0.0-RC1"

val ficus = "net.ceedubs" %% "ficus" % "1.1.2"

libraryDependencies ++= Seq(cache , ws  , specs2 % Test, slickev,
  corenlp, corenlpmodels, joda, playslick, post, google, cal,
  slickpg, jodapg, guice, silho, ficus, silper, silcry, silcrypto)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

resolvers := ("Atlassian Releases" at "https://maven.atlassian.com/public/") +: resolvers.value

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
