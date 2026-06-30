// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.google.crashlytics) apply false
}

tasks.register("generateKeystore") {
  doLast {
    val keystoreFile = file("${rootDir}/my-upload-key.jks")
    if (!keystoreFile.exists()) {
      println("Generating release keystore at ${keystoreFile.absolutePath}...")
      val pb = ProcessBuilder(
        "keytool", "-genkeypair",
        "-v",
        "-keystore", keystoreFile.absolutePath,
        "-storetype", "PKCS12",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-alias", "upload",
        "-storepass", "mdfinance123",
        "-keypass", "mdfinance123",
        "-dname", "CN=Muneeswaran, O=MDFinance, C=IN"
      )
      pb.redirectErrorStream(true)
      val process = pb.start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      val exitCode = process.waitFor()
      if (exitCode == 0) {
        println("Keystore generated successfully.")
      } else {
        println("Keystore generation failed.")
        println(output)
      }
    } else {
      println("Keystore already exists.")
    }
  }
}

tasks.register("showKeystoreFingerprints") {
  doLast {
    val keystoreFile = file("${rootDir}/my-upload-key.jks")
    if (keystoreFile.exists()) {
      val pb = ProcessBuilder(
        "keytool", "-list", "-v",
        "-keystore", keystoreFile.absolutePath,
        "-storepass", "mdfinance123"
      )
      pb.redirectErrorStream(true)
      val process = pb.start()
      val output = process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor()
      println("### KEYSTORE FINGERPRINTS & DETAILS ###")
      println(output)
      println("#######################################")
    } else {
      println("Error: Keystore file my-upload-key.jks does not exist!")
    }
  }
}
