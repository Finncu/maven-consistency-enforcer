package dev.silverhorn.fca.maven_consistency_enforcer

import com.intellij.AbstractBundle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.EnforcerBundle"

object EnforcerBundle : AbstractBundle(BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: @NlsSafe Any?) =
        getMessage(key, *params)
}