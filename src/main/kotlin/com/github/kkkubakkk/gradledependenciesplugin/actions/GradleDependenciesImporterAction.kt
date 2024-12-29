package com.github.kkkubakkk.gradledependenciesplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GradleDependenciesImporterAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fileChooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
        val virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null) ?: return

        val dependencies = readDependenciesFromFile(virtualFile)
        if (dependencies.isNotEmpty()) {
            addDependenciesToBuildFile(project, dependencies)
            Messages.showInfoMessage(project, "Dependencies imported successfully.", "Success")
        } else {
            Messages.showErrorDialog(project, "No dependencies found in the file.", "Error")
        }
    }

    private fun readDependenciesFromFile(file: VirtualFile): List<String> {
        val content = String(file.contentsToByteArray())
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(content, type)
    }

    private fun addDependenciesToBuildFile(project: Project, dependencies: List<String>) {
        val buildFile = project.projectFile?.findChild("build.gradle.kts") ?: return    // TODO: Support for java projects (this is only for kotlin projects)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(buildFile) ?: return@runWriteCommandAction
            val newContent = buildString {
                append(document.text)
                append("\n")
                dependencies.forEach { append("implementation(\"$it\")\n") }
            }
            document.setText(newContent)
        }
    }
}