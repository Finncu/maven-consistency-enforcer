package dev.silverhorn.fca.maven_consistency_enforcer

object EnforcerConstants {
    const val DATE_FORMAT_PATTERN = "HH:mm:ss"
    const val ATTACHED_JAR_IDENTIFIER = "ATTACHED-JAR"
    const val GAV_PATTERN = "[^ :]+:([^ :]+):[^ :]+.*"
    const val MAVEN_PATTERN = "^Maven: ?[^ :]+:([^ :]+)"
    // System / path constants
    const val SYSTEM_PROP_USER_HOME = "user.home"
    const val DEFAULT_M2_RELATIVE_PATH = ".m2/repository"
}