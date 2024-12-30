package com.github.kkkubakkk.gradledependenciesplugin.dataClasses

data class Dependency (
    val configuration: String,
    val group: String,
    val name: String,
    val version: String
)