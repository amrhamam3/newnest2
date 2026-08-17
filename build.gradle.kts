plugins {
    kotlin("multiplatform") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    kotlin("plugin.compose") version "2.0.0"
}

// 1. تحديد مستودعات تحميل مكتبات المطورين للمشروع
repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("jvm") {
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                // مكتبات Compose Desktop كانت ناقصة هنا وده كان بيمنع البناء بالكامل
                implementation(compose.desktop.currentOs)
                implementation(compose.material)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(compose.ui)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageVersion = "1.0.0"
            description = "Amr3D Nesting Pro Application"

            // بنحدد موديولات الـ JDK صراحةً بدل ما نسيب jlink يكتشفها تلقائي:
            // AWT/Swing/JavaFX الاكتشاف التلقائي بتاعهم بيفشل أحيانًا وبيطلع runtime
            // ناقص موديولات، فالتطبيق بيكراش لحظة الفتح من غير أي رسالة (لأنه GUI بلا console)
            modules(
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.xml",
                "java.naming",
                "java.scripting",
                "java.management",
                "java.instrument",
                "java.net.http",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.crypto.ec"
            )

            windows {
                // بنفتح نافذة console مؤقتًا عشان لو التطبيق كراش نشوف رسالة الخطأ.
                // بعد ما نتأكد إن كل حاجة شغالة نقدر نرجعها false
                console = true
            }
        }
    }
}
