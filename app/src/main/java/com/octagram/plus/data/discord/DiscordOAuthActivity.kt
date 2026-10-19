package com.octagram.plus.data.discord

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class DiscordOAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val pending = pendingAuthorization
        if (uri == null || pending == null) {
            finish()
            return
        }

        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        when {
            error != null -> pending.completeExceptionally(IllegalStateException("Discord authorization was declined"))
            code.isNullOrBlank() -> pending.completeExceptionally(IllegalStateException("Discord did not return an authorization code"))
            else -> pending.complete(AuthorizationCode(code, state.orEmpty()))
        }
        finish()
    }

    data class AuthorizationCode(val code: String, val state: String)

    companion object {
        @Volatile
        private var pendingAuthorization: CompletableDeferred<AuthorizationCode>? = null

        @Synchronized
        fun beginAuthorization(): CompletableDeferred<AuthorizationCode> {
            pendingAuthorization?.takeIf { !it.isCompleted }
                ?.completeExceptionally(CancellationException("A newer Discord authorization was started"))
            return CompletableDeferred<AuthorizationCode>().also { pendingAuthorization = it }
        }

        suspend fun awaitAuthorization(timeoutMs: Long): AuthorizationCode {
            val pending = pendingAuthorization ?: throw CancellationException("No Discord authorization is pending")
            return withTimeout(timeoutMs) { pending.await() }
        }

        fun cancelAuthorization() {
            pendingAuthorization?.takeIf { !it.isCompleted }
                ?.completeExceptionally(CancellationException("Discord authorization cancelled"))
            pendingAuthorization = null
        }
    }
}
