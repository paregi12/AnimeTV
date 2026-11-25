package com.paregi.animetv

/** Prefixes profile-owned preferences with the same user ID used by the original app. */
class ProfileNamespace {
    @Volatile
    var prefix: String = ""
        private set

    fun activate(prefix: String) {
        this.prefix = prefix
    }

    fun key(name: String): String = prefix + name

    fun storeKey(name: String): String = "viewstorage_" + prefix + name
}
